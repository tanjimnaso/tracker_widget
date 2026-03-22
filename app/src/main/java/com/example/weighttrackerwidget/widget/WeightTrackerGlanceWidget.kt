package com.example.weighttrackerwidget.widget

import android.content.Context
import android.graphics.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.weighttrackerwidget.MainActivity
import com.example.weighttrackerwidget.WeightDatabase
import com.example.weighttrackerwidget.WeightEntry
import com.example.weighttrackerwidget.viewmodel.ChartPoint
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

object WeightTrackerGlanceWidget : GlanceAppWidget() {
    
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(100.dp, 100.dp), DpSize(250.dp, 110.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = WeightDatabase.getDatabase(context)
        val entries = database.weightDao().getAllEntriesSortedByDate().first()
        val settings = database.weightDao().getUserSettings().first() ?: com.example.weighttrackerwidget.UserSettings()
        
        val startingWeight = settings.startingWeightKg
        val goalWeight = settings.goalWeightKg
        
        val chartData = calculateEma(entries)
        val latestEntry = entries.lastOrNull()
        val latestEma = chartData.lastOrNull()?.emaWeight ?: latestEntry?.weightKg ?: startingWeight
        
        val progress = calculateProgress(latestEma, startingWeight, goalWeight)

        provideContent {
            GlanceTheme {
                WidgetContent(
                    startingWeight = startingWeight,
                    goalWeight = goalWeight,
                    currentEmaWeight = latestEma,
                    progress = progress,
                    chartData = chartData,
                    startDate = settings.startDateMillis,
                    goalDate = settings.goalDateMillis
                )
            }
        }
    }

    private fun calculateEma(entries: List<WeightEntry>): List<ChartPoint> {
        val chartPoints = mutableListOf<ChartPoint>()
        var currentEma: Double? = null
        val alpha = 0.25

        for (entry in entries) {
            val weight = entry.weightKg
            val newEma = if (currentEma == null) weight else (weight * alpha) + (currentEma * (1.0 - alpha))
            currentEma = newEma
            chartPoints.add(ChartPoint(Date(entry.dateMillis), weight, newEma))
        }
        return chartPoints
    }

    private fun calculateProgress(latestEma: Double, start: Double, goal: Double): Float {
        if (start == goal) return 100f
        val total = start - goal
        val current = start - latestEma
        return ((current / total) * 100.0).toFloat().coerceIn(0f, 100f)
    }

    @Composable
    private fun WidgetContent(
        startingWeight: Double,
        goalWeight: Double,
        currentEmaWeight: Double,
        progress: Float,
        chartData: List<ChartPoint>,
        startDate: Long,
        goalDate: Long
    ) {
        val size = LocalSize.current
        val isWide = size.width >= 200.dp
        
        // Colors from the design
        val accentColor = ColorProvider(
            day = Color(0xFFB48377), // AppAccentLight
            night = Color(0xFFD4A276) // AppAccentDark
        )
        val onSurfaceColor = ColorProvider(
            day = Color(0xFF1C1B19), // AppTextPrimaryLight
            night = Color(0xFFE6E1DC) // AppTextPrimaryDark
        )
        val surfaceColor = ColorProvider(
            day = Color(0xFFFDF8F5), // AppBackgroundLight
            night = Color(0xFF1C1B19) // AppBackgroundDark
        )
        val surfaceVariantColor = ColorProvider(
            day = Color(0xFFF7F0EB), // AppCardBackgroundLight
            night = Color(0xFF262422) // AppCardBackgroundDark
        )

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(surfaceColor)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = if (isWide) GlanceModifier.width(100.dp).fillMaxHeight() else GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "%.1f", currentEmaWeight),
                    style = TextStyle(
                        color = onSurfaceColor,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "kg now",
                    style = TextStyle(
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                
                Spacer(modifier = GlanceModifier.height(8.dp))
                
                // Progress Bar
                val barProgress = progress / 100f
                val barWidth = if (isWide) 76.dp else (size.width - 24.dp)
                
                Box(
                    modifier = GlanceModifier
                        .width(barWidth)
                        .height(4.dp)
                        .background(surfaceVariantColor)
                ) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .width(barWidth * barProgress.coerceIn(0.01f, 1f))
                            .background(accentColor)
                    ) {}
                }
                
                Text(
                    text = "${progress.toInt()}%",
                    style = TextStyle(
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            if (isWide) {
                Spacer(modifier = GlanceModifier.width(16.dp))
                
                Column(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                ) {
                    val chartBitmap = drawChartBitmap(chartData, startingWeight, goalWeight, startDate, goalDate)
                    Image(
                        provider = ImageProvider(chartBitmap),
                        contentDescription = "Weight Trend",
                        modifier = GlanceModifier.fillMaxSize()
                    )
                }
            }
        }
    }

    private fun drawChartBitmap(
        data: List<ChartPoint>,
        startingWeight: Double,
        goalWeight: Double,
        startDate: Long,
        goalDate: Long
    ): Bitmap {
        val width = 400
        val height = 240
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Using light mode accent for the bitmap as it's harder to make it dynamic inside a Bitmap
        val accentColor = android.graphics.Color.parseColor("#B48377")

        val paintEMA = Paint().apply {
            color = accentColor
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }

        val paintForecast = Paint().apply {
            color = accentColor
            strokeWidth = 3f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            isAntiAlias = true
            alpha = 100
        }

        val startMillis = startDate
        val endMillis = goalDate
        val totalTime = maxOf(1L, endMillis - startMillis)

        val weights = data.flatMap { listOf(it.emaWeight) } + goalWeight + startingWeight
        val minY = (weights.minOrNull() ?: 70.0) - 2.0
        val maxY = (weights.maxOrNull() ?: 85.0) + 2.0
        val range = maxY - minY
        
        fun getY(w: Double) = (height - 40 - ( (w - minY) / range * (height - 60) )).toFloat()
        fun getX(date: Long) = ((date - startMillis).toFloat() / totalTime) * width

        // Forecast Line
        canvas.drawLine(getX(startMillis), getY(startingWeight), getX(endMillis), getY(goalWeight), paintForecast)

        // Actual Trend
        val filteredData = data.filter { it.date.time >= startMillis }
        if (filteredData.isNotEmpty()) {
            val emaPath = android.graphics.Path()
            emaPath.moveTo(getX(filteredData.first().date.time), getY(filteredData.first().emaWeight))
            filteredData.forEach { point ->
                emaPath.lineTo(getX(point.date.time), getY(point.emaWeight))
            }
            canvas.drawPath(emaPath, paintEMA)
            
            // Dot at end
            val last = filteredData.last()
            val paintDot = Paint().apply { color = accentColor; style = Paint.Style.FILL; isAntiAlias = true }
            canvas.drawCircle(getX(last.date.time), getY(last.emaWeight), 6f, paintDot)
        }

        return bitmap
    }
}
