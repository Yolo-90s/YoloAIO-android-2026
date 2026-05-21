package com.example.yoloaio.features.weather

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yoloaio.data.LocalAppConfig
import com.example.yoloaio.ui.components.FeatureScaffold
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private sealed interface WeatherState {
    data object Initial : WeatherState
    data object Loading : WeatherState
    data object NeedsPermission : WeatherState
    data object NoLocation : WeatherState
    data object MissingKey : WeatherState
    data class Ready(val weather: WeatherInfo) : WeatherState
    data class Error(val message: String) : WeatherState
}

@Composable
fun WeatherScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val config = LocalAppConfig.current

    var hasPermission by remember { mutableStateOf(LocationProvider.hasPermission(context)) }
    var state by remember { mutableStateOf<WeatherState>(WeatherState.Initial) }
    var reloadKey by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasPermission = granted
        if (granted) reloadKey++
    }

    LaunchedEffect(hasPermission, config.weatherApiKey, reloadKey) {
        if (config.weatherApiKey.isBlank()) {
            state = WeatherState.MissingKey
            return@LaunchedEffect
        }
        if (!hasPermission) {
            state = WeatherState.NeedsPermission
            return@LaunchedEffect
        }
        state = WeatherState.Loading
        val location = LocationProvider.lastKnown(context)
        if (location == null) {
            state = WeatherState.NoLocation
            return@LaunchedEffect
        }
        WeatherClient.fetch(location.latitude, location.longitude, config.weatherApiKey)
            .onSuccess { state = WeatherState.Ready(it) }
            .onFailure { state = WeatherState.Error(it.message ?: "Failed to load") }
    }

    FeatureScaffold(title = "Weather", onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (val s = state) {
                is WeatherState.Ready -> WeatherBackground(s.weather.condition)
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1B263B), Color(0xFF415A77))
                            )
                        )
                )
            }

            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (val s = state) {
                    WeatherState.Initial, WeatherState.Loading -> Centered {
                        CircularProgressIndicator(color = Color.White)
                    }
                    WeatherState.MissingKey -> EmptyState(
                        icon = Icons.Rounded.CloudOff,
                        title = "Weather key missing",
                        message = "Add `weatherApiKey` to the Firestore config/app document. " +
                            "Get a free key at openweathermap.org/api."
                    )
                    WeatherState.NeedsPermission -> NeedsPermissionState(onRequest = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    })
                    WeatherState.NoLocation -> EmptyState(
                        icon = Icons.Rounded.LocationOff,
                        title = "No location available",
                        message = "Turn on Location Services in system settings, then open a " +
                            "map app once so a recent fix is cached, then come back and refresh.",
                        actionLabel = "Retry",
                        onAction = { reloadKey++ }
                    )
                    is WeatherState.Error -> EmptyState(
                        icon = Icons.Rounded.CloudOff,
                        title = "Couldn't load",
                        message = s.message,
                        actionLabel = "Retry",
                        onAction = { reloadKey++ }
                    )
                    is WeatherState.Ready -> WeatherContent(
                        weather = s.weather,
                        onRefresh = { reloadKey++ }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherContent(weather: WeatherInfo, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LocationHeader(weather = weather, onRefresh = onRefresh)
        HeroSection(weather = weather)
        QuickStatsRow(weather = weather)
        if (weather.hourly.isNotEmpty()) HourlyForecastCard(weather.hourly)
        if (weather.daily.isNotEmpty()) DailyForecastCard(weather.daily)
        DetailsCard(weather)
        SunArcCard(weather)
        UpdatedFooter(weather.observedAtEpochMs)
        Spacer(Modifier.height(8.dp))
    }
}

// ─────────────────────── header ───────────────────────

@Composable
private fun LocationHeader(weather: WeatherInfo, onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.LocationOn,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                weather.locationName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            if (weather.countryCode.isNotBlank()) {
                Text(
                    weather.countryCode,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = Color.White)
        }
    }
}

// ─────────────────────── hero ───────────────────────

@Composable
private fun HeroSection(weather: WeatherInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Big condition icon overhead — gives the hero a clear focal
        // anchor before the user reads the temperature.
        Icon(
            iconFor(weather.condition),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                "${weather.tempC.roundToInt()}",
                fontWeight = FontWeight.Thin,
                fontSize = 116.sp,
                color = Color.White
            )
            Text(
                "°",
                fontWeight = FontWeight.Thin,
                fontSize = 56.sp,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 14.dp)
            )
        }
        Text(
            weather.description,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

@Composable
private fun QuickStatsRow(weather: WeatherInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Chip("Feels like", "${weather.feelsLikeC.roundToInt()}°", Modifier.weight(1f))
        Chip("High", "${weather.tempMaxC.roundToInt()}°", Modifier.weight(1f))
        Chip("Low", "${weather.tempMinC.roundToInt()}°", Modifier.weight(1f))
    }
}

