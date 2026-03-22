package com.example.weighttrackerwidget

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val weightKg: Double,
    val dateMillis: Long = System.currentTimeMillis()
)
