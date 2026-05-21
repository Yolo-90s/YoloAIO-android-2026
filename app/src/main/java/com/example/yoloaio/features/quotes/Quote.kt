package com.example.yoloaio.features.quotes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

enum class BackgroundType { Gradient, Solid, Image }

/**
 * Firestore-friendly: every field has a default so the auto-mapper can deserialize
 * docs even when older quotes are missing newer fields. Color/long values are stored
 * as ARGB packed in Long.
 */
data class QuoteStyle(
    val textColor: Long = 0xFFFFFFFFL,
    val fontSize: Int = 28,
    val bold: Boolean = false,
    val italic: Boolean = true,
    val alignment: String = ALIGN_CENTER,
    val backgroundType: String = BG_GRADIENT,
    val backgroundColors: List<Long> = listOf(0xFF1A237EL, 0xFF4A148CL),
    val backgroundImageUrl: String? = null
) {
    val textComposeColor: Color get() = Color(textColor.toInt())
    val backgroundComposeColors: List<Color>
        get() = backgroundColors.map { Color(it.toInt()) }

    val textFontWeight: FontWeight
        get() = if (bold) FontWeight.SemiBold else FontWeight.Normal
    val textFontStyle: FontStyle
        get() = if (italic) FontStyle.Italic else FontStyle.Normal
    val textAlign: TextAlign
        get() = when (alignment) {
            ALIGN_START -> TextAlign.Start
            ALIGN_END -> TextAlign.End
            else -> TextAlign.Center
        }
    val bgType: BackgroundType
        get() = when (backgroundType) {
            BG_SOLID -> BackgroundType.Solid
            BG_IMAGE -> BackgroundType.Image
            else -> BackgroundType.Gradient
        }

    companion object {
        const val ALIGN_START = "start"
        const val ALIGN_CENTER = "center"
        const val ALIGN_END = "end"
        const val BG_GRADIENT = "gradient"
        const val BG_SOLID = "solid"
        const val BG_IMAGE = "image"

        val Default = QuoteStyle()
    }
}

data class Quote(
    val id: String = "",
    val text: String = "",
    val author: String = "",
    val style: QuoteStyle = QuoteStyle.Default,
    val isCustom: Boolean = false,
    val createdAt: Long = 0L,
    // Privacy: "private" = only the owner sees it; "public" = visible to all
    // signed-in users via the top-level publicQuotes collection.
    val visibility: String = VISIBILITY_PRIVATE,
    // Denormalised owner info — only populated for public quotes so that the
    // "by <author>" badge on Community cards doesn't require a second fetch.
    val ownerUid: String = "",
    val ownerName: String = ""
) {
    val isPublic: Boolean get() = visibility == VISIBILITY_PUBLIC
    val isPrivate: Boolean get() = visibility == VISIBILITY_PRIVATE

    companion object {
        const val VISIBILITY_PRIVATE = "private"
        const val VISIBILITY_PUBLIC = "public"
    }
}
