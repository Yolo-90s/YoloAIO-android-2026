package com.example.yoloaio.features.movies

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Cast button shown in the Movie/TV player TopBar.
 *
 * Honest UX note: the player is a Vidking WebView, so the underlying
 * HLS stream URL isn't exposed to us. That means we can't push playback
 * directly to a Chromecast — the Cast SDK's `loadMedia()` needs a real
 * stream URL.
 *
 * What we CAN do on Android: route the user to the system **Cast
 * Settings** screen, which lets them pick a Chromecast and start
 * **screen mirroring** of the whole device. Everything they see — the
 * Vidking iframe included — gets mirrored to the TV.
 *
 * The dialog before the redirect explains this so users don't tap the
 * Cast icon expecting normal app casting and end up confused at the
 * Settings screen.
 */
@Composable
fun PlayerCastButton() {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showDialog = true }) {
        Icon(Icons.Rounded.Cast, contentDescription = "Cast to TV")
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text("Cast this video to your TV", fontWeight = FontWeight.SemiBold)
            },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(
                        "The in-app player is an embedded video, so casting it directly " +
                            "isn't possible. You can mirror your whole phone screen to a " +
                            "Chromecast and watch on your TV instead.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Step(1, "Tap “Open Cast Settings” below.")
                    Spacer(Modifier.height(6.dp))
                    Step(2, "Pick your Chromecast / smart TV from the list.")
                    Spacer(Modifier.height(6.dp))
                    Step(3, "Come back to this player — it'll show on the TV.")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Tip: full-screen the player first, then start the mirror — " +
                            "the TV will pick up the player at full size.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_CAST_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure {
                        Toast.makeText(
                            context,
                            "Cast settings not available on this device",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) {
                    Text("Open Cast Settings", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                n.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
