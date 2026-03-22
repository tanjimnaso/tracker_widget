package com.example.weighttrackerwidget

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = 0,
    val startingWeightKg: Double = 80.25,
    val startDateMillis: Long = Calendar.getInstance().apply {
        set(2023, Calendar.MARCH, 23, 0, 0)
    }.timeInMillis,
    val goalDateMillis: Long = Calendar.getInstance().apply {
        set(2026, Calendar.JUNE, 23, 0, 0)
    }.timeInMillis,
    val birthDateMillis: Long = Calendar.getInstance().apply {
        set(1993, Calendar.AUGUST, 1, 0, 0)
    }.timeInMillis,
    val heightCm: Double = 175.0
)
