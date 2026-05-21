package com.example.yoloaio.features.audio

import android.net.Uri
import com.example.yoloaio.features.music.SaavnTrack

/**
 * What the user picked to trim from. Either a content/file URI from the device,
 * or a JioSaavn track whose stream we'll download before trimming.
 */
sealed interface TrimSource {
    val displayName: String
    val sourceLabel: String
    val artUrl: String?
    val knownDurationSec: Double

    data class Local(
        val uri: Uri,
        val fileName: String,
        override val knownDurationSec: Double
    ) : TrimSource {
        override val displayName: String get() = fileName
        override val sourceLabel: String get() = "Device file"
        override val artUrl: String? get() = null
    }

    data class Saavn(val track: SaavnTrack) : TrimSource {
        override val displayName: String get() = track.title
        override val sourceLabel: String get() = "Songs · ${track.artist}"
        override val artUrl: String? get() = track.artworkUrlLarge
        override val knownDurationSec: Double get() = track.durationSec.toDouble()
    }
}
