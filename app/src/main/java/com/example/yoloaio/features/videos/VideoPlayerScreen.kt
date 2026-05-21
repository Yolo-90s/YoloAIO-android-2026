package com.example.yoloaio.features.videos

import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.yoloaio.data.LocalAppConfig
import com.example.yoloaio.ui.components.FeatureScaffold
import kotlinx.coroutines.delay

private val SPEED_OPTIONS = listOf(0.5f, 1.0f, 1.5f, 2.0f)

@Composable
fun VideoPlayerScreen(
    videoId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val config = LocalAppConfig.current

    // Resolve the streaming URL once — if the proxy isn't configured we show
    // a friendly empty state instead of crashing the player.
    val streamUrl = remember(config.videosApiBaseUrl, videoId) {
        if (config.videosApiBaseUrl.isBlank() || videoId.isBlank()) null
        else runCatching { VideosClient.streamUrlFor(config.videosApiBaseUrl, videoId) }.getOrNull()
    }

    // We hold onto the MediaPlayer reference once `onPrepared` fires so we
    // can tweak volume and playback speed from the Compose controls.
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var isReady by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1.0f) }
    var durationMs by remember { mutableStateOf(0) }
    var positionMs by remember { mutableStateOf(0) }
    var showSpeedSheet by remember { mutableStateOf(false) }

    // Poll the VideoView for current position once per ~250 ms while playing.
    // VideoView/MediaPlayer doesn't push position updates, so this is the
    // accepted pattern.
    LaunchedEffect(isPlaying, isReady) {
        while (isReady && isPlaying) {
            videoView?.let { positionMs = it.currentPosition }
            delay(250)
        }
    }

    // Defensive cleanup: stop playback if the user navigates away.
    DisposableEffect(Unit) {
        onDispose {
            runCatching { videoView?.stopPlayback() }
            mediaPlayer = null
            videoView = null
        }
    }

    FeatureScaffold(
        title = "Player",
        onBack = onBack,
        actions = {
            if (streamUrl != null) {
                IconButton(onClick = { shareVideoUrl(context, streamUrl) }) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Video surface ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                if (streamUrl != null) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoPath(streamUrl)
                                setOnPreparedListener { mp ->
                                    mediaPlayer = mp
                                    durationMs = mp.duration
                                    isReady = true
                                    // Autoplay on prepare to match the web app's behaviour.
                                    start()
                                    isPlaying = true
                                    applyMute(mp, muted)
                                    applySpeed(mp, speed)
                                }
                                setOnCompletionListener { isPlaying = false }
                                setOnErrorListener { _, _, _ ->
                                    isPlaying = false
                                    isReady = false
                                    true
                                }
                            }.also { videoView = it }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    NotConfigured()
                }

                if (streamUrl != null && !isReady) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Color.White) }
                }
            }

            // ── Custom controls ──
            if (streamUrl != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Scrub bar with time labels
                    Slider(
                        value = positionMs.coerceAtMost(durationMs).toFloat(),
                        valueRange = 0f..(durationMs.coerceAtLeast(1)).toFloat(),
                        onValueChange = { v ->
                            positionMs = v.toInt()
                            videoView?.seekTo(v.toInt())
                        },
                        enabled = isReady,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatVideoDuration(positionMs.toLong()).ifBlank { "0:00" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatVideoDuration(durationMs.toLong()).ifBlank { "0:00" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play / pause (primary, large)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(enabled = isReady) {
                                    val v = videoView ?: return@clickable
                                    if (v.isPlaying) {
                                        v.pause(); isPlaying = false
                                    } else {
                                        v.start(); isPlaying = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.width(20.dp))

                        // Mute
                        ControlChip(
                            icon = if (muted) Icons.AutoMirrored.Rounded.VolumeOff
                            else Icons.AutoMirrored.Rounded.VolumeUp,
                            label = if (muted) "Muted" else "Sound on",
                            highlighted = muted,
                            onClick = {
                                muted = !muted
                                mediaPlayer?.let { applyMute(it, muted) }
                            }
                        )
                        Spacer(Modifier.width(10.dp))

                        // Speed
                        ControlChip(
                            icon = Icons.Rounded.Speed,
                            label = "${speed}×",
                            highlighted = speed != 1f,
                            onClick = { showSpeedSheet = true }
                        )

                        Spacer(Modifier.weight(1f))

                        // Share
                        ControlChip(
                            icon = Icons.Rounded.Share,
                            label = "Share",
                            highlighted = false,
                            onClick = { shareVideoUrl(context, streamUrl) }
                        )
                    }
                }

                // Speed picker
                if (showSpeedSheet) {
                    SpeedPicker(
                        current = speed,
                        onPick = { picked ->
                            speed = picked
                            mediaPlayer?.let { applySpeed(it, picked) }
                            showSpeedSheet = false
                        },
                        onDismiss = { showSpeedSheet = false }
                    )
                }
            }
        }
    }
}

// ───────────── helper composables ─────────────

@Composable
private fun ControlChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val bg = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (highlighted) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SpeedPicker(
    current: Float,
    onPick: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed") },
        text = {
            Column {
                SPEED_OPTIONS.forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(s) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${s}×",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal,
                            color = if (s == current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (s == current) {
                            Text(
                                "Current",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun NotConfigured() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.VideoFile,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Videos proxy not configured",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

// ───────────── MediaPlayer helpers ─────────────

private fun applyMute(mp: MediaPlayer, muted: Boolean) {
    val v = if (muted) 0f else 1f
    runCatching { mp.setVolume(v, v) }
}

private fun applySpeed(mp: MediaPlayer, speed: Float) {
    runCatching {
        // PlaybackParams requires API 23+; minSdk is 24 so always available.
        val wasPlaying = mp.isPlaying
        mp.playbackParams = PlaybackParams().setSpeed(speed)
        if (!wasPlaying) mp.pause()
    }
}

private fun shareVideoUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share video link"))
    }
}
