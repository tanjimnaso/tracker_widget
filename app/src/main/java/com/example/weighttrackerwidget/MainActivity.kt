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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog

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

    val isLifelongMode = state.isLifelongMode

    val accentColor = MaterialTheme.colorScheme.primary

    // Scaffold with no topBar — paddingValues includes status bar + nav bar insets
    Scaffold(containerColor = Color.Transparent) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 80.dp)
            ) {
                // 1. Current Weight + Goal (auto-computed, read-only)
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
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { showAddDialog = true }
                    ) {
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
                } // end Row

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
                    onToggleMode = { viewModel.setChartMode(!isLifelongMode) },
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
        }
    }

    if (showAddDialog) {
        WeighInDialog(
            currentWeight = state.currentWeightKg ?: state.startingWeightKg,
            onDismiss = { showAddDialog = false }
        ) { w ->
            viewModel.addWeightEntry(w, System.currentTimeMillis())
            showAddDialog = false
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(state = state, onDismiss = { showSettingsDialog = false }) { sw, sd, gd ->
            viewModel.updateSettings(sw, sd, gd)
            showSettingsDialog = false
        }
    }

    if (showHistoryDialog) {
        HistoryDialog(
            state = state,
            onDismiss = { showHistoryDialog = false },
            onDelete = { viewModel.deleteWeightEntry(it) },
            onAddHistoric = { age, weight -> viewModel.addHistoricWeightEntry(weight, age) },
            onUpdateBirthYear = { viewModel.updateBirthYear(it) },
            onUpdateHeight = { viewModel.updateHeight(it) }
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
    onAddHistoric: (Int, Double) -> Unit,
    onUpdateBirthYear: (Int) -> Unit,
    onUpdateHeight: (Double) -> Unit
) {
    var isAdding by remember { mutableStateOf(false) }

    val currentBirthYear = remember(state.birthDateMillis) {
        Calendar.getInstance().apply { timeInMillis = state.birthDateMillis }.get(Calendar.YEAR)
    }
    var birthYearStr by remember(currentBirthYear) { mutableStateOf(currentBirthYear.toString()) }
    var heightStr by remember(state.heightCm) { mutableStateOf(state.heightCm.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f),
        title = {
            Text(
                "History",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif
            )
        },
        text = {
            Column {
                // Profile rows
                ProfileRow(
                    label = "Birth year",
                    value = birthYearStr,
                    onValueChange = { birthYearStr = it },
                    onSave = { birthYearStr.toIntOrNull()?.let { onUpdateBirthYear(it) } }
                )
                Spacer(Modifier.height(8.dp))
                ProfileRow(
                    label = "Height",
                    value = heightStr,
                    onValueChange = { heightStr = it },
                    onSave = { heightStr.toDoubleOrNull()?.let { onUpdateHeight(it) } },
                    unit = "cm"
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(Modifier.height(8.dp))

                if (isAdding) {
                    var ageStr by remember { mutableStateOf("") }
                    var weightStr by remember { mutableStateOf("") }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = ageStr,
                            onValueChange = { ageStr = it },
                            label = { Text("Age") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = weightStr,
                            onValueChange = { weightStr = it },
                            label = { Text("Weight (kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { isAdding = false }) { Text("Cancel") }
                            TextButton(onClick = {
                                val age = ageStr.toIntOrNull()
                                val w = weightStr.toDoubleOrNull()
                                if (age != null && w != null) {
                                    onAddHistoric(age, w)
                                    isAdding = false
                                }
                            }) {
                                Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(state.allEntries) { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isAdding) {
                TextButton(
                    onClick = { isAdding = true }
                ) {
                    Text("Add Historic Weight", color = MaterialTheme.colorScheme.primary)
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
private fun ProfileRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    unit: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            suffix = if (unit.isNotEmpty()) ({ Text(unit, fontSize = 12.sp) }) else null,
            modifier = Modifier.width(110.dp)
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onSave) {
            Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun WeighInDialog(currentWeight: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var weightStr by remember {
        mutableStateOf(String.format(Locale.getDefault(), "%.1f", currentWeight))
    }
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text(
                    "WEIGH IN",
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = weightStr,
                        onValueChange = { weightStr = it },
                        textStyle = TextStyle(
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = FontFamily.Serif,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            weightStr.toDoubleOrNull()?.let { onConfirm(it) }
                        }),
                        singleLine = true,
                        modifier = Modifier.weight(1f).focusRequester(focusRequester)
                    )
                    Text(
                        "kg",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, end = 16.dp)
                    )
                    TextButton(onClick = { weightStr.toDoubleOrNull()?.let { onConfirm(it) } }) {
                        Text(
                            "Save",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun SettingsDialog(state: WeightState, onDismiss: () -> Unit, onConfirm: (Double, Long, Long) -> Unit) {
    var startW by remember { mutableStateOf(String.format(Locale.getDefault(), "%.1f", state.startingWeightKg)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            OutlinedTextField(
                value = startW,
                onValueChange = { startW = it },
                label = { Text("Start Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(startW.toDoubleOrNull() ?: state.startingWeightKg, state.startDateMillis, state.goalDateMillis)
            }) {
                Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
