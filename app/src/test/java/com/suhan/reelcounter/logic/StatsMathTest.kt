package com.suhan.reelcounter.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class StatsMathTest {

    @Test
    fun testCountAt2359And0001LandOnDifferentDates() {
        val zone = ZoneId.of("Asia/Kolkata")
        val nightTime = ZonedDateTime.of(2026, 10, 24, 23, 59, 0, 0, zone)
        val morningTime = ZonedDateTime.of(2026, 10, 25, 0, 1, 0, 0, zone)

        val (date1, hour1) = StatsMath.getDateAndHour(nightTime)
        val (date2, hour2) = StatsMath.getDateAndHour(morningTime)

        assertEquals("2026-10-24", date1)
        assertEquals(23, hour1)

        assertEquals("2026-10-25", date2)
        assertEquals(0, hour2)

        assertNotEquals(date1, date2)
    }

    @Test
    fun testWeekRangeForWednesdaySundayMondayAreAllSameWeek() {
        // Wednesday: Oct 14, 2026
        // Monday of that week: Oct 12, 2026
        // Sunday of that week: Oct 18, 2026
        val wednesday = LocalDate.of(2026, 10, 14)
        val sunday = LocalDate.of(2026, 10, 18)
        val monday = LocalDate.of(2026, 10, 12)

        val expectedMonday = LocalDate.of(2026, 10, 12)
        val expectedSunday = LocalDate.of(2026, 10, 18)

        val rangeWed = StatsMath.getWeekRange(wednesday)
        val rangeSun = StatsMath.getWeekRange(sunday)
        val rangeMon = StatsMath.getWeekRange(monday)

        assertEquals(Pair(expectedMonday, expectedSunday), rangeWed)
        assertEquals(Pair(expectedMonday, expectedSunday), rangeSun)
        assertEquals(Pair(expectedMonday, expectedSunday), rangeMon)
    }

    @Test
    fun testCurrentWeekDailyAverageOnWednesdayDividesBy3() {
        // Wednesday Oct 14, 2026 (Mon=10, Tue=20, Wed=30 => sum = 60; 60 / 3 = 20)
        val monday = LocalDate.of(2026, 10, 12)
        val wednesday = LocalDate.of(2026, 10, 14)

        val dailyTotals = mapOf(
            monday to 10,
            monday.plusDays(1) to 20,
            wednesday to 30
        )

        val average = StatsMath.calculateWeekDailyAverage(
            weekMonday = monday,
            today = wednesday,
            isCurrentWeek = true,
            dailyTotals = dailyTotals
        )

        assertEquals(20, average)
    }

    @Test
    fun testPastWeekDividesBy7() {
        // Mon..Sun sum = 70 => 70 / 7 = 10
        val monday = LocalDate.of(2026, 10, 5)
        val dailyTotals = mapOf(
            monday to 10,
            monday.plusDays(1) to 10,
            monday.plusDays(2) to 10,
            monday.plusDays(3) to 10,
            monday.plusDays(4) to 10,
            monday.plusDays(5) to 10,
            monday.plusDays(6) to 10
        )

        val average = StatsMath.calculateWeekDailyAverage(
            weekMonday = monday,
            today = LocalDate.of(2026, 10, 25),
            isCurrentWeek = false,
            dailyTotals = dailyTotals
        )

        assertEquals(10, average)
    }

    @Test
    fun testWeekWithNoDataShows0EverywhereAndDoesNotCrash() {
        val monday = LocalDate.of(2026, 10, 5)
        val emptyTotals = emptyMap<LocalDate, Int>()

        val averagePast = StatsMath.calculateWeekDailyAverage(
            weekMonday = monday,
            today = LocalDate.of(2026, 10, 25),
            isCurrentWeek = false,
            dailyTotals = emptyTotals
        )
        val averageCurrent = StatsMath.calculateWeekDailyAverage(
            weekMonday = monday,
            today = monday.plusDays(2), // Wednesday
            isCurrentWeek = true,
            dailyTotals = emptyTotals
        )
        val highestDay = StatsMath.calculateHighestDay(monday, emptyTotals)
        val sevenDayAvg = StatsMath.calculateSevenDayAverage(monday, emptyTotals)
        val yesterday = StatsMath.calculateYesterdayTotal(monday, emptyTotals)

        assertEquals(0, averagePast)
        assertEquals(0, averageCurrent)
        assertEquals(0, highestDay.count)
        assertEquals(monday, highestDay.date)
        assertEquals(0, sevenDayAvg)
        assertEquals(0, yesterday)
    }

    @Test
    fun testHighestDayPicksRightDayAndTiesPickEarlierOne() {
        val monday = LocalDate.of(2026, 10, 12)
        val tuesday = monday.plusDays(1)
        val wednesday = monday.plusDays(2)
        val thursday = monday.plusDays(3)

        // Tuesday has 50, Thursday has 50. Earlier day (Tuesday) should win the tie!
        val dailyTotals = mapOf(
            monday to 10,
            tuesday to 50,
            wednesday to 30,
            thursday to 50
        )

        val result = StatsMath.calculateHighestDay(monday, dailyTotals)
        assertEquals(50, result.count)
        assertEquals(tuesday, result.date)
    }

    @Test
    fun testLeftChevronRule() {
        val monday = LocalDate.of(2026, 10, 12)

        // No earlier data
        assertFalse(StatsMath.canNavigateLeft(monday, null))
        assertFalse(StatsMath.canNavigateLeft(monday, monday))
        assertFalse(StatsMath.canNavigateLeft(monday, monday.plusDays(2)))

        // Has earlier data
        assertTrue(StatsMath.canNavigateLeft(monday, monday.minusDays(1)))
        assertTrue(StatsMath.canNavigateLeft(monday, LocalDate.of(2026, 9, 20)))
    }

    @Test
    fun testWeekLabelFormatting() {
        val mondaySameMonth = LocalDate.of(2026, 10, 12)
        val mondayCrossMonth = LocalDate.of(2026, 9, 28)

        assertEquals("This week", StatsMath.formatWeekLabel(0, mondaySameMonth))
        assertEquals("Oct 12 – 18", StatsMath.formatWeekLabel(-1, mondaySameMonth))
        assertEquals("Sep 28 – Oct 4", StatsMath.formatWeekLabel(-2, mondayCrossMonth))
    }

    @Test
    fun testCalculateMaxStreak() {
        val today = LocalDate.of(2026, 10, 25)
        val dailyMap = mapOf(
            LocalDate.of(2026, 10, 20) to 5,
            LocalDate.of(2026, 10, 21) to 12,
            LocalDate.of(2026, 10, 22) to 3,
            LocalDate.of(2026, 10, 23) to 0,
            LocalDate.of(2026, 10, 24) to 8,
            LocalDate.of(2026, 10, 25) to 14
        )

        // 20, 21, 22 is 3 days. 24, 25 is 2 days. Max streak = 3.
        val streak = StatsMath.calculateMaxStreak(dailyMap, 2026, today)
        assertEquals(3, streak)

        // Empty data has streak 0
        assertEquals(0, StatsMath.calculateMaxStreak(emptyMap(), 2026, today))
    }
}