@Composable
private fun Chip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            value,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─────────────────────── hourly forecast ───────────────────────

@Composable
private fun HourlyForecastCard(hourly: List<HourEntry>) {
    GlassPanel {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            SectionTitle("Next 24 hours")
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(hourly, key = { it.timeEpochMs }) { entry ->
                    HourCell(entry)
                }
            }
        }
    }
}

@Composable
private fun HourCell(entry: HourEntry) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            formatHour(entry.timeEpochMs),
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(Modifier.height(6.dp))
        Icon(
            iconFor(entry.condition),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${entry.tempC.roundToInt()}°",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (entry.popPct >= 20) {
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Umbrella,
                    contentDescription = null,
                    tint = Color(0xFF80D8FF),
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    "${entry.popPct}%",
                    color = Color(0xFF80D8FF),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// ─────────────────────── daily forecast ───────────────────────

@Composable
private fun DailyForecastCard(daily: List<DayEntry>) {
    GlassPanel {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitleInline("5-day forecast")
            Spacer(Modifier.height(10.dp))
            val overallMin = daily.minOf { it.minC }
            val overallMax = daily.maxOf { it.maxC }
            daily.forEach { day ->
                DayRow(
                    day = day,
                    overallMin = overallMin,
                    overallMax = overallMax
                )
                if (day != daily.last()) {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DayRow(day: DayEntry, overallMin: Double, overallMax: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            formatDay(day.dateEpochMs),
            modifier = Modifier.width(56.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            iconFor(day.condition),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(8.dp))
        if (day.popPct >= 20) {
            Text(
                "${day.popPct}%",
                color = Color(0xFF80D8FF),
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            Spacer(Modifier.width(28.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            "${day.minC.roundToInt()}°",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(34.dp)
        )
        // Temp-range bar — visual hint at how the day's range sits inside the
        // overall 5-day envelope.
        TempRangeBar(
            min = day.minC,
            max = day.maxC,
            overallMin = overallMin,
            overallMax = overallMax,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        )
        Text(
            "${day.maxC.roundToInt()}°",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(34.dp)
        )
    }
}

@Composable
private fun TempRangeBar(
    min: Double,
    max: Double,
    overallMin: Double,
    overallMax: Double,
    modifier: Modifier = Modifier
) {
    val range = (overallMax - overallMin).coerceAtLeast(1.0)
    val startFrac = ((min - overallMin) / range).coerceIn(0.0, 1.0).toFloat()
    val endFrac = ((max - overallMin) / range).coerceIn(0.0, 1.0).toFloat()
    Canvas(
        modifier = modifier.height(6.dp)
    ) {
        val w = size.width
        val h = size.height
        // Track
        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(0f, 0f),
            size = Size(w, h),
            cornerRadius = CornerRadius(h / 2)
        )
        // Filled segment (cool blue → warm orange gradient)
        val barStart = w * startFrac
        val barEnd = w * endFrac
        val barWidth = (barEnd - barStart).coerceAtLeast(h)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF4FC3F7), Color(0xFFFFB300), Color(0xFFFF7043)),
                startX = 0f,
                endX = w
            ),
            topLeft = Offset(barStart, 0f),
            size = Size(barWidth, h),
            cornerRadius = CornerRadius(h / 2)
        )
    }
}

// ─────────────────────── details ───────────────────────

@Composable
private fun DetailsCard(weather: WeatherInfo) {
    GlassPanel {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitleInline("Details")
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Stat(Icons.Rounded.Opacity, "Humidity", "${weather.humidityPct}%")
                Stat(Icons.Rounded.Air, "Wind", "${weather.windKph.roundToInt()} km/h")
                Stat(Icons.Rounded.Speed, "Pressure", "${weather.pressureHpa} hPa")
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Stat(Icons.Rounded.Cloud, "Clouds", "${weather.cloudsPct}%")
                Stat(
                    Icons.Rounded.Visibility,
                    "Visibility",
                    if (weather.visibilityKm > 0) "${"%.1f".format(weather.visibilityKm)} km"
                    else "—"
                )
                Stat(
                    Icons.Rounded.Thermostat,
                    "Feels like",
                    "${weather.feelsLikeC.roundToInt()}°"
                )
            }
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

// ─────────────────────── sun arc ───────────────────────

@Composable
private fun SunArcCard(weather: WeatherInfo) {
    if (weather.sunriseEpochMs <= 0 || weather.sunsetEpochMs <= 0) return
    GlassPanel {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.WbSunny,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Sun",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(12.dp))
            SunArc(
                sunriseMs = weather.sunriseEpochMs,
                sunsetMs = weather.sunsetEpochMs,
                nowMs = weather.observedAtEpochMs
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Sunrise",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        formatTime(weather.sunriseEpochMs),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Sunset",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        formatTime(weather.sunsetEpochMs),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SunArc(sunriseMs: Long, sunsetMs: Long, nowMs: Long) {
    val dayLength = (sunsetMs - sunriseMs).coerceAtLeast(1L)
    val progress = ((nowMs - sunriseMs).toDouble() / dayLength).coerceIn(0.0, 1.0).toFloat()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val w = size.width
        val h = size.height
        val margin = 16f
        // Arc spans from (margin, h-12) to (w-margin, h-12), apex at center top.
        val baselineY = h - 12f
        val arcWidth = w - 2 * margin

        // Build arc path (a parabola-ish — half-cosine for simplicity).
        val path = Path().apply {
            moveTo(margin, baselineY)
            for (i in 0..50) {
                val t = i / 50f
                val x = margin + arcWidth * t
                val y = baselineY - (h - 24f) * sin(t * PI.toFloat())
                lineTo(x, y)
            }
        }

        // Dashed full arc (the full day path).
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.25f),
            style = Stroke(
                width = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
        )

        // Solid filled segment from sunrise → now.
        val tNow = progress
        val pathDone = Path().apply {
            moveTo(margin, baselineY)
            val steps = (50 * tNow).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val t = i / 50f
                val x = margin + arcWidth * t
                val y = baselineY - (h - 24f) * sin(t * PI.toFloat())
                lineTo(x, y)
            }
        }
        drawPath(
            path = pathDone,
            brush = Brush.horizontalGradient(
                listOf(Color(0xFFFFB300), Color(0xFFFFE082))
            ),
            style = Stroke(width = 3.dp.toPx())
        )

        // Sun dot at "now".
        val sunX = margin + arcWidth * tNow
        val sunY = baselineY - (h - 24f) * sin(tNow * PI.toFloat())
        drawCircle(
            color = Color(0xFFFFE082).copy(alpha = 0.35f),
            center = Offset(sunX, sunY),
            radius = 14f
        )
        drawCircle(
            color = Color(0xFFFFB300),
            center = Offset(sunX, sunY),
            radius = 7f
        )

        // Horizon line.
        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(0f, baselineY),
            end = Offset(w, baselineY),
            strokeWidth = 1f
        )
    }
}

// ─────────────────────── footer ───────────────────────

@Composable
private fun UpdatedFooter(observedAtMs: Long) {
    Text(
        "Updated ${formatRelative(observedAtMs)}",
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

// ─────────────────────── glass panel + section title ───────────────────────

@Composable
private fun GlassPanel(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            // Slightly darker glass + brighter hairline border than v1 —
            // reads cleaner against the animated weather backdrop and
            // gives panel edges a subtle "pop" without being heavy.
            .background(Color.Black.copy(alpha = 0.32f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(24.dp)
            )
    ) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = 0.85f),
        modifier = Modifier.padding(start = 16.dp)
    )
}

@Composable
private fun SectionTitleInline(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = 0.85f)
    )
}

