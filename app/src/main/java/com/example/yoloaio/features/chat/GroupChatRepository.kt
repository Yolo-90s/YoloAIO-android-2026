package com.example.yoloaio.features.chat

import android.net.Uri
import com.example.yoloaio.data.FirebaseModule
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Group-chat counterpart to [ChatRepository] — kept as a fully separate
 * set of functions rather than retrofitting the 1:1 ones, since every
 * existing `ChatRepository` function is `otherUid`-shaped and derives a
 * 2-party `chatId` internally; groups have no "other uid," only a
 * `chatId` (a fresh auto-id) from the moment they're created. This
 * protects the already-working 1:1 path from regression.
 *
 * Two implementation gotchas load-bearing enough to repeat here (see
 * firestore.rules' `chats/{chatId}` + `groupInvites/{inviteId}` blocks
 * for the actual rule text):
 *  - Group creation is two separate top-level calls, never one
 *    WriteBatch — a batch's `get()` calls (used by the invite `create`
 *    rule to check `adminUids`) see the pre-batch snapshot, so batching
 *    would make every invite-create fail against a chat doc that
 *    "doesn't exist yet" from the rule's point of view.
 *  - Invite accept is two separate sequential writes (invite doc
 *    `status→'accepted'`, THEN `participants` arrayUnion) for the same
 *    reason — the join rule checks the invite doc's already-committed
 *    status. [reconcileAcceptedInvites] covers a crash between the two.
 */
class GroupChatRepository {
    private val firestore = FirebaseModule.firestore
    private val auth = FirebaseModule.auth
    private val storage = FirebaseModule.storage

    private val currentUid: String?
        get() = auth.currentUser?.uid

    private fun chatsCol() = firestore.collection("chats")
    private fun invitesCol() = firestore.collection("groupInvites")
    private fun inviteRef(chatId: String, uid: String) = invitesCol().document("${chatId}_$uid")

