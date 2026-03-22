package com.example.weighttrackerwidget.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weighttrackerwidget.viewmodel.ChartPoint
import com.example.weighttrackerwidget.viewmodel.LifelongData
import com.example.weighttrackerwidget.viewmodel.LifelongPoint
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CombinedChartSection(
    isLifelongMode: Boolean,
    onToggleMode: () -> Unit,
    actualPoints: List<ChartPoint>,
    startingWeight: Double,
    goalWeight: Double,
    minY3Month: Double,
    maxY3Month: Double,
    startDate: Long,
    goalDate: Long,
    lifelongData: LifelongData,
    accentColor: Color,
    progressPercentage: Float,
    modifier: Modifier = Modifier
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trainedColor = Color(0xFFD4A276)
    val sdf = SimpleDateFormat("MMM d", Locale.getDefault())

    Column(modifier = modifier.fillMaxWidth()) {
        // Mode label only — no toggle button; tap the chart to toggle
        Text(
            if (isLifelongMode) "LIFELONG PROJECTION" else "3-MONTH TREND",
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            letterSpacing = 2.sp,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        // Legend — natural height, no clipping
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendItem("excellent", Color(0xFF2D352D), isSquare = true)
                LegendItem("acceptable", Color(0xFF2C2825), isSquare = true)
                LegendItem("unhealthy", Color(0xFF33221C), isSquare = true)
                LegendItem("underweight", Color(0xFF1B1F1B), isSquare = true)
            }
            if (isLifelongMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendItem("historic", trainedColor, isSquare = false, strokeWidth = 3f)
                    LegendItem("trained", trainedColor, isSquare = false, isDashed = true)
                    LegendItem("detraining", trainedColor.copy(alpha = 0.5f), isSquare = false, isDashed = true)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendItem("actual", accentColor, isSquare = false)
                    LegendItem("forecast", accentColor.copy(alpha = 0.5f), isSquare = false, isDashed = true)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // Chart — tap anywhere to toggle between modes (no ripple)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onToggleMode() }
        ) {
            if (isLifelongMode) {
                LifelongChart(
                    data = lifelongData,
                    labelColor = labelColor,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                WeightChart(
                    actualPoints = actualPoints,
                    startingWeight = startingWeight,
                    goalWeight = goalWeight,
                    minY = minY3Month,
                    maxY = maxY3Month,
                    startDate = startDate,
                    goalDate = goalDate,
                    accentColor = accentColor,
                    labelColor = labelColor,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bottom section — identical height in both modes so chart canvas is same size
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (isLifelongMode) {
                Text("16", fontSize = 11.sp, color = labelColor)
                Text("AGE", fontSize = 11.sp, color = labelColor, fontWeight = FontWeight.Bold)
                Text("80", fontSize = 11.sp, color = labelColor)
            } else {
                Text(sdf.format(Date(startDate)).uppercase(), fontSize = 11.sp, color = labelColor)
                Text("${progressPercentage.toInt()}%", fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Bold)
                Text(sdf.format(Date(goalDate)).uppercase(), fontSize = 11.sp, color = labelColor)
            }
        }
        Spacer(Modifier.height(6.dp))
        // Progress bar — visible in 3-month; invisible 4dp placeholder in lifelong to keep heights equal
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    if (!isLifelongMode) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                    RoundedCornerShape(2.dp)
                )
        ) {
            if (!isLifelongMode) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((progressPercentage / 100f).coerceIn(0.01f, 1f))
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
fun WeightChart(
    actualPoints: List<ChartPoint>,
    startingWeight: Double,
    goalWeight: Double,
    minY: Double,
    maxY: Double,
    startDate: Long,
    goalDate: Long,
    accentColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    val rangeY = (maxY - minY).coerceAtLeast(1.0)
    val rangeX = (goalDate - startDate).coerceAtLeast(1L).toFloat()

    val dotRadius = with(LocalDensity.current) { 4.dp.toPx() }
    val strokeWidth = with(LocalDensity.current) { 2.dp.toPx() }

    BoxWithConstraints(modifier = modifier) {
        val chartH = maxHeight
        val startFrac = (1.0 - (startingWeight - minY) / rangeY).toFloat().coerceIn(0f, 1f)
        val goalFrac = (1.0 - (goalWeight - minY) / rangeY).toFloat().coerceIn(0f, 1f)

        Canvas(Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            fun mapX(time: Long): Float = ((time - startDate).toFloat() / rangeX) * width
            fun mapY(weight: Double): Float = ((1.0 - (weight - minY) / rangeY) * height).toFloat()

            // Forecast line
            val forecastPath = Path()
            forecastPath.moveTo(mapX(startDate), mapY(startingWeight))
            forecastPath.lineTo(mapX(goalDate), mapY(goalWeight))
            drawPath(
                forecastPath,
                accentColor.copy(alpha = 0.5f),
                style = Stroke(strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
            )

            // Actual data line
            val validPoints = actualPoints.filter { it.date.time >= startDate }
            if (validPoints.isNotEmpty()) {
                val path = Path()
                path.moveTo(mapX(validPoints.first().date.time), mapY(validPoints.first().emaWeight))
                validPoints.forEach { path.lineTo(mapX(it.date.time), mapY(it.emaWeight)) }
                drawPath(path, accentColor, style = Stroke(strokeWidth))
                val last = validPoints.last()
                drawCircle(accentColor, dotRadius, Offset(mapX(last.date.time), mapY(last.emaWeight)))
            }
        }

        // Y-axis labels
        Text(
            "${startingWeight.toInt()} kg",
            fontSize = 9.sp,
            color = labelColor.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = (chartH * startFrac - 16.dp).coerceAtLeast(0.dp))
        )
        Text(
            "${goalWeight.toInt()} kg",
            fontSize = 9.sp,
            color = labelColor.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = (chartH * goalFrac - 16.dp).coerceAtLeast(0.dp))
        )
    }
}

@Composable
fun LegendItem(
    label: String,
    color: Color,
    isSquare: Boolean,
    isDashed: Boolean = false,
    strokeWidth: Float = 2f
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isSquare) {
            Box(Modifier.size(10.dp).background(color))
        } else {
            Box(Modifier.size(14.dp, 2.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(
                        color,
                        Offset(0f, size.height / 2),
                        Offset(size.width, size.height / 2),
                        strokeWidth.dp.toPx(),
                        pathEffect = if (isDashed) PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f) else null
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LifelongChart(
    data: LifelongData,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    val ageStart = 16.0
    val ageEnd = 80.0

    val healthyMin = data.zones.underweight(ageStart) - 8.0
    val healthyMax = data.zones.acceptable(ageStart) + 14.0
    val relevantPoints = (data.trainedPath + data.untrainedPath).filter { it.age >= ageStart }
    val dataMin = relevantPoints.minOfOrNull { it.weight } ?: healthyMin
    val dataMax = relevantPoints.maxOfOrNull { it.weight } ?: healthyMax
    val weightMin = minOf(healthyMin, dataMin) - 2.0
    val weightMax = maxOf(healthyMax, dataMax) + 2.0

    val trainedColor = Color(0xFFD4A276)
    val underweightZoneColor = Color(0xFF1B1F1B)
    val excellentZoneColor = Color(0xFF2D352D)
    val acceptableZoneColor = Color(0xFF2C2825)
    val unhealthyZoneColor = Color(0xFF33221C)

    val refAge = 40.0
    val zoneLabelWeights = listOf(
        data.zones.underweight(refAge),
        data.zones.excellent(refAge),
        data.zones.acceptable(refAge)
    ).filter { it in weightMin..weightMax }

    BoxWithConstraints(modifier = modifier) {
        val chartH = maxHeight

        fun yFrac(weight: Double) = (1.0 - (weight - weightMin) / (weightMax - weightMin)).toFloat().coerceIn(0f, 1f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            fun mapX(age: Double): Float = ((age - ageStart) / (ageEnd - ageStart)).toFloat() * width
            fun mapY(weight: Double): Float = ((1.0 - (weight - weightMin) / (weightMax - weightMin)).toFloat() * height)

            // Zone fills
            val heavyPath = Path()
            heavyPath.moveTo(mapX(ageStart), 0f)
            for (a in 16..80) heavyPath.lineTo(mapX(a.toDouble()), mapY(data.zones.acceptable(a.toDouble())))
            heavyPath.lineTo(width, 0f)
            heavyPath.close()
            drawPath(heavyPath, unhealthyZoneColor)

            val acceptablePath = Path()
            acceptablePath.moveTo(mapX(ageStart), mapY(data.zones.acceptable(ageStart)))
            for (a in 16..80) acceptablePath.lineTo(mapX(a.toDouble()), mapY(data.zones.acceptable(a.toDouble())))
            for (a in 80 downTo 16) acceptablePath.lineTo(mapX(a.toDouble()), mapY(data.zones.excellent(a.toDouble())))
            acceptablePath.close()
            drawPath(acceptablePath, acceptableZoneColor)

            val excellentPath = Path()
            excellentPath.moveTo(mapX(ageStart), mapY(data.zones.excellent(ageStart)))
            for (a in 16..80) excellentPath.lineTo(mapX(a.toDouble()), mapY(data.zones.excellent(a.toDouble())))
            for (a in 80 downTo 16) excellentPath.lineTo(mapX(a.toDouble()), mapY(data.zones.underweight(a.toDouble())))
            excellentPath.close()
            drawPath(excellentPath, excellentZoneColor)

            val underweightPath = Path()
            underweightPath.moveTo(mapX(ageStart), mapY(data.zones.underweight(ageStart)))
            for (a in 16..80) underweightPath.lineTo(mapX(a.toDouble()), mapY(data.zones.underweight(a.toDouble())))
            underweightPath.lineTo(width, height)
            underweightPath.lineTo(0f, height)
            underweightPath.close()
            drawPath(underweightPath, underweightZoneColor)

            // Zone boundary grid lines
            for (w in listOf(
                data.zones.underweight(40.0),
                data.zones.excellent(40.0),
                data.zones.acceptable(40.0)
            )) {
                if (w in weightMin..weightMax) {
                    drawLine(Color.Gray.copy(alpha = 0.12f), Offset(0f, mapY(w)), Offset(width, mapY(w)), 1f)
                }
            }

            // Vertical dashed line at current age
            val currentAgeX = mapX(data.currentAge.toDouble())
            drawLine(
                color = Color.Gray.copy(alpha = 0.3f),
                start = Offset(currentAgeX, 0f),
                end = Offset(currentAgeX, height),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
            )

            val currentAgeDbl = data.currentAge.toDouble()

            fun drawSegment(points: List<LifelongPoint>, color: Color, isHistoric: Boolean) {
                val filtered = points.filter { it.age >= ageStart }
                if (filtered.isEmpty()) return
                val path = Path()
                path.moveTo(mapX(filtered.first().age), mapY(filtered.first().weight))
                filtered.forEach { path.lineTo(mapX(it.age), mapY(it.weight)) }
                drawPath(
                    path, color,
                    style = Stroke(
                        width = if (isHistoric) 3.dp.toPx() else 2.dp.toPx(),
                        pathEffect = if (isHistoric) null else PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                )
            }

            // Historic portion — solid, thicker
            val historicPoints = data.trainedPath.filter { it.age in ageStart..currentAgeDbl }
            drawSegment(historicPoints, trainedColor, isHistoric = true)

            // Trained projection (dashed) — maintains fitness toward goal
            val projectionPoints = data.trainedPath.filter { it.age >= currentAgeDbl }
            drawSegment(projectionPoints, trainedColor, isHistoric = false)

            // Detraining projection (dashed, dimmer)
            val detrainingPoints = data.untrainedPath.filter { it.age >= currentAgeDbl }
            drawSegment(detrainingPoints, trainedColor.copy(alpha = 0.5f), isHistoric = false)

            // Dot at current position
            val currentTrainedPoint = data.trainedPath.find { it.age.toInt() == data.currentAge }
            if (currentTrainedPoint != null) {
                drawCircle(trainedColor, 6.dp.toPx(), Offset(currentAgeX, mapY(currentTrainedPoint.weight)))
            }
        }

        // Zone boundary labels
        for (w in zoneLabelWeights) {
            Text(
                "${w.toInt()}",
                fontSize = 9.sp,
                color = labelColor.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = (chartH * yFrac(w) - 16.dp).coerceAtLeast(0.dp))
            )
        }
    }
}
