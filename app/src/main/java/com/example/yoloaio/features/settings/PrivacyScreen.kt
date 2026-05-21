package com.example.yoloaio.features.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yoloaio.features.quotes.Quote
import com.example.yoloaio.ui.components.FeatureScaffold
import com.example.yoloaio.ui.components.GlassCard

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val prefs = rememberPrivacyPrefs()
    val defaultVisibility by prefs.defaultVisibility
    val discoverable by prefs.discoverableInChat
    val showPhoto by prefs.showProfilePhoto
    val showOnline by prefs.showOnlineStatus
    val readReceipts by prefs.readReceipts

    var showDataDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    FeatureScaffold(title = "Privacy", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Quote sharing ──
            SectionLabel("Quote sharing")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column {
                    HeaderRow(
                        Icons.Rounded.Visibility,
                        "Default visibility",
                        "Pre-selected when you create a new quote.",
                        accent = listOf(Color(0xFFB85AC1), Color(0xFF6A1B9A))
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    VisibilityRadio(
                        icon = Icons.Rounded.Lock,
                        title = "Private",
                        subtitle = "Only you can see it",
                        selected = defaultVisibility == Quote.VISIBILITY_PRIVATE,
                        onClick = { prefs.setDefaultVisibility(Quote.VISIBILITY_PRIVATE) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    VisibilityRadio(
                        icon = Icons.Rounded.Public,
                        title = "Public",
                        subtitle = "Everyone signed in can see it",
                        selected = defaultVisibility == Quote.VISIBILITY_PUBLIC,
                        onClick = { prefs.setDefaultVisibility(Quote.VISIBILITY_PUBLIC) }
                    )
                }
            }
            HintCard(
                "You can still override this per-quote in the editor — this just " +
                    "pre-selects the choice."
            )

            // ── Profile visibility ──
            SectionLabel("Profile")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column {
                    ToggleRow(
                        Icons.Rounded.PersonSearch,
                        "Discoverable in chat",
                        "Others can find you when starting a new conversation.",
                        accent = listOf(Color(0xFF5A8DEE), Color(0xFF1A237E)),
                        checked = discoverable,
                        onCheckedChange = prefs::setDiscoverable
                    )
                    DividerLine()
                    ToggleRow(
                        Icons.Rounded.Photo,
                        "Show profile photo",
                        "Hide your avatar from people you haven't chatted with.",
                        accent = listOf(Color(0xFFFF9F73), Color(0xFFE65100)),
                        checked = showPhoto,
                        onCheckedChange = prefs::setShowPhoto
                    )
                }
            }

            // ── Chat privacy ──
            SectionLabel("Chat")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column {
                    ToggleRow(
                        Icons.Rounded.RadioButtonChecked,
                        "Show online status",
                        "Let others see when you're active.",
                        accent = listOf(Color(0xFF00BFA5), Color(0xFF1B5E20)),
                        checked = showOnline,
                        onCheckedChange = prefs::setShowOnline
                    )
                    DividerLine()
                    ToggleRow(
                        Icons.Rounded.Visibility,
                        "Read receipts",
                        "Notify senders when you've read their messages.",
                        accent = listOf(Color(0xFFA8C7FF), Color(0xFF263238)),
                        checked = readReceipts,
                        onCheckedChange = prefs::setReadReceipts
                    )
                    DividerLine()
                    ActionRow(
                        Icons.Rounded.Block,
                        "Blocked accounts",
                        "Manage who can't message or see you.",
                        accent = listOf(Color(0xFFEF5350), Color(0xFFB71C1C))
                    ) { /* placeholder — block list not yet implemented */ }
                }
            }

            // ── Data ──
            SectionLabel("Your data")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column {
                    ActionRow(
                        Icons.Rounded.Download,
                        "Download my data",
                        "Get a copy of your quotes, chats, and profile.",
                        accent = listOf(Color(0xFFE0AAFF), Color(0xFF6A1B9A))
                    ) { showDataDialog = true }
                    DividerLine()
                    ActionRow(
                        Icons.Rounded.RemoveCircle,
                        "Delete account",
                        "Permanently remove your account and all data.",
                        accent = listOf(Color(0xFFEF5350), Color(0xFFB71C1C)),
                        destructive = true
                    ) { showDeleteDialog = true }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showDataDialog) {
        AlertDialog(
            onDismissRequest = { showDataDialog = false },
            title = { Text("Data export") },
            text = {
                Text(
                    "Data export isn't wired up yet — when it is, you'll get a " +
                        "JSON bundle of your quotes, chats, and profile sent to " +
                        "your registered email."
                )
            },
            confirmButton = {
                TextButton(onClick = { showDataDialog = false }) { Text("Got it") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete account?") },
            text = {
                Text(
                    "Account deletion isn't enabled in this build. When it is, " +
                        "it will permanently remove your profile, every quote you " +
                        "saved (public and private), all chat history, and any " +
                        "favorites. There's no undo."
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun IconBadge(icon: ImageVector, accent: List<Color>) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(accent)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HeaderRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: List<Color>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, accent)
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: List<Color>,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, accent)
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color(0xFF34C759)
            )
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: List<Color>,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, accent)
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (destructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VisibilityRadio(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            if (selected) Icons.Rounded.RadioButtonChecked
            else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HintCard(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DividerLine() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
