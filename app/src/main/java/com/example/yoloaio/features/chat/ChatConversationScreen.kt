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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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

private val emojiSet = listOf(
    "😀", "😂", "🥹", "😍", "😎", "🤔", "🙃", "😴",
    "👍", "🙏", "👏", "🔥", "🎉", "💯", "❤️", "💜",
    "🚀", "✨", "⭐", "🌈", "☕", "🍕", "🎵", "📸"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConversationScreen(
    userId: String,
    onBack: () -> Unit,
    onOpenProfile: (uid: String) -> Unit
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

    val messages by repo.observeMessages(userId).collectAsState(initial = emptyList())
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val glass = LocalGlass.current
    val headerColor = yoloSurfaceColor(strong = true, isDark = glass.isDark)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
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
                verticalArrangement = Arrangement.spacedBy(2.dp),
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
                            isFirstInGroup = isFirstInGroup,
                            isLastInGroup = isLastInGroup,
                            onJoinCall = { room, video ->
                                JitsiCallLauncher.launch(
                                    ctx, room, video, jitsiServerUrl
                                )
                            },
                            onRefreshLocation = { messageId ->
                                shareCurrentLocation(refreshMessageId = messageId)
                            },
                            onOpenLocation = { lat, lon -> openInMaps(ctx, lat, lon) }
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
                    draft = ""
                    scope.launch {
                        val result = repo.sendText(userId, pending)
                        result.onFailure {
                            sendError = it.message ?: "Send failed"
                            draft = pending
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

@Composable
private fun MessageBubble(
    msg: ChatMessageDoc,
    fromMe: Boolean,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    onJoinCall: (roomName: String, video: Boolean) -> Unit,
    onRefreshLocation: (messageId: String) -> Unit,
    onOpenLocation: (lat: Double, lon: Double) -> Unit
) {
    val alignment = if (fromMe) Alignment.End else Alignment.Start
    val timeString = msg.timestamp?.toDate()?.let {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(it)
    } ?: ""

    // Asymmetric corner radii flip based on position-in-group. The
    // "tail" corner (4dp) only appears on the FIRST bubble of a group,
    // so consecutive bubbles share rounded edges instead of repeating
    // the tail and looking visually jagged.
    val r = 18.dp
    val tail = 4.dp
    val shape = when {
        fromMe -> RoundedCornerShape(
            topStart = r,
            topEnd = if (isFirstInGroup) tail else r,
            bottomEnd = if (isLastInGroup) r else tail,
            bottomStart = r
        )
        else -> RoundedCornerShape(
            topStart = if (isFirstInGroup) tail else r,
            topEnd = r,
            bottomEnd = r,
            bottomStart = if (isLastInGroup) r else tail
        )
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        when (msg.type) {
            ChatMessageDoc.TYPE_TEXT -> TextBubble(
                text = msg.text.orEmpty(), fromMe = fromMe, shape = shape
            )
            ChatMessageDoc.TYPE_IMAGE -> MediaBubble(msg.mediaUrl, "PHOTO", fromMe)
            ChatMessageDoc.TYPE_GIF -> MediaBubble(msg.mediaUrl, "GIF", fromMe)
            ChatMessageDoc.TYPE_CALL -> CallInviteBubble(
                video = msg.callVideo,
                fromMe = fromMe,
                shape = shape,
                onJoin = {
                    msg.callRoom?.takeIf { it.isNotBlank() }
                        ?.let { onJoinCall(it, msg.callVideo) }
                }
            )
            ChatMessageDoc.TYPE_LOCATION -> LocationBubble(
                msg = msg,
                fromMe = fromMe,
                shape = shape,
                onRefresh = { onRefreshLocation(msg.id) },
                onOpen = { onOpenLocation(msg.locLat, msg.locLon) }
            )
            else -> TextBubble(msg.text.orEmpty(), fromMe, shape)
        }
        // Timestamp only on the LAST bubble of a group — middle bubbles
        // get a clean look without per-message clutter.
        if (isLastInGroup) {
            Text(
                timeString,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun LocationBubble(
    msg: ChatMessageDoc,
    fromMe: Boolean,
    shape: RoundedCornerShape,
    onRefresh: () -> Unit,
    onOpen: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val fg = if (fromMe) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val bgModifier = if (fromMe) {
        Modifier.background(Brush.linearGradient(listOf(primary, tertiary)))
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
    }
    val updatedLabel = msg.locUpdatedAt
        .takeIf { it > 0L }
        ?.let { relativeAgo(it) }
        ?: "just now"

    Column(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clip(shape)
            .then(bgModifier)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (fromMe) "My location" else "Shared location",
                    color = fg,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "%.5f, %.5f".format(msg.locLat, msg.locLon),
                    color = fg.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    "Updated $updatedLabel",
                    color = fg.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sender-only: refresh updates the same doc with the current
            // coords. Recipient can't push the sender's location for them.
            if (fromMe) {
                TextButton(onClick = onRefresh) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh", color = fg, style = MaterialTheme.typography.labelMedium)
                }
            }
            TextButton(onClick = onOpen) {
                Icon(
                    Icons.Rounded.Map,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Open in Maps", color = fg, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** "12 sec ago", "5 min ago", "2 hr ago", or absolute time for older. */
private fun relativeAgo(epochMs: Long): String {
    val deltaSec = ((System.currentTimeMillis() - epochMs) / 1000L).coerceAtLeast(0L)
    return when {
        deltaSec < 10 -> "just now"
        deltaSec < 60 -> "$deltaSec sec ago"
        deltaSec < 3600 -> "${deltaSec / 60} min ago"
        deltaSec < 86_400 -> "${deltaSec / 3600} hr ago"
        else -> java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(epochMs))
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

@Composable
private fun CallInviteBubble(
    video: Boolean,
    fromMe: Boolean,
    shape: RoundedCornerShape,
    onJoin: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val fg = if (fromMe) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val bgModifier = if (fromMe) {
        Modifier.background(Brush.linearGradient(listOf(primary, tertiary)))
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
    }
    val label = if (video) "Video call" else "Voice call"
    val icon = if (video) Icons.Rounded.Videocam else Icons.Rounded.Call

    Row(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clip(shape)
            .then(bgModifier)
            .clickable(onClick = onJoin)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                label,
                color = fg,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Tap to join",
                color = fg.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
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

// ───────────────────────── date separators ─────────────────────────

@Composable
private fun DateSeparator(epochMs: Long) {
    // Hairline rule on each side of the day label — much more elegant
    // than a chip. Subtle, doesn't compete with bubbles.
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        )
        Text(
            text = formatDayLabel(epochMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        )
    }
}

/** Day bucket key (year * 1000 + day-of-year) — used to detect day flips. */
private fun dayKeyOf(epochMs: Long): Int {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
    return cal.get(java.util.Calendar.YEAR) * 1000 +
        cal.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun formatDayLabel(epochMs: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
    val sameYear = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
    val dayDelta = dayKeyOf(now.timeInMillis) - dayKeyOf(epochMs)
    return when {
        dayDelta == 0 -> "Today"
        dayDelta == 1 -> "Yesterday"
        sameYear -> java.text.SimpleDateFormat("EEEE, MMM d", java.util.Locale.getDefault())
            .format(java.util.Date(epochMs))
        else -> java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(epochMs))
    }
}

/** Fires a `geo:` intent — opens any installed maps app at the given pin. */
private fun openInMaps(context: android.content.Context, lat: Double, lon: Double) {
    val uri = android.net.Uri.parse("geo:$lat,$lon?q=$lat,$lon(Shared%20location)")
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
private fun TextBubble(text: String, fromMe: Boolean, shape: RoundedCornerShape) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    // Own messages: linear gradient from primary → tertiary. Reads
    // "neon" / modern messenger. Incoming: translucent glass on the
    // surfaceVariant so the global app backdrop bleeds through subtly.
    val textColor = if (fromMe) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val bgModifier = if (fromMe) {
        Modifier.background(Brush.linearGradient(listOf(primary, tertiary)))
    } else {
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
    }

    Box(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clip(shape)
            .then(bgModifier)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.35f
        )
    }
}

@Composable
private fun MediaBubble(url: String?, tag: String, fromMe: Boolean) {
    val shape =
        if (fromMe) RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
        else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    Box(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = url,
            contentDescription = tag,
            modifier = Modifier
                .widthIn(max = 240.dp)
                .clip(shape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(tag, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun InputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    uploading: Boolean,
    sharingLocation: Boolean,
    onEmojiClick: () -> Unit,
    onGifClick: () -> Unit,
    onLocationClick: () -> Unit,
    onSend: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val canSend = draft.isNotBlank()

    Column {
        if (uploading || sharingLocation) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (sharingLocation) "Getting location…" else "Uploading…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Pill-shaped glass row holding all attachment affordances + the
        // text field. Send button sits outside as a circular gradient
        // FAB so the eye reads the pill+button pair as one composition.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEmojiClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.EmojiEmotions,
                        contentDescription = "Emoji",
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onGifClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.Gif,
                        contentDescription = "GIF",
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(
                    onClick = onLocationClick,
                    enabled = !sharingLocation,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Rounded.MyLocation,
                        contentDescription = "Share location",
                        modifier = Modifier.size(22.dp)
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp, vertical = 12.dp)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                event.key == Key.Enter &&
                                !event.isShiftPressed
                            ) {
                                onSend()
                                true
                            } else false
                        },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = Brush.linearGradient(listOf(primary, tertiary)),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(
                                "Message",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = 0.65f)
                            )
                        }
                        inner()
                    }
                )
            }

            // Gradient circular send FAB. Disabled state fades to the
            // surface tone so it visually retreats until you type.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) Brush.linearGradient(listOf(primary, tertiary))
                        else Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            )
                        )
                    )
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send",
                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmojiPicker(onPick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Emoji", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.height(220.dp)
        ) {
            itemsIndexed(emojiSet) { _, emoji ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPick(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadingShell(title: String, onBack: () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
