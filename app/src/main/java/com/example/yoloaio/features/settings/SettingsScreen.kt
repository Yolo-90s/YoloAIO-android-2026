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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import android.app.Activity
import android.widget.Toast
import com.example.yoloaio.BuildConfig
import com.example.yoloaio.data.AppUpdateChecker
import com.example.yoloaio.data.UserProfile
import com.example.yoloaio.data.rememberCurrentUser
import com.example.yoloaio.features.auth.AuthRepository
import com.example.yoloaio.ui.components.GlassCard
import com.example.yoloaio.ui.theme.ThemePalette
import com.example.yoloaio.ui.theme.ThemePreferenceStore
import com.example.yoloaio.ui.theme.rememberThemePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPrivacyClick: () -> Unit,
    onSignOut: () -> Unit
) {
    var darkMode by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(true) }
    val authRepo = remember { AuthRepository() }
    val user by rememberCurrentUser()
    // Bump this on any successful profile edit so the derived displayName /
    // initials re-read FirebaseUser's now-updated cache. AuthStateListener
    // doesn't fire on profile-only changes, so without this the UI shows
    // the stale name even though the save succeeded.
    var profileVersion by remember { mutableStateOf(0) }
    val displayName = remember(user, profileVersion) {
        user?.displayName?.takeIf { it.isNotBlank() } ?: "Yolo User"
    }
    val email = remember(user) { user?.email ?: "" }
    val initials = remember(displayName) { UserProfile.computeInitials(displayName) }
    // Does the signed-in user have an email/password credential? If they
    // signed in with Google only, "Change password" must be hidden / blocked.
    val hasPasswordProvider = remember(user) {
        user?.providerData?.any { it.providerId == "password" } == true
    }
    var showEditProfile by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var googlePasswordNotice by remember { mutableStateOf(false) }
    var showPlaygroundUrl by remember { mutableStateOf(false) }
    val playgroundUrl by rememberPlaygroundUrl()
    var checkingForUpdate by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx as? Activity }
    val updateScope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProfileCard(name = displayName, email = email, initials = initials)
            Spacer(Modifier.height(4.dp))

            SectionLabel("Account")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column {
                    NavRow(
                        Icons.Rounded.AccountCircle, "Edit profile",
                        accent = listOf(Color(0xFF5A8DEE), Color(0xFF1A237E))
                    ) { showEditProfile = true }
                    DividerLine()
                    NavRow(
                        Icons.Rounded.Lock, "Change password",
                        accent = listOf(Color(0xFFB85AC1), Color(0xFF6A1B9A))
                    ) {
                        // Google-only accounts have no password to change.
                        if (hasPasswordProvider) showChangePassword = true
                        else googlePasswordNotice = true
                    }
                    DividerLine()
                    NavRow(
                        Icons.Rounded.PrivacyTip, "Privacy",
                        accent = listOf(Color(0xFFFF7AB6), Color(0xFFAD1457))
                    ) { onPrivacyClick() }
                    DividerLine()
                    NavRow(
                        Icons.Rounded.SportsEsports, "PlayGround URL",
                        trailingText = playgroundUrl
                            .takeIf { it.isNotBlank() }
                            ?.let { trimDisplayUrl(it) }
                            ?: "Not set",
                        accent = listOf(Color(0xFF4FC3F7), Color(0xFF1A237E))
                    ) { showPlaygroundUrl = true }
                }
            }

            SectionLabel("App")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column {
                    ToggleRow(
                        Icons.Rounded.DarkMode, "Dark mode",
                        accent = listOf(Color(0xFF263238), Color(0xFF000000)),
                        checked = darkMode, onCheckedChange = { darkMode = it }
                    )
                    DividerLine()
                    ToggleRow(
                        Icons.Rounded.Notifications, "Notifications",
                        accent = listOf(Color(0xFFFF9F73), Color(0xFFE65100)),
                        checked = notifications, onCheckedChange = { notifications = it }
                    )
                    DividerLine()
                    NavRow(
                        Icons.Rounded.Language, "Language",
                        trailingText = "English",
                        accent = listOf(Color(0xFF00BFA5), Color(0xFF1B5E20))
                    ) { }
                }
            }

            SectionLabel("Appearance")
            AppearanceCard()

            SectionLabel("About")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column {
                    NavRow(
                        Icons.Rounded.SystemUpdate, "Check for updates",
                        trailingText = if (checkingForUpdate) "Checking…" else null,
                        accent = listOf(Color(0xFF66BB6A), Color(0xFF1B5E20))
                    ) {
                        val act = activity ?: return@NavRow
                        if (checkingForUpdate) return@NavRow
                        checkingForUpdate = true
                        updateScope.launch {
                            val result = AppUpdateChecker.forceCheck(act)
                            checkingForUpdate = false
                            val msg = when (result) {
                                is AppUpdateChecker.Status.UpToDate ->
                                    "You're on the latest version."
                                is AppUpdateChecker.Status.UpdatePending ->
                                    "Update v${result.versionName} available — follow the prompt to install."
                                is AppUpdateChecker.Status.Failed ->
                                    "Couldn't check for updates: ${result.message}"
                            }
                            Toast.makeText(act, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                    DividerLine()
                    NavRow(
                        Icons.Rounded.Info, "About Yolo AIO",
                        trailingText = "v${BuildConfig.VERSION_NAME}",
                        accent = listOf(Color(0xFFA8C7FF), Color(0xFF263238))
                    ) { }
                }
            }

            Spacer(Modifier.height(8.dp))
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    authRepo.signOut()
                    onSignOut()
                },
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "Sign out",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showEditProfile) {
        EditProfileDialog(
            initialName = displayName,
            repo = authRepo,
            onDismiss = { showEditProfile = false },
            onSaved = {
                profileVersion++          // force re-read of user.displayName
                showEditProfile = false
            }
        )
    }
    if (showChangePassword) {
        ChangePasswordDialog(
            repo = authRepo,
            onDismiss = { showChangePassword = false }
        )
    }
    if (showPlaygroundUrl) {
        PlaygroundUrlDialog(
            initial = playgroundUrl,
            onDismiss = { showPlaygroundUrl = false }
        )
    }
    if (googlePasswordNotice) {
        AlertDialog(
            onDismissRequest = { googlePasswordNotice = false },
            title = { Text("Managed by Google") },
            text = {
                Text(
                    "You signed in with Google, so this app doesn't hold a " +
                        "password to change. Manage your Google Account " +
                        "password at myaccount.google.com → Security."
                )
            },
            confirmButton = {
                TextButton(onClick = { googlePasswordNotice = false }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun ProfileCard(name: String, email: String, initials: String) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(20.dp),
        strong = true
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFF7AB6), Color(0xFFB85AC1), Color(0xFF7C9CFF))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initials,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.size(16.dp))
            Column {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
private fun AppearanceCard() {
    val context = LocalContext.current
    val store = remember { ThemePreferenceStore.get(context) }
    val selected by rememberThemePalette()

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(
                    Icons.Rounded.Palette,
                    listOf(Color(0xFFE0AAFF), Color(0xFF6A1B9A))
                )
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Color theme",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        selected.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp),
                color = Color.White.copy(alpha = 0.25f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThemePalette.All.forEach { palette ->
                    PaletteSwatch(
                        palette = palette,
                        isSelected = palette.key == selected.key,
                        onClick = { store.setPalette(palette) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteSwatch(
    palette: ThemePalette,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(palette.primary, palette.secondary, palette.tertiary)
                    )
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            palette.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
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
private fun NavRow(
    icon: ImageVector,
    title: String,
    trailingText: String? = null,
    accent: List<Color>,
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
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (trailingText != null) {
            Text(
                trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(6.dp))
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
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
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
private fun DividerLine() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        color = Color.White.copy(alpha = 0.25f)
    )
}

@Composable
private fun EditProfileDialog(
    initialName: String,
    repo: AuthRepository,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit profile", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Display name") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    saving = true
                    scope.launch {
                        val res = repo.updateDisplayName(name)
                        saving = false
                        res.onSuccess { onSaved() }
                            .onFailure { error = it.message ?: "Couldn't save" }
                    }
                },
                enabled = !saving && name.isNotBlank() && name.trim() != initialName
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
private fun ChangePasswordDialog(
    repo: AuthRepository,
    onDismiss: () -> Unit
) {
    var current by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val canSubmit = current.isNotBlank() &&
        newPwd.length >= 6 &&
        newPwd == confirm &&
        !saving

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Change password", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it; error = null },
                    label = { Text("Current password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPwd,
                    onValueChange = { newPwd = it; error = null },
                    label = { Text("New password (6+ chars)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = { Text("Confirm new password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    isError = confirm.isNotEmpty() && confirm != newPwd
                )
                if (confirm.isNotEmpty() && confirm != newPwd) {
                    Text(
                        "Passwords don't match",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    saving = true
                    scope.launch {
                        val res = repo.changePassword(current, newPwd)
                        saving = false
                        res.onSuccess { onDismiss() }
                            .onFailure { error = it.message ?: "Couldn't change password" }
                    }
                },
                enabled = canSubmit
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

// ───────────── PlayGround URL dialog ─────────────

@Composable
private fun PlaygroundUrlDialog(
    initial: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { PlaygroundPreferenceStore.get(context) }
    var url by remember { mutableStateOf(initial) }
    val trimmed = url.trim()
    val dirty = trimmed != initial.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PlayGround URL", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The Browser tab of PlayGround will auto-load this page. " +
                        "Paste any URL — https:// is added if you don't.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    store.url = trimmed
                    onDismiss()
                },
                enabled = dirty
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial.isNotBlank()) {
                    TextButton(onClick = {
                        store.url = ""
                        onDismiss()
                    }) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** Trim a URL to a host-shaped preview suitable for a trailing list cell. */
private fun trimDisplayUrl(raw: String): String {
    val stripped = raw
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .trimEnd('/')
    return if (stripped.length > 22) stripped.take(20) + "…" else stripped
}
