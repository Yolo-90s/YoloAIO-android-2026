package com.example.yoloaio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Genuinely translucent "frosted glass" card — blurs whatever's behind it
 * via [LocalHazeState] (provided by [AppBackground], which marks the ambient
 * gradient + blobs as the blur source) and tints the result with a
 * palette-relative translucent fill from [LocalGlass]. On API levels below
 * 31 (where Compose has no native RenderEffect blur), `haze` automatically
 * falls back to [HazeStyle.fallbackTint] — a slightly stronger solid-ish
 * tint — so the card still reads as a deliberate glass surface, just without
 * the blur itself.
 *
 * Keeps the legacy `GlassCard` name + signature so the 30+ existing call
 * sites don't need to change.
 *
 * - `strong = true` bumps to a brighter/more opaque tint, used for hero
 *   panels (auth card, settings profile, etc.).
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
    val hazeModifier = Modifier.glassEffect(strong)
    val border = BorderStroke(0.5.dp, hairlineColor())
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
            modifier = modifier.then(hazeModifier),
            shape = shape,
            color = Color.Transparent,
            shadowElevation = shadow,
            border = border,
            content = body
        )
    } else {
        Surface(
            modifier = modifier.then(hazeModifier),
            shape = shape,
            color = Color.Transparent,
            shadowElevation = shadow,
            border = border,
            content = body
        )
    }
}

/**
 * Thin variant for chrome — a glass surface without inner padding, so the
 * caller can draw flush content (top app bars, full-width rows, etc.). Same
 * real translucency as [GlassCard].
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = YoloShapes.Card,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val hazeModifier = Modifier.glassEffect(strong)
    val border = BorderStroke(0.5.dp, hairlineColor())
    val shadow = if (strong) 8.dp else 3.dp

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.then(hazeModifier),
            shape = shape,
            color = Color.Transparent,
            shadowElevation = shadow,
            border = border,
            content = content
        )
    } else {
        Surface(
            modifier = modifier.then(hazeModifier),
            shape = shape,
            color = Color.Transparent,
            shadowElevation = shadow,
            border = border,
            content = content
        )
    }
}

/**
 * The actual frosted-glass blur + tint, shared by [GlassCard]/[GlassSurface].
 * Reads [LocalGlass] for palette-relative fill colors and [LocalHazeState]
 * for what to blur. Public so other chrome (top app bars, input bars) can
 * apply the identical glass treatment directly via `Modifier`.
 */
@Composable
fun Modifier.glassEffect(strong: Boolean = false): Modifier {
    val glass = LocalGlass.current
    val hazeState = LocalHazeState.current
    val tintColor = if (strong) glass.fillStrong else glass.fill
    val fallbackColor = if (strong) glass.fillStrong.copy(alpha = (glass.fillStrong.alpha + 0.35f).coerceAtMost(1f))
        else glass.fill.copy(alpha = (glass.fill.alpha + 0.35f).coerceAtMost(1f))

    return this.hazeEffect(
        state = hazeState,
        style = HazeStyle(
            tint = HazeTint(tintColor),
            blurRadius = 20.dp,
            fallbackTint = HazeTint(fallbackColor)
        )
    )
}

/**
 * Legacy solid color for chrome that hasn't moved to real glass yet (e.g.
 * bars that paint their own background rather than using [GlassSurface]
 * directly). Unchanged from before this redesign — kept so existing call
 * sites keep looking the same until they're migrated.
 */
fun yoloSurfaceColor(strong: Boolean, isDark: Boolean): Color = when {
    isDark && strong -> Color(0xFF1B1726)
    isDark -> Color(0xFF110D1A)
    !isDark && strong -> Color(0xFFF3EDFA)
    else -> Color(0xFFFFFFFF)
}

@Composable
private fun hairlineColor(): Color = LocalGlass.current.border
