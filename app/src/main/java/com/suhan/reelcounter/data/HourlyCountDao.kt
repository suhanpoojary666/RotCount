package com.suhan.reelcounter.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface HourlyCountDao {

    @Query("SELECT * FROM hourly_count WHERE date = :date AND hour = :hour LIMIT 1")
    suspend fun getHourly(date: String, hour: Int): HourlyCountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: HourlyCountEntity)

    @Transaction
    suspend fun upsertAdd(date: String, hour: Int, amountToAdd: Int) {
        val existing = getHourly(date, hour)
        val currentCount = existing?.count ?: 0
        insertOrReplace(HourlyCountEntity(date, hour, currentCount + amountToAdd))
    }

    @Query("SELECT SUM(count) FROM hourly_count WHERE date = :date")
    suspend fun getDayTotal(date: String): Int?

    @Query("SELECT * FROM hourly_count WHERE date = :date ORDER BY hour ASC")
    suspend fun getDayHours(date: String): List<HourlyCountEntity>

    @Query("SELECT * FROM hourly_count WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC, hour ASC")
    suspend fun getRange(startDate: String, endDate: String): List<HourlyCountEntity>

    @Query("SELECT MIN(date) FROM hourly_count")
    suspend fun getEarliestDate(): String?
}
