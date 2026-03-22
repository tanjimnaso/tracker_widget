package com.example.weighttrackerwidget.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlin.math.max
import kotlin.math.min

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
    accentColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isLifelongMode) "LIFELONG PROJECTION" else "3-MONTH TREND",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp,
                fontSize = 14.sp
            )
            
            Text(
                if (isLifelongMode) "SHOW RECENT" else "SHOW LIFELONG",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onToggleMode() }.padding(4.dp)
            )
        }
        
        Spacer(Modifier.height(8.dp))
        
        if (isLifelongMode) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendItem("excellent", Color(0xFF2D352D), isSquare = true)
                    LegendItem("acceptable", Color(0xFF2C2825), isSquare = true)
                    LegendItem("unhealthy", Color(0xFF33221C), isSquare = true)
                    LegendItem("underweight", Color(0xFF1B1F1B), isSquare = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendItem("trained", Color(0xFFD4A276), isSquare = false)
                    LegendItem("untrained drift", Color(0xFFB48377), isSquare = false, isDashed = true)
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            LifelongChart(
                data = lifelongData,
                modifier = Modifier.fillMaxWidth().height(280.dp)
            )
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("16", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("AGE", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Text("80", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                LegendItem("ACTUAL", accentColor, isSquare = false)
                LegendItem("FORECAST", accentColor.copy(alpha = 0.5f), isSquare = false, isDashed = true)
            }
            
            WeightChart(
                actualPoints = actualPoints,
                startingWeight = startingWeight,
                goalWeight = goalWeight,
                minY = minY3Month,
                maxY = maxY3Month,
                startDate = startDate,
                goalDate = goalDate,
                accentColor = accentColor,
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
            
            Spacer(Modifier.height(16.dp))
            
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(sdf.format(Date(startDate)).uppercase(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("NOW", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Text(sdf.format(Date(goalDate)).uppercase(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    modifier: Modifier = Modifier
) {
    val rangeY = (maxY - minY).coerceAtLeast(1.0)
    val rangeX = (goalDate - startDate).coerceAtLeast(1L).toFloat()
    
    val dotRadius = with(LocalDensity.current) { 4.dp.toPx() }
    val strokeWidth = with(LocalDensity.current) { 2.dp.toPx() }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        fun mapX(time: Long): Float = ((time - startDate).toFloat() / rangeX) * width
        fun mapY(weight: Double): Float = ((1.0 - (weight - minY) / rangeY) * height).toFloat()

        // 1. Draw Forecast Line (Straight line from start date/weight to goal date/weight)
        val forecastPath = Path()
        forecastPath.moveTo(mapX(startDate), mapY(startingWeight))
        forecastPath.lineTo(mapX(goalDate), mapY(goalWeight))
        
        drawPath(
            forecastPath, 
            accentColor.copy(alpha = 0.5f), 
            style = Stroke(strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
        )

        // 2. Draw Actual Data Line
        // We only want to draw actual points that fall within the startDate-goalDate window
        val validPoints = actualPoints.filter { it.date.time >= startDate }
        if (validPoints.isNotEmpty()) {
            val path = Path()
            path.moveTo(mapX(validPoints.first().date.time), mapY(validPoints.first().emaWeight))
            
            validPoints.forEach { point ->
                path.lineTo(mapX(point.date.time), mapY(point.emaWeight))
            }
            
            drawPath(path, accentColor, style = Stroke(strokeWidth))
            
            val last = validPoints.last()
            drawCircle(accentColor, dotRadius, Offset(mapX(last.date.time), mapY(last.emaWeight)))
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color, isSquare: Boolean, isDashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isSquare) {
            Box(Modifier.size(10.dp).background(color))
        } else {
            Box(Modifier.size(14.dp, 2.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(
                        color, 
                        Offset(0f, size.height/2), 
                        Offset(size.width, size.height/2), 
                        2.dp.toPx(),
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
    modifier: Modifier = Modifier
) {
    val ageStart = 16.0
    val ageEnd = 80.0
    
    // Y-axis dynamically centers on the 'Excellent' target weight (72.0)
    // Ensures min and max weights from historical and projected data are visible.
    val targetCenter = 72.0
    val dataMin = min(40.0, data.minWeight)
    val dataMax = max(110.0, data.maxWeight)
    
    val maxDelta = max(targetCenter - dataMin, dataMax - targetCenter)
    // Add 5kg padding for breathing room
    val weightMin = targetCenter - maxDelta - 5.0
    val weightMax = targetCenter + maxDelta + 5.0
    
    val trainedColor = Color(0xFFD4A276)
    val untrainedColor = Color(0xFFB48377)
    
    val underweightZoneColor = Color(0xFF1B1F1B)
    val excellentZoneColor = Color(0xFF2D352D)
    val acceptableZoneColor = Color(0xFF2C2825)
    val unhealthyZoneColor = Color(0xFF33221C)
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        fun mapX(age: Double): Float = ((age - ageStart) / (ageEnd - ageStart)).toFloat() * width
        fun mapY(weight: Double): Float = ((1.0 - (weight - weightMin) / (weightMax - weightMin)).toFloat() * height)

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

        // Horizontal grid lines for context, dynamically rendered if they fit on screen
        for (w in listOf(50.0, 72.0, 90.0)) {
            if (w in weightMin..weightMax) {
                val yw = mapY(w)
                drawLine(Color.Gray.copy(alpha = 0.1f), Offset(0f, yw), Offset(width, yw), 1f)
            }
        }

        val currentAgeX = mapX(data.currentAge.toDouble())
        drawLine(
            color = Color.Gray.copy(alpha = 0.3f),
            start = Offset(currentAgeX, 0f),
            end = Offset(currentAgeX, height),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
        )

        fun drawDataPath(pathPoints: List<LifelongPoint>, color: Color, isDashed: Boolean) {
            if (pathPoints.isEmpty()) return
            val path = Path()
            val filtered = pathPoints.filter { it.age >= 16 }
            if (filtered.isEmpty()) return
            
            path.moveTo(mapX(filtered.first().age), mapY(filtered.first().weight))
            filtered.forEach { path.lineTo(mapX(it.age), mapY(it.weight)) }
            
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = if (isDashed) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
                )
            )
        }
        
        drawDataPath(data.untrainedPath, untrainedColor, true)
        drawDataPath(data.trainedPath, trainedColor, false)
        
        val currentTrainedPoint = data.trainedPath.find { it.age.toInt() == data.currentAge }
        if (currentTrainedPoint != null) {
            drawCircle(trainedColor, 6.dp.toPx(), Offset(currentAgeX, mapY(currentTrainedPoint.weight)))
        }
    }
}
