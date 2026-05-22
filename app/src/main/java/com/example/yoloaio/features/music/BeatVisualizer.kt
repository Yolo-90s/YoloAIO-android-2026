package com.example.yoloaio.features.music

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// =========================================================================
// Public composables
// =========================================================================

/**
 * Horizontal bar-row visualiser. Two modes:
 *  - `liveBands != null` → real FFT magnitudes from [BeatAnalyzer]
 *  - otherwise → synthetic animation
 *
 * Bass region (low FFT bins) gets a 1.6× amplitude boost so kicks land
 * harder than mid/high content. The optional `pulse` parameter lifts
 * every bar uniformly on each detected drum onset.
 */
@Composable
fun BeatVisualizer(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 36,
    liveBands: FloatArray? = null,
    pulse: Float = 0f
) {
    if (liveBands != null && liveBands.isNotEmpty()) {
        LiveLinearBars(liveBands, isPlaying, color, modifier, barCount, pulse)
    } else {
        SyntheticLinearBars(isPlaying, color, modifier, barCount)
    }
}

/**
 * Radial bar visualiser. Designed to wrap a circular album-art element —
 * draws bars starting at [innerRadiusFraction] of the canvas radius,
 * extending outward to the canvas edge.
 *
 * Put a circular Box (album art clipped to CircleShape) at the centre of
 * the same parent Box, sized so its diameter matches inner radius × 2,
 * and the bars will frame it nicely.
 *
 * The optional `pulse` parameter pushes the ring outward by up to ~6% of
 * the outer radius on each drum onset, making the visualiser breathe in
 * time with the beat.
 */
@Composable
fun CircularBeatVisualizer(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 64,
    innerRadiusFraction: Float = 0.72f,
    liveBands: FloatArray? = null,
    pulse: Float = 0f
) {
    if (liveBands != null && liveBands.isNotEmpty()) {
        LiveRadialBars(liveBands, isPlaying, color, modifier, barCount, innerRadiusFraction, pulse)
    } else {
        SyntheticRadialBars(isPlaying, color, modifier, barCount, innerRadiusFraction)
    }
}

// =========================================================================
// Live (FFT-driven) bar height
// =========================================================================

private fun liveBarHeight(
    barIndex: Int,
    barCount: Int,
    bands: FloatArray,
    isPlaying: Boolean
): Float {
    val maxBin = (bands.size - 1).coerceAtMost(128)
    if (maxBin < 2) return 0.06f

    // Logarithmic frequency mapping — more bars dedicated to lows.
    val fracStart = barIndex.toFloat() / barCount
    val fracEnd = (barIndex + 1).toFloat() / barCount
    val binStart = maxBin.toDouble().pow(fracStart.toDouble())
        .toInt().coerceAtLeast(1)
    val binEnd = maxBin.toDouble().pow(fracEnd.toDouble())
        .toInt().coerceAtLeast(binStart + 1).coerceAtMost(maxBin)

    var sum = 0f
    var n = 0
    for (b in binStart..binEnd) {
        sum += bands[b]
        n++
    }
    val mean = if (n > 0) sum / n else 0f

    // Bass-region boost so kicks dominate the silhouette.
    val amplification = when {
        barIndex < barCount / 4 -> 1.6f
        barIndex < barCount / 2 -> 1.2f
        else -> 0.95f
    }
    val raw = (mean * amplification * 3.5f).coerceIn(0.05f, 1f)
    return if (isPlaying) raw else raw.coerceAtMost(0.18f)
}

// =========================================================================
// Synthetic bar height
// =========================================================================

private data class BarSpec(
    val phaseOffset: Float,
    val freqMultiplier: Float,
    val baseHeight: Float,
    val amplitude: Float
)

private fun syntheticBarHeight(spec: BarSpec, isPlaying: Boolean, phase: Float): Float {
    return if (isPlaying) {
        val sample = sin(phase * spec.freqMultiplier + spec.phaseOffset)
        spec.baseHeight + spec.amplitude * ((sample + 1f) / 2f)
    } else {
        spec.baseHeight * 0.6f
    }.coerceIn(0.06f, 1f)
}

@Composable
private fun rememberBarSpecs(barCount: Int): List<BarSpec> =
    remember(barCount) {
        List(barCount) {
            BarSpec(
                phaseOffset = Random.nextFloat() * 2f * PI.toFloat(),
                freqMultiplier = 0.7f + Random.nextFloat() * 0.7f,
                baseHeight = 0.18f + Random.nextFloat() * 0.30f,
                amplitude = 0.30f + Random.nextFloat() * 0.55f
            )
        }
    }

