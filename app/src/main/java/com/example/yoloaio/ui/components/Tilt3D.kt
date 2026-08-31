package com.example.yoloaio.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity

/**
 * Pointer-tracked 3D tilt, shared by [BentoTile] and [GlassCard]/
 * [GlassSurface] — the app's core "looks like a 3D render" primitive.
 * Rotates the surface around X/Y based on where within it the pointer
 * currently is, springing back to flat on release.
 *
 * Reads raw pointer position at [PointerEventPass.Initial] and never calls
 * `consume()`, so this is purely observational — it doesn't interfere with
 * a `Surface`'s own click handling layered on top.
 *
 * @param maxTiltDeg how far the surface can rotate at the pointer's most
 * extreme position, in degrees. Bento tiles (bold, hero branding) use a
 * slightly wider range than Glass cards (content surfaces — subtler reads
 * as "alive" without feeling gimmicky on things like list rows).
 */
@Composable
internal fun Modifier.tilt3D(maxTiltDeg: Float): Modifier {
    val density = LocalDensity.current
    var tiltTargetX by remember { mutableFloatStateOf(0f) }
    var tiltTargetY by remember { mutableFloatStateOf(0f) }
    val tiltX by animateFloatAsState(
        targetValue = tiltTargetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tilt3dX"
    )
    val tiltY by animateFloatAsState(
        targetValue = tiltTargetY,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tilt3dY"
    )

    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull()
                    if (change != null && change.pressed) {
                        val nx = ((change.position.x / size.width) - 0.5f) * 2f
                        val ny = ((change.position.y / size.height) - 0.5f) * 2f
                        tiltTargetY = nx.coerceIn(-1f, 1f) * maxTiltDeg
                        tiltTargetX = -ny.coerceIn(-1f, 1f) * maxTiltDeg
                    } else {
                        tiltTargetX = 0f
                        tiltTargetY = 0f
                    }
                }
            }
        }
        .graphicsLayer {
            rotationX = tiltX
            rotationY = tiltY
            cameraDistance = 12f * density.density
        }
}
