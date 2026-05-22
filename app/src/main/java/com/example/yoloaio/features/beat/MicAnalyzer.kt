package com.example.yoloaio.features.beat

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Per-band amplitudes from the mic. Same shape as the music
 * BeatAnalyzer's [com.example.yoloaio.features.music.BandEnergies] but
 * kept local so the two analyzers stay independent.
 */
data class MicBandEnergies(
    val sub: Float = 0f,
    val kick: Float = 0f,
    val bass: Float = 0f,
    val mids: Float = 0f,
    val highs: Float = 0f
) {
    companion object {
        val EMPTY = MicBandEnergies()
    }
}

/**
 * Ambient-microphone analyzer for the Beat Analyser screen.
 *
 * Captures raw PCM from the device mic with [AudioRecord], runs a
 * radix-2 Cooley-Tukey FFT on each frame, and exposes:
 *
 *   - [rmsDb]            instantaneous loudness in dBFS (-90 ≈ silence, 0 ≈ peak)
 *   - [bandMagnitudes]   smoothed per-bin FFT magnitudes (0..1)
 *   - [bandEnergies]     mean amplitude in named bands (sub/kick/bass/mids/highs)
 *   - [pulse]            drum-onset pulse (0..1, springs back over ~250 ms)
 *
 * Unlike the music BeatAnalyzer (which attaches a Visualizer to the
 * player's audio session), this captures everything the mic hears —
 * external music, voices, claps, ambient noise — so the Beat Analyser
 * screen can react to whatever's playing in the room.
 *
 * Requires `RECORD_AUDIO` permission; [start] returns false if the
 * permission isn't granted or the OS refuses the AudioRecord instance
 * (some Vivo / OxygenOS builds block third-party mic capture).
 */
class MicAnalyzer {

    private val tag = "MicAnalyzer"

    private var audioRecord: AudioRecord? = null
    private var scope: CoroutineScope? = null
    private var captureJob: Job? = null

    private val sampleRate = 44100
    private val fftSize = 2048
    private val binCount = fftSize / 2

    /** -90 dB on silence, ~0 dB on a peak. Mapped 0..1 by the UI. */
    var rmsDb by mutableFloatStateOf(-90f)
        private set

    var bandMagnitudes by mutableStateOf<FloatArray?>(null)
        private set

    var bandEnergies by mutableStateOf(MicBandEnergies.EMPTY)
        private set

    var pulse by mutableFloatStateOf(0f)
        private set

    var isRunning by mutableStateOf(false)
        private set

    // Spectral-flux state (drum-onset detection).
    private var previousRaw: FloatArray? = null
    private val fluxHistory = FloatArray(20)
    private var fluxHistoryIdx = 0
    private var pulseTarget = 0f
    private var lastPulseMs = 0L

