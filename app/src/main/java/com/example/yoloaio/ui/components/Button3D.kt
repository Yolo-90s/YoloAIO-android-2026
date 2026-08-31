package com.example.yoloaio.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.yoloaio.ui.theme.YoloShapes

/**
 * A button with the same layered-depth language as [BentoTile]/[GlassCard]:
 * pointer-tracked tilt, press-reactive elevation (drops toward the surface
 * on tap instead of a flat static shadow), and a scale squeeze — reads as a
 * physically raised, pressable object rather than a flat colored rect.
 *
 * Compose has no MUI-style "override every Button app-wide" theme hook, so
 * this is opt-in: swap a screen's `Button(onClick = ...) { Text(...) }` for
 * `Yolo3DButton(onClick = ..., label = ...)` where you want the treatment.
 * Home's account icon (`Yolo3DIconButton`) is the reference usage — adopt
 * elsewhere as needed.
 *
 * Sizes naturally to its label + padding, same as a stock M3 `Button` —
 * pass `modifier` if you need a fixed size/width instead.
 */
@Composable
fun Yolo3DButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    shape: Shape = YoloShapes.Button
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "yolo3dButtonScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) 2.dp else 8.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "yolo3dButtonElevation"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .tilt3D(maxTiltDeg = 6f)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = elevation
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}

/**
 * Icon-only variant — same depth treatment, round by default (chip/FAB
 * style). Sizes to icon + padding (≈44dp with a default-size icon),
 * matching Material's standard touch target — pass `modifier` to override.
 */
@Composable
fun Yolo3DIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    shape: Shape = CircleShape
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "yolo3dIconButtonScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) 1.dp else 6.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "yolo3dIconButtonElevation"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .tilt3D(maxTiltDeg = 8f)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = elevation
    ) {
        Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}
