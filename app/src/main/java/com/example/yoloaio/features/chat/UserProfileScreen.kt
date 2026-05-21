package com.example.yoloaio.features.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yoloaio.data.FirebaseModule
import com.example.yoloaio.data.UserProfile
import com.example.yoloaio.ui.components.FeatureScaffold
import com.example.yoloaio.ui.components.GlassCard
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Profile detail screen — shown when the user taps the chat header in a
 * conversation. Displays the other person's identity (avatar / name /
 * email) plus the most-recent location they pushed to their user doc
 * (written by [LocationPresence][com.example.yoloaio.data.LocationPresence]
 * whenever they open the app or a chat).
 */
@Composable
fun UserProfileScreen(uid: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        if (uid.isBlank()) {
            error = "Missing user id."
            loading = false
            return@LaunchedEffect
        }
        loading = true
        runCatching {
            FirebaseModule.firestore
                .collection("users")
                .document(uid)
                .get()
                .await()
                .toObject(UserProfile::class.java)
        }
            .onSuccess {
                profile = it
                if (it == null) error = "User not found."
            }
            .onFailure { error = it.message ?: "Couldn't load profile." }
        loading = false
    }

    FeatureScaffold(title = "Profile", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }

                error != null || profile == null -> GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        error ?: "User not found.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                else -> {
                    HeaderCard(profile!!)
                    DetailsCard(profile!!)
                    LocationCard(profile!!, context = context)
                    PrivacyNote()
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(profile: UserProfile) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        strong = true
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                profile.avatarComposeColor,
                                profile.avatarComposeColor.copy(alpha = 0.55f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    profile.initials.ifBlank { UserProfile.computeInitials(profile.displayName) },
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                profile.displayName.ifBlank { "Unknown" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (profile.email.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    profile.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DetailsCard(profile: UserProfile) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            DetailRow(
                icon = Icons.Rounded.Email,
                label = "Email",
                value = profile.email.ifBlank { "—" }
            )
            if (profile.createdAt > 0L) {
                Spacer(Modifier.height(12.dp))
                DetailRow(
                    icon = Icons.Rounded.Schedule,
                    label = "Joined",
                    value = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        .format(Date(profile.createdAt))
                )
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LocationCard(profile: UserProfile, context: android.content.Context) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (profile.hasLocation) Icons.Rounded.LocationOn else Icons.Rounded.LocationOff,
                    contentDescription = null,
                    tint = if (profile.hasLocation) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Last known location",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))

            if (!profile.hasLocation) {
                Text(
                    "${profile.displayName.ifBlank { "This user" }} hasn't shared a " +
                        "location yet. Their location updates the next time they open the " +
                        "app with location permission granted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "%.5f, %.5f".format(profile.lastLat, profile.lastLon),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Updated ${relativeAgoLong(profile.lastLocationAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            openInMaps(context, profile.lastLat, profile.lastLon)
                        },
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Open in Maps",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyNote() {
    Text(
        "Location is refreshed when the person last opened the app or this " +
            "chat — never live-tracked. Permission is required on their device.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

private fun openInMaps(context: android.content.Context, lat: Double, lon: Double) {
    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Last%20known%20location)")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun relativeAgoLong(epochMs: Long): String {
    val deltaSec = ((System.currentTimeMillis() - epochMs) / 1000L).coerceAtLeast(0L)
    return when {
        deltaSec < 60 -> "just now"
        deltaSec < 3600 -> "${deltaSec / 60} min ago"
        deltaSec < 86_400 -> "${deltaSec / 3600} hr ago"
        deltaSec < 7 * 86_400 -> "${deltaSec / 86_400} day(s) ago"
        else -> SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.getDefault())
            .format(Date(epochMs))
    }
}
