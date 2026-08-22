package com.example.yoloaio.features.walkietalkie

import kotlin.random.Random

/**
 * Short, easy-to-read-aloud codes for a WalkieTalkie channel. Charset
 * excludes 0/O and 1/I/L so two people trading a code over voice or text
 * don't hit ambiguous characters.
 */
object WalkieChannelId {
    private const val CHARSET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    private const val LENGTH = 6

    fun generate(): String = buildString {
        repeat(LENGTH) { append(CHARSET[Random.nextInt(CHARSET.length)]) }
    }

    /** Loose validation for what the user types into the "peer's code" field. */
    fun normalize(input: String): String =
        input.trim().uppercase().filter { it in CHARSET }
}
