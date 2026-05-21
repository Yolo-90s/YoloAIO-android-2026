package com.example.yoloaio.features.ringtones

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessAlarm
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun ToneRow(
    tone: Tone,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    onToggle: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(gradientFor(tone.id))),
            contentAlignment = Alignment.Center
        ) {
            tone.artUrl?.let { art ->
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            when {
                isLoading -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                isPlaying -> Icon(Icons.Rounded.Pause, contentDescription = "Pause", tint = Color.White)
                else -> Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color.White)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tone.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            val tagPreview = tone.tags.take(3).joinToString(" · ")
            Text(
                if (tagPreview.isNotBlank()) "${tone.subtitle} · $tagPreview" else tone.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            tone.durationFormatted,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onMoreClick) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = "More actions",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToneActionsSheet(
    tone: Tone,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var inFlight by remember { mutableStateOf<RingtoneSlot?>(null) }
    var permissionPromptForSlot by remember { mutableStateOf<RingtoneSlot?>(null) }

    fun closeSheet() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    suspend fun install(slot: RingtoneSlot, setAsDefault: Boolean) {
        inFlight = slot
        val res = RingtoneInstaller.install(
            context = context,
            tone = tone,
            slot = slot,
            setAsDefault = setAsDefault
        )
        inFlight = null
        when (res) {
            is RingtoneInstaller.InstallResult.Success -> {
                val msg = if (res.setAsDefault) "Set as ${slot.name.lowercase()}"
                else "Saved to ${slot.dirName}"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                closeSheet()
            }
            RingtoneInstaller.InstallResult.NeedsAndroid10 -> {
                Toast.makeText(
                    context,
                    "Setting tones requires Android 10 or newer.",
                    Toast.LENGTH_LONG
                ).show()
            }
            RingtoneInstaller.InstallResult.NeedsWriteSettingsPermission -> {
                permissionPromptForSlot = slot
            }
            is RingtoneInstaller.InstallResult.Failed -> {
                Toast.makeText(context, "Couldn't install: ${res.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                tone.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )
            Text(
                "${tone.subtitle} · ${tone.durationFormatted}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            ActionRow(
                icon = if (isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                title = if (isFavorite) "Remove from favorites" else "Add to favorites",
                onClick = onToggleFavorite
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            ActionRow(
                icon = Icons.Rounded.RingVolume,
                title = "Set as ringtone",
                loading = inFlight == RingtoneSlot.Ringtone,
                onClick = { scope.launch { install(RingtoneSlot.Ringtone, setAsDefault = true) } }
            )
            ActionRow(
                icon = Icons.Rounded.Notifications,
                title = "Set as notification",
                loading = inFlight == RingtoneSlot.Notification,
                onClick = { scope.launch { install(RingtoneSlot.Notification, setAsDefault = true) } }
            )
            ActionRow(
                icon = Icons.Rounded.AccessAlarm,
                title = "Set as alarm",
                loading = inFlight == RingtoneSlot.Alarm,
                onClick = { scope.launch { install(RingtoneSlot.Alarm, setAsDefault = true) } }
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            ActionRow(
                icon = Icons.Rounded.Download,
                title = "Save to device library",
                onClick = {
                    scope.launch { install(RingtoneSlot.Ringtone, setAsDefault = false) }
                }
            )

            if (onDelete != null) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                ActionRow(
                    icon = Icons.Rounded.Delete,
                    title = "Delete trimmed clip",
                    onClick = {
                        onDelete()
                        closeSheet()
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { closeSheet() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Close") }
        }
    }

    permissionPromptForSlot?.let { slot ->
        AlertDialog(
            onDismissRequest = { permissionPromptForSlot = null },
            title = { Text("Permission needed") },
            text = {
                Text(
                    "To set this as your default ${slot.name.lowercase()}, " +
                        "Yolo AIO needs permission to modify system settings.\n\n" +
                        "Tap Open Settings, toggle \"Modify system settings\" on, " +
                        "then come back and tap Set again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    permissionPromptForSlot = null
                    RingtoneInstaller.openWriteSettingsScreen(context)
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { permissionPromptForSlot = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

internal val toneTileGradients = listOf(
    listOf(Color(0xFF7C9CFF), Color(0xFF1A237E)),
    listOf(Color(0xFFE0AAFF), Color(0xFF6A1B9A)),
    listOf(Color(0xFF263238), Color(0xFF000000)),
    listOf(Color(0xFFFF7AB6), Color(0xFFAD1457)),
    listOf(Color(0xFFA8C7FF), Color(0xFF263238)),
    listOf(Color(0xFF00BFA5), Color(0xFF1B5E20)),
    listOf(Color(0xFFFFC36B), Color(0xFFE65100)),
    listOf(Color(0xFFB85AC1), Color(0xFF311B92))
)

internal fun gradientFor(id: String): List<Color> {
    val bucket = (id.hashCode() and Int.MAX_VALUE) % toneTileGradients.size
    return toneTileGradients[bucket]
}
