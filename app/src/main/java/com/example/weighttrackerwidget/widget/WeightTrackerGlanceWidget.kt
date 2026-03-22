package com.example.weighttrackerwidget.widget

import android.content.Context
import android.content.res.Configuration
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
        val settings = database.weightDao().getUserSettings().first()
            ?: com.example.weighttrackerwidget.UserSettings()

        val startingWeight = settings.startingWeightKg
        val h = settings.heightCm / 100.0
        val goalWeight = 22.5 * h * h

        // Only use entries from the past year — matches ViewModel behaviour and excludes
        // old historic entries (age 16 etc.) that skew the EMA away from current weight
        val oneYearAgo = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000
        val recentEntries = entries.filter { it.dateMillis >= oneYearAgo }

        val chartData = calculateEma(recentEntries)
        val latestEntry = recentEntries.lastOrNull()
        val latestEma = chartData.lastOrNull()?.emaWeight ?: latestEntry?.weightKg ?: startingWeight
        val progress = calculateProgress(latestEma, startingWeight, goalWeight)

        val isDarkMode = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        provideContent {
            GlanceTheme {
                WidgetContent(
                    startingWeight = startingWeight,
                    goalWeight = goalWeight,
                    currentEmaWeight = latestEma,
                    progress = progress,
                    chartData = chartData,
                    startDate = settings.startDateMillis,
                    goalDate = settings.goalDateMillis,
                    isDarkMode = isDarkMode
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
        goalDate: Long,
        isDarkMode: Boolean
    ) {
        val size = LocalSize.current
        val isWide = size.width >= 200.dp

        // Theme colors — ColorProvider handles day/night automatically for Glance elements
        val accentColor = ColorProvider(Color(0xFFB48377), Color(0xFFD4A276))
        val onSurfaceColor = ColorProvider(Color(0xFF1C1B19), Color(0xFFE6E1DC))
        val secondaryColor = ColorProvider(Color(0xFF8B8782), Color(0xFF8B8782))
        val surfaceColor = ColorProvider(Color(0xFFFDF8F5), Color(0xFF1C1B19))
        val trackColor = ColorProvider(Color(0xFFE0D5CE), Color(0xFF3A3530))
        val dividerColor = ColorProvider(Color(0x28000000), Color(0x28FFFFFF))

        val leftW = 95.dp
        val barFill = (progress / 100f).coerceIn(0.02f, 1f)

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(surfaceColor)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Left panel ──────────────────────────────────────────────────
            Column(
                modifier = GlanceModifier.width(leftW).fillMaxHeight(),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.Start
            ) {
                // Large current weight
                Text(
                    text = String.format(Locale.getDefault(), "%.1f", currentEmaWeight),
                    style = TextStyle(
                        color = onSurfaceColor,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "kg now",
                    style = TextStyle(color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                )
                Spacer(modifier = GlanceModifier.height(7.dp))

                // Divider
                Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(dividerColor)) {}
                Spacer(modifier = GlanceModifier.height(7.dp))

                // Goal weight
                Text(
                    text = String.format(Locale.getDefault(), "%.0f kg", goalWeight),
                    style = TextStyle(color = onSurfaceColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "goal",
                    style = TextStyle(color = secondaryColor, fontSize = 11.sp)
                )

                // Push progress to bottom
                Spacer(modifier = GlanceModifier.defaultWeight())

                // Progress bar
                Box(
                    modifier = GlanceModifier
                        .width(leftW)
                        .height(4.dp)
                        .background(trackColor)
                ) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .width(leftW * barFill)
                            .background(accentColor)
                    ) {}
                }
                Spacer(modifier = GlanceModifier.height(3.dp))
                Text(
                    text = "${progress.toInt()}%",
                    style = TextStyle(color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
            }

            // ── Chart panel ─────────────────────────────────────────────────
            if (isWide) {
                Spacer(modifier = GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                    val chartBitmap = drawChartBitmap(
                        data = chartData,
                        startingWeight = startingWeight,
                        goalWeight = goalWeight,
                        startDate = startDate,
                        goalDate = goalDate,
                        isDarkMode = isDarkMode
                    )
                    Image(
                        provider = ImageProvider(chartBitmap),
                        contentDescription = "Weight trend chart",
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
        goalDate: Long,
        isDarkMode: Boolean
    ): Bitmap {
        val bW = 600
        val bH = 300
        val bitmap = Bitmap.createBitmap(bW, bH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Transparent background — widget card bg shows through
        canvas.drawColor(android.graphics.Color.TRANSPARENT)

        val accentRgb = if (isDarkMode) 0xFFD4A276.toInt() else 0xFFB48377.toInt()
        val secondaryRgb = 0xFF8B8782.toInt()

        val padL = 10f
        val padT = 16f
        val padR = 10f
        val padB = 52f   // space for date labels
        val chartW = bW - padL - padR
        val chartH = bH - padT - padB

        val startMillis = startDate
        val endMillis = goalDate
        val totalTime = maxOf(1L, endMillis - startMillis)

        val filteredData = data.filter { it.date.time >= startMillis }
        // Y-axis anchored to startingWeight (top) and goalWeight (bottom), matching the main app chart.
        // Clamp if EMA somehow exceeds this range.
        val minY = minOf(goalWeight, filteredData.minOfOrNull { it.emaWeight } ?: goalWeight) - 1.5
        val maxY = maxOf(startingWeight, filteredData.maxOfOrNull { it.emaWeight } ?: startingWeight) + 1.5
        val rangeY = maxOf(1.0, maxY - minY)

        fun getX(ms: Long): Float = padL + ((ms - startMillis).toFloat() / totalTime) * chartW
        fun getY(w: Double): Float = padT + chartH - ((w - minY) / rangeY * chartH).toFloat()

        // When no real data yet, synthesise a gentle downward slope so the chart isn't blank
        val displayData = if (filteredData.isNotEmpty()) filteredData else {
            val steps = 10
            (0..steps).map { i ->
                val t = startMillis + (totalTime * i / steps)
                val w = startingWeight - (startingWeight - goalWeight) * i / steps * 0.3
                ChartPoint(Date(t), w, w)
            }
        }

        if (displayData.isNotEmpty()) {
            // Filled area under EMA line
            val fillPath = android.graphics.Path()
            fillPath.moveTo(getX(displayData.first().date.time), getY(displayData.first().emaWeight))
            displayData.forEach { fillPath.lineTo(getX(it.date.time), getY(it.emaWeight)) }
            fillPath.lineTo(getX(displayData.last().date.time), padT + chartH)
            fillPath.lineTo(getX(displayData.first().date.time), padT + chartH)
            fillPath.close()
            canvas.drawPath(fillPath, Paint().apply {
                color = accentRgb
                alpha = if (isDarkMode) 48 else 52
                style = Paint.Style.FILL
                isAntiAlias = true
            })

            // EMA trend line
            val emaPath = android.graphics.Path()
            emaPath.moveTo(getX(displayData.first().date.time), getY(displayData.first().emaWeight))
            displayData.forEach { emaPath.lineTo(getX(it.date.time), getY(it.emaWeight)) }
            canvas.drawPath(emaPath, Paint().apply {
                color = accentRgb
                strokeWidth = 5f
                style = Paint.Style.STROKE
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            })

            // Dot at latest data point
            val last = displayData.last()
            canvas.drawCircle(getX(last.date.time), getY(last.emaWeight), 8f, Paint().apply {
                color = accentRgb
                style = Paint.Style.FILL
                isAntiAlias = true
            })
        }

        // Horizontal dashed goal line
        val goalY = getY(goalWeight)
        canvas.drawLine(padL, goalY, padL + chartW, goalY, Paint().apply {
            color = accentRgb
            alpha = 90
            strokeWidth = 3f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(14f, 14f), 0f)
            isAntiAlias = true
        })

        // Date labels
        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
        val textPaint = Paint().apply {
            color = secondaryRgb
            textSize = 30f
            isAntiAlias = true
            typeface = Typeface.DEFAULT
        }
        val startLabel = sdf.format(Date(startDate)).uppercase()
        val endLabel = sdf.format(Date(goalDate)).uppercase()
        val labelY = bH - 10f
        canvas.drawText(startLabel, padL, labelY, textPaint)
        canvas.drawText(endLabel, bW - padR - textPaint.measureText(endLabel), labelY, textPaint)

        return bitmap
    }
}
