package com.example.yoloaio.features.quotes

import com.example.yoloaio.data.FirebaseModule
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await

private data class QuoteDoc(
    val text: String = "",
    val author: String = "",
    val textColor: Long = 0xFFFFFFFFL,
    val fontSize: Int = 28,
    val bold: Boolean = false,
    val italic: Boolean = true,
    val alignment: String = QuoteStyle.ALIGN_CENTER,
    val backgroundType: String = QuoteStyle.BG_GRADIENT,
    val backgroundColors: List<Long> = listOf(0xFF1A237EL, 0xFF4A148CL),
    val backgroundImageUrl: String? = null,
    val createdAt: Long = 0L,
    // Firestore deserializes missing fields with these defaults — keeps older
    // private docs (which never had visibility set) reading as "private".
    val visibility: String = Quote.VISIBILITY_PRIVATE,
    val ownerUid: String = "",
    val ownerName: String = ""
) {
    fun toQuote(id: String): Quote = Quote(
        id = id,
        text = text,
        author = author,
        style = QuoteStyle(
            textColor = textColor,
            fontSize = fontSize,
            bold = bold,
            italic = italic,
            alignment = alignment,
            backgroundType = backgroundType,
            backgroundColors = backgroundColors,
            backgroundImageUrl = backgroundImageUrl
        ),
        isCustom = true,
        createdAt = createdAt,
        visibility = visibility,
        ownerUid = ownerUid,
        ownerName = ownerName
    )
}

/**
 * Two storage paths:
 *   - `users/{uid}/customQuotes` — private quotes, only the owner reads/writes.
 *   - `publicQuotes` (top-level) — public quotes, any signed-in user reads,
 *     only the owner writes/deletes. Has denormalised `ownerUid` + `ownerName`
 *     so the Community list can render without a second fetch.
 *
 * The split path keeps the privacy boundary at the Firestore-rules layer
 * rather than relying on app-side filtering — a private quote literally
 * cannot be queried by another user, by design.
 */
class QuoteRepository {
    private val firestore = FirebaseModule.firestore
    private val auth = FirebaseModule.auth

    private fun privateCol(uid: String) =
        firestore.collection("users").document(uid)
            .collection("customQuotes")

    private val publicCol = firestore.collection("publicQuotes")

    /** All of the current user's quotes (private + public-they-authored). */
    fun observeMyQuotes(): Flow<List<Quote>> {
        val uid = auth.currentUser?.uid ?: return emptyFlow()
        return combine(
            observePrivateOwned(uid),
            observePublicOwned(uid)
        ) { priv, pub ->
            (priv + pub).sortedByDescending { it.createdAt }
        }
    }

    /** Public quotes authored by everyone else (community feed). */
    fun observeCommunityQuotes(): Flow<List<Quote>> = callbackFlow {
        val myUid = auth.currentUser?.uid
        val registration = publicCol
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.w("QuoteRepository", "community listen error", err)
                    return@addSnapshotListener
                }
                val quotes = snap?.documents
                    ?.mapNotNull { doc -> doc.toObject(QuoteDoc::class.java)?.toQuote(doc.id) }
                    ?.filter { it.ownerUid != myUid }
                    ?: emptyList()
                trySend(quotes)
            }
        awaitClose { registration.remove() }
    }

    private fun observePrivateOwned(uid: String): Flow<List<Quote>> = callbackFlow {
        val registration = privateCol(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    // Don't emit on transient errors — the UI keeps the
                    // previous list. Emitting emptyList here caused a flicker
                    // loop as Firestore auto-retried.
                    android.util.Log.w("QuoteRepository", "private listen error", err)
                    return@addSnapshotListener
                }
                val quotes = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(QuoteDoc::class.java)
                        ?.toQuote(doc.id)
                        ?.copy(visibility = Quote.VISIBILITY_PRIVATE, ownerUid = uid)
                } ?: emptyList()
                trySend(quotes)
            }
        awaitClose { registration.remove() }
    }

    /**
     * No orderBy in the Firestore query: that would force a composite
     * index (ownerUid + createdAt). Since [observeMyQuotes] re-sorts the
     * combined private+public list client-side anyway, the orderBy here
     * was pure cost — and the missing index made the query fail in a
     * retry loop that flickered the UI on every failure.
     */
    private fun observePublicOwned(uid: String): Flow<List<Quote>> = callbackFlow {
        val registration = publicCol
            .whereEqualTo("ownerUid", uid)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.w("QuoteRepository", "public-owned listen error", err)
                    return@addSnapshotListener
                }
                val quotes = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(QuoteDoc::class.java)?.toQuote(doc.id)
                } ?: emptyList()
                trySend(quotes)
            }
        awaitClose { registration.remove() }
    }

    suspend fun saveQuote(
        text: String,
        author: String,
        style: QuoteStyle,
        visibility: String = Quote.VISIBILITY_PRIVATE
    ): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in")
        val uid = user.uid
        val cleanText = text.trim()
        val cleanAuthor = author.trim()
        require(cleanText.isNotEmpty()) { "Quote can't be empty" }
        require(
            visibility == Quote.VISIBILITY_PRIVATE || visibility == Quote.VISIBILITY_PUBLIC
        ) { "Invalid visibility: $visibility" }

        val ownerName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore('@')
            ?: "Anonymous"

        val data = mutableMapOf<String, Any?>(
            "text" to cleanText,
            "author" to cleanAuthor,
            "textColor" to style.textColor,
            "fontSize" to style.fontSize,
            "bold" to style.bold,
            "italic" to style.italic,
            "alignment" to style.alignment,
            "backgroundType" to style.backgroundType,
            "backgroundColors" to style.backgroundColors,
            "backgroundImageUrl" to style.backgroundImageUrl,
            "createdAt" to System.currentTimeMillis(),
            "visibility" to visibility
        )

        if (visibility == Quote.VISIBILITY_PUBLIC) {
            // Public quotes need owner attribution so the Community feed can
            // render "by <ownerName>" without a join.
            data["ownerUid"] = uid
            data["ownerName"] = ownerName
            publicCol.add(data).await()
        } else {
            privateCol(uid).add(data).await()
        }
        Unit
    }

    suspend fun deleteQuote(quote: Quote): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        when {
            quote.isPublic -> {
                require(quote.ownerUid == uid) { "You can only delete your own quotes" }
                publicCol.document(quote.id).delete().await()
            }
            else -> privateCol(uid).document(quote.id).delete().await()
        }
        Unit
    }
}

private fun <T> emptyFlow(): Flow<List<T>> = callbackFlow {
    trySend(emptyList())
    awaitClose { }
}
