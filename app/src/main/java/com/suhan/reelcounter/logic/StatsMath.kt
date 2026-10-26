package com.suhan.reelcounter.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

data class HighestDayResult(
    val date: LocalDate,
    val count: Int
)

object StatsMath {

    private val MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val DAY_ONLY_FORMATTER = DateTimeFormatter.ofPattern("d", Locale.US)

    /**
     * Splits a timestamp into the local date string (yyyy-MM-dd) and hour (0..23).
     */
    fun getDateAndHour(zonedDateTime: ZonedDateTime): Pair<String, Int> {
        val dateStr = zonedDateTime.toLocalDate().toString()
        val hour = zonedDateTime.hour
        return Pair(dateStr, hour)
    }

    /**
     * Weeks run Monday to Sunday.
     */
    fun getWeekRange(date: LocalDate): Pair<LocalDate, LocalDate> {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        return Pair(monday, sunday)
    }

    /**
     * Offset 0 is current week ("This week").
     * Past weeks format as "Oct 12 – 18" or "Sep 28 – Oct 4".
     */
    fun formatWeekLabel(offset: Int, monday: LocalDate): String {
        if (offset == 0) {
            return "This week"
        }
        val sunday = monday.plusDays(6)
        return if (monday.month == sunday.month) {
            "${monday.format(MONTH_DAY_FORMATTER)} – ${sunday.format(DAY_ONLY_FORMATTER)}"
        } else {
            "${monday.format(MONTH_DAY_FORMATTER)} – ${sunday.format(MONTH_DAY_FORMATTER)}"
        }
    }

    /**
     * Total of yesterday.
     */
    fun calculateYesterdayTotal(today: LocalDate, dailyTotals: Map<LocalDate, Int>): Int {
        val yesterday = today.minusDays(1)
        return dailyTotals[yesterday] ?: 0
    }

    /**
     * Total of the 7 days before today, divided by 7, rounded to the nearest whole number.
     */
    fun calculateSevenDayAverage(today: LocalDate, dailyTotals: Map<LocalDate, Int>): Int {
        var sum = 0
        for (i in 1..7) {
            val d = today.minusDays(i.toLong())
            sum += (dailyTotals[d] ?: 0)
        }
        return (sum.toDouble() / 7.0).roundToInt()
    }

    /**
     * Week Daily Average:
     * - Past week: week total divided by 7, rounded to nearest whole number.
     * - Current week: week total divided by number of days from Monday to today inclusive (1..7), rounded to nearest whole number.
     * - Empty data returns 0 and never crashes.
     */
    fun calculateWeekDailyAverage(
        weekMonday: LocalDate,
        today: LocalDate,
        isCurrentWeek: Boolean,
        dailyTotals: Map<LocalDate, Int>
    ): Int {
        var total = 0
        for (i in 0..6) {
            val d = weekMonday.plusDays(i.toLong())
            total += (dailyTotals[d] ?: 0)
        }

        if (total == 0) return 0

        val daysCount = if (isCurrentWeek) {
            // Days from Monday to today inclusive (Monday = 1, Wednesday = 3, etc.)
            today.dayOfWeek.value.coerceIn(1, 7)
        } else {
            7
        }

        return (total.toDouble() / daysCount.toDouble()).roundToInt()
    }

    /**
     * Highest day: largest single-day total in that week.
     * Evaluated Monday through Sunday in order; ties pick the earlier day.
     */
    fun calculateHighestDay(
        weekMonday: LocalDate,
        dailyTotals: Map<LocalDate, Int>
    ): HighestDayResult {
        var maxCount = 0
        var bestDate = weekMonday

        for (i in 0..6) {
            val d = weekMonday.plusDays(i.toLong())
            val count = dailyTotals[d] ?: 0
            if (count > maxCount) {
                maxCount = count
                bestDate = d
            }
        }

        return HighestDayResult(bestDate, maxCount)
    }

    /**
     * Left chevron rule: enabled only if at least one row exists with a date before that week's Monday.
     */
    fun canNavigateLeft(weekMonday: LocalDate, earliestDataDate: LocalDate?): Boolean {
        if (earliestDataDate == null) return false
        return earliestDataDate.isBefore(weekMonday)
    }

    /**
     * Calculates the longest consecutive streak of active days (reels > 0) within [year].
     */
    fun calculateMaxStreak(
        dailyTotals: Map<LocalDate, Int>,
        year: Int,
        today: LocalDate = LocalDate.now()
    ): Int {
        val startDay = LocalDate.of(year, 1, 1)
        val endDay = if (year == today.year) today else LocalDate.of(year, 12, 31)
        if (startDay.isAfter(endDay)) return 0

        var currentStreak = 0
        var maxStreak = 0
        var d = startDay
        while (!d.isAfter(endDay)) {
            val count = dailyTotals[d] ?: 0
            if (count > 0) {
                currentStreak++
                if (currentStreak > maxStreak) {
                    maxStreak = currentStreak
                }
            } else {
                currentStreak = 0
            }
            d = d.plusDays(1)
        }
        return maxStreak
    }
}
