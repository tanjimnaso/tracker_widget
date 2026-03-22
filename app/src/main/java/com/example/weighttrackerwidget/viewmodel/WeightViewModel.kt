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
    val isLifelongMode: Boolean = false,
    val startingWeightKg: Double = 80.25,
    val goalWeightKg: Double = 72.0,
    val startDateMillis: Long = System.currentTimeMillis(),
    val goalDateMillis: Long = Calendar.getInstance().apply { add(Calendar.MONTH, 3) }.timeInMillis,
    val birthDateMillis: Long = Calendar.getInstance().apply { set(1993, Calendar.AUGUST, 1) }.timeInMillis,
    val heightCm: Double = 175.0,
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

    private val prefs = application.getSharedPreferences("chart_prefs", android.content.Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        WeightState(isLifelongMode = prefs.getBoolean("isLifelongMode", false))
    )
    val state: StateFlow<WeightState> = _state.asStateFlow()

    companion object {
        private const val EMA_ALPHA = 0.25f
    }

    init {
        loadData()
    }

    fun setChartMode(isLifelong: Boolean) {
        prefs.edit().putBoolean("isLifelongMode", isLifelong).apply()
        _state.update { it.copy(isLifelongMode = isLifelong) }
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(repository.allEntries, repository.userSettings) { entries, settings ->
                Pair(entries, settings)
            }.collect { (entries, settings) ->
                val currentSettings = settings ?: UserSettings()
                
                // Goal weight auto-computed from height: BMI 22.5 (lean athletic target for
                // strength training + mixed cardio). Not user-settable.
                val h = currentSettings.heightCm / 100.0
                val goalWeight = 22.5 * h * h

                _state.update {
                    it.copy(
                        isLoading = true,
                        startingWeightKg = currentSettings.startingWeightKg,
                        goalWeightKg = goalWeight,
                        startDateMillis = currentSettings.startDateMillis,
                        goalDateMillis = currentSettings.goalDateMillis,
                        birthDateMillis = currentSettings.birthDateMillis,
                        heightCm = currentSettings.heightCm
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
                    val progress = calculateProgress(latestEma, currentSettings.startingWeightKg, goalWeight)
                    
                    val currentW = latestEntry?.weightKg ?: currentSettings.startingWeightKg
                    val totalLost = currentSettings.startingWeightKg - currentW
                    
                    val birthCal = Calendar.getInstance().apply { timeInMillis = currentSettings.birthDateMillis }
                    val nowCal = Calendar.getInstance()
                    var age = nowCal.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
                    if (nowCal.get(Calendar.DAY_OF_YEAR) < birthCal.get(Calendar.DAY_OF_YEAR)) age--

                    val lifelong = generateLifelongData(age, latestEma, sortedEntries, currentSettings.heightCm)

                    // EMA slope: use last 7 days if available, else default 0.6% body weight/week
                    val rateKgPerDay = if (chartPoints.size >= 7) {
                        val week = chartPoints.takeLast(7)
                        (week.last().emaWeight - week.first().emaWeight) / 7.0
                    } else {
                        -(latestEma * 0.006 / 7.0)
                    }

                    val daysToGoal = maxOf(1L, (currentSettings.goalDateMillis - now) / (24L * 60 * 60 * 1000))
                    val halfDays = (daysToGoal / 2).toInt()

                    val weekChange = rateKgPerDay * 7.0
                    val monthChange = rateKgPerDay * 30.0

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

                            thisWeekProjection = latestEma + weekChange,
                            thisWeekChange = weekChange,
                            monthEndProjection = latestEma + monthChange,
                            monthEndChange = monthChange,
                            halfwayWeight = latestEma + rateKgPerDay * halfDays,
                            halfwayDays = halfDays
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

    private fun generateLifelongData(currentAge: Int, currentWeight: Double, allEntries: List<WeightEntry>, heightCm: Double = 175.0): LifelongData {
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
        
        // Athletic BMI thresholds × height² — parameterised by actual heightCm
        // BMI 19.3 = lower bound of athletic/lean; 25.5 = upper excellent; 28.8 = acceptable
        val h = heightCm / 100.0
        return LifelongData(
            currentAge = currentAge,
            trainedPath = trained,
            untrainedPath = untrained,
            zones = LifelongZones(
                underweight = { age -> 19.3 * h * h + maxOf(0.0, (age - 65.0) * 0.2) },
                excellent   = { age -> 25.5 * h * h + maxOf(0.0, (age - 50.0) * 0.15) },
                acceptable  = { age -> 28.8 * h * h + maxOf(0.0, (age - 40.0) * 0.2).coerceAtMost(5.0) }
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
        startDate: Long,
        goalDate: Long
    ) {
        viewModelScope.launch {
            repository.updateUserSettings(
                UserSettings(
                    startingWeightKg = startingWeight,
                    startDateMillis = startDate,
                    goalDateMillis = goalDate,
                    birthDateMillis = state.value.birthDateMillis,
                    heightCm = state.value.heightCm
                )
            )
            WeightTrackerGlanceWidget.updateAll(getApplication())
        }
    }

    fun updateHeight(heightCm: Double) {
        viewModelScope.launch {
            repository.updateUserSettings(
                UserSettings(
                    startingWeightKg = state.value.startingWeightKg,
                    startDateMillis = state.value.startDateMillis,
                    goalDateMillis = state.value.goalDateMillis,
                    birthDateMillis = state.value.birthDateMillis,
                    heightCm = heightCm
                )
            )
            WeightTrackerGlanceWidget.updateAll(getApplication())
        }
    }

    fun updateBirthYear(year: Int) {
        viewModelScope.launch {
            val cal = Calendar.getInstance().apply {
                timeInMillis = state.value.birthDateMillis
                set(Calendar.YEAR, year)
            }
            repository.updateUserSettings(
                UserSettings(
                    startingWeightKg = state.value.startingWeightKg,
                    startDateMillis = state.value.startDateMillis,
                    goalDateMillis = state.value.goalDateMillis,
                    birthDateMillis = cal.timeInMillis,
                    heightCm = state.value.heightCm
                )
            )
        }
    }
}
