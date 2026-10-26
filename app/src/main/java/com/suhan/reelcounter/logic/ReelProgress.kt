package com.suhan.reelcounter.logic

class ReelProgress {
    private var current = 0
    private var furthest = 0

    fun reset() {
        current = 0
        furthest = 0
    }

    // pages: +1 per page forward, -1 per page back, for one settled move.
    // Returns how many NEW reels to add to the count.
    fun onSettledMove(pages: Int): Int {
        current += pages.coerceIn(-5, 5)
        if (current > furthest) {
            val added = current - furthest
            furthest = current
            return added
        }
        return 0
    }
}
