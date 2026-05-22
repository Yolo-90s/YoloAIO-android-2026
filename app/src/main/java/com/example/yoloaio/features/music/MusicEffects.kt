package com.example.yoloaio.features.music

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log

/**
 * Equalizer presets. We don't expose the OS's built-in
 * [Equalizer.getNumberOfPresets] list directly because vendors expose
 * wildly different sets — instead we ship a fixed curated table and
 * apply it as a per-band gain so the listener sees the same options on
 * every device.
 *
 * Values are gains in **millibels** per band (1 mB = 0.01 dB). The
 * default 5-band layout from Android's Equalizer covers roughly
 *   band 0 → 60 Hz   (bass)
 *   band 1 → 230 Hz  (low mid)
 *   band 2 → 910 Hz  (mid)
 *   band 3 → 3.6 kHz (high mid)
 *   band 4 → 14 kHz  (treble)
 */
enum class EqPreset(val label: String, val gainsMb: ShortArray) {
    Flat(      "Flat",       shortArrayOf( 0,   0,    0,    0,    0 )),
    Pop(       "Pop",        shortArrayOf( 200, 100, -100, -200,  100)),
    Rock(      "Rock",       shortArrayOf( 400, 200, -100,  100,  400)),
    Classical( "Classical",  shortArrayOf( 300, 150,  0,    150,  300)),
    Dance(     "Dance",      shortArrayOf( 500, 300,  0,    200,  400)),
    HipHop(    "Hip-Hop",    shortArrayOf( 600, 300,  0,    100,  300)),
    Jazz(      "Jazz",       shortArrayOf( 300, 200,  0,    200,  300)),
    Vocal(     "Vocal",      shortArrayOf( -200, -100, 200, 400,  100)),
    BassPunch( "Bass punch", shortArrayOf( 700, 400, -100, -100,  100));

    companion object {
        val Default: EqPreset = Flat
    }
}

/**
 * Audio post-processing chain attached to a MediaPlayer's audio
 * session. Equalizer + BassBoost + Virtualizer + LoudnessEnhancer all
 * pull from the same session id, so the listener hears the combined
 * effect on whatever the MusicPlayer is playing.
 *
 * Each effect is wrapped in a try/catch because some OEMs (Vivo, MIUI,
 * a few Oppo builds) restrict third-party AudioEffect access entirely
 * or throw on specific effects. We fail open — the user just won't
 * hear that particular enhancement.
 *
 * Lifecycle: created with a non-zero session id, used while the
 * MusicPlayer lives, [release]d when MusicPlayer is released.
 */
class MusicEffects(private val sessionId: Int) {

    private val tag = "MusicEffects"

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null

    /**
     * Attach effects. Safe to call repeatedly — already-attached
     * effects are left as-is.
     */
    fun attach() {
        if (sessionId == 0) {
            Log.w(tag, "Session id is 0, can't attach effects")
            return
        }
        if (equalizer == null) {
            runCatching {
                equalizer = Equalizer(0, sessionId).apply { enabled = true }
            }.onFailure { Log.w(tag, "Equalizer init failed: ${it.message}") }
        }
        if (bassBoost == null) {
            runCatching {
                bassBoost = BassBoost(0, sessionId).apply { enabled = true }
            }.onFailure { Log.w(tag, "BassBoost init failed: ${it.message}") }
        }
        if (virtualizer == null) {
            runCatching {
                virtualizer = Virtualizer(0, sessionId).apply { enabled = true }
            }.onFailure { Log.w(tag, "Virtualizer init failed: ${it.message}") }
        }
        if (loudness == null) {
            runCatching {
                loudness = LoudnessEnhancer(sessionId).apply { enabled = true }
            }.onFailure { Log.w(tag, "LoudnessEnhancer init failed: ${it.message}") }
        }
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
        runCatching { bassBoost?.enabled = enabled }
        runCatching { virtualizer?.enabled = enabled }
        runCatching { loudness?.enabled = enabled }
    }

    /**
     * Apply an [EqPreset]'s per-band gains to the underlying OS
     * equalizer. Bands beyond what the device exposes are skipped.
     */
    fun setPreset(preset: EqPreset) {
        val eq = equalizer ?: return
        runCatching {
            val bands = eq.numberOfBands.toInt()
            for (i in 0 until minOf(bands, preset.gainsMb.size)) {
                eq.setBandLevel(i.toShort(), preset.gainsMb[i])
            }
        }.onFailure { Log.w(tag, "setPreset failed: ${it.message}") }
    }

    /** Bass boost strength, 0-1000. 0 = off, 1000 = max (typically +6 dB at low end). */
    fun setBassStrength(strength: Int) {
        val bb = bassBoost ?: return
        runCatching {
            bb.setStrength(strength.coerceIn(0, 1000).toShort())
        }
    }

    /** Stereo virtualizer strength, 0-1000. Adds depth/width on headphones. */
    fun setVirtualizerStrength(strength: Int) {
        val v = virtualizer ?: return
        runCatching {
            v.setStrength(strength.coerceIn(0, 1000).toShort())
        }
    }

    /** Loudness gain in **millibels** (0..2000 = 0 to +20 dB). Use sparingly — too
     *  much will clip and distort. */
    fun setLoudnessGainMb(gainMb: Int) {
        val le = loudness ?: return
        runCatching {
            le.setTargetGain(gainMb.coerceIn(0, 2000))
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudness = null
    }
}
