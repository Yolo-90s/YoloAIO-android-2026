package com.example.yoloaio.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.yoloaio.data.UserProfile
import com.example.yoloaio.data.rememberCurrentUser
import com.example.yoloaio.features.mindmatch.MindMatchRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMindMatch: (code: String) -> Unit
) {
    val repo = remember { ChatRepository() }
    val mindMatchRepo = remember { MindMatchRepository() }
    val scope = rememberCoroutineScope()
    val previews by repo.observeChatPreviews().collectAsState(initial = emptyList())
    val me by rememberCurrentUser()

    // One-tap "create a session + invite this person" shortcut from a
    // contact row. Claiming/answering happens once inside
    // MindMatchSessionScreen, same as every other entry point.
    fun inviteToMindMatch(otherUid: String) {
        scope.launch {
            mindMatchRepo.createSession()
                .onSuccess { code ->
                    repo.sendMindMatchInvite(otherUid, code)
                    onOpenMindMatch(code)
                }
        }
    }

    var query by remember { mutableStateOf("") }
    val hasQuery = query.isNotBlank()

    // No more "browse every registered user" default — that leaked everyone
    // who's ever signed into the app onto the first screen. Default view is
    // just your own conversations (chats you've actually exchanged a
    // message in); typing in the search bar is now the only way to look up
    // someone new, searching the full directory by name/email, and tapping
    // a result opens (or lazily starts) that conversation.
    val conversations by remember(previews) {
        derivedStateOf { previews.filter { it.lastTimeMs > 0L } }
    }
    val searchResults by remember(previews, query) {
        derivedStateOf {
            val q = query.trim().lowercase()
            if (q.isBlank()) emptyList()
            else previews.filter {
                it.user.displayName.lowercase().contains(q) ||
                    it.user.email.lowercase().contains(q)
            }
        }
    }
    val displayed = if (hasQuery) searchResults else conversations

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Chat", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBackIos,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            ComposeFab(onClick = {
                // Take the user to the top of the list — fast way back
                // to the search bar if they've scrolled deep into the
                // conversation history. Doesn't open a "new chat" sheet
                // because every visible user IS already a chat target.
            })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp), // breathing room for the FAB
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item("hero") {
                GreetingHero(
                    user = me?.displayName?.takeIf { it.isNotBlank() }
                        ?: me?.email?.substringBefore('@')
                        ?: "Friend",
                    onAvatarClick = onOpenSettings,
                    chatCount = conversations.size
                )
            }
            item("search") {
                FloatingSearchBar(
                    query = query,
                    onChange = { query = it },
                    onClear = { query = "" }
                )
            }
            if (displayed.isEmpty()) {
                item("empty") {
                    EmptyState(query = query, hasQuery = hasQuery)
                }
            } else {
                items(displayed, key = { it.user.uid }) { preview ->
                    ChatPreviewCard(
                        preview = preview,
                        onClick = { onUserClick(preview.user.uid) },
                        onInviteToMindMatch = { inviteToMindMatch(preview.user.uid) }
                    )
                }
            }
        }
    }
}

// ─────────────────────── greeting hero ───────────────────────

@Composable
private fun GreetingHero(user: String, onAvatarClick: () -> Unit, chatCount: Int) {
    val initials = remember(user) { UserProfile.computeInitials(user) }
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Hi"
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                greeting,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                user.substringBefore(' ').take(20),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (chatCount > 0) "$chatCount conversation${if (chatCount == 1) "" else "s"}"
                else "No conversations yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(primary, tertiary)))
                .clickable(onClick = onAvatarClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initials,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─────────────────────── search ───────────────────────

@Composable
private fun FloatingSearchBar(
    query: String,
    onChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            // Frosted glass — the global AppBackground bleeds through.
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(28.dp)
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.tertiary
                )
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        "Search by name or email",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                inner()
            }
        )
        if (query.isNotEmpty()) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Clear",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────── card ───────────────────────

@Composable
private fun ChatPreviewCard(
    preview: ChatPreview,
    onClick: () -> Unit,
    onInviteToMindMatch: () -> Unit
) {
    val user = preview.user
    val hasChat = preview.lastTimeMs > 0L
    val accent = gradientForUser(user.uid)
    val now = System.currentTimeMillis()
    val isActive = user.lastLocationAt > 0L &&
        now - user.lastLocationAt < 30 * 60 * 1000L

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarWithStatus(user = user, gradient = accent, isActive = isActive)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.displayName.ifBlank { "Unknown" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    if (hasChat) {
                        Text(
                            formatPreviewTime(preview.lastTimeMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (hasChat) preview.lastMessage.ifBlank { "Shared media" }
                    else if (isActive) "Active now · tap to start"
                    else "Tap to start a chat",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = onInviteToMindMatch) {
                Icon(
                    Icons.Rounded.Psychology,
                    contentDescription = "Invite to MindMatch",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AvatarWithStatus(
    user: UserProfile,
    gradient: List<Color>,
    isActive: Boolean
) {
    Box(modifier = Modifier.size(56.dp)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                user.initials.ifBlank { UserProfile.computeInitials(user.displayName) },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        if (isActive) {
            // Online dot — small green disc with a colored ring matching
            // the app background so it reads as "attached to the avatar"
            // rather than floating.
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color(0xFF0F0E17))
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E676))
            )
        }
    }
}

// ─────────────────────── empty / fab ───────────────────────

@Composable
private fun EmptyState(query: String, hasQuery: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if (hasQuery) Icons.Rounded.Search else Icons.Rounded.Forum,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (hasQuery) "No matches for \"$query\"" else "No conversations yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasQuery) "Try a different name or email."
            else "Search for someone by name or email to start a conversation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        )
    }
}

@Composable
private fun ComposeFab(onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    FloatingActionButton(
        onClick = onClick,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = androidx.compose.material3.FloatingActionButtonDefaults
            .elevation(defaultElevation = 8.dp, pressedElevation = 12.dp),
        shape = CircleShape,
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(primary, tertiary)))
    ) {
        Icon(Icons.Rounded.EditNote, contentDescription = "Compose")
    }
}

// ─────────────────────── helpers ───────────────────────

private val avatarGradients = listOf(
    listOf(Color(0xFF7C9CFF), Color(0xFF1A237E)),
    listOf(Color(0xFFE0AAFF), Color(0xFF6A1B9A)),
    listOf(Color(0xFFFF7AB6), Color(0xFFAD1457)),
    listOf(Color(0xFFFFC36B), Color(0xFFE65100)),
    listOf(Color(0xFF00BFA5), Color(0xFF1B5E20)),
    listOf(Color(0xFFA8C7FF), Color(0xFF1565C0)),
    listOf(Color(0xFFB85AC1), Color(0xFF311B92)),
    listOf(Color(0xFF42E6B4), Color(0xFF00838F))
)

private fun gradientForUser(uid: String): List<Color> {
    val bucket = (uid.hashCode() and Int.MAX_VALUE) % avatarGradients.size
    return avatarGradients[bucket]
}

private fun formatPreviewTime(epochMs: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = epochMs }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val pattern = when {
        sameDay -> "HH:mm"
        sameYear -> "MMM d"
        else -> "MMM d, yyyy"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMs))
}

// Lightweight icon-only used by the GreetingHero — currently unused but
// retained because Icons.Rounded.Bolt may be picked up for AI chips later.
@Suppress("unused")
private val placeholderForFutureAiChip = Icons.Rounded.Bolt