// ─────────────────────── icon mapping ───────────────────────

private fun iconFor(condition: WeatherCondition): ImageVector = when (condition) {
    WeatherCondition.ClearDay -> Icons.Rounded.WbSunny
    WeatherCondition.ClearNight -> Icons.Rounded.Nightlight
    WeatherCondition.Cloudy -> Icons.Rounded.Cloud
    WeatherCondition.Rain -> Icons.Rounded.Umbrella
    WeatherCondition.Thunderstorm -> Icons.Rounded.FlashOn
    WeatherCondition.Snow -> Icons.Rounded.AcUnit
    WeatherCondition.Mist -> Icons.Rounded.BlurOn
}

// ─────────────────────── formatters ───────────────────────

private fun formatTime(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    return fmt.format(Date(epochMs))
}

private fun formatHour(epochMs: Long): String {
    val fmt = SimpleDateFormat("ha", Locale.getDefault())
    return fmt.format(Date(epochMs)).lowercase()
}

private fun formatDay(epochMs: Long): String {
    val nowCal = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val sameDay = nowCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
        nowCal.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "Today"
    val fmt = SimpleDateFormat("EEE", Locale.getDefault())
    return fmt.format(Date(epochMs))
}

private fun formatRelative(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    val deltaSec = (System.currentTimeMillis() - epochMs) / 1000
    return when {
        deltaSec < 30 -> "just now"
        deltaSec < 90 -> "1 min ago"
        deltaSec < 3600 -> "${deltaSec / 60} min ago"
        deltaSec < 86_400 -> "${deltaSec / 3600} h ago"
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(epochMs))
    }
}

// ─────────────────────── helper composables (loading / errors) ───────────────────────

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
private fun NeedsPermissionState(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.LocationOff,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = Color.White
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Allow location for weather",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "We use your last known location to fetch local conditions. " +
                "Nothing leaves your device except a single lat/lon request " +
                "to OpenWeatherMap.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f)
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequest,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("Allow location") }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = Color.White
        )
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text(actionLabel) }
        }
    }
}
