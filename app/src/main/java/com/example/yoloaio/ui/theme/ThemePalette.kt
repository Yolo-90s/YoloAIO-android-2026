package com.example.yoloaio.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A selectable color palette that recolors the app's accents (primary,
 * secondary, tertiary) plus the three blurred background blobs behind the
 * glass surfaces. Containers and "on" colors are intentionally NOT in here —
 * they inherit from the base dark/light scheme so contrast and readability
 * stay consistent across palettes.
 *
 * The current generation (2026) of dark-first apps lean on:
 *  - One saturated hero accent (the primary) instead of a rainbow.
 *  - A complementary cool/warm secondary that sits ~120° around the wheel.
 *  - A small "support" tertiary used sparingly for warning/info pops.
 *  - Background blobs that ECHO the accents (same hue family) rather than
 *    fight them — this keeps the ambient backdrop feeling intentional.
 *
 * Each palette below is curated rather than auto-generated: hex values
 * were tuned for ~7:1 contrast against the dark base + sufficient
 * separation between the three accent stops.
 */
data class ThemePalette(
    val key: String,
    val displayName: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val blobA: Color,
    val blobB: Color,
    val blobC: Color
) {
    companion object {
        /** Vivid magenta hero on a deep-purple bed — current default. */
        val Aurora = ThemePalette(
            key = "aurora",
            displayName = "Aurora",
            primary = Color(0xFFE040FB),
            secondary = Color(0xFF7C9CFF),
            tertiary = Color(0xFFFFB36B),
            blobA = Color(0xFFD500F9),
            blobB = Color(0xFF536DFE),
            blobC = Color(0xFFAB47BC)
        )

        /** Hot coral with a tangerine warm-up — feels like late afternoon. */
        val Ember = ThemePalette(
            key = "ember",
            displayName = "Ember",
            primary = Color(0xFFFF6B6B),
            secondary = Color(0xFFFFB347),
            tertiary = Color(0xFFFFE066),
            blobA = Color(0xFFE63946),
            blobB = Color(0xFFF77F00),
            blobC = Color(0xFFFCBF49)
        )

        /** Mint green over deep teal — the Linear / Vercel dark look. */
        val Mint = ThemePalette(
            key = "mint",
            displayName = "Mint",
            primary = Color(0xFF00E5A8),
            secondary = Color(0xFF6EA8FE),
            tertiary = Color(0xFFB0F2C2),
            blobA = Color(0xFF00897B),
            blobB = Color(0xFF26A69A),
            blobC = Color(0xFF1DE9B6)
        )

        /** Electric cobalt — Discord-blurple meets cyber neon. */
        val Cobalt = ThemePalette(
            key = "cobalt",
            displayName = "Cobalt",
            primary = Color(0xFF5865F2),
            secondary = Color(0xFF4CDDF7),
            tertiary = Color(0xFFB388FF),
            blobA = Color(0xFF1A237E),
            blobB = Color(0xFF0277BD),
            blobC = Color(0xFF512DA8)
        )

        /** Sakura-pink with lavender support — soft, playful. */
        val Bloom = ThemePalette(
            key = "bloom",
            displayName = "Bloom",
            primary = Color(0xFFFF6B9D),
            secondary = Color(0xFFC8B4FF),
            tertiary = Color(0xFFFFD3E1),
            blobA = Color(0xFFD81B60),
            blobB = Color(0xFFAB47BC),
            blobC = Color(0xFFEC407A)
        )

        /** Forest sage + mossy gold — earthy and grounded. */
        val Sage = ThemePalette(
            key = "sage",
            displayName = "Sage",
            primary = Color(0xFF66E07A),
            secondary = Color(0xFFB7E83C),
            tertiary = Color(0xFFFFD740),
            blobA = Color(0xFF1B5E20),
            blobB = Color(0xFF558B2F),
            blobC = Color(0xFF827717)
        )

        /** Acid yellow + jet — high-contrast cyber aesthetic. */
        val Neon = ThemePalette(
            key = "neon",
            displayName = "Neon",
            primary = Color(0xFFFFEE32),
            secondary = Color(0xFF4ECDC4),
            tertiary = Color(0xFFFF6B6B),
            blobA = Color(0xFFFFB300),
            blobB = Color(0xFF00E5FF),
            blobC = Color(0xFFFF1744)
        )

        val All: List<ThemePalette> = listOf(
            Aurora, Ember, Mint, Cobalt, Bloom, Sage, Neon
        )

        /** New default. Mint feels modern + works equally well in light
         *  and dark mode without skewing too brand-specific. */
        val Default: ThemePalette = Mint

        fun fromKey(key: String): ThemePalette =
            All.firstOrNull { it.key == key } ?: Default
    }
}
