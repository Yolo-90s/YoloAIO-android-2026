package com.example.yoloaio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Single source of truth for corner-rounding across the app. Generous,
 * squircle-leaning radii give the redesign its modern feel — every chip,
 * card, button, and hero tile shares the same shape vocabulary.
 *
 * Material 3 token mapping:
 *   - extraSmall (8dp)  → chips, inline pills
 *   - small      (12dp) → buttons, inputs
 *   - medium     (16dp) → list items, small cards
 *   - large      (20dp) → standard cards (GlassCard default)
 *   - extraLarge (28dp) → hero / featured cards, bottom sheets
 *
 * `YoloShapes` exposes a couple of "above the Material token" values that
 * we use for big tile artwork — Material 3's Shapes object stops at 28dp.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

object YoloShapes {
    val Chip = RoundedCornerShape(12.dp)
    val Button = RoundedCornerShape(14.dp)
    val Card = RoundedCornerShape(20.dp)
    val Hero = RoundedCornerShape(28.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
}
