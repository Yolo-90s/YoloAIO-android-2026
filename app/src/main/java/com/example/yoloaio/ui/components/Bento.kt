package com.example.yoloaio.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yoloaio.ui.theme.YoloShapes

// Scoped to bento-tile headline text only — NOT applied globally in
// Type.kt, since a drop-shadow on body/reading text (chat, books, forms)
// would hurt legibility rather than help "attractiveness."
private val TITLE_SHADOW = Shadow(
    color = Color.Black.copy(alpha = 0.35f),
    offset = Offset(0f, 2f),
    blurRadius = 6f
)

/**
 * The app's bento-grid tile — a vivid gradient-fill card with a soft
 * top-left "glass sheen" highlight (ties it into the same Liquid-Glass
 * light language as [GlassCard] without dulling the brand color), a spring
 * press-scale, and an optional staggered fade/rise entrance for grids.
 *
 * Generalized from `HomeScreen`'s original `HeroTile`/`StandardTile` — Home
 * now consumes this directly rather than keeping its own copies. Use `hero
 * = true` for a 2-column-span featured tile, `false` for a square standard
 * tile in a `LazyVerticalGrid(columns = GridCells.Fixed(2))`.
 *
 * @param staggerIndex when >= 0, the tile fades/rises in on first
 * composition, delayed by `staggerIndex * 40ms` — pass the item's position
 * in its grid/list. Leave at -1 (default) to skip the entrance animation
 * (e.g. for a tile that's the sole/first thing on screen).
 */
@Composable
fun BentoTile(
    title: String,
    tagline: String,
    icon: ImageVector,
    accent: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    staggerIndex: Int = -1
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bentoPress"
    )
    // Pressing pushes the tile "into" the surface — elevation drops toward
    // the press-scale shrink, instead of staying a static flat shadow.
    // Combined with the tilt below, this is what sells "physically
    // extruded object" rather than "flat card with a shadow behind it."
    val restElevation = if (hero) 16.dp else 11.dp
    val pressElevation = if (hero) 6.dp else 4.dp
    val elevation by animateDpAsState(
        targetValue = if (pressed) pressElevation else restElevation,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bentoElevation"
    )

    // Pointer-tracked 3D tilt — see Tilt3D.kt. Bento tiles get a slightly
    // wider range than Glass cards since they're bold hero branding, not
    // dense content surfaces.
    val tiltModifier = Modifier.tilt3D(maxTiltDeg = if (hero) 5f else 7f)

    var entranceTarget by remember { mutableFloatStateOf(if (staggerIndex >= 0) 0f else 1f) }
    val entrance by animateFloatAsState(
        targetValue = entranceTarget,
        animationSpec = tween(durationMillis = 420),
        label = "bentoEntrance"
    )
    if (staggerIndex >= 0) {
        LaunchedEffect(staggerIndex) {
            kotlinx.coroutines.delay(staggerIndex * 40L)
            entranceTarget = 1f
        }
    }

    val sizeModifier = if (hero) {
        Modifier.fillMaxWidth().height(180.dp)
    } else {
        Modifier.fillMaxWidth().aspectRatio(1f)
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .then(sizeModifier)
            .then(tiltModifier)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = entrance
                translationY = (1f - entrance) * 24f
            },
        shape = if (hero) YoloShapes.Hero else YoloShapes.Card,
        color = Color.Transparent,
        shadowElevation = elevation
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(accent))
        ) {
            // Glass sheen — soft light from the top edge, the same rim-light
            // language as GlassCard's blur+tint, applied here as a highlight
            // over the vivid gradient instead of a translucent blur (a bento
            // tile is a bold branded surface, not a see-through pane).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                            start = Offset.Zero,
                            end = Offset(0f, if (hero) 320f else 200f)
                        )
                    )
            )
            // Bottom-edge inner shadow — paired with the top highlight above,
            // this is what reads as "the surface curves away from the light"
            // (embossed/extruded) instead of "flat card, shadow behind it."
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)),
                            startY = if (hero) 100f else 60f,
                            endY = if (hero) 340f else 220f
                        )
                    )
            )

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (hero) 0.18f else 0.16f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = if (hero) 16.dp else 12.dp, top = if (hero) 16.dp else 12.dp)
                    .size(if (hero) 140.dp else 80.dp)
            )

            if (hero) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(icon, size = 44.dp, iconSize = 24.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Featured",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column {
                        Text(
                            title,
                            style = MaterialTheme.typography.displaySmall.copy(shadow = TITLE_SHADOW),
                            color = Color.White
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tagline,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Rounded.ArrowOutward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    IconChip(icon, size = 36.dp, iconSize = 20.dp)
                    Column {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge.copy(shadow = TITLE_SHADOW),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            tagline,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconChip(icon: ImageVector, size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size.value * 0.32f))
            .background(Color.White.copy(alpha = 0.20f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}
