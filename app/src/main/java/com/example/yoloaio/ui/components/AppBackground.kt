package com.example.yoloaio.ui.components

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.yoloaio.ui.theme.LocalGlass
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Two-layer ambient backdrop:
 *  1. Fixed vertical gradient from the palette's `baseTop` → `baseMid` →
 *     `baseBottom`. Provides the deep indigo-black canvas.
 *  2. Two large radial accents that drift very slowly along a circular path
 *     (~60s per loop). They're huge and soft, so even at full opacity the
 *     motion reads as gentle atmosphere — never distracting from content.
 *
 * No blur passes, no offscreen surfaces — cheap to render on any device.
 */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val glass = LocalGlass.current

    val transition = rememberInfiniteTransition(label = "appBgDrift")
    val driftPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(glass.baseTop, glass.baseMid, glass.baseBottom)
                    )
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Accent A — top region, orbits a small circle near the upper-right
            val ax = w * (0.78f + 0.05f * cos(driftPhase))
            val ay = h * (0.12f + 0.03f * sin(driftPhase))
            val ar = w * 0.95f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glass.blobB.copy(alpha = if (glass.isDark) 0.28f else 0.20f),
                        Color.Transparent
                    ),
                    center = Offset(ax, ay),
                    radius = ar
                ),
                radius = ar,
                center = Offset(ax, ay)
            )

            // Accent B — bottom-left, orbits opposite direction so the two
            // never sync up. Slightly smaller, slightly different hue.
            val bx = w * (0.10f + 0.06f * cos(driftPhase + PI.toFloat()))
            val by = h * (0.90f + 0.04f * sin(driftPhase + PI.toFloat()))
            val br = w * 0.80f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glass.blobA.copy(alpha = if (glass.isDark) 0.22f else 0.16f),
                        Color.Transparent
                    ),
                    center = Offset(bx, by),
                    radius = br
                ),
                radius = br,
                center = Offset(bx, by)
            )

            // Accent C — mid-screen, slow contrasting hue, smaller. Adds a
            // third nucleus so the composition never feels static.
            val cx = w * (0.45f + 0.10f * cos(driftPhase * 0.7f + 1.5f))
            val cy = h * (0.55f + 0.07f * sin(driftPhase * 0.7f + 1.5f))
            val cr = w * 0.55f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glass.blobC.copy(alpha = if (glass.isDark) 0.16f else 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = cr
                ),
                radius = cr,
                center = Offset(cx, cy)
            )
        }

        content()
    }
}
