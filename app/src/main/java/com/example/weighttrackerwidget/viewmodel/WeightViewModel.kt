package com.example.weighttrackerwidget.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.glance.appwidget.updateAll
import com.example.weighttrackerwidget.WeightEntry
import com.example.weighttrackerwidget.WeightRepository
import com.example.weighttrackerwidget.UserSettings
import com.example.weighttrackerwidget.widget.WeightTrackerGlanceWidget
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import kotlin.math.abs

data class WeightState(
    val startingWeightKg: Double = 80.25,
    val goalWeightKg: Double = 72.0,
    val startDateMillis: Long = System.currentTimeMillis(),
    val goalDateMillis: Long = Calendar.getInstance().apply { add(Calendar.MONTH, 3) }.timeInMillis,
    val birthDateMillis: Long = Calendar.getInstance().apply { set(1991, 0, 1) }.timeInMillis,
    val currentWeightKg: Double? = null,
    val emaWeightKg: Double? = null,
    val totalLostKg: Double = 0.0,
    val progressPercentage: Float = 0f,
    val chartData: List<ChartPoint> = emptyList(),
    val lifelongData: LifelongData = LifelongData(),
    val allEntries: List<WeightEntry> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    
    val thisWeekProjection: Double = 0.0,
    val thisWeekChange: Double = 0.0,
    val monthEndProjection: Double = 0.0,
    val monthEndChange: Double = 0.0,
    val halfwayWeight: Double = 0.0,
    val halfwayDays: Int = 0
)

data class ChartPoint(
    val date: Date,
    val rawWeight: Double?,
    val emaWeight: Double
)

data class LifelongData(
    val currentAge: Int = 32,
    val trainedPath: List<LifelongPoint> = emptyList(),
    val untrainedPath: List<LifelongPoint> = emptyList(),
    val zones: LifelongZones = LifelongZones(),
    val minWeight: Double = 50.0,
    val maxWeight: Double = 100.0
)

data class LifelongPoint(val age: Double, val weight: Double)

data class LifelongZones(
    val underweight: (Double) -> Double = { 64.0 },
    val excellent: (Double) -> Double = { 72.0 },
    val acceptable: (Double) -> Double = { 80.0 }
)

