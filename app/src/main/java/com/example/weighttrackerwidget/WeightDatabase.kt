package com.example.weighttrackerwidget

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [WeightEntry::class, UserSettings::class], version = 3, exportSchema = false)
abstract class WeightDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao

    companion object {
        @Volatile
        private var Instance: WeightDatabase? = null

        fun getDatabase(context: Context): WeightDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, WeightDatabase::class.java, "weight_database")
                    .fallbackToDestructiveMigration() 
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
