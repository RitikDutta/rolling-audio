package dev.ritik.rollingaudio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/** Writes one fixed-name WAV into public Downloads/Sarvam using MediaStore (Android 10+). */
class SnapshotStorage(private val context: Context) {
    private val resolver = context.contentResolver
    private val prefs = context.getSharedPreferences("snapshot_file", Context.MODE_PRIVATE)
    private val uriKey = "uri"

    fun save(samples: ShortArray): Uri {
        // One stable MediaStore URI is reused, so snapshots do not pile up.
        var uri = prefs.getString(uriKey, null)?.let(Uri::parse)
        if (uri != null) {
            // Preserve the same document URI across requests for HTTP Shortcuts.
            val previous = requireNotNull(uri)
            try {
                val descriptor = resolver.openFileDescriptor(previous, "r")
                if (descriptor == null) uri = null else descriptor.close()
            } catch (_: Exception) { uri = null }
        }
        if (uri == null) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "voice.wav")
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Sarvam/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create download")
            prefs.edit().putString(uriKey, uri.toString()).apply()
        }
        val target = requireNotNull(uri)
        resolver.update(target, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }, null, null)
        try {
            resolver.openOutputStream(target, "wt").use { out ->
                requireNotNull(out) { "Unable to open WAV file" }
                WavWriter.write(out, samples)
            }
        } catch (e: Exception) {
            // Never publish a partially written WAV. Retain the URI for a retry.
            try {
                resolver.openOutputStream(target, "wt")?.close()
            } catch (cleanup: Exception) {
                e.addSuppressed(cleanup)
            }
            throw e
        }
        // Only publish after the complete WAV was written and the stream closed.
        check(resolver.update(target, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null) == 1) { "Unable to publish WAV" }
        return target
    }

    fun clearAudio() {
        // Preserve file URI (HTTP Shortcuts may have a persisted SAF grant to it),
        // but remove the recorded audio bytes by truncating the file to 0 bytes.
        val uri = prefs.getString(uriKey, null)?.let(Uri::parse) ?: return
        requireNotNull(resolver.openOutputStream(uri, "wt")) {
            "Unable to clear WAV file"
        }.use { /* truncate while preserving the URI */ }
    }
}