class WeightViewModel(
    application: Application,
    private val repository: WeightRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(WeightState())
    val state: StateFlow<WeightState> = _state.asStateFlow()

    companion object {
        private const val EMA_ALPHA = 0.25f
    }

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(repository.allEntries, repository.userSettings) { entries, settings ->
                Pair(entries, settings)
            }.collect { (entries, settings) ->
                val currentSettings = settings ?: UserSettings()
                
                _state.update { 
                    it.copy(
                        isLoading = true,
                        startingWeightKg = currentSettings.startingWeightKg,
                        goalWeightKg = currentSettings.goalWeightKg,
                        startDateMillis = currentSettings.startDateMillis,
                        goalDateMillis = currentSettings.goalDateMillis,
                        birthDateMillis = currentSettings.birthDateMillis
                    ) 
                }
                
                try {
                    val sortedEntries = entries.sortedBy { it.dateMillis }
                    
                    val now = System.currentTimeMillis()
                    val oneYearAgo = now - 365L * 24 * 60 * 60 * 1000
                    val currentEntries = sortedEntries.filter { it.dateMillis >= oneYearAgo }
                    
                    val chartPoints = calculateEma(currentEntries)
                    
                    val latestEntry = currentEntries.lastOrNull()
                    val latestEma = chartPoints.lastOrNull()?.emaWeight ?: latestEntry?.weightKg ?: currentSettings.startingWeightKg
                    val progress = calculateProgress(latestEma, currentSettings.startingWeightKg, currentSettings.goalWeightKg)
                    
                    val currentW = latestEntry?.weightKg ?: currentSettings.startingWeightKg
                    val totalLost = currentSettings.startingWeightKg - currentW
                    
                    val birthCal = Calendar.getInstance().apply { timeInMillis = currentSettings.birthDateMillis }
                    val nowCal = Calendar.getInstance()
                    var age = nowCal.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
                    if (nowCal.get(Calendar.DAY_OF_YEAR) < birthCal.get(Calendar.DAY_OF_YEAR)) age--

                    val lifelong = generateLifelongData(age, latestEma, sortedEntries)

                    _state.update {
                        it.copy(
                            currentWeightKg = latestEntry?.weightKg,
                            emaWeightKg = latestEma,
                            totalLostKg = totalLost,
                            progressPercentage = progress,
                            chartData = chartPoints,
                            lifelongData = lifelong,
                            allEntries = sortedEntries.reversed(),
                            isLoading = false,
                            errorMessage = if (entries.isEmpty()) "No weight entries found. Please add an entry." else null,
                            
                            thisWeekProjection = latestEma - 0.6,
                            thisWeekChange = -0.6,
                            monthEndProjection = latestEma - 1.7,
                            monthEndChange = -1.7,
                            halfwayWeight = (currentSettings.startingWeightKg + currentSettings.goalWeightKg) / 2,
                            halfwayDays = 50 
                        )
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Error processing weight data: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    private fun calculateEma(entries: List<WeightEntry>): List<ChartPoint> {
        val chartPoints = mutableListOf<ChartPoint>()
        var currentEma: Double? = null

        val recentEntries = entries.filter { it.dateMillis >= state.value.startDateMillis - 30L * 24 * 60 * 60 * 1000 }

        for (entry in recentEntries) {
            val weight = entry.weightKg
            val newEma = if (currentEma == null) weight else (weight * EMA_ALPHA) + (currentEma * (1.0 - EMA_ALPHA))
            currentEma = newEma
            chartPoints.add(ChartPoint(Date(entry.dateMillis), weight, newEma))
        }
        return chartPoints
    }

    private fun generateLifelongData(currentAge: Int, currentWeight: Double, allEntries: List<WeightEntry>): LifelongData {
        val trained = mutableListOf<LifelongPoint>()
        val untrained = mutableListOf<LifelongPoint>()
        
        val targetExcellent = 72.0
        val birthMillis = state.value.birthDateMillis
        
        // Find historical weight points
        val historicPoints = allEntries.mapNotNull {
            val ageAtEntry = (it.dateMillis - birthMillis) / (365.25 * 24 * 60 * 60 * 1000)
            if (ageAtEntry in 15.0..currentAge.toDouble()) {
                LifelongPoint(ageAtEntry, it.weightKg)
            } else null
        }.sortedBy { it.age }

        for (a in 0..80) {
            val age = a.toDouble()
            val growthWeight = if (age <= 15) {
                3.5 + (age * (62.0 - 3.5) / 15.0)
            } else {
                62.0
            }
            
            if (age < 15) {
                trained.add(LifelongPoint(age, growthWeight))
                untrained.add(LifelongPoint(age, growthWeight))
                continue
            }
            
            if (age <= currentAge) {
                // Find nearest historic points for interpolation
                val prevPoint = historicPoints.lastOrNull { it.age <= age }
                val nextPoint = historicPoints.firstOrNull { it.age > age }

                val w = when {
                    // We have a point right here (within a year)
                    historicPoints.any { abs(it.age - age) < 0.5 } -> {
                        historicPoints.minByOrNull { abs(it.age - age) }!!.weight
                    }
                    // We are between two historic points - interpolate
                    prevPoint != null && nextPoint != null -> {
                        val fraction = (age - prevPoint.age) / (nextPoint.age - prevPoint.age)
                        prevPoint.weight + fraction * (nextPoint.weight - prevPoint.weight)
                    }
                    // We only have a point before this - interpolate from 15 to that point or that point to current
                    prevPoint != null -> {
                         val fraction = (age - prevPoint.age) / (currentAge - prevPoint.age)
                         prevPoint.weight + fraction * (currentWeight - prevPoint.weight)
                    }
                    // We only have a point after this - interpolate from 15 to that point
                    nextPoint != null -> {
                         val fraction = (age - 15.0) / (nextPoint.age - 15.0)
                         62.0 + fraction * (nextPoint.weight - 62.0)
                    }
                    // No historic points, just linear from 15 to current
                    else -> {
                        62.0 + (age - 15.0) * (currentWeight - 62.0) / (currentAge - 15.0)
                    }
                }
                trained.add(LifelongPoint(age, w))
                untrained.add(LifelongPoint(age, w))
            } else {
                // Trained path: reach goal weight around age 40, then slow creep with aging
                val trainedW = if (age < 40) {
                    currentWeight - (currentWeight - targetExcellent) * (age - currentAge) / (40.0 - currentAge)
                } else {
                    targetExcellent + (if (age > 60) (age - 60.0) * 0.1 else 0.0)
                }
                trained.add(LifelongPoint(age, trainedW))

                // Detraining path: partial progress then slow regain (~0.6 kg/year)
                // Reaches ~55% of goal by year 4, then drifts back up
                val yearsSinceNow = age - currentAge
                val partialGoal = currentWeight - (currentWeight - targetExcellent) * 0.55
                val detrained = if (yearsSinceNow <= 4.0) {
                    currentWeight - (currentWeight - partialGoal) * (yearsSinceNow / 4.0)
                } else {
                    partialGoal + (yearsSinceNow - 4.0) * 0.6
                }
                untrained.add(LifelongPoint(age, detrained.coerceAtMost(currentWeight + 8.0)))
            }
        }
        
        val minWeight = minOf(
            trained.minOfOrNull { it.weight } ?: 50.0,
            untrained.minOfOrNull { it.weight } ?: 50.0,
            historicPoints.minOfOrNull { it.weight } ?: 50.0
        )
        val maxWeight = maxOf(
            trained.maxOfOrNull { it.weight } ?: 100.0,
            untrained.maxOfOrNull { it.weight } ?: 100.0,
            historicPoints.maxOfOrNull { it.weight } ?: 100.0
        )
        
        // Research-based zones for ~175cm male (BMI thresholds × height²)
        // BMI 18.5 → 56.6 kg, BMI 22 → 67.4 kg, BMI 25 → 76.6 kg, BMI 27.5 → 84.2 kg
        // Age adjustments: clinical guidelines allow slightly higher BMI at older ages
        return LifelongData(
            currentAge = currentAge,
            trainedPath = trained,
            untrainedPath = untrained,
            zones = LifelongZones(
                // Underweight upper bound: BMI 18.5 baseline, small rise after 65 (muscle/bone loss)
                underweight = { age -> 56.5 + maxOf(0.0, (age - 65.0) * 0.18) },
                // Excellent/healthy upper bound: BMI ~22.5 baseline, gradual rise after 50
                excellent = { age -> 68.5 + maxOf(0.0, (age - 50.0) * 0.12) },
                // Acceptable upper bound: BMI ~27 baseline, rises with age (clinical tolerance)
                acceptable = { age -> 82.0 + maxOf(0.0, (age - 40.0) * 0.15).coerceAtMost(8.0) }
            ),
            minWeight = minWeight,
            maxWeight = maxWeight
        )
    }

    private fun calculateProgress(latestEma: Double, start: Double, goal: Double): Float {
        if (abs(start - goal) < 0.001) return 100f
        val total = start - goal
        val current = start - latestEma
        return ((current / total) * 100.0).toFloat().coerceIn(0f, 100f)
    }
    
    fun addWeightEntry(weightKg: Double, dateMillis: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            repository.insert(WeightEntry(weightKg = weightKg, dateMillis = dateMillis))
            WeightTrackerGlanceWidget.updateAll(getApplication())
        }
    }
    
    fun addHistoricWeightEntry(weightKg: Double, age: Int) {
        val birthCal = Calendar.getInstance().apply { timeInMillis = state.value.birthDateMillis }
        birthCal.add(Calendar.YEAR, age)
        addWeightEntry(weightKg, birthCal.timeInMillis)
    }

    fun deleteWeightEntry(entry: WeightEntry) {
        viewModelScope.launch {
            repository.delete(entry)
            WeightTrackerGlanceWidget.updateAll(getApplication())
        }
    }

    fun updateSettings(
        startingWeight: Double,
        goalWeight: Double,
        startDate: Long,
        goalDate: Long
    ) {
        viewModelScope.launch {
            repository.updateUserSettings(
                UserSettings(
                    startingWeightKg = startingWeight,
                    goalWeightKg = goalWeight,
                    startDateMillis = startDate,
                    goalDateMillis = goalDate,
                    birthDateMillis = state.value.birthDateMillis
                )
            )
            WeightTrackerGlanceWidget.updateAll(getApplication())
        }
    }
}
