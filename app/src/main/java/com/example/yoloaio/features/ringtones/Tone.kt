package com.example.yoloaio.features.ringtones

import com.example.yoloaio.features.music.SaavnTrack

enum class ToneSource(val key: String) {
    Freesound("freesound"),
    Saavn("saavn"),
    Trimmed("trimmed");

    companion object {
        fun fromKey(key: String): ToneSource = when (key) {
            Saavn.key -> Saavn
            Trimmed.key -> Trimmed
            else -> Freesound
        }
    }
}

/**
 * Source-agnostic ringtone-row model. Freesound previews are mp3, JioSaavn tracks
 * are AAC-in-MP4 (.m4a) — both supported by MediaStore + RingtoneManager.
 *
 * `id` is namespaced with the source key so the same numeric id from two sources
 * doesn't collide in lists or favorites.
 */
data class Tone(
    val id: String,
    val name: String,
    val subtitle: String,
    val durationSec: Double,
    val streamUrl: String,
    val tags: List<String>,
    val source: ToneSource,
    val mimeType: String,
    val fileExtension: String,
    val artUrl: String? = null
) {
    val durationFormatted: String
        get() {
            val s = durationSec.toInt()
            return if (s < 60) "${s}s" else "%d:%02d".format(s / 60, s % 60)
        }
}

fun FreesoundTone.toTone(): Tone = Tone(
    id = "${ToneSource.Freesound.key}-$id",
    name = name,
    subtitle = username,
    durationSec = durationSec,
    streamUrl = previewUrl,
    tags = tags,
    source = ToneSource.Freesound,
    mimeType = "audio/mpeg",
    fileExtension = "mp3"
)

fun SaavnTrack.toTone(): Tone = Tone(
    id = "${ToneSource.Saavn.key}-$id",
    name = title,
    subtitle = artist,
    durationSec = durationSec.toDouble(),
    streamUrl = streamUrl,
    tags = listOfNotNull(
        language.takeIf { it.isNotBlank() },
        year.takeIf { it.isNotBlank() }
    ),
    source = ToneSource.Saavn,
    mimeType = "audio/mp4",
    fileExtension = "m4a",
    artUrl = artworkUrlSmall
)
