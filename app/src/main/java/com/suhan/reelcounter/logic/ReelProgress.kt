package com.suhan.reelcounter.logic

class ReelProgress {
    var current: Int = 0
        private set
    var furthest: Int = 0
        private set

    fun reset() {
        current = 0
        furthest = 0
    }

    /**
     * pages: +N per page forward, -N per page back, for one settled move.
     * Returns 1 if a new furthest reel was reached, 0 otherwise.
     * Fast flings (e.g. +5) count as 1.
     * Jumps larger than 15 are ignored (return 0).
     */
    fun onSettledMove(pages: Int): Int {
        if (Math.abs(pages) > 15) {
            return 0
        }
        current = (current + pages).coerceAtLeast(0)
        if (current > furthest) {
            furthest = current
            return 1
        }
        return 0
    }
}
