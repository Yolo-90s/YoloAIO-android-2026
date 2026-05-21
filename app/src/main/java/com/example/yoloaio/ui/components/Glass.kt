package com.example.yoloaio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.yoloaio.ui.theme.LocalGlass
import com.example.yoloaio.ui.theme.YoloShapes

/**
 * Solid Material 3 surface card with real elevation + shadow. Keeps the
 * legacy `GlassCard` name so the 30+ call sites don't need a rename.
 *
 * - `strong = true` bumps to a brighter container colour with heavier shadow,
 *   used for hero panels (auth card, settings profile, etc.).
 * - `accentColors` (optional) paints a thin gradient stripe at the very top
 *   of the card — gives the hero / featured cards a distinctive "branded"
 *   edge without overwhelming the surface.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = YoloShapes.Card,
    onClick: (() -> Unit)? = null,
    strong: Boolean = false,
    accentColors: List<Color>? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val glass = LocalGlass.current
    val container = surfaceColor(strong, glass.isDark)
    val border = BorderStroke(0.5.dp, hairlineColor(glass.isDark))
    val shadow = if (strong) 10.dp else 4.dp

    val body: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (accentColors != null && accentColors.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Brush.horizontalGradient(accentColors))
                )
            }
            Box(modifier = Modifier.padding(contentPadding)) { content() }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = container,
            shadowElevation = shadow,
            border = border,
            content = body
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = container,
            shadowElevation = shadow,
            border = border,
            content = body
        )
    }
}

/**
 * Thin variant for chrome — a card without inner padding, so the caller can
 * draw flush content (top app bars, full-width rows, etc.).
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = YoloShapes.Card,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val glass = LocalGlass.current
    val container = surfaceColor(strong, glass.isDark)
    val border = BorderStroke(0.5.dp, hairlineColor(glass.isDark))
    val shadow = if (strong) 8.dp else 3.dp

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = container,
            shadowElevation = shadow,
            border = border,
            content = content
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = container,
            shadowElevation = shadow,
            border = border,
            content = content
        )
    }
}

/**
 * Shared opaque card colour. Public so other chrome (top app bars, input
 * bars) can match the cards pixel-for-pixel.
 */
fun yoloSurfaceColor(strong: Boolean, isDark: Boolean): Color = surfaceColor(strong, isDark)

private fun surfaceColor(strong: Boolean, isDark: Boolean): Color = when {
    isDark && strong -> Color(0xFF1B1726)
    isDark -> Color(0xFF110D1A)
    !isDark && strong -> Color(0xFFF3EDFA)
    else -> Color(0xFFFFFFFF)
}

private fun hairlineColor(isDark: Boolean): Color =
    if (isDark) Color(0x1FFFFFFF) else Color(0x14000000)
