package com.suhan.reelcounter.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class ReelProgressTest {

    @Test
    fun testMoves_1_1_1_equals_3() {
        val progress = ReelProgress()
        var total = 0
        total += progress.onSettledMove(1)
        total += progress.onSettledMove(1)
        total += progress.onSettledMove(1)
        assertEquals(3, total)
    }

    @Test
    fun testMoves_3_then_minus2_then_2_equals_3() {
        val progress = ReelProgress()
        var total = 0
        total += progress.onSettledMove(3)
        total += progress.onSettledMove(-2)
        total += progress.onSettledMove(2)
        assertEquals(3, total)
    }

    @Test
    fun testMoves_3_minus2_2_then_1_equals_4() {
        val progress = ReelProgress()
        var total = 0
        total += progress.onSettledMove(3)
        total += progress.onSettledMove(-2)
        total += progress.onSettledMove(2)
        total += progress.onSettledMove(1)
        assertEquals(4, total)
    }

    @Test
    fun testMoves_1_minus1_1_equals_1() {
        val progress = ReelProgress()
        var total = 0
        total += progress.onSettledMove(1)
        total += progress.onSettledMove(-1)
        total += progress.onSettledMove(1)
        assertEquals(1, total)
    }

    @Test
    fun testMoves_2_then_minus2_then_2_equals_2() {
        val progress = ReelProgress()
        var total = 0
        total += progress.onSettledMove(2)
        total += progress.onSettledMove(-2)
        total += progress.onSettledMove(2)
        assertEquals(2, total)
    }

    @Test
    fun testMoves_minus1_then_1_equals_0() {
        val progress = ReelProgress()
        var total = 0
        total += progress.onSettledMove(-1)
        total += progress.onSettledMove(1)
        assertEquals(0, total)
    }

    @Test
    fun testMoves_minus1_1_1_equals_1() {
        val progress = ReelProgress()
        var total = 0
        total += progress.onSettledMove(-1)
        total += progress.onSettledMove(1)
        total += progress.onSettledMove(1)
        assertEquals(1, total)
    }

    @Test
    fun testMoves_1_reset_1_equals_2() {
        val progress = ReelProgress()
        var total = 0
        total += progress.onSettledMove(1)
        progress.reset()
        total += progress.onSettledMove(1)
        assertEquals(2, total)
    }
}
