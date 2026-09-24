package com.example.yoloaio.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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

/**
 * Message-bubble rendering + the input bar, shared by [ChatConversationScreen]
 * (1:1) and [GroupChatScreen] — extracted here since both need the same
 * reactions/reply/read-receipt UI and it'd otherwise be duplicated. The
 * two screens keep their own headers/data-loading (see the group-chat
 * plan's "don't share the header" note) — only the render layer lives here.
 */

internal val quickReactionEmojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

internal val emojiSet = listOf(
    "😀", "😂", "🥹", "😍", "😎", "🤔", "🙃", "😴",
    "👍", "🙏", "👏", "🔥", "🎉", "💯", "❤️", "💜",
    "🚀", "✨", "⭐", "🌈", "☕", "🍕", "🎵", "📸"
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    msg: ChatMessageDoc,
    fromMe: Boolean,
    myUid: String?,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    seen: Boolean = false,
    senderLabel: String? = null,
    onJoinCall: (roomName: String, video: Boolean) -> Unit = { _, _ -> },
    onJoinMindMatch: (code: String) -> Unit = {},
    onRefreshLocation: (messageId: String) -> Unit = {},
    onOpenLocation: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    onLongPress: (ChatMessageDoc) -> Unit = {},
    onToggleReaction: (emoji: String) -> Unit = {},
    onReplyPreviewClick: (messageId: String) -> Unit = {}
) {
    if (msg.type == ChatMessageDoc.TYPE_SYSTEM) {
        SystemMessageLine(text = msg.text.orEmpty())
        return
    }

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

    val longPressModifier = Modifier.combinedClickable(
        onClick = {
            msg.replyToId?.let { onReplyPreviewClick(it) }
        },
        onLongClick = { onLongPress(msg) }
    )

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        // Sender name — group chats only, first bubble of a group, incoming only.
        if (senderLabel != null && !fromMe && isFirstInGroup) {
            Text(
                senderLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 2.dp)
            )
        }

        if (msg.replyToId != null) {
            ReplyQuoteStrip(senderId = msg.replyToSenderId, text = msg.replyToText.orEmpty(), fromMe = fromMe)
        }

        Box(modifier = longPressModifier) {
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
                ChatMessageDoc.TYPE_MINDMATCH -> MindMatchInviteBubble(
                    fromMe = fromMe,
                    shape = shape,
                    onJoin = {
                        msg.mindMatchCode?.takeIf { it.isNotBlank() }
                            ?.let { onJoinMindMatch(it) }
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
        }

        if (msg.reactions.isNotEmpty()) {
            ReactionChipsRow(reactions = msg.reactions, myUid = myUid, onToggle = onToggleReaction)
        }

        // Timestamp (+ seen tick, mine only) on the LAST bubble of a group.
        if (isLastInGroup) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(
                    timeString,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
                if (fromMe) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (seen) Icons.Rounded.DoneAll else Icons.Rounded.Check,
                        contentDescription = if (seen) "Seen" else "Sent",
                        tint = if (seen) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemMessageLine(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReplyQuoteStrip(senderId: String?, text: String, fromMe: Boolean) {
    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text.ifBlank { "Message" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}

@Composable
private fun ReactionChipsRow(reactions: Map<String, String>, myUid: String?, onToggle: (String) -> Unit) {
    val counts = reactions.values.groupingBy { it }.eachCount()
    val myReaction = myUid?.let { reactions[it] }
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        counts.forEach { (emoji, count) ->
            val mine = emoji == myReaction
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                    .clickable { onToggle(emoji) }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("$emoji $count", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Long-press action sheet content: quick-react row + Reply. */
@Composable
fun MessageActionSheetContent(
    myCurrentReaction: String?,
    onReact: (String) -> Unit,
    onReply: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            quickReactionEmojis.forEach { emoji ->
                val mine = emoji == myCurrentReaction
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent
                        )
                        .clickable { onReact(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onReply).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text("Reply", style = MaterialTheme.typography.bodyLarge)
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
internal fun relativeAgo(epochMs: Long): String {
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

/** Fires a `geo:` intent — opens any installed maps app at the given pin. */
internal fun openLocationInMaps(context: android.content.Context, lat: Double, lon: Double) {
    val uri = android.net.Uri.parse("geo:$lat,$lon?q=$lat,$lon(Shared%20location)")
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
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

@Composable
private fun MindMatchInviteBubble(
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

    Row(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clip(shape)
            .then(bgModifier)
            .clickable(onClick = onJoin)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Psychology, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "MindMatch invite",
                color = fg,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Tap to play",
                color = fg.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// ───────────────────────── date separators ─────────────────────────

@Composable
fun DateSeparator(epochMs: Long) {
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
internal fun dayKeyOf(epochMs: Long): Int {
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
fun ReplyComposerStrip(replyTo: ReplyPreview, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Replying to", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Text(replyTo.text.ifBlank { "Message" }, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun InputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    uploading: Boolean,
    sharingLocation: Boolean,
    replyTo: ReplyPreview? = null,
    onCancelReply: () -> Unit = {},
    onEmojiClick: () -> Unit,
    onGifClick: () -> Unit,
    onLocationClick: () -> Unit,
    onSend: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val canSend = draft.isNotBlank()

    Column {
        if (replyTo != null) {
            ReplyComposerStrip(replyTo = replyTo, onCancel = onCancelReply)
        }
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
                BasicTextField(
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
fun EmojiPicker(onPick: (String) -> Unit) {
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
fun LoadingShell(title: String, onBack: () -> Unit) {
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
