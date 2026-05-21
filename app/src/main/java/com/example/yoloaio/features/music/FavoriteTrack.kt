package com.example.yoloaio.features.music

/**
 * Firestore-friendly favourite track. Defaults on every field so older docs
 * missing newer columns still deserialise cleanly.
 */
data class FavoriteTrack(
    val id: String = "",
    val trackId: String = "",
    val title: String = "",
    val artist: String = "",
    val durationSec: Int = 0,
    val artworkUrlSmall: String? = null,
    val artworkUrlLarge: String? = null,
    val language: String = "",
    val year: String = "",
    val streamUrl: String = "",
    val addedAt: Long = 0L
) {
    fun toTrack(): SaavnTrack = SaavnTrack(
        id = trackId,
        title = title,
        artist = artist,
        durationSec = durationSec,
        artworkUrlSmall = artworkUrlSmall,
        artworkUrlLarge = artworkUrlLarge,
        language = language,
        year = year,
        streamUrl = streamUrl
    )

    companion object {
        fun fromTrack(track: SaavnTrack): FavoriteTrack = FavoriteTrack(
            id = track.id,
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            durationSec = track.durationSec,
            artworkUrlSmall = track.artworkUrlSmall,
            artworkUrlLarge = track.artworkUrlLarge,
            language = track.language,
            year = track.year,
            streamUrl = track.streamUrl,
            addedAt = System.currentTimeMillis()
        )
    }
}