@Composable
private fun rememberLoopingPhase(label: String): Float {
    val phase by rememberInfiniteTransition(label = label).animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "$label-phase"
    )
    return phase
}

// =========================================================================
// Linear renderers
// =========================================================================

@Composable
private fun LiveLinearBars(
    bands: FloatArray,
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier,
    barCount: Int,
    pulse: Float
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barWidth = (w * 0.60f) / barCount
        val gap = (w - barWidth * barCount) / (barCount - 1).coerceAtLeast(1)
        val cornerR = barWidth / 2f
        // Drum onset lifts every bar uniformly — small enough that
        // sustained sections look natural, big enough that kicks read.
        val pulseLift = pulse * 0.20f
        for (i in 0 until barCount) {
            val base = liveBarHeight(i, barCount, bands, isPlaying)
            val heightFraction = (base + pulseLift).coerceIn(0.05f, 1f)
            val barH = h * heightFraction
            val y = (h - barH) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(cornerR, cornerR)
            )
        }
    }
}

@Composable
private fun SyntheticLinearBars(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier,
    barCount: Int
) {
    val specs = rememberBarSpecs(barCount)
    val phase = rememberLoopingPhase("synth-linear")
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barWidth = (w * 0.60f) / barCount
        val gap = (w - barWidth * barCount) / (barCount - 1).coerceAtLeast(1)
        val cornerR = barWidth / 2f
        for (i in 0 until barCount) {
            val heightFraction = syntheticBarHeight(specs[i], isPlaying, phase)
            val barH = h * heightFraction
            val y = (h - barH) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(cornerR, cornerR)
            )
        }
    }
}

// =========================================================================
// Radial renderers
// =========================================================================

@Composable
private fun LiveRadialBars(
    bands: FloatArray,
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier,
    barCount: Int,
    innerRadiusFraction: Float,
    pulse: Float
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val outerR = minOf(w, h) / 2f
        // Drum onset pushes the whole ring outward up to 6% of the outer
        // radius, then springs back — gives the visualiser a heartbeat
        // that follows the kick instead of just twitching.
        val pulseRadius = outerR * 0.06f * pulse
        val innerR = outerR * innerRadiusFraction
        val gap = outerR * 0.012f
        val barInner = innerR + gap + pulseRadius
        val maxBarLength = (outerR - barInner - gap).coerceAtLeast(1f)
        // Bar width = half the arc spacing → bars look spaced, not solid ring.
        val barStroke = (2f * PI.toFloat() * barInner / barCount) * 0.55f
        for (i in 0 until barCount) {
            val heightFraction = liveBarHeight(i, barCount, bands, isPlaying)
            // Start angle from top (-π/2) so bar 0 is at 12 o'clock.
            val angle = (i.toFloat() / barCount) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val cosA = cos(angle)
            val sinA = sin(angle)
            val r1 = barInner
            val r2 = barInner + maxBarLength * heightFraction
            drawLine(
                color = color,
                start = Offset(cx + cosA * r1, cy + sinA * r1),
                end = Offset(cx + cosA * r2, cy + sinA * r2),
                strokeWidth = barStroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun SyntheticRadialBars(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier,
    barCount: Int,
    innerRadiusFraction: Float
) {
    val specs = rememberBarSpecs(barCount)
    val phase = rememberLoopingPhase("synth-radial")
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val outerR = minOf(w, h) / 2f
        val innerR = outerR * innerRadiusFraction
        val gap = outerR * 0.012f
        val barInner = innerR + gap
        val maxBarLength = (outerR - barInner - gap).coerceAtLeast(1f)
        val barStroke = (2f * PI.toFloat() * barInner / barCount) * 0.55f
        for (i in 0 until barCount) {
            val heightFraction = syntheticBarHeight(specs[i], isPlaying, phase)
            val angle = (i.toFloat() / barCount) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val cosA = cos(angle)
            val sinA = sin(angle)
            val r1 = barInner
            val r2 = barInner + maxBarLength * heightFraction
            drawLine(
                color = color,
                start = Offset(cx + cosA * r1, cy + sinA * r1),
                end = Offset(cx + cosA * r2, cy + sinA * r2),
                strokeWidth = barStroke,
                cap = StrokeCap.Round
            )
        }
    }
}
