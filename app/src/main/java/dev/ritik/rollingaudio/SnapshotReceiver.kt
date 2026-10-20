package dev.ritik.rollingaudio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.concurrent.thread

/** External entrypoint: accepts requests only with the randomly generated token. */
class SnapshotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val incoming = intent.getStringExtra("token") ?: return
        val expected = SecretStore.token(context)
        if (!MessageDigest.isEqual(
                incoming.toByteArray(StandardCharsets.UTF_8),
                expected.toByteArray(StandardCharsets.UTF_8)
            )) return

        val service = RecorderService.instance
        when (intent.action) {
            RecorderService.ACTION_SNAPSHOT -> {
                if (service == null) {
                    RecorderService.report(context, "NOT_RUNNING")
                } else service.exportSnapshot()
            }
            RecorderService.ACTION_CLEAR -> {
                if (service == null) {
                    val pending = goAsync()
                    val app = context.applicationContext
                    thread(name = "ClearAudioSnapshot") {
                        try {
                            SnapshotStorage(app).clearAudio()
                        } catch (e: Exception) {
                            RecorderService.report(app, "ERROR", e.message ?: "Could not clear WAV")
                        } finally {
                            pending.finish()
                        }
                    }
                } else service.clearSnapshot()
            }
        }
    }
}
