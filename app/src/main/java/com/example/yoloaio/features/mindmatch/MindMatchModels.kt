package com.example.yoloaio.features.mindmatch

import com.google.firebase.Timestamp

data class MindMatchQuestion(
    val text: String,
    val options: List<String>
)

val MIND_MATCH_QUESTIONS = listOf(
    MindMatchQuestion(
        "Friday night, no plans — what's your instinct?",
        listOf(
            "Cozy night in with a movie",
            "Spontaneous outing with friends",
            "Deep-dive into a hobby project",
            "Early night, save energy for tomorrow"
        )
    ),
    MindMatchQuestion(
        "Which snack disappears first in your house?",
        listOf(
            "Chips and salsa",
            "Chocolate, always chocolate",
            "Fresh fruit",
            "Something spicy"
        )
    ),
    MindMatchQuestion(
        "Pick a superpower you'd actually use.",
        listOf(
            "Teleportation",
            "Reading minds",
            "Time travel",
            "Talking to animals"
        )
    ),
    MindMatchQuestion(
        "Your ideal vacation is...",
        listOf(
            "Beach, sun, and doing absolutely nothing",
            "Backpacking somewhere new",
            "A city full of museums and food",
            "A cabin in the mountains, off the grid"
        )
    ),
    MindMatchQuestion(
        "How do you actually make big decisions?",
        listOf(
            "Gut feeling — first instinct wins",
            "Pros-and-cons list",
            "Ask everyone I trust for opinions",
            "Sleep on it for a few days"
        )
    )
)

/** Firestore doc shape for `mindMatchSessions/{code}`. See MindMatchRepository's header comment for the schema. */
data class MindMatchSessionDoc(
    val hostUid: String = "",
    val hostDisplayName: String = "",
    val guestUid: String = "",
    val guestDisplayName: String = "",
    val hostAnswers: List<Long> = emptyList(),
    val guestAnswers: List<Long> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

enum class MindMatchRole { HOST, GUEST }

/** `(# matching answers / 5) * 100` — only possible values: 0/20/40/60/80/100. */
fun computeMatchPercent(hostAnswers: List<Long>, guestAnswers: List<Long>): Int {
    if (hostAnswers.size != MIND_MATCH_QUESTIONS.size || guestAnswers.size != MIND_MATCH_QUESTIONS.size) return 0
    val matches = hostAnswers.indices.count { hostAnswers[it] == guestAnswers[it] }
    return (matches * 100) / MIND_MATCH_QUESTIONS.size
}

/** Headline label + one-line flavor text for a computed match percentage. */
fun matchLabel(percent: Int): Pair<String, String> = when {
    percent >= 100 -> "Mind Twins" to "You two think identically. Spooky."
    percent >= 80 -> "Great Match" to "Seriously in sync."
    percent >= 60 -> "Good Vibes" to "More alike than not."
    percent >= 40 -> "Some Overlap" to "A few things in common — plenty to debate."
    else -> "Total Opposites" to "Practically no overlap — somehow it works?"
}
