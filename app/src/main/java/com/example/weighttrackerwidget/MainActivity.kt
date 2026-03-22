package com.example.weighttrackerwidget

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.weighttrackerwidget.ui.components.CombinedChartSection
import com.example.weighttrackerwidget.viewmodel.WeightViewModel
import com.example.weighttrackerwidget.viewmodel.WeightState
import com.example.weighttrackerwidget.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.text.KeyboardOptions

class MainActivity : ComponentActivity() {
    private lateinit var repository: WeightRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val database = WeightDatabase.getDatabase(applicationContext)
        repository = WeightRepository(database.weightDao())

        setContent {
            WeightTrackerWidgetTheme {
                val viewModel: WeightViewModel = rememberWeightViewModel(application, repository)
                val window = remember(this) { WindowCompat.getInsetsController(window, window.decorView) }
                window?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WeightTrackerScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun rememberWeightViewModel(application: Application, repository: WeightRepository): WeightViewModel {
    val factory = remember(application, repository) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WeightViewModel(application, repository) as T
            }
        }
    }
    return viewModel(factory = factory)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightTrackerScreen(viewModel: WeightViewModel) {
    val state by viewModel.state.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    var isLifelongMode by remember { mutableStateOf(false) }

    val accentColor = MaterialTheme.colorScheme.primary

    // Scaffold with no topBar — paddingValues includes status bar + nav bar insets
    Scaffold(containerColor = Color.Transparent) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 108.dp)
            ) {
                // 1. Current Weight + GOAL — tap label to open settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            "CURRENT WEIGHT",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 2.sp,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { showSettingsDialog = true }
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            val displayWeight = state.currentWeightKg ?: state.startingWeightKg
                            Text(
                                String.format(Locale.getDefault(), "%.1f", displayWeight),
                                fontSize = 72.sp,
                                fontWeight = FontWeight.Light,
                                fontFamily = FontFamily.Serif,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                " kg",
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
                            )
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Text(
                            "GOAL",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            String.format(Locale.getDefault(), "%.1f", state.goalWeightKg),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 2. Stat Cards — THIS WEEK, MONTH END, HALFWAY
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCard("THIS WEEK", state.thisWeekProjection, state.thisWeekChange, "kg")
                    StatCard("MONTH END", state.monthEndProjection, state.monthEndChange, "kg")
                    StatCard("HALFWAY", state.halfwayWeight, state.halfwayDays.toDouble(), "days")
                }

                Spacer(Modifier.height(12.dp))

                // 3. Chart — tap to toggle, fills remaining space; progress bar is inside
                CombinedChartSection(
                    isLifelongMode = isLifelongMode,
                    onToggleMode = { isLifelongMode = !isLifelongMode },
                    actualPoints = state.chartData,
                    startingWeight = state.startingWeightKg,
                    goalWeight = state.goalWeightKg,
                    minY3Month = minOf(state.goalWeightKg, (state.chartData.minOfOrNull { it.emaWeight } ?: state.goalWeightKg)) - 2.0,
                    maxY3Month = maxOf(state.startingWeightKg, (state.chartData.maxOfOrNull { it.emaWeight } ?: state.startingWeightKg)) + 2.0,
                    startDate = state.startDateMillis,
                    goalDate = state.goalDateMillis,
                    lifelongData = state.lifelongData,
                    accentColor = accentColor,
                    progressPercentage = state.progressPercentage,
                    modifier = Modifier.weight(1f)
                )
            }

            // History — bottom left
            TextButton(
                onClick = { showHistoryDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 30.dp)
            ) {
                Text(
                    "HISTORY",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }

            // FAB — bottom right
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).size(64.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(28.dp))
            }
        }
    }

    if (showAddDialog) {
        AddWeightDialog(onDismiss = { showAddDialog = false }) { w, d ->
            viewModel.addWeightEntry(w, d)
            showAddDialog = false
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(state = state, onDismiss = { showSettingsDialog = false }) { sw, gw, sd, gd ->
            viewModel.updateSettings(sw, gw, sd, gd)
            showSettingsDialog = false
        }
    }

    if (showHistoryDialog) {
        HistoryDialog(
            state = state,
            onDismiss = { showHistoryDialog = false },
            onDelete = { viewModel.deleteWeightEntry(it) },
            onAddHistoric = { age, weight -> viewModel.addHistoricWeightEntry(weight, age) }
        )
    }
}

@Composable
fun StatCard(label: String, projection: Double, change: Double, unit: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            String.format(Locale.getDefault(), "%.1f", projection),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif
        )
        Text(
            if (unit == "days") "${change.toInt()} $unit" else String.format(Locale.getDefault(), "%.1f $unit", change),
            fontSize = 14.sp,
            color = if (change < 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDialog(
    state: WeightState,
    onDismiss: () -> Unit,
    onDelete: (WeightEntry) -> Unit,
    onAddHistoric: (Int, Double) -> Unit
) {
    var isAdding by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f),
        title = {
            Text(
                "Historic Weights",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif
            )
        },
        text = {
            if (isAdding) {
                var ageStr by remember { mutableStateOf("") }
                var weightStr by remember { mutableStateOf("") }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Add a weight from your past:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = ageStr,
                        onValueChange = { ageStr = it },
                        label = { Text("Age (e.g. 16)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = weightStr,
                        onValueChange = { weightStr = it },
                        label = { Text("Weight (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { isAdding = false }) { Text("Cancel") }
                        Button(onClick = {
                            val age = ageStr.toIntOrNull()
                            val w = weightStr.toDoubleOrNull()
                            if (age != null && w != null) {
                                onAddHistoric(age, w)
                                isAdding = false
                            }
                        }) { Text("Save") }
                    }
                }
            } else {
                // Natural-height list — no forced full-screen height
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(state.allEntries) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    String.format(Locale.getDefault(), "%.1f kg", entry.weightKg),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    fontFamily = FontFamily.Serif
                                )
                                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                Text(
                                    sdf.format(Date(entry.dateMillis)),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDelete(entry) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    }
                }
            }
        },
        confirmButton = {
            if (!isAdding) {
                Button(
                    onClick = { isAdding = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("Add Historic Weight")
                }
            }
        },
        dismissButton = {
            if (!isAdding) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
fun AddWeightDialog(onDismiss: () -> Unit, onConfirm: (Double, Long) -> Unit) {
    var weightStr by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Weigh-in") },
        text = {
            OutlinedTextField(
                value = weightStr,
                onValueChange = { weightStr = it },
                label = { Text("Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { weightStr.toDoubleOrNull()?.let { onConfirm(it, System.currentTimeMillis()) } }) {
                Text("Add")
            }
        }
    )
}

@Composable
fun SettingsDialog(state: WeightState, onDismiss: () -> Unit, onConfirm: (Double, Double, Long, Long) -> Unit) {
    var startW by remember { mutableStateOf(state.startingWeightKg.toString()) }
    var goalW by remember { mutableStateOf(state.goalWeightKg.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                OutlinedTextField(value = startW, onValueChange = { startW = it }, label = { Text("Start Weight") })
                OutlinedTextField(value = goalW, onValueChange = { goalW = it }, label = { Text("Goal Weight") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    startW.toDoubleOrNull() ?: 80.0,
                    goalW.toDoubleOrNull() ?: 70.0,
                    state.startDateMillis,
                    state.goalDateMillis
                )
            }) { Text("Save") }
        }
    )
}
