package com.example.yoloaio.features.audio

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

/**
 * Trim an audio source between [startSec, endSec] and write the result as an
 * `.m4a` file. Lossless: we just remux AAC samples, no decode/encode.
 *
 * Only AAC sources are supported right now (JioSaavn streams and `.m4a`/`.mp4`
 * local files). MP3/OGG/FLAC inputs return [TrimResult.UnsupportedFormat] —
 * we'd need a MediaCodec decode→encode pipeline for those.
 */
object AudioTrimmer {

    private const val TAG = "AudioTrimmer"

    sealed interface TrimResult {
        data class Success(val file: File, val durationSec: Double) : TrimResult
        data class UnsupportedFormat(val mime: String) : TrimResult
        data class Failed(val message: String) : TrimResult
    }

    sealed interface SaveResult {
        data class Success(val uri: Uri) : SaveResult
        data object NeedsAndroid10 : SaveResult
        data class Failed(val message: String) : SaveResult
    }

    /**
     * Inserts the trimmed audio into MediaStore under `Music/YoloAIO Trims/`
     * with IS_MUSIC=1 so it shows up in the device's music library and can be
     * opened by any audio app. Returns the resulting content URI which we
     * persist as the trim's `streamUrl`.
     */
    suspend fun saveToMusicLibrary(
        context: Context,
        sourceFile: File,
        displayName: String
    ): SaveResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext SaveResult.NeedsAndroid10
        }
        try {
            val bytes = sourceFile.readBytes()
            val safeName = sanitizeName(displayName) + ".m4a"
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val relativePath = "${Environment.DIRECTORY_MUSIC}/YoloAIO Trims"

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, safeName)
                put(MediaStore.Audio.Media.TITLE, displayName.ifBlank { safeName })
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.SIZE, bytes.size)
                put(MediaStore.Audio.Media.IS_MUSIC, true)
                put(MediaStore.Audio.Media.IS_RINGTONE, false)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, false)
                put(MediaStore.Audio.Media.IS_ALARM, false)
                put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values)
                ?: return@withContext SaveResult.Failed("MediaStore insert failed")

            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: return@withContext SaveResult.Failed("Couldn't open output stream")

            val finalValues = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            resolver.update(uri, finalValues, null, null)

            Log.d(TAG, "saved trim to MediaStore: $uri (${bytes.size} bytes)")
            SaveResult.Success(uri)
        } catch (e: Exception) {
            Log.e(TAG, "saveToMusicLibrary failed", e)
            SaveResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun sanitizeName(raw: String): String {
        val cleaned = raw.replace(Regex("[^A-Za-z0-9._ -]"), "").trim()
        return cleaned.ifBlank { "yolo_trim_${System.currentTimeMillis()}" }
    }

    suspend fun trim(
        context: Context,
        sourceUri: Uri,
        startSec: Double,
        endSec: Double,
        outputFile: File
    ): TrimResult = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, sourceUri, null)

            var audioTrack = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrack = i
                    trackFormat = f
                    break
                }
            }
            if (audioTrack < 0 || trackFormat == null) {
                return@withContext TrimResult.Failed("No audio track in source")
            }

            val sourceMime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
            if (sourceMime != MediaFormat.MIMETYPE_AUDIO_AAC) {
                return@withContext TrimResult.UnsupportedFormat(sourceMime)
            }

            extractor.selectTrack(audioTrack)

            val muxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            val outTrack = muxer.addTrack(trackFormat)
            muxer.start()

            val startUs = (startSec.coerceAtLeast(0.0) * 1_000_000).toLong()
            val endUs = (endSec.coerceAtLeast(startSec + 0.1) * 1_000_000).toLong()

            // SEEK_TO_PREVIOUS_SYNC: AAC samples are independently decodable so
            // any sample at-or-after startUs is a fine cut point.
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val maxInputSize = if (trackFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                256 * 1024
            }.coerceAtLeast(64 * 1024)
            val buffer = ByteBuffer.allocate(maxInputSize)
            val info = MediaCodec.BufferInfo()

            var firstSamplePts = -1L
            var lastSamplePts = 0L

            while (true) {
                info.offset = 0
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val pts = extractor.sampleTime
                if (pts > endUs) break
                if (pts < startUs) {
                    // Sync-frame might land slightly before startUs — skip until
                    // we cross it so the output begins at the requested cut.
                    if (!extractor.advance()) break
                    continue
                }
                if (firstSamplePts < 0) firstSamplePts = pts
                info.size = sampleSize
                info.presentationTimeUs = pts - firstSamplePts
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(outTrack, buffer, info)
                lastSamplePts = pts
                if (!extractor.advance()) break
            }

            try {
                muxer.stop()
            } finally {
                muxer.release()
            }

            if (firstSamplePts < 0) {
                outputFile.delete()
                return@withContext TrimResult.Failed("No audio in selected range")
            }
            val outSec = (lastSamplePts - firstSamplePts) / 1_000_000.0
            Log.d(TAG, "trimmed ${outputFile.name}: ${outSec}s (${outputFile.length()} bytes)")
            TrimResult.Success(outputFile, outSec)
        } catch (e: Exception) {
            Log.e(TAG, "trim failed", e)
            outputFile.delete()
            TrimResult.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            extractor.release()
        }
    }

    /**
     * Reads total duration of an audio source. Returns 0.0 on any error so the
     * caller can still render a placeholder.
     */
    suspend fun probeDurationSec(context: Context, uri: Uri): Double = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            ms / 1000.0
        } catch (e: Exception) {
            Log.w(TAG, "probe failed for $uri: ${e.message}")
            0.0
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Streams [url] into a temp file under the app cache. Used for remote
     * sources (JioSaavn) — MediaExtractor with an https Uri sometimes hangs on
     * older devices, so we go through a local file.
     */
    suspend fun downloadToCache(
        context: Context,
        url: String,
        extension: String
    ): File? = withContext(Dispatchers.IO) {
        val outFile = File(context.cacheDir, "trim_src_${System.currentTimeMillis()}.$extension")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; YoloAIO) Mobile")
        }
        try {
            if (conn.responseCode !in 200..299) return@withContext null
            conn.inputStream.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "download failed for $url", e)
            outFile.delete()
            null
        } finally {
            conn.disconnect()
        }
    }
}
