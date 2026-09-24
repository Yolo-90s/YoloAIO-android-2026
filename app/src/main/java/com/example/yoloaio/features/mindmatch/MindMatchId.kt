package com.example.yoloaio.features.mindmatch

import kotlin.random.Random

/**
 * Short, easy-to-read-aloud pairing codes for a MindMatch session. Same
 * shape as [com.example.yoloaio.features.walkietalkie.WalkieChannelId] —
 * kept as its own object rather than shared so the two features stay
 * decoupled. Charset excludes 0/O and 1/I/L so two people trading a code
 * over voice or text don't hit ambiguous characters.
 */
object MindMatchId {
    private const val CHARSET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    private const val LENGTH = 6

    fun generate(): String = buildString {
        repeat(LENGTH) { append(CHARSET[Random.nextInt(CHARSET.length)]) }
    }

    /** Loose validation for what the user types into the "enter a code" field. */
    fun normalize(input: String): String =
        input.trim().uppercase().filter { it in CHARSET }

    fun format(code: String): String = code.chunked(3).joinToString(" ")
}
