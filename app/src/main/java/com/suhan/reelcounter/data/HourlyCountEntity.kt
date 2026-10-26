package com.suhan.reelcounter.data

import androidx.room.Entity

@Entity(
    tableName = "hourly_count",
    primaryKeys = ["date", "hour"]
)
data class HourlyCountEntity(
    val date: String, // Local date, yyyy-MM-dd
    val hour: Int,    // 0 to 23, local time
    val count: Int    // Reels counted in that hour
)
