package com.suhan.reelcounter.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class ReelProgressTest {

    @Test
    fun testMoves_0_to_1_to_2_to_3_equals_3() {
        val progress = ReelProgress()
        var total = 0
        total += progress.onSettledMove(1)
        total += progress.onSettledMove(1)
        total += progress.onSettledMove(1)
        assertEquals(3, total)
    }

    @Test
    fun testMoves_0_to_1_to_2_to_3_to_2_to_3_equals_3() {
        val progress = ReelProgress()
        var total = 0
        total += progress.onSettledMove(1)
        total += progress.onSettledMove(1)
        total += progress.onSettledMove(1)
        total += progress.onSettledMove(-1)
        total += progress.onSettledMove(1)
        assertEquals(3, total)
    }

    @Test
    fun testMoves_0_to_1_to_2_to_3_to_2_to_1_to_2_to_3_to_4_equals_4() {
        val progress = ReelProgress()
        var total = 0
        total += progress.onSettledMove(1)
        total += progress.onSettledMove(1)
        total += progress.onSettledMove(1)
        total += progress.onSettledMove(-1)
        total += progress.onSettledMove(-1)
        total += progress.onSettledMove(1)
        total += progress.onSettledMove(1)
        total += progress.onSettledMove(1)
        assertEquals(4, total)
    }

    @Test
    fun testFling_0_to_5_equals_1() {
        val progress = ReelProgress()
        var total = 0
        total += progress.onSettledMove(5)
        assertEquals(1, total)
    }

    @Test
    fun testReset_then_0_to_1_equals_1() {
        val progress = ReelProgress()
        var total = 0
        total += progress.onSettledMove(1)
        progress.reset()
        total += progress.onSettledMove(1)
        assertEquals(2, total)
    }

    @Test
    fun testJumpLargerThan15_isIgnored() {
        val progress = ReelProgress()
        var total = 0
        total += progress.onSettledMove(16)
        assertEquals(0, total)
        assertEquals(0, progress.current)
        assertEquals(0, progress.furthest)
    }
}
