package com.example.yoloaio.features.threedmenu

import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.yoloaio.data.LocalAppConfig
import com.example.yoloaio.ui.components.GlassSurface
import design.spline.runtime.SplineView

/**
 * A fully 3D scene acting as the primary UI — rendered full-bleed via
 * Spline's Android runtime, with a floating glass back button as the only
 * chrome (no [com.example.yoloaio.ui.components.FeatureScaffold] app bar,
 * so the scene truly fills the screen).
 *
 * ## Why "buttons" are invisible Compose overlays, not real 3D taps
 * Spline's public Android SDK (as of this writing) only exposes
 * [SplineView.loadUrl]/`loadResource` — there is no load-completion
 * callback, no way to look up a 3D object by name, and no API to attach a
 * tap listener to a specific object from Kotlin (their own docs mark the
 * "Code API for Kotlin" as not yet shipped). Any object-level hover/press
 * *visuals* are authored inside the Spline Editor itself and play back
 * automatically once the scene loads — that part just works. But getting a
 * *Kotlin-side* reaction (navigation, a Toast, updating app state) needs a
 * bridge: [ObjectHitbox] maps a fractional region of the canvas to a real
 * Compose `clickable`, positioned by hand to match where a button sits in
 * the exported scene. It's an approximation, not true 3D hit-testing — if
 * the camera or the object's position ever changes, these fractions need
 * re-tuning. Swap this for real object-tap events once Spline ships that
 * API.
 */
@Composable
fun ThreeDMenuScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val config = LocalAppConfig.current
    var splineView by remember { mutableStateOf<SplineView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (config.threeDMenuSceneUrl.isBlank()) {
            EmptyState()
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val containerWidth = maxWidth
                val containerHeight = maxHeight

                // ── Layer 1: the real, GPU-rendered 3D scene ──────────────
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        SplineView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            loadUrl(config.threeDMenuSceneUrl)
                            splineView = this
                        }
                    },
                    onRelease = { view ->
                        // AndroidView's real teardown hook — SplineView has
                        // no documented release()/dispose() of its own, so
                        // detaching from the window here is what lets the
                        // GL surface + native resources get reclaimed
                        // instead of leaking a GPU context when this screen
                        // is popped.
                        (view.parent as? ViewGroup)?.removeView(view)
                        splineView = null
                    }
                )

                // ── Layer 2: invisible hitboxes bridging tap → Kotlin state
                THREE_D_BUTTONS.forEach { hitbox ->
                    Box(
                        modifier = Modifier
                            .offset(
                                x = containerWidth * hitbox.xFraction,
                                y = containerHeight * hitbox.yFraction
                            )
                            .size(
                                width = containerWidth * hitbox.widthFraction,
                                height = containerHeight * hitbox.heightFraction
                            )
                            .clickable {
                                Toast.makeText(context, hitbox.tapMessage, Toast.LENGTH_SHORT).show()
                            }
                    )
                }
            }
        }

        // Floating back control — glass chip over the 3D layer, matching
        // the app's Glass+Bento chrome instead of a solid app bar.
        GlassSurface(
            modifier = Modifier
                .padding(16.dp)
                .size(44.dp)
                .align(Alignment.TopStart),
            shape = CircleShape,
            onClick = onBack
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = "Back")
            }
        }
    }

    // SplineView isn't documented to expose pause()/resume(), but as a
    // standard Android View it still stops receiving frames once it's
    // invisible/detached. This is a safety net: force it fully invisible
    // on ON_STOP (backgrounded) so it doesn't keep the GPU busy behind
    // other apps, and visible again on ON_START.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> splineView?.visibility = View.INVISIBLE
                Lifecycle.Event.ON_START -> splineView?.visibility = View.VISIBLE
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Rounded.ViewInAr,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "3D scene not configured yet",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    "Publish a scene in Spline (Export → Public URL) and set " +
                        "threeDMenuSceneUrl in Firestore config/app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** Fractional hitbox for one 3D "button" — see the class doc above for why. */
private data class ObjectHitbox(
    val xFraction: Float,
    val yFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float,
    val tapMessage: String
)

// Placeholder positions — tune against your actual exported scene. Give a
// Box a visible `.background(Color.Red.copy(alpha = 0.3f))` temporarily
// while lining these up, then remove it.
private val THREE_D_BUTTONS = listOf(
    ObjectHitbox(
        xFraction = 0.30f, yFraction = 0.55f,
        widthFraction = 0.40f, heightFraction = 0.12f,
        tapMessage = "Start tapped"
    ),
    ObjectHitbox(
        xFraction = 0.35f, yFraction = 0.72f,
        widthFraction = 0.30f, heightFraction = 0.10f,
        tapMessage = "Settings tapped"
    )
)
