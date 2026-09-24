package com.example.yoloaio.features.chat

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import com.example.yoloaio.data.UserProfile
import kotlinx.coroutines.launch

/**
 * Member picker + name field, reached from the Chat screen's FAB. Reuses
 * the same "search the full directory by name/email" pattern the Chat
 * list's own search bar uses — [ChatRepository.observeOtherUsers] is the
 * full directory; this just makes it multi-select instead of tap-to-open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(onBack: () -> Unit, onCreated: (chatId: String) -> Unit) {
    val repo = remember { ChatRepository() }
    val groupRepo = remember { GroupChatRepository() }
    val scope = rememberCoroutineScope()
    val users by repo.observeOtherUsers().collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val filtered = remember(users, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) users
        else users.filter { it.displayName.lowercase().contains(q) || it.email.lowercase().contains(q) }
    }

    fun onCreate() {
        if (creating || selected.isEmpty()) return
        creating = true
        error = null
        scope.launch {
            groupRepo.createGroup(name, selected.toList())
                .onSuccess { chatId -> onCreated(chatId) }
                .onFailure { error = it.message ?: "Couldn't create group" }
            creating = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("New group") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search people to add") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
            )
            if (selected.isNotEmpty()) {
                Text(
                    "${selected.size} selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filtered, key = { it.uid }) { user ->
                    SelectableUserRow(
                        user = user,
                        selected = user.uid in selected,
                        onToggle = {
                            selected = if (user.uid in selected) selected - user.uid else selected + user.uid
                        }
                    )
                }
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            Button(
                onClick = ::onCreate,
                enabled = selected.isNotEmpty() && !creating,
                modifier = Modifier.fillMaxWidth().padding(20.dp).height(56.dp)
            ) {
                if (creating) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text(if (selected.isEmpty()) "Select at least one person" else "Create group")
            }
        }
    }
}

@Composable
private fun SelectableUserRow(user: UserProfile, selected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(user.avatarComposeColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                user.initials.ifBlank { UserProfile.computeInitials(user.displayName) },
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName.ifBlank { "Unknown" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(user.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (selected) Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
                    else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                )
                .then(
                    if (!selected) Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
