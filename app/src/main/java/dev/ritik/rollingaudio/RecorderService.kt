package dev.ritik.rollingaudio

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** User-started foreground service: holds only the last 10 seconds in memory. */
class RecorderService : Service() {
    companion object {
        const val ACTION_START = "dev.ritik.rollingaudio.START"
        const val ACTION_STOP = "dev.ritik.rollingaudio.STOP"
        const val ACTION_SNAPSHOT = "dev.ritik.rollingaudio.SNAPSHOT"
        const val ACTION_CLEAR = "dev.ritik.rollingaudio.CLEAR_SNAPSHOT"
        const val ACTION_READY = "dev.ritik.rollingaudio.SNAPSHOT_READY"
        const val RATE = 16_000
        const val WINDOW = 10
        private const val CHANNEL = "rolling_recording"
        private const val NOTIFICATION_ID = 1801
        @Volatile var instance: RecorderService? = null
            private set

        fun report(context: Context, status: String, detail: String = "") {
            val intent = Intent(ACTION_READY).apply {
                // Delivers to MacroDroid's Intent Received trigger without involving Tasker.
                setPackage("com.arlosoft.macrodroid")
                putExtra("status", status)
                putExtra("detail", detail)
                if (status == "READY") {
                    putExtra("path", "/storage/emulated/0/Download/Sarvam/voice.wav")
                }
            }
            context.sendBroadcast(intent)
        }
    }

    private val ring = AudioRingBuffer(RATE * WINDOW)
    private val io = Executors.newSingleThreadExecutor()
    private val exportBusy = AtomicBoolean(false)
    @Volatile private var active = false
    @Volatile private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val channel = NotificationChannel(CHANNEL, "Rolling microphone", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_START -> if (!active) startRecording()
        }
        return START_NOT_STICKY // Never automatically restart the mic after process death.
    }

    private fun startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            report(this, "ERROR", "Microphone permission missing")
            stopSelf()
            return
        }
        try {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            report(this, "ERROR", "Could not start microphone service: ${e.message}")
            stopSelf()
            return
        }
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) {
            report(this, "ERROR", "16-kHz microphone input unavailable")
            stopSelf()
            return
        }
        val record = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minimum, 4096))
                .build()
        } catch (e: Exception) {
            report(this, "ERROR", "AudioRecord initialization failed: ${e.message}")
            stopSelf()
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            report(this, "ERROR", "AudioRecord not initialized")
            stopSelf()
            return
        }
        recorder = record
        active = true
        recordingThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            try {
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    error("Microphone failed to start")
                }
                val chunk = ShortArray(512)
                while (active) {
                    val read = record.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                    if (read > 0) ring.append(chunk, read)
                    else if (active) error("Audio read failed: $read")
                }
            } catch (e: Exception) {
                Log.e("RollingAudio", "Microphone stopped", e)
                if (active) report(this, "ERROR", "Recording failed: ${e.message}")
                active = false
                stopSelf()
            } finally {
                try { record.stop() } catch (_: Exception) { }
                record.release()
                recorder = null
            }
        }, "AudioRingBufferCapture").also { it.start() }
    }

    /** Captures data synchronously at broadcast-receipt time, writes WAV off main thread. */
    fun exportSnapshot() {
        if (!active) { report(this, "NOT_RUNNING"); return }
        if (!exportBusy.compareAndSet(false, true)) {
            report(this, "BUSY", "A snapshot is already exporting")
            return
        }
        val samples = ring.snapshotOrNull()
        if (samples == null) {
            exportBusy.set(false)
            report(this, "NOT_READY", "Less than 10 seconds recorded")
            return
        }
        io.execute {
            try {
                val uri = SnapshotStorage(this).save(samples)
                report(this, "READY", uri.toString())
            } catch (e: Exception) {
                Log.e("RollingAudio", "Snapshot export failed", e)
                report(this, "ERROR", e.message ?: "Could not save WAV")
            } finally {
                exportBusy.set(false)
            }
        }
    }

    fun clearSnapshot() {
        io.execute {
            try {
                SnapshotStorage(this).clearAudio()
            } catch (e: Exception) {
                Log.e("RollingAudio", "Snapshot cleanup failed", e)
                report(this, "ERROR", e.message ?: "Could not clear WAV")
            }
        }
    }

    private fun buildNotification(): Notification {
        val stop = PendingIntent.getService(this, 1,
            Intent(this, RecorderService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val home = PendingIntent.getActivity(this, 2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Rolling audio is ON")
            .setContentText("Only last 10 seconds in RAM. Tap Stop to end.")
            .setContentIntent(home)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_media_pause, "Stop", stop).build())
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        active = false
        try { recorder?.stop() } catch (_: Exception) { }
        instance = null
        io.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
