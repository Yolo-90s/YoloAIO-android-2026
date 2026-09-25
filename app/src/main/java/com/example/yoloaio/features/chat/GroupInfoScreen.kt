package com.example.yoloaio.features.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.yoloaio.data.FirebaseModule
import com.example.yoloaio.data.UserProfile
import kotlinx.coroutines.launch

/**
 * Admin toolkit + member list — rename, photo (admin), add/remove members
 * (admin), leave group (anyone). Owner-leaves edge case is an accepted
 * simplification per the group-chat plan: adminUids (not ownerUid) gates
 * permissions, so a group can end up admin-less if every admin leaves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(chatId: String, onBack: () -> Unit, onLeft: () -> Unit) {
    val repo = remember { GroupChatRepository() }
    val chatRepo = remember { ChatRepository() }
    val scope = rememberCoroutineScope()
    val myUid = remember { FirebaseModule.auth.currentUser?.uid }

    val group by repo.observeGroup(chatId).collectAsState(initial = null)
    val myInvites by repo.observeMyInvitesFor(chatId).collectAsState(initial = emptyList())

    if (group == null) {
        LoadingShell(title = "Group info", onBack = onBack)
        return
    }
    val g = group!!
    val isAdmin = myUid != null && myUid in g.adminUids

    var memberNames by remember { mutableStateOf(mapOf<String, String>()) }
    LaunchedEffect(g.participants, myInvites) {
        val uids = (g.participants + myInvites.map { it.invitedUid }).filter { it.isNotBlank() && it !in memberNames }
        if (uids.isEmpty()) return@LaunchedEffect
        val fetched = uids.mapNotNull { uid ->
            chatRepo.fetchUser(uid)?.let { uid to (it.displayName.takeIf { n -> n.isNotBlank() } ?: "Unknown") }
        }
        memberNames = memberNames + fetched
    }

    var renaming by remember { mutableStateOf(false) }
    var showAddMembers by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch { repo.setGroupPhoto(chatId, uri).onFailure { error = it.message } }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Group info") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .then(if (isAdmin) Modifier.clickable {
                            photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (g.groupPhotoUrl.isNotBlank()) {
                        AsyncImage(model = g.groupPhotoUrl, contentDescription = null, modifier = Modifier.size(88.dp).clip(CircleShape))
                    } else {
                        Icon(Icons.Rounded.Group, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (renaming) {
                    RenameField(
                        initial = g.groupName,
                        onSave = { newName ->
                            scope.launch { repo.renameGroup(chatId, newName).onFailure { error = it.message } }
                            renaming = false
                        },
                        onCancel = { renaming = false }
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.groupName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (isAdmin) {
                            IconButton(onClick = { renaming = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Rename", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Text(
                    "${g.participants.size} member${if (g.participants.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
            }

            if (isAdmin) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    OutlinedButton(onClick = { showAddMembers = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add members")
                    }
                }
            }

            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
                item {
                    Text(
                        "Members",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }
                items(g.participants, key = { "m_$it" }) { uid ->
                    MemberRow(
                        name = if (uid == myUid) "You" else memberNames[uid] ?: "…",
                        isAdmin = uid in g.adminUids,
                        showRemove = isAdmin && uid != myUid,
                        onRemove = { pendingRemove = uid }
                    )
                }
                if (myInvites.isNotEmpty()) {
                    item {
                        Text(
                            "Invited (pending)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        )
                    }
                    items(myInvites, key = { "i_${it.inviteId}" }) { invite ->
                        PendingMemberRow(
                            name = memberNames[invite.invitedUid] ?: "…",
                            onCancel = { scope.launch { repo.cancelInvite(chatId, invite.invitedUid) } }
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { showLeaveConfirm = true },
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(20.dp)
            ) {
                Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Leave group")
            }
        }
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave group?") },
            text = { Text("You'll stop seeing new messages in \"${g.groupName}\" unless someone invites you back.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    scope.launch { repo.leaveGroup(chatId).onSuccess { onLeft() } }
                }) { Text("Leave", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") } }
        )
    }

    pendingRemove?.let { uid ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove member?") },
            text = { Text("${memberNames[uid] ?: "This person"} will stop seeing new messages.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemove = null
                    scope.launch { repo.removeMember(chatId, uid, memberNames[uid] ?: "Someone") }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("Cancel") } }
        )
    }

    if (showAddMembers) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showAddMembers = false }, sheetState = sheetState) {
            AddMembersSheetContent(
                excludeUids = g.participants.toSet() + myInvites.map { it.invitedUid }.toSet(),
                onAdd = { uids -> repo.inviteMembers(chatId, g.groupName, uids) },
                onDone = { showAddMembers = false }
            )
        }
    }
}

@Composable
private fun RenameField(initial: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
        OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, modifier = Modifier.weight(1f))
        IconButton(onClick = { onSave(value) }) { Icon(Icons.Rounded.Check, contentDescription = "Save") }
        IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, contentDescription = "Cancel") }
    }
}

@Composable
private fun MemberRow(name: String, isAdmin: Boolean, showRemove: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(UserProfile.computeInitials(name))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (isAdmin) {
                Text("Admin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (showRemove) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.PersonRemove, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PendingMemberRow(name: String, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.HourglassEmpty, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun AddMembersSheetContent(
    excludeUids: Set<String>,
    onAdd: suspend (List<String>) -> Result<Unit>,
    onDone: () -> Unit
) {
    val repo = remember { ChatRepository() }
    val users by repo.observeOtherUsers().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var inviting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun handleInvite() {
        if (inviting || selected.isEmpty()) return
        inviting = true
        error = null
        scope.launch {
            onAdd(selected.toList())
                .onSuccess { onDone() }
                // Without this, a failed invite left the sheet with no
                // feedback — the actual bug behind the "invite doesn't
                // seem to do anything" report (a rules gotcha that's
                // since been fixed, but this stays as a safety net for
                // any future failure too).
                .onFailure { error = it.message ?: "Couldn't send invites" }
            inviting = false
        }
    }

    val filtered = remember(users, query, excludeUids) {
        val q = query.trim().lowercase()
        users.filter { it.uid !in excludeUids }
            .filter { q.isBlank() || it.displayName.lowercase().contains(q) || it.email.lowercase().contains(q) }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Add members", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.fillMaxWidth().height(320.dp)) {
            LazyColumn {
                items(filtered, key = { it.uid }) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = if (user.uid in selected) selected - user.uid else selected + user.uid }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(user.displayName.ifBlank { "Unknown" }, modifier = Modifier.weight(1f))
                        if (user.uid in selected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = ::handleInvite,
            enabled = selected.isNotEmpty() && !inviting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    inviting -> "Inviting…"
                    selected.isEmpty() -> "Select people to invite"
                    else -> "Invite ${selected.size}"
                }
            )
        }
    }
}
