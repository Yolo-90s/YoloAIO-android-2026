package com.example.yoloaio.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Premium ~5-second AI operating-system boot animation, mirroring the
 * React [LaunchAnimation.jsx] on the web side.
 *
 * Visual stack:
 *  - data stream row (small, dim, fast-scramble)
 *  - brand row (bold, scrambles → locks in)
 *  - data stream row (mirror)
 *  - decryption scan line: single horizontal beam sweeps top→bottom
 *    across the brand during the scramble phase
 *
 * Phase timeline (millis since composition):
 *  - 0       – 2000   full scramble + scan line sweep
 *  - 2000    – 3500   staggered lock-in, data rows fade out
 *  - 3500    – 4800   hold final text with slow glow pulse
 *  - 4800    – 5100   fade out, then [onComplete] fires
 */
@Composable
fun LaunchAnimation(onComplete: () -> Unit) {
    val finalText = "YOLO AIO"
    val scrambleChars = "#@%*<>[]{}/\\~01ABCDEFGHJKLMNPQRSTUVWXYZ"
    val dataStreamLen = 24

    val scrambleEndMs = 2000L
    val lockDoneMs    = 3500L
    val holdEndMs     = 4800L
    val fadeMs        = 300L

    var brandText by remember { mutableStateOf(randomScramble(finalText.length, scrambleChars)) }
    var topStream by remember { mutableStateOf(randomScramble(dataStreamLen, scrambleChars)) }
    var botStream by remember { mutableStateOf(randomScramble(dataStreamLen, scrambleChars)) }
    var streamAlpha by remember { mutableFloatStateOf(0.55f) }
    var glitch by remember { mutableFloatStateOf(0f) }
    var glowPulse by remember { mutableFloatStateOf(0f) }
    var scanOffsetFraction by remember { mutableFloatStateOf(-0.25f) }
    var scanAlpha by remember { mutableFloatStateOf(0f) }
    var fading by remember { mutableStateOf(false) }

    val rootAlpha by animateFloatAsState(
        targetValue = if (fading) 0f else 1f,
        label = "launchFade",
    )

    LaunchedEffect(Unit) {
        val startNs = System.nanoTime()
        var done = false
        while (!done) {
            withFrameNanos { nowNs ->
                val elapsedMs = (nowNs - startNs) / 1_000_000L
                if (elapsedMs >= holdEndMs) {
                    fading = true
                    done = true
                    return@withFrameNanos
                }

                // Brand row scramble → lock
                val sb = StringBuilder(finalText.length)
                for (i in finalText.indices) {
                    val finalChar = finalText[i]
                    if (finalChar == ' ') {
                        sb.append(' ')
                        continue
                    }
                    val t = i.toFloat() / finalText.length
                    val lockTime = scrambleEndMs + (t * (lockDoneMs - scrambleEndMs)).toLong()
                    if (elapsedMs >= lockTime) {
                        sb.append(finalChar)
                    } else {
                        sb.append(scrambleChars[Random.nextInt(scrambleChars.length)])
                    }
                }
                brandText = sb.toString()

                glitch = if (elapsedMs < lockDoneMs && Random.nextFloat() < 0.15f) {
                    (Random.nextFloat() - 0.5f) * 4f
                } else 0f

                // Data stream rows: refresh every frame, fade as brand locks
                topStream = randomScramble(dataStreamLen, scrambleChars)
                botStream = randomScramble(dataStreamLen, scrambleChars)
                streamAlpha = when {
                    elapsedMs < scrambleEndMs -> 0.55f
                    elapsedMs < lockDoneMs ->
                        0.55f * (1f - (elapsedMs - scrambleEndMs).toFloat() / (lockDoneMs - scrambleEndMs))
                    else -> 0f
                }

                // Glow pulse during hold — two sine pulses over the period.
                glowPulse = if (elapsedMs >= lockDoneMs) {
                    val t = (elapsedMs - lockDoneMs).toFloat() / (holdEndMs - lockDoneMs)
                    0.5f + 0.5f * sin(t * PI.toFloat() * 2f)
                } else 0f

                // Scan line — single sweep during scramble, ease-in-out cubic
                if (elapsedMs < scrambleEndMs) {
                    val raw = elapsedMs.toFloat() / scrambleEndMs
                    val eased = if (raw < 0.5f) 4f * raw * raw * raw
                                else 1f - (-2f * raw + 2f).toDouble().pow(3.0).toFloat() / 2f
                    scanOffsetFraction = -0.25f + eased * 0.5f
                    scanAlpha = 0.75f
                } else {
                    scanAlpha = 0f
                }
            }
        }
        kotlinx.coroutines.delay(fadeMs)
        onComplete()
    }

    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = rootAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.alpha(rootAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top data stream
            Text(
                text = topStream,
                color = Color.White.copy(alpha = streamAlpha),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 3.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(20.dp))

            // Brand text with stacked-shadow glow + scramble shake
            GlowBrandText(
                text = brandText,
                offsetX = glitch,
                pulse = glowPulse,
                alpha = rootAlpha,
            )

            Spacer(Modifier.height(20.dp))
            // Bottom data stream
            Text(
                text = botStream,
                color = Color.White.copy(alpha = streamAlpha),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 3.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        // Decryption scan line — thin horizontal beam, fades as it
        // crosses the brand row, disappears once decryption "completes".
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .offset(y = with(density) {
                    // scanOffsetFraction is in (-0.25 .. +0.25) of the
                    // screen height — convert to dp using a fixed
                    // reference height (800 dp) so the sweep distance is
                    // consistent across devices.
                    (scanOffsetFraction * 800f).dp
                })
                .alpha(scanAlpha * rootAlpha)
                .background(
                    Brush.horizontalGradient(
                        0f   to Color.White.copy(alpha = 0f),
                        0.5f to Color.White.copy(alpha = 0.85f),
                        1f   to Color.White.copy(alpha = 0f),
                    )
                ),
        )
    }
}

@Composable
private fun GlowBrandText(text: String, offsetX: Float, pulse: Float, alpha: Float) {
    // Stacked translucent copies behind the crisp top layer simulate the
    // soft white glow. `pulse` widens the halos during the hold phase.
    val baseStyle = TextStyle(
        color = Color.White.copy(alpha = alpha),
        fontSize = 56.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 4.sp,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center,
    )
    val outerAlpha = (0.18f + pulse * 0.15f) * alpha
    val innerAlpha = (0.45f + pulse * 0.20f) * alpha
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = baseStyle.copy(color = Color.White.copy(alpha = outerAlpha)),
            modifier = Modifier.offset(x = offsetX.dp),
        )
        Text(
            text = text,
            style = baseStyle.copy(color = Color.White.copy(alpha = innerAlpha)),
            modifier = Modifier.offset(x = offsetX.dp),
        )
        Text(
            text = text,
            style = baseStyle,
            modifier = Modifier.offset(x = offsetX.dp),
        )
    }
}

private fun randomScramble(len: Int, chars: String): String {
    val sb = StringBuilder(len)
    for (i in 0 until len) {
        sb.append(chars[Random.nextInt(chars.length)])
    }
    return sb.toString()
}
