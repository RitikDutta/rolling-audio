package dev.ritik.rollingaudio

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Minimal visible UI. Always start microphone recording here, not from MacroDroid background. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private val permissionCode = 14

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 54, 36, 36)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        fun label(text: String): TextView = TextView(this).apply {
            this.text = text
            textSize = 17f
            setPadding(0, 8, 0, 20)
            layout.addView(this)
        }
        fun button(text: String, action: () -> Unit) {
            layout.addView(Button(this).apply {
                this.text = text
                setOnClickListener { action() }
            })
        }
        label("Rolling Audio — 10-second PCM microphone buffer")
        status = label("Ready. Tap Start and allow microphone access.")
        button("Start rolling microphone") { requestAndStart() }
        button("Stop microphone") {
            stopService(Intent(this, RecorderService::class.java))
            status.text = "Stopped."
        }
        button("Test snapshot (after 10s)") {
            RecorderService.instance?.exportSnapshot()
                ?: Toast.makeText(this, "Start microphone first", Toast.LENGTH_LONG).show()
        }
        button("Copy MacroDroid token") {
            val token = SecretStore.token(this)
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("Snapshot auth token", token))
            Toast.makeText(this, "Token copied", Toast.LENGTH_SHORT).show()
        }
        label("Audio stays in RAM until a snapshot. Snapshot: Downloads/Sarvam/voice.wav. " +
            "MacroDroid needs the token copied above. Keep it private.")
        val scroll = ScrollView(this).apply {
            addView(layout)
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        setContentView(scroll)
        scroll.requestApplyInsets()
        SecretStore.token(this)
    }

    private fun requestAndStart() {
        val needs = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            needs.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            needs.add(Manifest.permission.POST_NOTIFICATIONS)
        if (needs.isNotEmpty()) {
            requestPermissions(needs.toTypedArray(), permissionCode)
        } else startMic()
    }

    private fun startMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Microphone permission required."
            return
        }
        try {
            startForegroundService(Intent(this, RecorderService::class.java).apply {
                action = RecorderService.ACTION_START
            })
            status.text = "Mic starting. First full snapshot available after 10 seconds."
        } catch (e: Exception) {
            status.text = "Unable to start microphone: ${e.message}"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionCode) startMic()
    }
}