    // Pre-allocated FFT buffers — we run ~30 times per second, no point
    // reallocating each frame.
    private val fftReal = FloatArray(fftSize)
    private val fftImag = FloatArray(fftSize)
    private val window = hannWindow(fftSize)

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (audioRecord != null) return true
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            Log.w(tag, "Invalid min buffer size: $minBuf — mic likely unavailable")
            return false
        }
        // Read at least fftSize samples per loop. Use double the min buffer
        // as a safety margin so OS reads stay smooth.
        val bufBytes = maxOf(minBuf * 2, fftSize * 2)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufBytes
            )
        } catch (e: SecurityException) {
            Log.w(tag, "RECORD_AUDIO denied: ${e.message}")
            return false
        } catch (e: Exception) {
            Log.w(tag, "AudioRecord init failed: ${e.message}")
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(tag, "AudioRecord state != INITIALIZED")
            try { rec.release() } catch (_: Exception) {}
            return false
        }
        try {
            rec.startRecording()
        } catch (e: Exception) {
            Log.w(tag, "startRecording failed: ${e.message}")
            try { rec.release() } catch (_: Exception) {}
            return false
        }
        audioRecord = rec

        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        captureJob = s.launch {
            val samples = ShortArray(fftSize)
            while (isActive) {
                val read = try {
                    rec.read(samples, 0, fftSize)
                } catch (e: Exception) {
                    Log.w(tag, "read failed: ${e.message}")
                    break
                }
                if (read <= 0) continue
                processFrame(samples, read)
            }
        }
        isRunning = true
        return true
    }

    fun stop() {
        isRunning = false
        captureJob?.cancel()
        captureJob = null
        scope?.cancel()
        scope = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        rmsDb = -90f
        pulse = 0f
        pulseTarget = 0f
        previousRaw = null
        bandMagnitudes = null
        bandEnergies = MicBandEnergies.EMPTY
        for (i in fluxHistory.indices) fluxHistory[i] = 0f
        fluxHistoryIdx = 0
    }

    private fun processFrame(samples: ShortArray, length: Int) {
        // ── RMS / loudness in dBFS ─────────────────────────────────
        var sumSq = 0.0
        for (i in 0 until length) {
            val v = samples[i].toDouble()
            sumSq += v * v
        }
        val rms = sqrt(sumSq / length)
        // 32768 is full-scale for a signed 16-bit sample. Floor at -90 dB
        // so the UI gauge doesn't whip to -Infinity in pure silence.
        val db = if (rms > 1.0) (20.0 * log10(rms / 32768.0)).toFloat() else -90f
        rmsDb = db.coerceIn(-90f, 0f)

        // ── FFT ────────────────────────────────────────────────────
        // Apply Hann window first to reduce spectral leakage; then
        // copy into the pre-allocated FFT buffers. Imag starts at 0
        // because the input signal is real.
        for (i in 0 until fftSize) {
            fftReal[i] = if (i < length) {
                (samples[i] / 32768f) * window[i]
            } else 0f
            fftImag[i] = 0f
        }
        fft(fftReal, fftImag)

        val raw = FloatArray(binCount)
        for (i in 0 until binCount) {
            val r = fftReal[i]
            val im = fftImag[i]
            // Normalize roughly to 0..1. 2/fftSize is the conventional
            // single-sided spectrum scaling for a windowed FFT.
            raw[i] = (sqrt(r * r + im * im) * (2f / fftSize)).coerceAtMost(1.5f)
        }

        // Spectral-flux drum onset (same algorithm as music BeatAnalyzer).
        val flux = computeDrumFlux(raw, previousRaw, binCount)
        previousRaw = raw.copyOf()
        updatePulse(flux)

        // Asymmetric peak-hold smoothing for the published per-bin output.
        val smoothed = raw.copyOf()
        val prev = bandMagnitudes
        if (prev != null && prev.size == smoothed.size) {
            for (i in smoothed.indices) {
                val r = raw[i]
                val p = prev[i]
                smoothed[i] = if (r > p) p + (r - p) * 0.55f
                              else p + (r - p) * 0.08f
            }
        }
        bandMagnitudes = smoothed
        bandEnergies = computeBandEnergies(smoothed, binCount)
    }

    private fun computeDrumFlux(
        raw: FloatArray,
        prev: FloatArray?,
        binCount: Int
    ): Float {
        if (prev == null || prev.size != raw.size) return 0f
        // At 44.1 kHz with fftSize=2048 → ~21.5 Hz per bin.
        //   bins 1-7   ≈ 22-150 Hz (sub + kick)
        //   bins 7-15  ≈ 150-323 Hz (low bass)
        // We sample bins 1..15 for the flux — captures the broadband
        // burst of a kick drum without picking up vocal sibilance.
        val start = 1
        val end = (binCount * 0.015f).toInt().coerceAtLeast(start + 5)
        var flux = 0f
        for (i in start until end) {
            val d = raw[i] - prev[i]
            if (d > 0f) flux += d
        }
        return flux / (end - start).coerceAtLeast(1)
    }

    private fun updatePulse(flux: Float) {
        var avg = 0f
        for (v in fluxHistory) avg += v
        avg /= fluxHistory.size
        fluxHistory[fluxHistoryIdx] = flux
        fluxHistoryIdx = (fluxHistoryIdx + 1) % fluxHistory.size

        val now = System.nanoTime() / 1_000_000L
        val dtMs = (now - (if (lastPulseMs == 0L) now else lastPulseMs)).coerceAtLeast(1L)
        lastPulseMs = now

        // Lower threshold + bigger multiplier than the music BeatAnalyzer
        // because mic input is naturally quieter than playback through
        // the audio session. Tuned aggressively here — soft taps,
        // ambient music from another room, conversation should all
        // still produce a visible pulse for the disco / tiles. Trade-
        // off is more false positives on ambient noise, but for a
        // visualiser that reads as "more reactive" rather than wrong.
        val threshold = maxOf(0.0007f, avg * 1.10f)
        if (flux > threshold) {
            pulseTarget = ((flux - threshold) * 38f).coerceIn(0f, 1f)
        }

        val current = pulse
        pulse = if (pulseTarget > current) {
            current + (pulseTarget - current) * 0.55f
        } else {
            current + (pulseTarget - current) * 0.12f
        }.coerceIn(0f, 1f)
        pulseTarget *= 0.5f.pow(dtMs.toFloat() / 180f)
    }

    private fun computeBandEnergies(mags: FloatArray, binCount: Int): MicBandEnergies {
        fun band(fracStart: Float, fracEnd: Float): Float {
            val s = (binCount * fracStart).toInt().coerceAtLeast(1)
            val e = (binCount * fracEnd).toInt().coerceAtLeast(s + 1).coerceAtMost(binCount)
            var sum = 0f
            for (i in s until e) sum += mags[i]
            return (sum / (e - s)).coerceIn(0f, 1f)
        }
        return MicBandEnergies(
            sub = band(0.001f, 0.005f),
            kick = band(0.005f, 0.012f),
            bass = band(0.012f, 0.025f),
            mids = band(0.025f, 0.15f),
            highs = band(0.15f, 0.45f)
        )
    }

    // ── In-place radix-2 Cooley-Tukey FFT ──────────────────────────
    // Standard implementation. Operates on real/imag pairs; size must
    // be a power of 2 (we set fftSize=2048, so it is). Roughly 50
    // lines, no external dependencies.
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wReal = cos(angle).toFloat()
            val wImag = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var wr = 1f
                var wi = 0f
                val half = len shr 1
                for (k in 0 until half) {
                    val a = i + k
                    val b = i + k + half
                    val tReal = wr * real[b] - wi * imag[b]
                    val tImag = wr * imag[b] + wi * real[b]
                    real[b] = real[a] - tReal
                    imag[b] = imag[a] - tImag
                    real[a] = real[a] + tReal
                    imag[a] = imag[a] + tImag
                    val nwr = wr * wReal - wi * wImag
                    wi = wr * wImag + wi * wReal
                    wr = nwr
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun hannWindow(n: Int): FloatArray {
        val w = FloatArray(n)
        for (i in 0 until n) {
            w[i] = (0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))).toFloat()
        }
        return w
    }
}
