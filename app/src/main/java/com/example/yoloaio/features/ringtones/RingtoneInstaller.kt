package com.example.yoloaio.features.ringtones

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

enum class RingtoneSlot(val mediaStoreFlag: String, val ringtoneType: Int, val dirName: String) {
    Ringtone(MediaStore.Audio.Media.IS_RINGTONE, RingtoneManager.TYPE_RINGTONE, Environment.DIRECTORY_RINGTONES),
    Notification(MediaStore.Audio.Media.IS_NOTIFICATION, RingtoneManager.TYPE_NOTIFICATION, Environment.DIRECTORY_NOTIFICATIONS),
    Alarm(MediaStore.Audio.Media.IS_ALARM, RingtoneManager.TYPE_ALARM, Environment.DIRECTORY_ALARMS),
    Music(MediaStore.Audio.Media.IS_MUSIC, -1, Environment.DIRECTORY_MUSIC)
}

object RingtoneInstaller {

    sealed interface InstallResult {
        data class Success(val uri: Uri, val setAsDefault: Boolean) : InstallResult
        data object NeedsAndroid10 : InstallResult
        data object NeedsWriteSettingsPermission : InstallResult
        data class Failed(val message: String) : InstallResult
    }

    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun openWriteSettingsScreen(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Downloads the tone, inserts it into MediaStore with the correct IS_* flag,
     * and optionally sets it as the system default for that slot.
     *
     * Requires Android 10 (Q) or newer for the MediaStore scoped-write path.
     * On older Android we'd need WRITE_EXTERNAL_STORAGE + raw file access — not
     * supported here.
     */
    suspend fun install(
        context: Context,
        tone: Tone,
        slot: RingtoneSlot,
        setAsDefault: Boolean
    ): InstallResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext InstallResult.NeedsAndroid10
        }
        if (setAsDefault && !canWriteSettings(context)) {
            return@withContext InstallResult.NeedsWriteSettingsPermission
        }

        try {
            val bytes = readToneBytes(context, tone.streamUrl)
                ?: return@withContext InstallResult.Failed("Couldn't read source audio")

            val fileName = sanitizeName(tone.name) + "." + tone.fileExtension
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.TITLE, tone.name)
                put(MediaStore.Audio.Media.MIME_TYPE, tone.mimeType)
                put(MediaStore.Audio.Media.SIZE, bytes.size)

                // Slot flags — exclusive among IS_RINGTONE/IS_NOTIFICATION/IS_ALARM/IS_MUSIC.
                put(MediaStore.Audio.Media.IS_RINGTONE, slot == RingtoneSlot.Ringtone)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, slot == RingtoneSlot.Notification)
                put(MediaStore.Audio.Media.IS_ALARM, slot == RingtoneSlot.Alarm)
                put(MediaStore.Audio.Media.IS_MUSIC, slot == RingtoneSlot.Music)

                put(MediaStore.Audio.Media.RELATIVE_PATH, slot.dirName)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values)
                ?: return@withContext InstallResult.Failed("MediaStore insert failed")

            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: return@withContext InstallResult.Failed("Couldn't open output stream")

            val finalValues = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            resolver.update(uri, finalValues, null, null)

            if (setAsDefault && slot.ringtoneType >= 0) {
                runCatching {
                    RingtoneManager.setActualDefaultRingtoneUri(
                        context,
                        slot.ringtoneType,
                        uri
                    )
                }
            }

            InstallResult.Success(uri = uri, setAsDefault = setAsDefault)
        } catch (e: Exception) {
            InstallResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Bridges the two source types we install from: remote tone URLs from
     * Freesound/JioSaavn (`https`) and on-device trimmed clips
     * (`content://media/...`).
     */
    private fun readToneBytes(context: Context, url: String): ByteArray? {
        val uri = Uri.parse(url)
        return when (uri.scheme) {
            "content", "file" -> try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (_: Exception) {
                null
            }
            else -> downloadBytes(url)
        }
    }

    private fun downloadBytes(url: String): ByteArray? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; YoloAIO) Mobile")
        }
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun sanitizeName(raw: String): String {
        val cleaned = raw.replace(Regex("[^A-Za-z0-9._ -]"), "").trim()
        return cleaned.ifBlank { "yolo_tone_${System.currentTimeMillis()}" }
    }
}
