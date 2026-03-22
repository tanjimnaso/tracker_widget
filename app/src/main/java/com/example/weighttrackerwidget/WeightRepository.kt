package com.example.weighttrackerwidget

import kotlinx.coroutines.flow.Flow

class WeightRepository(private val weightDao: WeightDao) {
    val allEntries: Flow<List<WeightEntry>> = weightDao.getAllEntriesSortedByDate()
    val userSettings: Flow<UserSettings?> = weightDao.getUserSettings()

    suspend fun insert(weightEntry: WeightEntry) {
        weightDao.insert(weightEntry)
    }

    suspend fun delete(weightEntry: WeightEntry) {
        weightDao.delete(weightEntry)
    }

    suspend fun getLatestEntry(): WeightEntry? {
        return weightDao.getLatestEntry()
    }

    suspend fun updateUserSettings(settings: UserSettings) {
        weightDao.insertUserSettings(settings)
    }
}
