package com.example.yoloaio.features.weather

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fullscreen animated background that matches the current weather condition.
 * Drawn into [Canvas] so it scales smoothly across device sizes without us
 * shipping a per-condition sprite sheet.
 */
@Composable
fun WeatherBackground(condition: WeatherCondition, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brushFor(condition))
    ) {
        when (condition) {
            WeatherCondition.ClearDay -> SunLayer()
            WeatherCondition.ClearNight -> {
                StarsLayer()
                MoonLayer()
            }
            WeatherCondition.Cloudy -> CloudsLayer(count = 7, opacity = 0.75f)
            WeatherCondition.Rain -> {
                CloudsLayer(count = 5, opacity = 0.45f, topBias = true)
                RainLayer(intensity = 1f)
            }
            WeatherCondition.Thunderstorm -> {
                CloudsLayer(count = 5, opacity = 0.5f, topBias = true)
                RainLayer(intensity = 1.6f)
                LightningLayer()
            }
            WeatherCondition.Snow -> {
                CloudsLayer(count = 4, opacity = 0.35f, topBias = true)
                SnowLayer()
            }
            WeatherCondition.Mist -> MistLayer()
        }
    }
}

private fun brushFor(condition: WeatherCondition): Brush = when (condition) {
    WeatherCondition.ClearDay -> Brush.verticalGradient(
        listOf(Color(0xFF4FC3F7), Color(0xFFFFE082))
    )
    WeatherCondition.ClearNight -> Brush.verticalGradient(
        listOf(Color(0xFF0D1B2A), Color(0xFF1B263B))
    )
    WeatherCondition.Cloudy -> Brush.verticalGradient(
        listOf(Color(0xFF546E7A), Color(0xFF90A4AE))
    )
    WeatherCondition.Rain -> Brush.verticalGradient(
        listOf(Color(0xFF263238), Color(0xFF455A64))
    )
    WeatherCondition.Thunderstorm -> Brush.verticalGradient(
        listOf(Color(0xFF14142B), Color(0xFF323264))
    )
    WeatherCondition.Snow -> Brush.verticalGradient(
        listOf(Color(0xFF90CAF9), Color(0xFFE3F2FD))
    )
    WeatherCondition.Mist -> Brush.verticalGradient(
        listOf(Color(0xFF78909C), Color(0xFFCFD8DC))
    )
}

@Composable
private fun SunLayer() {
    val transition = rememberInfiniteTransition(label = "sun")
    val rayAngle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rayAngle"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Box(
        modifier = Modifier.fillMaxSize().padding(top = 70.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(modifier = Modifier.size(280.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val sunR = 70f * pulse
            val innerR = sunR + 16f
            val outerR = sunR + 48f
            for (i in 0 until 12) {
                val a = (rayAngle + i * 30f) * (PI / 180.0).toFloat()
                val sx = cx + cos(a) * innerR
                val sy = cy + sin(a) * innerR
                val ex = cx + cos(a) * outerR
                val ey = cy + sin(a) * outerR
                drawLine(
                    color = Color(0xFFFFF59D).copy(alpha = 0.85f),
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                    strokeWidth = 7f,
                    cap = StrokeCap.Round
                )
            }
            // Glow halo
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFF59D).copy(alpha = 0.5f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = sunR * 2.5f
                ),
                radius = sunR * 2.5f,
                center = Offset(cx, cy)
            )
            // Sun disc
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFFDE7), Color(0xFFFFB74D)),
                    center = Offset(cx, cy),
                    radius = sunR
                ),
                radius = sunR,
                center = Offset(cx, cy)
            )
        }
    }
}

private data class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val phase: Float,
    val speed: Float
)

@Composable
private fun StarsLayer() {
    val stars = remember {
        List(120) { i ->
            Star(
                x = Random.nextFloat(),
                y = Random.nextFloat() * 0.7f,
                size = 1.3f + Random.nextFloat() * 1.7f,
                phase = Random.nextFloat() * 2f * PI.toFloat(),
                speed = 0.4f + Random.nextFloat() * 0.8f
            )
        }
    }
    val twinkle by rememberInfiniteTransition(label = "stars").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "twinkle"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        stars.forEach { s ->
            val a = 0.25f + 0.65f * (0.5f + 0.5f * sin(twinkle * 6.2831853f * s.speed + s.phase))
            drawCircle(
                color = Color.White.copy(alpha = a),
                radius = s.size,
                center = Offset(s.x * w, s.y * h)
            )
        }
    }
}

@Composable
private fun MoonLayer() {
    Box(
        modifier = Modifier.fillMaxSize().padding(top = 70.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val r = 65f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFF9C4).copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r * 2.5f
                ),
                radius = r * 2.5f,
                center = Offset(cx, cy)
            )
            drawCircle(Color(0xFFFFF9C4), r, Offset(cx, cy))
            // Crescent shadow — overlay the background color
            drawCircle(Color(0xFF0D1B2A), r * 0.92f, Offset(cx + 22f, cy - 14f))
        }
    }
}

