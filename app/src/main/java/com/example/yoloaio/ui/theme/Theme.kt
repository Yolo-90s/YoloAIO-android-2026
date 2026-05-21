package com.example.yoloaio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class GlassPalette(
    val fill: Color,
    val fillStrong: Color,
    val border: Color,
    val highlight: Color,
    val blobA: Color,
    val blobB: Color,
    val blobC: Color,
    val baseTop: Color,
    val baseMid: Color,
    val baseBottom: Color,
    val isDark: Boolean
)

val LocalGlass = staticCompositionLocalOf<GlassPalette> {
    error("GlassPalette not provided")
}

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant
)

private val LightGlass = GlassPalette(
    fill = LightGlassFill,
    fillStrong = LightGlassFillStrong,
    border = LightGlassBorder,
    highlight = LightGlassHighlight,
    blobA = LightBlobA,
    blobB = LightBlobB,
    blobC = LightBlobC,
    baseTop = LightBlobBase1,
    baseMid = LightBlobBase2,
    baseBottom = LightBlobBase3,
    isDark = false
)

private val DarkGlass = GlassPalette(
    fill = DarkGlassFill,
    fillStrong = DarkGlassFillStrong,
    border = DarkGlassBorder,
    highlight = DarkGlassHighlight,
    blobA = DarkBlobA,
    blobB = DarkBlobB,
    blobC = DarkBlobC,
    baseTop = DarkBlobBase1,
    baseMid = DarkBlobBase2,
    baseBottom = DarkBlobBase3,
    isDark = true
)

/**
 * Overlays the user-selected [ThemePalette] onto the base scheme. We only
 * recolor the accent triple (primary/secondary/tertiary) plus the three
 * background blobs — containers and "on" colors stay baseline so contrast
 * doesn't break when the palette changes.
 */
private fun ColorScheme.applyPalette(palette: ThemePalette): ColorScheme = copy(
    primary = palette.primary,
    secondary = palette.secondary,
    tertiary = palette.tertiary
)

private fun GlassPalette.applyPalette(palette: ThemePalette): GlassPalette = copy(
    blobA = palette.blobA,
    blobB = palette.blobB,
    blobC = palette.blobC
)

@Composable
fun YoloAIOTheme(
    palette: ThemePalette = ThemePalette.Default,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val baseColors = if (darkTheme) DarkColors else LightColors
    val baseGlass = if (darkTheme) DarkGlass else LightGlass

    val colors = baseColors.applyPalette(palette)
    val glass = baseGlass.applyPalette(palette)

    // Provide LocalContentColor at the theme root. Without this, any Text
    // that doesn't pass an explicit `color` falls back to Color.Black —
    // invisible on the dark gradient background. By pinning it to
    // onBackground, every screen automatically gets readable text in
    // whichever mode the app is in.
    CompositionLocalProvider(
        LocalGlass provides glass,
        LocalContentColor provides colors.onBackground
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
