package com.suhan.reelcounter.service

data class ReelAppSignature(
    val packageName: String,
    val pagerClassNames: Set<String>,
    val viewIdKeywords: List<String>
)

object ReelApps {
    val INSTAGRAM = ReelAppSignature(
        packageName = "com.instagram.android",
        pagerClassNames = setOf(
            "androidx.viewpager2.widget.ViewPager2",
            "com.instagram.common.ui.widget.reboundviewpager.ReboundViewPager",
            "androidx.recyclerview.widget.RecyclerView",
            "ViewPager2",
            "RecyclerView",
            "ViewPager"
        ),
        viewIdKeywords = listOf("clips", "reel", "video", "feed")
    )

    val YOUTUBE = ReelAppSignature(
        packageName = "com.google.android.youtube",
        pagerClassNames = setOf(
            "androidx.viewpager2.widget.ViewPager2",
            "androidx.recyclerview.widget.RecyclerView",
            "ViewPager2",
            "RecyclerView"
        ),
        viewIdKeywords = listOf("reel", "shorts", "carousel")
    )

    fun getSignature(packageName: String): ReelAppSignature? {
        return when (packageName) {
            INSTAGRAM.packageName -> INSTAGRAM
            YOUTUBE.packageName -> YOUTUBE
            else -> null
        }
    }
}