    /** Creates the group (caller becomes owner/admin/sole initial participant), then invites the rest. */
    suspend fun createGroup(name: String, memberUids: List<String>): Result<String> = runCatching {
        val me = currentUid ?: error("Not signed in")
        val trimmedName = name.trim().ifBlank { "New Group" }

        val ref = chatsCol().document()
        ref.set(
            mapOf(
                "isGroup" to true,
                "groupName" to trimmedName,
                "groupPhotoUrl" to "",
                "ownerUid" to me,
                "adminUids" to listOf(me),
                "participants" to listOf(me),
                "lastMessage" to "",
                "lastTime" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()

        inviteMembers(ref.id, trimmedName, memberUids)

        ref.id
    }

    /**
     * Invites (or re-invites) each uid to an existing group. A previously
     * declined/pending invite is deleted then re-created (the invite doc's
     * id is deterministic, so `create` fails on an existing doc — Firestore
     * rules also classify `.set()` on an existing doc as `update`, not
     * `create`, which the invitee-only update rule would reject from the
     * inviter's uid anyway). Already-accepted invitees are silently skipped.
     */
    suspend fun inviteMembers(chatId: String, groupName: String, memberUids: List<String>): Result<Unit> = runCatching {
        val me = currentUid ?: error("Not signed in")
        val myName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Unknown"
        for (uid in memberUids) {
            if (uid == me) continue
            val ref = inviteRef(chatId, uid)
            val existing = ref.get().await()
            if (existing.exists()) {
                if (existing.getString("status") == GroupInviteDoc.STATUS_ACCEPTED) continue
                ref.delete().await()
            }
            ref.set(
                mapOf(
                    "chatId" to chatId,
                    "groupName" to groupName,
                    "invitedUid" to uid,
                    "invitedByUid" to me,
                    "invitedByName" to myName,
                    "status" to GroupInviteDoc.STATUS_PENDING,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Unit
    }

    /** Inviter cancels a still-pending invite. */
    suspend fun cancelInvite(chatId: String, uid: String): Result<Unit> = runCatching {
        inviteRef(chatId, uid).delete().await()
        Unit
    }

    fun observeMyGroupChats(): Flow<List<GroupChatPreview>> = callbackFlow {
        val me = currentUid
        if (me == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = chatsCol()
            .whereArrayContains("participants", me)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val groups = snap?.documents
                    ?.filter { it.getBoolean("isGroup") == true }
                    ?.map { doc ->
                        GroupChatPreview(
                            chatId = doc.id,
                            groupName = doc.getString("groupName")?.takeIf { it.isNotBlank() } ?: "Group",
                            groupPhotoUrl = doc.getString("groupPhotoUrl").orEmpty(),
                            memberCount = (doc.get("participants") as? List<*>)?.size ?: 0,
                            lastMessage = doc.getString("lastMessage").orEmpty(),
                            lastTimeMs = doc.getTimestamp("lastTime")?.toDate()?.time ?: 0L
                        )
                    }
                    ?: emptyList()
                trySend(groups)
            }
        awaitClose { registration.remove() }
    }

    suspend fun fetchGroup(chatId: String): GroupChatDoc? = runCatching {
        chatsCol().document(chatId).get().await().toObject(GroupChatDoc::class.java)
    }.getOrNull()

    fun observeGroup(chatId: String): Flow<GroupChatDoc?> = callbackFlow {
        val registration = chatsCol().document(chatId).addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(null)
                return@addSnapshotListener
            }
            trySend(snap?.takeIf { it.exists() }?.toObject(GroupChatDoc::class.java))
        }
        awaitClose { registration.remove() }
    }

    fun observeGroupMessages(chatId: String): Flow<List<ChatMessageDoc>> = callbackFlow {
        val registration = chatsCol().document(chatId).collection("messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val msgs = snap?.documents?.mapNotNull { doc ->
                    runCatching { doc.toObject(ChatMessageDoc::class.java)?.copy(id = doc.id) }.getOrNull()
                } ?: emptyList()
                trySend(msgs)
            }
        awaitClose { registration.remove() }
    }

    suspend fun sendGroupText(chatId: String, text: String, replyTo: ReplyPreview? = null): Result<Unit> = runCatching {
        val me = currentUid ?: error("Not signed in")
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Message is empty" }
        val chatRef = chatsCol().document(chatId)

        chatRef.set(
            mapOf("lastMessage" to trimmed, "lastTime" to FieldValue.serverTimestamp()),
            SetOptions.merge()
        ).await()

        chatRef.collection("messages").add(
            mapOf(
                "senderId" to me,
                "type" to ChatMessageDoc.TYPE_TEXT,
                "text" to trimmed,
                "replyToId" to replyTo?.messageId,
                "replyToSenderId" to replyTo?.senderId,
                "replyToText" to replyTo?.text,
                "timestamp" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }

    suspend fun sendGroupMedia(chatId: String, uri: Uri, type: String, label: String? = null): Result<Unit> = runCatching {
        val me = currentUid ?: error("Not signed in")
        require(type == ChatMessageDoc.TYPE_IMAGE || type == ChatMessageDoc.TYPE_GIF) {
            "Unsupported media type: $type"
        }
        val ext = if (type == ChatMessageDoc.TYPE_GIF) "gif" else "jpg"
        val storageRef = storage.reference.child("chats/$chatId/${UUID.randomUUID()}.$ext")
        storageRef.putFile(uri).await()
        val downloadUrl = storageRef.downloadUrl.await().toString()
        val previewLabel = if (type == ChatMessageDoc.TYPE_GIF) "[GIF]" else "[Photo]"
        val chatRef = chatsCol().document(chatId)

        chatRef.set(
            mapOf("lastMessage" to previewLabel, "lastTime" to FieldValue.serverTimestamp()),
            SetOptions.merge()
        ).await()

        chatRef.collection("messages").add(
            mapOf(
                "senderId" to me,
                "type" to type,
                "mediaUrl" to downloadUrl,
                "mediaLabel" to label,
                "timestamp" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }

    /** Drops a plain announcement line into the group ("X joined the group", etc). */
    private suspend fun postSystemMessage(chatId: String, text: String) {
        val me = currentUid ?: return
        runCatching {
            chatsCol().document(chatId).collection("messages").add(
                mapOf(
                    "senderId" to me,
                    "type" to ChatMessageDoc.TYPE_SYSTEM,
                    "text" to text,
                    "timestamp" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    // ── Invites (recipient side) ─────────────────────────────────────

    fun observePendingInvites(): Flow<List<PendingInvite>> = callbackFlow {
        val me = currentUid
        if (me == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = invitesCol()
            .whereEqualTo("invitedUid", me)
            .whereEqualTo("status", GroupInviteDoc.STATUS_PENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val invites = snap?.documents?.map { doc ->
                    PendingInvite(
                        inviteId = doc.id,
                        chatId = doc.getString("chatId").orEmpty(),
                        groupName = doc.getString("groupName")?.takeIf { it.isNotBlank() } ?: "Group",
                        invitedByName = doc.getString("invitedByName")?.takeIf { it.isNotBlank() } ?: "Someone"
                    )
                } ?: emptyList()
                trySend(invites)
            }
        awaitClose { registration.remove() }
    }

    suspend fun acceptInvite(invite: PendingInvite): Result<Unit> = runCatching {
        val me = currentUid ?: error("Not signed in")
        invitesCol().document(invite.inviteId).update(
            mapOf(
                "status" to GroupInviteDoc.STATUS_ACCEPTED,
                "respondedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        chatsCol().document(invite.chatId).update(
            "participants", FieldValue.arrayUnion(me)
        ).await()
        postSystemMessage(invite.chatId, "${auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Someone"} joined the group")
        Unit
    }

    suspend fun declineInvite(invite: PendingInvite): Result<Unit> = runCatching {
        invitesCol().document(invite.inviteId).update(
            mapOf(
                "status" to GroupInviteDoc.STATUS_DECLINED,
                "respondedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }

    /**
     * Covers a crash between [acceptInvite]'s two writes: any of my own
     * invites already marked accepted whose chat doc doesn't yet list me
     * as a participant gets the join write retried. Cheap (one query),
     * safe to call on every chat-list load.
     */
    suspend fun reconcileAcceptedInvites() {
        val me = currentUid ?: return
        runCatching {
            val accepted = invitesCol()
                .whereEqualTo("invitedUid", me)
                .whereEqualTo("status", GroupInviteDoc.STATUS_ACCEPTED)
                .get().await()
            for (doc in accepted.documents) {
                val chatId = doc.getString("chatId") ?: continue
                val chatSnap = chatsCol().document(chatId).get().await()
                val participants = (chatSnap.get("participants") as? List<*>).orEmpty()
                if (me !in participants) {
                    chatsCol().document(chatId).update("participants", FieldValue.arrayUnion(me)).await()
                }
            }
        }
    }

    /**
     * Pending invites for this group that *I* sent — scoped to
     * `invitedByUid == me` so the query is guaranteed to satisfy
     * groupInvites' per-doc read rule (an admin can only ever read an
     * invite doc where they're the inviter or invitee). A co-admin's own
     * invites aren't visible here — an accepted simplification, same
     * pragmatic rigor level as the rest of this feature.
     */
    fun observeMyInvitesFor(chatId: String): Flow<List<PendingInvite>> = callbackFlow {
        val me = currentUid
        if (me == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = invitesCol()
            .whereEqualTo("chatId", chatId)
            .whereEqualTo("invitedByUid", me)
            .whereEqualTo("status", GroupInviteDoc.STATUS_PENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val invites = snap?.documents?.map { doc ->
                    PendingInvite(
                        inviteId = doc.id,
                        chatId = doc.getString("chatId").orEmpty(),
                        groupName = doc.getString("groupName").orEmpty(),
                        invitedByName = doc.getString("invitedByName").orEmpty(),
                        invitedUid = doc.getString("invitedUid").orEmpty()
                    )
                } ?: emptyList()
                trySend(invites)
            }
        awaitClose { registration.remove() }
    }

    // ── Admin toolkit ─────────────────────────────────────────────────

    suspend fun renameGroup(chatId: String, newName: String): Result<Unit> = runCatching {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "Name is empty" }
        chatsCol().document(chatId).update("groupName", trimmed).await()
        postSystemMessage(chatId, "Group renamed to \"$trimmed\"")
        Unit
    }

    suspend fun setGroupPhoto(chatId: String, uri: Uri): Result<Unit> = runCatching {
        val storageRef = storage.reference.child("chats/$chatId/group_photo_${UUID.randomUUID()}.jpg")
        storageRef.putFile(uri).await()
        val downloadUrl = storageRef.downloadUrl.await().toString()
        chatsCol().document(chatId).update("groupPhotoUrl", downloadUrl).await()
        Unit
    }

    suspend fun removeMember(chatId: String, memberUid: String, memberName: String): Result<Unit> = runCatching {
        chatsCol().document(chatId).update("participants", FieldValue.arrayRemove(memberUid)).await()
        postSystemMessage(chatId, "$memberName was removed from the group")
        Unit
    }

    suspend fun leaveGroup(chatId: String): Result<Unit> = runCatching {
        val me = currentUid ?: error("Not signed in")
        val myName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Someone"
        // Post the announcement BEFORE removing myself — the message
        // create rule requires being a current participant, which is no
        // longer true the instant the arrayRemove below lands.
        postSystemMessage(chatId, "$myName left the group")
        chatsCol().document(chatId).update("participants", FieldValue.arrayRemove(me)).await()
        Unit
    }
}
