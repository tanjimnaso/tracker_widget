package com.example.weighttrackerwidget

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Tracker", 
                        fontWeight = FontWeight.Bold, 
                        fontFamily = FontFamily.Serif,
                        color = MaterialTheme.colorScheme.onSurface 
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // 1. Current Weight Section
                item {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            "CURRENT WEIGHT",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 2.sp,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            val displayWeight = state.currentWeightKg ?: state.startingWeightKg
                            Text(
                                text = String.format(Locale.getDefault(), "%.1f", displayWeight),
                                fontSize = 88.sp,
                                fontWeight = FontWeight.Light,
                                fontFamily = FontFamily.Serif,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                " kg",
                                fontSize = 28.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
                            )
                        }
                    }
                }

                // 2. Goal Metric (Replacing Start/Goal/Lost)
                if (!isLifelongMode) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            MetricItem("GOAL", String.format(Locale.getDefault(), "%.1f", state.goalWeightKg))
                        }
                    }
                }

                // 3. Combined Trend / Lifelong Chart Section
                item {
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
                        accentColor = accentColor
                    )
                }

                // 4. Overall Progress Bar
                item {
                    if (!isLifelongMode) {
                        Column(modifier = Modifier.offset(y = (-16).dp)) {
                            val sdf = SimpleDateFormat("MMM yy", Locale.getDefault())
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(sdf.format(Date(state.startDateMillis)).uppercase(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${state.progressPercentage.toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(sdf.format(Date(state.goalDateMillis)).uppercase(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))
                            ) {
                                Box(
                                    Modifier.fillMaxHeight().fillMaxWidth((state.progressPercentage/100f).coerceIn(0.01f, 1f)).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    } else {
                        // Show START and GOAL horizontally stacked when in Lifelong mode under the chart
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            MetricItem("START", String.format(Locale.getDefault(), "%.1f", state.startingWeightKg))
                            MetricItem("GOAL", String.format(Locale.getDefault(), "%.1f", state.goalWeightKg))
                        }
                    }
                }

                // 5. Progress Stats (Goals)
                item {
                    Column {
                        // Title now clickable to open settings, acting as a clean, hidden settings button
                        Text(
                            "GOALS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 2.sp,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 16.dp).clickable { showSettingsDialog = true }.padding(4.dp)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCard("THIS WEEK", state.thisWeekProjection, state.thisWeekChange, "kg")
                            StatCard("MONTH END", state.monthEndProjection, state.monthEndChange, "kg")
                            StatCard("HALFWAY", state.halfwayWeight, state.halfwayDays.toDouble(), "days")
                        }
                    }
                }

                // 6. History
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 80.dp, top = 16.dp), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showHistoryDialog = true }) {
                            Text("HISTORIC WEIGHTS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            // FAB
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).size(72.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(32.dp))
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
fun MetricItem(label: String, value: String, isAccent: Boolean = false) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            color = if (isAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun StatCard(label: String, projection: Double, change: Double, unit: String) {
    Column(Modifier.width(100.dp)) {
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
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f),
        title = { Text("Historic Weights", fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif) },
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
                LazyColumn {
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
                                Text(sdf.format(Date(entry.dateMillis)), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onDelete(entry) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    }
                }
            }
        },
        confirmButton = {
            if (!isAdding) {
                Button(onClick = { isAdding = true }) {
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
                onConfirm(startW.toDoubleOrNull() ?: 80.0, goalW.toDoubleOrNull() ?: 70.0, state.startDateMillis, state.goalDateMillis)
            }) { Text("Save") }
        }
    )
}
