package com.example.yoloaio.features.audio

import com.example.yoloaio.features.ringtones.Tone
import com.example.yoloaio.features.ringtones.ToneSource

/**
 * Locally-stored trimmed tone. Defaults on every field so a corrupt or older
 * JSON entry still deserializes cleanly.
 *
 * `streamUrl` holds the MediaStore content:// URI of the trimmed audio file —
 * the same value MediaPlayer, MediaExtractor, and RingtoneInstaller all open
 * through ContentResolver.
 */
data class TrimmedTone(
    val id: String = "",
    val name: String = "",
    val sourceName: String = "",
    val durationSec: Double = 0.0,
    val streamUrl: String = "",
    val artUrl: String? = null,
    val mimeType: String = "audio/mp4",
    val fileExtension: String = "m4a",
    val createdAt: Long = 0L
) {
    fun toTone(): Tone = Tone(
        id = "${ToneSource.Trimmed.key}-$id",
        name = name,
        subtitle = if (sourceName.isNotBlank()) "Trimmed · $sourceName" else "Trimmed clip",
        durationSec = durationSec,
        streamUrl = streamUrl,
        tags = emptyList(),
        source = ToneSource.Trimmed,
        mimeType = mimeType,
        fileExtension = fileExtension,
        artUrl = artUrl
    )
}
