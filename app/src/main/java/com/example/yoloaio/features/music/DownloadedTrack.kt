package com.example.yoloaio.features.music

/**
 * A track saved to app-private storage for offline playback. Mirrors
 * [FavoriteTrack]'s shape plus the on-disk location — see
 * [DownloadedTracksStore] for where these live and [TrackDownloader] for
 * how they're created.
 */
data class DownloadedTrack(
    val id: String = "",
    val trackId: String = "",
    val title: String = "",
    val artist: String = "",
    val durationSec: Int = 0,
    val artworkUrlSmall: String? = null,
    val artworkUrlLarge: String? = null,
    val language: String = "",
    val year: String = "",
    val localFilePath: String = "",
    val fileSizeBytes: Long = 0L,
    val downloadedAt: Long = 0L
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
        // Local playback goes through MusicPlayer's downloaded-file check,
        // not this URL — kept here only so SaavnTrack's shape is satisfied
        // if something falls back to streaming (e.g. the file got deleted
        // out from under the app).
        streamUrl = ""
    )

    companion object {
        fun fromTrack(track: SaavnTrack, localFilePath: String, fileSizeBytes: Long): DownloadedTrack =
            DownloadedTrack(
                id = track.id,
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                durationSec = track.durationSec,
                artworkUrlSmall = track.artworkUrlSmall,
                artworkUrlLarge = track.artworkUrlLarge,
                language = track.language,
                year = track.year,
                localFilePath = localFilePath,
                fileSizeBytes = fileSizeBytes,
                downloadedAt = System.currentTimeMillis()
            )
    }
}
