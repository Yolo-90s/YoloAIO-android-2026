package com.example.yoloaio.features.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.yoloaio.data.FirebaseModule
import com.example.yoloaio.ui.components.yoloSurfaceColor
import com.example.yoloaio.ui.theme.LocalGlass
import kotlinx.coroutines.launch

/**
 * Group counterpart to [ChatConversationScreen] — own header (group name/
 * photo/member count, tap → info screen), but built on the SAME shared
 * message-bubble/input-bar components from ChatMessageComponents.kt so
 * reactions/reply/read-receipts aren't duplicated between the two screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    chatId: String,
    onBack: () -> Unit,
    onOpenInfo: (chatId: String) -> Unit
) {
    val repo = remember { GroupChatRepository() }
    val chatRepo = remember { ChatRepository() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val currentUid = remember { FirebaseModule.auth.currentUser?.uid }

    val group by repo.observeGroup(chatId).collectAsState(initial = null)
    val messages by repo.observeGroupMessages(chatId).collectAsState(initial = emptyList())

    if (group == null) {
        LoadingShell(title = "Group", onBack = onBack)
        return
    }
    val g = group!!

    // Small per-screen name cache — resolves sender uids to display names
    // for the "who said this" label. Re-fetches only uids not already
    // cached, so it stays cheap as new members join mid-conversation.
    var memberNames by remember { mutableStateOf(mapOf<String, String>()) }
    LaunchedEffect(g.participants) {
        val missing = g.participants.filter { it != currentUid && it !in memberNames }
        if (missing.isEmpty()) return@LaunchedEffect
        val fetched = missing.mapNotNull { uid ->
            chatRepo.fetchUser(uid)?.let { uid to (it.displayName.takeIf { n -> n.isNotBlank() } ?: "Unknown") }
        }
        memberNames = memberNames + fetched
    }

    var draft by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var attachmentMode by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<ChatMessageDoc?>(null) }
    var replyTo by remember { mutableStateOf<ReplyPreview?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val actionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val glass = LocalGlass.current
    val headerColor = yoloSurfaceColor(strong = true, isDark = glass.isDark)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LaunchedEffect(chatId, messages.size) {
        ChatInteractions.markChatRead(ctx, chatId)
    }
    // Same suppression mechanism ChatConversationScreen uses, keyed the
    // same way (ChatNotifications generalized activeChatPartnerUid to mean
    // "the notification key of whatever chat is on screen" — for a group
    // that key is the chatId itself).
    DisposableEffect(chatId) {
        com.example.yoloaio.notifications.ChatNotifications.activeChatPartnerUid = chatId
        onDispose { com.example.yoloaio.notifications.ChatNotifications.activeChatPartnerUid = null }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            uploading = true
            scope.launch {
                val result = repo.sendGroupMedia(chatId, uri, ChatMessageDoc.TYPE_GIF)
                uploading = false
                result.onFailure { sendError = it.message ?: "Upload failed" }
            }
        }
    }

    // "Seen by everyone" once every OTHER participant's lastRead is >= the
    // last message's timestamp — matches the plan's 2-state design; group
    // chats show a simple all-seen tick rather than per-person avatars.
    val lastMsgMs = messages.lastOrNull()?.timestamp?.toDate()?.time ?: 0L
    val seenByAll = lastMsgMs > 0 && g.participants.filter { it != currentUid }.let { others ->
        others.isNotEmpty() && others.all { uid -> (g.lastRead[uid]?.toDate()?.time ?: 0L) >= lastMsgMs }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { GroupTitle(name = g.groupName, memberCount = g.participants.size, photoUrl = g.groupPhotoUrl, onClick = { onOpenInfo(chatId) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenInfo(chatId) }) {
                        Icon(Icons.Rounded.Info, contentDescription = "Group info")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = headerColor)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                var lastDayKey: Int? = null
                messages.forEachIndexed { index, msg ->
                    val day = dayKeyOf(msg.timestamp?.toDate()?.time ?: System.currentTimeMillis())
                    if (day != lastDayKey) {
                        item(key = "sep-$day-${msg.id}") {
                            DateSeparator(epochMs = msg.timestamp?.toDate()?.time ?: System.currentTimeMillis())
                        }
                        lastDayKey = day
                    }
                    val prev = messages.getOrNull(index - 1)
                    val next = messages.getOrNull(index + 1)
                    val msgMs = msg.timestamp?.toDate()?.time ?: 0L
                    val prevMs = prev?.timestamp?.toDate()?.time ?: 0L
                    val nextMs = next?.timestamp?.toDate()?.time ?: 0L
                    val sameDayPrev = prev != null && day == dayKeyOf(prevMs.takeIf { it > 0 } ?: System.currentTimeMillis())
                    val sameDayNext = next != null && day == dayKeyOf(nextMs.takeIf { it > 0 } ?: System.currentTimeMillis())
                    val isFirstInGroup = prev == null || prev.senderId != msg.senderId || !sameDayPrev ||
                        (msgMs > 0 && prevMs > 0 && msgMs - prevMs > 2 * 60 * 1000)
                    val isLastInGroup = next == null || next.senderId != msg.senderId || !sameDayNext ||
                        (nextMs > 0 && msgMs > 0 && nextMs - msgMs > 2 * 60 * 1000)
                    val isVeryLastMessage = index == messages.lastIndex

                    item(key = msg.id) {
                        MessageBubble(
                            msg = msg,
                            fromMe = msg.senderId == currentUid,
                            myUid = currentUid,
                            isFirstInGroup = isFirstInGroup,
                            isLastInGroup = isLastInGroup,
                            seen = msg.senderId == currentUid && isVeryLastMessage && seenByAll,
                            senderLabel = if (msg.senderId != currentUid) memberNames[msg.senderId] ?: "…" else null,
                            onLongPress = { actionMessage = it },
                            onToggleReaction = { emoji ->
                                val mine = msg.reactions[currentUid]
                                scope.launch { ChatInteractions.toggleReaction(chatId, msg.id, emoji, mine) }
                            }
                        )
                        if (isLastInGroup) Spacer(Modifier.height(6.dp))
                    }
                }
            }
            sendError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            InputBar(
                draft = draft,
                onDraftChange = { draft = it; sendError = null },
                uploading = uploading,
                sharingLocation = false,
                replyTo = replyTo,
                onCancelReply = { replyTo = null },
                onEmojiClick = { attachmentMode = true },
                onGifClick = {
                    mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.SingleMimeType("image/gif")))
                },
                onLocationClick = {},
                onSend = {
                    val text = draft.trim()
                    if (text.isEmpty()) return@InputBar
                    val pending = text
                    val pendingReply = replyTo
                    draft = ""
                    replyTo = null
                    scope.launch {
                        repo.sendGroupText(chatId, pending, pendingReply).onFailure {
                            sendError = it.message ?: "Send failed"
                            draft = pending
                            replyTo = pendingReply
                        }
                    }
                }
            )
        }

        if (attachmentMode) {
            ModalBottomSheet(onDismissRequest = { attachmentMode = false }, sheetState = sheetState) {
                EmojiPicker(onPick = { emoji ->
                    draft += emoji
                    scope.launch { sheetState.hide(); attachmentMode = false }
                })
            }
        }

        actionMessage?.let { target ->
            ModalBottomSheet(onDismissRequest = { actionMessage = null }, sheetState = actionSheetState) {
                MessageActionSheetContent(
                    myCurrentReaction = target.reactions[currentUid],
                    onReact = { emoji ->
                        val mine = target.reactions[currentUid]
                        scope.launch { ChatInteractions.toggleReaction(chatId, target.id, emoji, mine) }
                        scope.launch { actionSheetState.hide(); actionMessage = null }
                    },
                    onReply = {
                        replyTo = ReplyPreview(
                            messageId = target.id,
                            senderId = target.senderId,
                            text = target.text?.takeIf { it.isNotBlank() }
                                ?: when (target.type) {
                                    ChatMessageDoc.TYPE_IMAGE -> "📷 Photo"
                                    ChatMessageDoc.TYPE_GIF -> "🎞️ GIF"
                                    else -> "Message"
                                }
                        )
                        scope.launch { actionSheetState.hide(); actionMessage = null }
                    }
                )
            }
        }
    }
}

@Composable
private fun GroupTitle(name: String, memberCount: Int, photoUrl: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            if (photoUrl.isNotBlank()) {
                AsyncImage(model = photoUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape))
            } else {
                Icon(Icons.Rounded.Group, contentDescription = null, tint = Color.White)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "$memberCount member${if (memberCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
