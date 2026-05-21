package com.example.yoloaio.features.ringtones

/**
 * Firestore-friendly favorite entry. All fields default so older docs missing
 * newer fields (source, mimeType, fileExtension, artUrl) still deserialize —
 * we fall back to Freesound defaults in that case.
 */
data class RingtoneFavorite(
    val id: String = "",
    val toneId: String = "",
    val name: String = "",
    val subtitle: String = "",          // artist (Saavn) or contributor username (Freesound)
    val durationSec: Double = 0.0,
    val tags: List<String> = emptyList(),
    val streamUrl: String = "",
    val source: String = ToneSource.Freesound.key,
    val mimeType: String = "audio/mpeg",
    val fileExtension: String = "mp3",
    val artUrl: String? = null,
    val addedAt: Long = 0L
) {
    fun toTone(): Tone = Tone(
        id = toneId,
        name = name,
        subtitle = subtitle,
        durationSec = durationSec,
        streamUrl = streamUrl,
        tags = tags,
        source = ToneSource.fromKey(source),
        mimeType = mimeType,
        fileExtension = fileExtension,
        artUrl = artUrl
    )

    companion object {
        fun fromTone(tone: Tone): RingtoneFavorite = RingtoneFavorite(
            id = tone.id,
            toneId = tone.id,
            name = tone.name,
            subtitle = tone.subtitle,
            durationSec = tone.durationSec,
            tags = tone.tags,
            streamUrl = tone.streamUrl,
            source = tone.source.key,
            mimeType = tone.mimeType,
            fileExtension = tone.fileExtension,
            artUrl = tone.artUrl,
            addedAt = System.currentTimeMillis()
        )
    }
}
