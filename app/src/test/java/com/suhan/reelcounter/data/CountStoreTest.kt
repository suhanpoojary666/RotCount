package com.suhan.reelcounter.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CountStoreTest {

    private class FakeHourlyCountDao : HourlyCountDao {
        val storage = mutableMapOf<Pair<String, Int>, Int>()
        var shouldThrowOnUpsert = false

        override suspend fun getHourly(date: String, hour: Int): HourlyCountEntity? {
            val count = storage[Pair(date, hour)] ?: return null
            return HourlyCountEntity(date, hour, count)
        }

        override suspend fun insertOrReplace(entity: HourlyCountEntity) {
            storage[Pair(entity.date, entity.hour)] = entity.count
        }

        override suspend fun upsertAdd(date: String, hour: Int, amountToAdd: Int) {
            if (shouldThrowOnUpsert) {
                throw IOException("Simulated disk error")
            }
            val current = storage[Pair(date, hour)] ?: 0
            storage[Pair(date, hour)] = current + amountToAdd
        }

        override suspend fun getDayTotal(date: String): Int? {
            val sum = storage.filterKeys { it.first == date }.values.sum()
            return if (sum == 0) null else sum
        }

        override suspend fun getDayHours(date: String): List<HourlyCountEntity> {
            return storage.filterKeys { it.first == date }
                .map { HourlyCountEntity(it.key.first, it.key.second, it.value) }
                .sortedBy { it.hour }
        }

        override suspend fun getRange(startDate: String, endDate: String): List<HourlyCountEntity> {
            return storage.filterKeys { it.first in startDate..endDate }
                .map { HourlyCountEntity(it.key.first, it.key.second, it.value) }
                .sortedWith(compareBy({ it.date }, { it.hour }))
        }

        override suspend fun getEarliestDate(): String? {
            return storage.keys.minOfOrNull { it.first }
        }
    }

    private class MutableClock(
        private var currentInstant: Instant,
        private val zoneId: ZoneId = ZoneId.of("UTC")
    ) : Clock() {
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)
        override fun instant(): Instant = currentInstant

        fun advanceSeconds(seconds: Long) {
            currentInstant = currentInstant.plusSeconds(seconds)
        }
    }

    @Test
    fun testCountAccumulationAndTodayFlow() {
        val dao = FakeHourlyCountDao()
        val initialTime = ZonedDateTime.of(2026, 10, 26, 14, 0, 0, 0, ZoneId.of("UTC")).toInstant()
        val clock = MutableClock(initialTime)
        val store = CountStore(dao, clock)

        assertEquals(0, store.todayCount.value)
        store.recordCount(1)
        assertEquals(1, store.todayCount.value)
        store.recordCount(3)
        assertEquals(4, store.todayCount.value)
    }

    @Test
    fun testSavePendingCountsFlushesToDao() = runBlocking {
        val dao = FakeHourlyCountDao()
        val initialTime = ZonedDateTime.of(2026, 10, 26, 14, 0, 0, 0, ZoneId.of("UTC")).toInstant()
        val clock = MutableClock(initialTime)
        val store = CountStore(dao, clock)

        store.recordCount(5)
        store.savePendingCounts()

        assertEquals(5, dao.storage[Pair("2026-10-26", 14)])
    }

    @Test
    fun testFailedFlushRetainsPendingCounts() = runBlocking {
        val dao = FakeHourlyCountDao()
        val initialTime = ZonedDateTime.of(2026, 10, 26, 14, 0, 0, 0, ZoneId.of("UTC")).toInstant()
        val clock = MutableClock(initialTime)
        val store = CountStore(dao, clock)

        store.recordCount(5)
        dao.shouldThrowOnUpsert = true

        // Should not crash the process or clear pending count
        store.savePendingCounts()
        assertEquals(null, dao.storage[Pair("2026-10-26", 14)])

        // Recovery on next attempt
        dao.shouldThrowOnUpsert = false
        store.savePendingCounts()
        assertEquals(5, dao.storage[Pair("2026-10-26", 14)])
    }

    @Test
    fun testMidnightRollover() = runBlocking {
        val dao = FakeHourlyCountDao()
        // 23:59:50 UTC
        val beforeMidnight = ZonedDateTime.of(2026, 10, 26, 23, 59, 50, 0, ZoneId.of("UTC")).toInstant()
        val clock = MutableClock(beforeMidnight)
        val store = CountStore(dao, clock)

        store.recordCount(10)
        assertEquals(10, store.todayCount.value)

        // Advance 20 seconds past midnight -> 2026-10-27 00:00:10
        clock.advanceSeconds(20)

        // Ensure current day resets today count
        store.ensureCurrentDay()
        assertEquals(0, store.todayCount.value)

        // Count on the new day increments from 0
        store.recordCount(1)
        assertEquals(1, store.todayCount.value)
    }

    @Test
    fun testGetDayHourlyAndDailyTotalsMergePending() = runBlocking {
        val dao = FakeHourlyCountDao()
        val initialTime = ZonedDateTime.of(2026, 10, 26, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant()
        val clock = MutableClock(initialTime)
        val store = CountStore(dao, clock)

        // Persisted count in DAO
        dao.storage[Pair("2026-10-26", 10)] = 7

        // Pending count in memory for same hour
        store.recordCount(3)

        val hourly = store.getDayHourly(LocalDate.parse("2026-10-26"))
        assertEquals(10, hourly[10])

        val totals = store.getDailyTotals(LocalDate.parse("2026-10-26"), LocalDate.parse("2026-10-26"))
        assertEquals(10, totals[LocalDate.parse("2026-10-26")])
    }
}
