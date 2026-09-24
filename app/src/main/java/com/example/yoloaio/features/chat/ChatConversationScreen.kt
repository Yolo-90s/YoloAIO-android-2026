package com.example.yoloaio.features.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import com.example.yoloaio.notifications.ChatNotifications
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.example.yoloaio.data.FirebaseModule
import com.example.yoloaio.data.UserProfile
import com.example.yoloaio.features.weather.LocationProvider
import com.example.yoloaio.ui.components.yoloSurfaceColor
import com.example.yoloaio.ui.theme.LocalGlass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private enum class AttachmentMode { None, Emoji, Picker }
// PickerKind was here — Image and Gif. Image sharing was removed at the
// user's request; only GIFs remain, so the kind switch is gone too.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConversationScreen(
    userId: String,
    onBack: () -> Unit,
    onOpenProfile: (uid: String) -> Unit,
    onOpenMindMatch: (code: String) -> Unit
) {
    val repo = remember { ChatRepository() }
    val scope = rememberCoroutineScope()
    val currentUid = remember { FirebaseModule.auth.currentUser?.uid }
    // Allow Firestore-driven override of the Jitsi server URL. Defaults
    // are handled inside JitsiCallLauncher when this is blank.
    val jitsiServerUrl = com.example.yoloaio.data.LocalAppConfig.current.jitsiServerUrl

    // While this screen is on top, suppress system notifications for this
    // chat — new messages already render inline below, so a buzz would be
    // redundant and annoying. Also dismiss any already-posted notification
    // for this partner since opening the chat means the user saw it.
    val ctx = LocalContext.current
    DisposableEffect(userId) {
        ChatNotifications.activeChatPartnerUid = userId
        runCatching {
            NotificationManagerCompat.from(ctx).cancel(userId.hashCode())
        }
        // Opportunistic presence write — same throttle as the one in
        // MainActivity.onResume. Opening a chat is a strong signal that
        // the user is actively using the app, so it's a good time to
        // refresh their last-known location.
        com.example.yoloaio.data.LocationPresence.maybeUpdate(ctx)
        onDispose { ChatNotifications.activeChatPartnerUid = null }
    }

    val otherUserState = produceState<UserProfile?>(initialValue = null, userId) {
        value = repo.fetchUser(userId)
    }
    val otherUser = otherUserState.value

    if (otherUser == null) {
        LoadingShell(title = "Chat", onBack = onBack)
        return
    }

    val chatId = remember(userId, currentUid) { currentUid?.let { ChatIds.chatIdFor(it, userId) } }
    val messages by repo.observeMessages(userId).collectAsState(initial = emptyList())
    val chatDoc by repo.observeChatDoc(userId).collectAsState(initial = null)
    val otherLastRead = chatDoc?.lastRead?.get(userId)?.toDate()?.time ?: 0L

    // Declared up here so the call + location permission lambdas can write
    // to it. (They were running into "Unresolved reference 'sendError'"
    // because the original declaration sat below them.)
    var sendError by remember { mutableStateOf<String?>(null) }
    var pendingCallVideo by remember { mutableStateOf<Boolean?>(null) }

    // Permission launcher for the call buttons. We ask for RECORD_AUDIO
    // (always needed) and CAMERA (only meaningful for video calls). On
    // grant we send the chat invite + launch the Jitsi native activity.
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val video = pendingCallVideo ?: return@rememberLauncherForActivityResult
        val audioOk = grants[android.Manifest.permission.RECORD_AUDIO] == true
        val cameraOk = !video || grants[android.Manifest.permission.CAMERA] == true
        pendingCallVideo = null
        if (audioOk && cameraOk && currentUid != null) {
            startCall(
                repo = repo,
                scope = scope,
                me = currentUid,
                otherUid = userId,
                video = video,
                context = ctx,
                serverUrl = jitsiServerUrl
            )
        }
    }

    var sharingLocation by remember { mutableStateOf(false) }
    var pendingLocationAction by remember { mutableStateOf<LocationAction?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted =
            grants[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val action = pendingLocationAction
        pendingLocationAction = null
        if (granted && action != null && currentUid != null) {
            sharingLocation = true
            scope.launch {
                runLocationAction(
                    action = action,
                    context = ctx,
                    repo = repo,
                    otherUid = userId,
                    onError = { sendError = it }
                )
                sharingLocation = false
            }
        } else if (!granted) {
            sendError = "Location permission denied."
        }
    }

    fun shareCurrentLocation(refreshMessageId: String? = null) {
        if (sharingLocation || currentUid == null) return
        val action = if (refreshMessageId == null) LocationAction.Send
        else LocationAction.Refresh(refreshMessageId)
        val granted = LocationProvider.hasPermission(ctx)
        if (granted) {
            sharingLocation = true
            scope.launch {
                runLocationAction(
                    action = action,
                    context = ctx,
                    repo = repo,
                    otherUid = userId,
                    onError = { sendError = it }
                )
                sharingLocation = false
            }
        } else {
            pendingLocationAction = action
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun beginCall(video: Boolean) {
        if (currentUid == null) return
        pendingCallVideo = video
        val needed = mutableListOf(android.Manifest.permission.RECORD_AUDIO)
        if (video) needed += android.Manifest.permission.CAMERA
        val allGranted = needed.all {
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            pendingCallVideo = null
            startCall(
                repo = repo,
                scope = scope,
                me = currentUid,
                otherUid = userId,
                video = video,
                context = ctx,
                serverUrl = jitsiServerUrl
            )
        } else {
            callPermissionLauncher.launch(needed.toTypedArray())
        }
    }

    var draft by remember { mutableStateOf("") }
    var attachmentMode by remember { mutableStateOf(AttachmentMode.None) }
    var uploading by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<ChatMessageDoc?>(null) }
    var replyTo by remember { mutableStateOf<ReplyPreview?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val actionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val glass = LocalGlass.current
    val headerColor = yoloSurfaceColor(strong = true, isDark = glass.isDark)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Mark read whenever a new message arrives while this screen is open
    // (mirrors the notification-suppression DisposableEffect above — being
    // on this screen at all means you've seen the latest message).
    LaunchedEffect(chatId, messages.size) {
        chatId?.let { ChatInteractions.markChatRead(ctx, it) }
    }

    // GIF-only picker. (Image-share was removed; we no longer need a
    // kind switch — every pick goes through as a GIF.)
    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            uploading = true
            scope.launch {
                val result = repo.sendMedia(userId, uri, ChatMessageDoc.TYPE_GIF)
                uploading = false
                result.onFailure { sendError = it.message ?: "Upload failed" }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    ConversationTitle(
                        user = otherUser,
                        onClick = { onOpenProfile(otherUser.uid) }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { beginCall(video = false) }) {
                        Icon(
                            Icons.Rounded.Call,
                            contentDescription = "Voice call",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { beginCall(video = true) }) {
                        Icon(
                            Icons.Rounded.Videocam,
                            contentDescription = "Video call",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !deleting
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete chat",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                // Solid header colour so the title stays readable when the IME
                // is up and messages scroll right up against the app bar — the
                // previous transparent colour made it blend into the bubbles.
                colors = TopAppBarDefaults.topAppBarColors(containerColor = headerColor)
            )
        }
    ) { padding ->
        // imePadding() makes this content Column (messages + input bar) shift
        // up by the keyboard height. The Scaffold's topBar slot sits OUTSIDE
        // this Column, so the header stays pinned to the top of the screen
        // while typing.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                // 2dp default; the per-bubble spacing is computed below
                // (within-group = tight, between-group = bigger).
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                var lastDayKey: Int? = null
                messages.forEachIndexed { index, msg ->
                    val day = dayKeyOf(msg.timestamp?.toDate()?.time
                        ?: System.currentTimeMillis())
                    if (day != lastDayKey) {
                        item(key = "sep-$day-${msg.id}") {
                            DateSeparator(
                                epochMs = msg.timestamp?.toDate()?.time
                                    ?: System.currentTimeMillis()
                            )
                        }
                        lastDayKey = day
                    }

                    // Grouping: a message is "in the same group" as the
                    // previous one when the same sender posted both
                    // within a 2-minute window. First in group → show
                    // sender avatar + name. Last in group → show
                    // timestamp. Middle messages get nothing — pure
                    // bubbles, tight spacing. Classic iMessage pattern.
                    val prev = messages.getOrNull(index - 1)
                    val next = messages.getOrNull(index + 1)
                    val msgMs = msg.timestamp?.toDate()?.time ?: 0L
                    val prevMs = prev?.timestamp?.toDate()?.time ?: 0L
                    val nextMs = next?.timestamp?.toDate()?.time ?: 0L
                    val sameDayPrev = prev != null && day == dayKeyOf(prevMs.takeIf { it > 0 } ?: System.currentTimeMillis())
                    val sameDayNext = next != null && day == dayKeyOf(nextMs.takeIf { it > 0 } ?: System.currentTimeMillis())
                    val isFirstInGroup = prev == null ||
                        prev.senderId != msg.senderId ||
                        !sameDayPrev ||
                        (msgMs > 0 && prevMs > 0 && msgMs - prevMs > 2 * 60 * 1000)
                    val isLastInGroup = next == null ||
                        next.senderId != msg.senderId ||
                        !sameDayNext ||
                        (nextMs > 0 && msgMs > 0 && nextMs - msgMs > 2 * 60 * 1000)

                    item(key = msg.id) {
                        MessageBubble(
                            msg = msg,
                            fromMe = msg.senderId == currentUid,
                            myUid = currentUid,
                            isFirstInGroup = isFirstInGroup,
                            isLastInGroup = isLastInGroup,
                            seen = msg.senderId == currentUid && msgMs > 0 && otherLastRead >= msgMs,
                            onJoinCall = { room, video ->
                                JitsiCallLauncher.launch(
                                    ctx, room, video, jitsiServerUrl
                                )
                            },
                            onJoinMindMatch = onOpenMindMatch,
                            onRefreshLocation = { messageId ->
                                shareCurrentLocation(refreshMessageId = messageId)
                            },
                            onOpenLocation = { lat, lon -> openLocationInMaps(ctx, lat, lon) },
                            onLongPress = { actionMessage = it },
                            onToggleReaction = { emoji ->
                                val mine = msg.reactions[currentUid]
                                chatId?.let { cid ->
                                    scope.launch { ChatInteractions.toggleReaction(cid, msg.id, emoji, mine) }
                                }
                            }
                        )
                        // Extra breathing room AFTER the last item in a
                        // group separates clusters visually without
                        // disturbing the tight intra-group spacing.
                        if (isLastInGroup) Spacer(Modifier.height(6.dp))
                    }
                }
            }
            sendError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            InputBar(
                draft = draft,
                onDraftChange = { draft = it; sendError = null },
                uploading = uploading,
                sharingLocation = sharingLocation,
                replyTo = replyTo,
                onCancelReply = { replyTo = null },
                onEmojiClick = { attachmentMode = AttachmentMode.Emoji },
                onGifClick = {
                    mediaPicker.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.SingleMimeType("image/gif")
                        )
                    )
                },
                onLocationClick = { shareCurrentLocation() },
                onSend = {
                    val text = draft.trim()
                    if (text.isEmpty()) return@InputBar
                    val pending = text
                    val pendingReply = replyTo
                    draft = ""
                    replyTo = null
                    scope.launch {
                        val result = repo.sendText(userId, pending, pendingReply)
                        result.onFailure {
                            sendError = it.message ?: "Send failed"
                            draft = pending
                            replyTo = pendingReply
                        }
                    }
                }
            )
        }

        if (attachmentMode == AttachmentMode.Emoji) {
            ModalBottomSheet(
                onDismissRequest = { attachmentMode = AttachmentMode.None },
                sheetState = sheetState
            ) {
                EmojiPicker(onPick = { emoji ->
                    draft += emoji
                    scope.launch {
                        sheetState.hide()
                        attachmentMode = AttachmentMode.None
                    }
                })
            }
        }

        actionMessage?.let { target ->
            ModalBottomSheet(
                onDismissRequest = { actionMessage = null },
                sheetState = actionSheetState
            ) {
                MessageActionSheetContent(
                    myCurrentReaction = target.reactions[currentUid],
                    onReact = { emoji ->
                        val mine = target.reactions[currentUid]
                        chatId?.let { cid ->
                            scope.launch { ChatInteractions.toggleReaction(cid, target.id, emoji, mine) }
                        }
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
                                    ChatMessageDoc.TYPE_LOCATION -> "📍 Location"
                                    else -> "Message"
                                }
                        )
                        scope.launch { actionSheetState.hide(); actionMessage = null }
                    }
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteDialog = false },
            title = { Text("Delete chat?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "This permanently removes every message in your conversation " +
                        "with ${otherUser.displayName.ifBlank { "this person" }}. " +
                        "The chat disappears for both of you and cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = true
                        scope.launch {
                            val result = repo.deleteChat(userId)
                            result
                                .onSuccess {
                                    showDeleteDialog = false
                                    deleting = false
                                    onBack()
                                }
                                .onFailure {
                                    deleting = false
                                    sendError = it.message ?: "Couldn't delete chat"
                                    showDeleteDialog = false
                                }
                        }
                    },
                    enabled = !deleting
                ) {
                    if (deleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !deleting
                ) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ConversationTitle(user: UserProfile, onClick: () -> Unit) {
    // Presence proxy — lit ring when the other user opened the app
    // recently (writes their location every 5 min, so a fresh write
    // is a strong "active now" signal). Beyond 30 min we treat them
    // as away.
    val now = System.currentTimeMillis()
    val isActive = user.lastLocationAt > 0L &&
        now - user.lastLocationAt < 30 * 60 * 1000L
    val ringBrush = if (isActive) {
        Brush.sweepGradient(
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.primary
            )
        )
    } else {
        Brush.linearGradient(
            listOf(Color.Transparent, Color.Transparent)
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(ringBrush)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(user.avatarComposeColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    user.initials.ifBlank { UserProfile.computeInitials(user.displayName) },
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                user.displayName.ifBlank { "Unknown" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (isActive) "Active now" else "Tap for details",
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

internal sealed interface LocationAction {
    data object Send : LocationAction
    data class Refresh(val messageId: String) : LocationAction
}

/**
 * Reads the last-known location and writes/updates the chat message.
 * Called from a coroutine — does the IO work for the location button +
 * refresh button. Errors bubble up via [onError] (toast / inline message).
 */
internal suspend fun runLocationAction(
    action: LocationAction,
    context: android.content.Context,
    repo: ChatRepository,
    otherUid: String,
    onError: (String) -> Unit
) {
    val location = LocationProvider.lastKnown(context)
    if (location == null) {
        onError(
            "Couldn't get your location. Turn on Location Services and open " +
                "a map app once so a recent fix is cached, then try again."
        )
        return
    }
    val result = when (action) {
        LocationAction.Send -> repo.sendLocation(otherUid, location.latitude, location.longitude)
            .map { Unit }
        is LocationAction.Refresh -> repo.updateLocation(
            otherUid = otherUid,
            messageId = action.messageId,
            lat = location.latitude,
            lon = location.longitude
        )
    }
    result.onFailure { onError(it.message ?: "Couldn't share location") }
}

/**
 * Sends the call-invite chat message + launches the Jitsi native activity.
 * Called from the composable's `beginCall` after permissions are granted.
 * Lives outside the composable so the lambda holds onto the minimum
 * closure footprint.
 */
private fun startCall(
    repo: ChatRepository,
    scope: CoroutineScope,
    me: String,
    otherUid: String,
    video: Boolean,
    context: android.content.Context,
    serverUrl: String
) {
    val room = CallRoom.forUsers(me, otherUid)
    JitsiCallLauncher.launch(context, room, video, serverUrl)
    scope.launch {
        // Drop the invite into the chat so the recipient sees the same
        // "tap to join" affordance when they open the conversation. If
        // the write fails we still proceed with the call; the caller
        // already navigated.
        repo.sendCallInvite(otherUid, room, video)
    }
}
