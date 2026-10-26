package com.suhan.reelcounter.data

import com.suhan.reelcounter.logic.StatsMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

class CountStore(private val dao: HourlyCountDao) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val saveMutex = Mutex()

    // Thread-safe map of (date, hour) -> pending count
    private val pendingCounts = ConcurrentHashMap<Pair<String, Int>, Int>()

    private val _todayCount = MutableStateFlow(0)
    val todayCount: StateFlow<Int> = _todayCount.asStateFlow()

    @Volatile
    private var lastObservedDateStr: String = LocalDate.now().toString()

    init {
        scope.launch {
            refreshTodayFromDb()
        }
    }

    /**
     * Compares lastObservedDateStr with LocalDate.now(); if different, updates it,
     * saves pending counts, and refreshes todayCount.
     */
    fun ensureToday() {
        val todayStr = LocalDate.now().toString()
        if (todayStr != lastObservedDateStr) {
            lastObservedDateStr = todayStr
            _todayCount.value = 0
            scope.launch {
                savePendingCounts()
                refreshTodayFromDb()
            }
        }
    }

    /**
     * Records a new count immediately against the date and hour of this moment.
     */
    fun recordCount(amount: Int) {
        if (amount <= 0) return

        ensureToday()

        val now = ZonedDateTime.now()
        val (dateStr, hour) = StatsMath.getDateAndHour(now)

        pendingCounts.compute(Pair(dateStr, hour)) { _, current -> (current ?: 0) + amount }

        if (dateStr == LocalDate.now().toString()) {
            _todayCount.update { it + amount }
        }
    }

    /**
     * Saves pending counts to Room on a background thread.
     */
    suspend fun savePendingCounts() = saveMutex.withLock {
        if (pendingCounts.isEmpty()) return@withLock

        val entries = ArrayList(pendingCounts.entries)
        for (entry in entries) {
            val key = entry.key
            val count = entry.value
            if (count > 0) {
                dao.upsertAdd(key.first, key.second, count)
                pendingCounts.compute(key) { _, current ->
                    val rem = (current ?: 0) - count
                    if (rem <= 0) null else rem
                }
            }
        }
    }

    /**
     * Refreshes today's count from Room + pending.
     */
    suspend fun refreshTodayFromDb() = saveMutex.withLock {
        val todayStr = LocalDate.now().toString()
        val dbTotal = dao.getDayTotal(todayStr) ?: 0
        var pendingToday = 0
        for ((key, count) in pendingCounts) {
            if (key.first == todayStr) {
                pendingToday += count
            }
        }
        _todayCount.value = dbTotal + pendingToday
    }

    /**
     * Loads 24 hourly values for [date], merging persisted and pending counts.
     */
    suspend fun getDayHourly(date: LocalDate): List<Int> = saveMutex.withLock {
        val dateStr = date.toString()
        val persisted = dao.getDayHours(dateStr).associateBy({ it.hour }, { it.count })
        val result = ArrayList<Int>(24)

        for (h in 0..23) {
            val dbCount = persisted[h] ?: 0
            val pending = pendingCounts[Pair(dateStr, h)] ?: 0
            result.add(dbCount + pending)
        }
        return@withLock result
    }

    /**
     * Loads daily totals between [startDate] and [endDate], merging persisted and pending counts.
     */
    suspend fun getDailyTotals(startDate: LocalDate, endDate: LocalDate): Map<LocalDate, Int> = saveMutex.withLock {
        val startStr = startDate.toString()
        val endStr = endDate.toString()
        val range = dao.getRange(startStr, endStr)

        val map = HashMap<LocalDate, Int>()
        for (item in range) {
            val d = LocalDate.parse(item.date)
            map[d] = (map[d] ?: 0) + item.count
        }

        for ((key, count) in pendingCounts) {
            val d = LocalDate.parse(key.first)
            if (!d.isBefore(startDate) && !d.isAfter(endDate)) {
                map[d] = (map[d] ?: 0) + count
            }
        }

        return@withLock map
    }

    suspend fun getEarliestDate(): LocalDate? = saveMutex.withLock {
        val earliestStr = dao.getEarliestDate()
        return@withLock earliestStr?.let { LocalDate.parse(it) }
    }
}