private data class Cloud(
    val seedX: Float,
    val y: Float,
    val scale: Float,
    val speed: Float,
    val alpha: Float
)

@Composable
private fun CloudsLayer(count: Int, opacity: Float = 0.6f, topBias: Boolean = false) {
    val clouds = remember(count, topBias) {
        List(count) { i ->
            val yRange = if (topBias) 0.05f..0.35f else 0.05f..0.6f
            Cloud(
                seedX = Random.nextFloat(),
                y = yRange.start + Random.nextFloat() * (yRange.endInclusive - yRange.start),
                scale = 0.55f + Random.nextFloat() * 0.7f,
                speed = 0.18f + Random.nextFloat() * 0.35f,
                alpha = opacity * (0.65f + Random.nextFloat() * 0.35f)
            )
        }
    }
    val phase by rememberInfiniteTransition(label = "clouds").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(45_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        clouds.forEach { c ->
            val travel = w + 280f
            val baseX = ((phase * c.speed + c.seedX) % 1f) * travel - 140f
            val baseY = c.y * h
            val r = 38f * c.scale
            val color = Color.White.copy(alpha = c.alpha)
            drawCircle(color, r * 0.9f, Offset(baseX, baseY + r * 0.35f))
            drawCircle(color, r * 1.25f, Offset(baseX + r * 0.95f, baseY))
            drawCircle(color, r * 1.05f, Offset(baseX + r * 1.95f, baseY + r * 0.18f))
            drawCircle(color, r * 0.85f, Offset(baseX + r * 2.7f, baseY + r * 0.5f))
        }
    }
}

private data class Raindrop(
    val x: Float,
    val speed: Float,
    val offset: Float,
    val length: Float
)

@Composable
private fun RainLayer(intensity: Float = 1f) {
    val count = (90 * intensity).toInt().coerceIn(40, 200)
    val drops = remember(count) {
        List(count) {
            Raindrop(
                x = Random.nextFloat(),
                speed = 1.2f + Random.nextFloat() * 0.7f,
                offset = Random.nextFloat(),
                length = 14f + Random.nextFloat() * 16f
            )
        }
    }
    val phase by rememberInfiniteTransition(label = "rain").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drops.forEach { d ->
            val y = ((phase * d.speed + d.offset) % 1f) * (h + d.length) - d.length
            val x = d.x * w
            drawLine(
                color = Color(0xFFE1F5FE).copy(alpha = 0.72f),
                start = Offset(x, y),
                end = Offset(x + 2f, y + d.length),
                strokeWidth = 1.8f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun LightningLayer() {
    var flashAlpha by remember { mutableStateOf(0f) }
    LaunchedEffectFlasher { flashAlpha = it }
    if (flashAlpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = flashAlpha))
        )
    }
}

@Composable
private fun LaunchedEffectFlasher(onChange: (Float) -> Unit) {
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(3500L, 9000L))
            // Double-flash for realism
            onChange(0.7f); delay(80); onChange(0f); delay(70)
            onChange(0.9f); delay(120); onChange(0f)
        }
    }
}

private data class Snowflake(
    val x: Float,
    val size: Float,
    val speed: Float,
    val offset: Float,
    val swayAmp: Float,
    val swayPhase: Float
)

@Composable
private fun SnowLayer() {
    val flakes = remember {
        List(70) {
            Snowflake(
                x = Random.nextFloat(),
                size = 2.5f + Random.nextFloat() * 4.5f,
                speed = 0.35f + Random.nextFloat() * 0.5f,
                offset = Random.nextFloat(),
                swayAmp = 10f + Random.nextFloat() * 18f,
                swayPhase = Random.nextFloat() * 2f * PI.toFloat()
            )
        }
    }
    val phase by rememberInfiniteTransition(label = "snow").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        flakes.forEach { f ->
            val y = ((phase * f.speed + f.offset) % 1f) * (h + f.size * 4) - f.size * 2
            val swayX = f.x * w + sin(phase * 6.2831853f + f.swayPhase) * f.swayAmp
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = f.size,
                center = Offset(swayX, y)
            )
        }
    }
}

@Composable
private fun MistLayer() {
    val phase by rememberInfiniteTransition(label = "mist").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val bands = remember {
        List(10) { i ->
            Triple(
                Random.nextFloat(),               // y fraction
                0.6f + Random.nextFloat() * 0.4f, // width factor
                Random.nextFloat()                // phase offset
            )
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        bands.forEach { (yFrac, widthFactor, off) ->
            val travel = w + 400f
            val x = ((phase + off) % 1f) * travel - 200f
            val bandW = 320f * widthFactor + 120f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.18f),
                topLeft = Offset(x, yFrac * h),
                size = Size(bandW, 26f),
                cornerRadius = CornerRadius(13f, 13f)
            )
        }
    }
}
