package com.example.weighttrackerwidget

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(weightEntry: WeightEntry)

    @Delete
    suspend fun delete(weightEntry: WeightEntry)

    @Query("SELECT * FROM weight_entries ORDER BY dateMillis ASC")
    fun getAllEntriesSortedByDate(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_entries ORDER BY dateMillis DESC LIMIT 1")
    suspend fun getLatestEntry(): WeightEntry?

    @Query("SELECT * FROM user_settings WHERE id = 0")
    fun getUserSettings(): Flow<UserSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserSettings(settings: UserSettings)
}
