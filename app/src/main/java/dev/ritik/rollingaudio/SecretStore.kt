package dev.ritik.rollingaudio

import android.content.Context
import java.security.SecureRandom

object SecretStore {
    private const val PREFS = "rolling_auth"
    private const val KEY = "snapshot_token"
    fun token(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        prefs.edit().putString(KEY, token).apply()
        return token
    }
}
