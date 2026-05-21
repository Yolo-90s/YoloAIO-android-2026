package com.example.yoloaio.features.music

import android.media.audiofx.Visualizer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.sqrt

/**
 * Real-time FFT capture from a MediaPlayer's audio session.
 *
 * Wraps [android.media.audiofx.Visualizer], which is the only public Android
 * API for reading the playing audio stream. The Visualizer fires a callback
 * ~20 times/sec with raw FFT bytes; we compute per-bin magnitudes, apply
 * light temporal smoothing, and expose the result via a Compose state so the
 * visualiser composable recomposes on each update.
 *
 * Requires `RECORD_AUDIO` permission on most Android versions. If the user
 * denies the permission, or the OEM has blocked Visualizer entirely (some
 * MIUI / OxygenOS / FunTouch builds do), [start] returns false and
 * [bandMagnitudes] stays null — callers should treat null as "fall back to
 * synthetic animation".
 *
 * `bandMagnitudes` format: index 0 is the DC component (skip it); higher
 * indices map to higher frequencies. Each value is already normalised to
 * roughly the 0..1 range, but transient peaks can spike above 1, so the
 * visualiser should clamp.
 */
class BeatAnalyzer {

    private val tag = "BeatAnalyzer"

    private var visualizer: Visualizer? = null

    var bandMagnitudes by mutableStateOf<FloatArray?>(null)
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
            val captureSizeChoice = range[1].coerceAtMost(512)

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
    }

    private fun updateMagnitudes(fft: ByteArray) {
        // Android packs the FFT as:
        //   fft[0] = DC (real)
        //   fft[1] = Nyquist (real)
        //   fft[2k]   = real(band k)
        //   fft[2k+1] = imag(band k)
        // for k in 1..n/2 - 1.
        val binCount = fft.size / 2
        val out = FloatArray(binCount)

        // First slot: DC magnitude, kept for completeness — the visualiser
        // ignores it.
        out[0] = (fft[0].toInt().toFloat()).let { if (it < 0) -it else it } / 128f

        for (i in 1 until binCount) {
            val real = fft[i * 2].toInt().toFloat()
            val imag = fft[i * 2 + 1].toInt().toFloat()
            // Magnitude. Divide by 128 to roughly land in 0..1; loud bass hits
            // can still spike higher and the visualiser will clamp.
            out[i] = sqrt(real * real + imag * imag) / 128f
        }

        // Temporal smoothing — bars stay glued to the beat without strobing.
        // 0.5/0.5 is a good balance: bass hits land sharply but ambient
        // content (pads, reverb tails) doesn't flicker.
        val previous = bandMagnitudes
        if (previous != null && previous.size == out.size) {
            for (i in out.indices) {
                out[i] = previous[i] * 0.5f + out[i] * 0.5f
            }
        }

        bandMagnitudes = out
    }
}
