package com.example.yoloaio.features.music

import android.media.audiofx.Visualizer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Per-band amplitude snapshot (0..1 each). Lets layered visualisers
 * react to specific frequency ranges without re-scanning the FFT.
 */
data class BandEnergies(
    val sub: Float = 0f,
    val kick: Float = 0f,
    val bass: Float = 0f,
    val mids: Float = 0f,
    val highs: Float = 0f
) {
    companion object {
        val EMPTY = BandEnergies()
    }
}

/**
 * Real-time FFT capture from a MediaPlayer's audio session, with
 * spectral-flux onset detection on the drum band.
 *
 * Wraps [android.media.audiofx.Visualizer], which is the only public Android
 * API for reading the playing audio stream. The Visualizer fires a callback
 * ~20 times/sec with raw FFT bytes; on each callback we:
 *
 *   1. compute per-bin magnitudes from real+imag pairs
 *   2. compute drum-band spectral flux (sum of positive bin-to-bin
 *      changes in the kick/bass region) — the cleanest "is this a beat?"
 *      signal we can get without a tempo tracker
 *   3. compare flux against an adaptive threshold (1.6 × rolling avg) so
 *      loud songs need bigger transients to trigger and quiet songs still
 *      register on soft kicks
 *   4. spring-damp the resulting pulse (fast attack, slow release) so
 *      each kick produces a single cinematic flash instead of strobing
 *   5. asymmetric peak-hold smooth the bands themselves so individual
 *      bars rise fast on transients and fall back smoothly
 *
 * Requires `RECORD_AUDIO` permission on most Android versions. If the user
 * denies the permission, or the OEM has blocked Visualizer entirely (some
 * MIUI / OxygenOS / FunTouch builds do), [start] returns false and
 * [bandMagnitudes] stays null — callers should treat null as "fall back to
 * synthetic animation".
 *
 * `bandMagnitudes` format: index 0 is the DC component (skip it); higher
 * indices map to higher frequencies. Each value is roughly 0..1 but
 * transient peaks can spike above 1, so the visualiser should clamp.
 */
class BeatAnalyzer {

    private val tag = "BeatAnalyzer"

    private var visualizer: Visualizer? = null

    // Previous frame's raw (un-smoothed) magnitudes — needed for spectral
    // flux. Smoothing the bars before computing flux would bury onsets.
    private var previousRaw: FloatArray? = null

    // Spectral-flux rolling window. Visualizer fires ~20 Hz, so 20 entries
    // ≈ 1s of history — long enough to absorb tempo, short enough to
    // adapt to a song's dynamic range.
    private val fluxHistory = FloatArray(20)
    private var fluxHistoryIdx = 0
    private var pulseTarget = 0f
    private var lastPulseMs = 0L

    var bandMagnitudes by mutableStateOf<FloatArray?>(null)
        private set

    /** Drum-onset pulse (0..1). Springs back to 0 ~250 ms after each kick. */
    var pulse by mutableFloatStateOf(0f)
        private set

    /** Mean energy per named band (0..1 each). */
    var bandEnergies by mutableStateOf(BandEnergies.EMPTY)
        private set

