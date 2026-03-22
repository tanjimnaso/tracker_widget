package com.example.weighttrackerwidget

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = 0,
    val startingWeightKg: Double = 80.25,
    val goalWeightKg: Double = 72.0,
    val startDateMillis: Long = Calendar.getInstance().apply {
        set(2023, Calendar.MARCH, 23, 0, 0)
    }.timeInMillis,
    val goalDateMillis: Long = Calendar.getInstance().apply {
        set(2023, Calendar.JUNE, 23, 0, 0)
    }.timeInMillis,
    val birthDateMillis: Long = Calendar.getInstance().apply {
        set(1991, Calendar.JANUARY, 1, 0, 0) // Defaulting to 1991 for age 32 in 2023
    }.timeInMillis
)