    /**
     * Tries to attach to [audioSessionId]. Returns true on success. Safe to
     * call multiple times — already-started analysers are reused.
     */
    fun start(audioSessionId: Int): Boolean {
        if (visualizer != null) return true
        if (audioSessionId <= 0) return false
        try {
            val range = Visualizer.getCaptureSizeRange()
            // Bigger captureSize = finer frequency resolution. 1024 is the
            // hardware ceiling on most devices and gives ~21.5 Hz per bin at
            // 44.1 kHz, doubling the low-end precision vs the old 512.
            val captureSizeChoice = range[1].coerceAtMost(1024)

            val viz = Visualizer(audioSessionId).apply {
                captureSize = captureSizeChoice
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) = Unit

                        override fun onFftDataCapture(
                            v: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (fft == null || fft.size < 4) return
                            updateMagnitudes(fft)
                        }
                    },
                    Visualizer.getMaxCaptureRate(),
                    /* waveform = */ false,
                    /* fft = */ true
                )
                enabled = true
            }
            visualizer = viz
            return true
        } catch (e: Exception) {
            Log.w(tag, "couldn't attach Visualizer (perm denied / OEM-blocked?): ${e.message}")
            visualizer = null
            return false
        }
    }

    fun stop() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {
        }
        visualizer = null
        bandMagnitudes = null
        previousRaw = null
        pulse = 0f
        pulseTarget = 0f
        bandEnergies = BandEnergies.EMPTY
        for (i in fluxHistory.indices) fluxHistory[i] = 0f
        fluxHistoryIdx = 0
    }

    private fun updateMagnitudes(fft: ByteArray) {
        // Android packs the FFT as:
        //   fft[0] = DC (real)
        //   fft[1] = Nyquist (real)
        //   fft[2k]   = real(band k)
        //   fft[2k+1] = imag(band k)
        // for k in 1..n/2 - 1.
        val binCount = fft.size / 2
        val raw = FloatArray(binCount)

        raw[0] = (fft[0].toInt().toFloat()).let { if (it < 0) -it else it } / 128f
        for (i in 1 until binCount) {
            val real = fft[i * 2].toInt().toFloat()
            val imag = fft[i * 2 + 1].toInt().toFloat()
            // Magnitude. Divide by 128 to roughly land in 0..1; loud bass hits
            // can still spike higher and the visualiser will clamp.
            raw[i] = sqrt(real * real + imag * imag) / 128f
        }

        // Spectral flux on the drum band — sum of positive bin changes
        // since last frame. Drum hits broadly raise low-end energy at
        // once, producing a sharp positive spike that loudness alone
        // can't distinguish from a sustained bassline.
        val flux = computeDrumFlux(raw, previousRaw, binCount)
        // Stash raw (pre-smoothing) for next frame's flux comparison.
        previousRaw = raw.copyOf()

        // Adaptive threshold + spring-damped pulse.
        updatePulse(flux)

        // Asymmetric smoothing for the published per-bar magnitudes:
        // fast attack (0.55) on rising bars so transients land hard,
        // slow release (0.08) on falling bars so they decay fluidly.
        // Replaces the old symmetric 50/50 average.
        val out = raw.copyOf()
        val prevOut = bandMagnitudes
        if (prevOut != null && prevOut.size == out.size) {
            for (i in out.indices) {
                val r = raw[i]
                val p = prevOut[i]
                out[i] = if (r > p) p + (r - p) * 0.55f
                         else p + (r - p) * 0.08f
            }
        }

        bandMagnitudes = out
        bandEnergies = computeBandEnergies(out, binCount)
    }

    /**
     * Sum of positive bin-to-bin changes across the kick + low-bass
     * region. Returns 0 when there's no previous frame to diff against.
     */
    private fun computeDrumFlux(
        raw: FloatArray,
        prev: FloatArray?,
        binCount: Int
    ): Float {
        if (prev == null || prev.size != raw.size) return 0f
        // Bin coverage (44.1 kHz / fftSize):
        //   captureSize 1024 → 512 mag bins → ~21.5 Hz/bin → drum band ≈ bins 2-15
        //   captureSize 512  → 256 mag bins → ~86 Hz/bin   → drum band ≈ bins 1-4
        // Scale the band by binCount so the same code works across devices
        // that report different capture sizes.
        val start = (binCount * 0.005f).toInt().coerceAtLeast(1)
        val end = (binCount * 0.06f).toInt().coerceAtLeast(start + 1)
        var flux = 0f
        for (i in start until end) {
            val d = raw[i] - prev[i]
            if (d > 0f) flux += d
        }
        // Normalize to roughly 0..1.
        return flux / (end - start).coerceAtLeast(1)
    }

    private fun updatePulse(flux: Float) {
        // Rolling-average flux → adaptive threshold. Loud passages raise
        // the bar; quiet passages lower it.
        var avg = 0f
        for (v in fluxHistory) avg += v
        avg /= fluxHistory.size

        fluxHistory[fluxHistoryIdx] = flux
        fluxHistoryIdx = (fluxHistoryIdx + 1) % fluxHistory.size

        val now = System.nanoTime() / 1_000_000L
        val dtMs = (now - (if (lastPulseMs == 0L) now else lastPulseMs)).coerceAtLeast(1L)
        lastPulseMs = now

        val threshold = maxOf(0.02f, avg * 1.6f)
        if (flux > threshold) {
            pulseTarget = ((flux - threshold) * 6f).coerceIn(0f, 1f)
        }

        // Spring damping: fast attack toward target, slow release back to 0.
        val current = pulse
        val next = if (pulseTarget > current) {
            current + (pulseTarget - current) * 0.55f
        } else {
            current + (pulseTarget - current) * 0.12f
        }
        // Target decays so the pulse falls back to 0 between beats.
        pulseTarget *= 0.5f.pow(dtMs.toFloat() / 180f)
        pulse = next.coerceIn(0f, 1f)
    }

    private fun computeBandEnergies(mags: FloatArray, binCount: Int): BandEnergies {
        // Bin coverage anchors as fractions of total bin count so we don't
        // hard-code captureSize. Roughly:
        //   sub:   ~22-66 Hz
        //   kick:  ~66-150 Hz
        //   bass:  ~150-330 Hz
        //   mids:  ~330 Hz - 2 kHz
        //   highs: ~2-5 kHz
        fun band(fracStart: Float, fracEnd: Float): Float {
            val s = (binCount * fracStart).toInt().coerceAtLeast(1)
            val e = (binCount * fracEnd).toInt().coerceAtLeast(s + 1).coerceAtMost(binCount)
            var sum = 0f
            for (i in s until e) sum += mags[i]
            return (sum / (e - s)).coerceIn(0f, 1f)
        }
        return BandEnergies(
            sub = band(0.002f, 0.008f),
            kick = band(0.008f, 0.017f),
            bass = band(0.017f, 0.037f),
            mids = band(0.037f, 0.225f),
            highs = band(0.225f, 0.575f)
        )
    }
}
