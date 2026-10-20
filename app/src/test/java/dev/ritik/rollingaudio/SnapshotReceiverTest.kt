package dev.ritik.rollingaudio

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35])
class SnapshotReceiverTest {
    private lateinit var app: Application

    @Before fun prepare() {
        app = RuntimeEnvironment.getApplication()
    }

    @Test fun rejectsMissingAndIncorrectTokens() {
        val receiver = SnapshotReceiver()
        receiver.onReceive(app, Intent(RecorderService.ACTION_SNAPSHOT))
        receiver.onReceive(app, Intent(RecorderService.ACTION_SNAPSHOT).putExtra("token", "wrong"))
        assertTrue(shadowOf(app).broadcastIntents.isEmpty())
    }

    @Test fun authenticatedRequestReportsNotRunningWithoutStartingMicrophone() {
        SnapshotReceiver().onReceive(app, Intent(RecorderService.ACTION_SNAPSHOT)
            .putExtra("token", SecretStore.token(app)))
        val result = shadowOf(app).broadcastIntents.single()
        assertEquals("dev.ritik.rollingaudio.SNAPSHOT_READY", result.action)
        assertEquals("com.arlosoft.macrodroid", result.`package`)
        assertEquals("NOT_RUNNING", result.getStringExtra("status"))
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun ignoresUnrecognizedActionsEvenWithValidToken() {
        SnapshotReceiver().onReceive(app, Intent("unknown.action")
            .putExtra("token", SecretStore.token(app)))
        assertTrue(shadowOf(app).broadcastIntents.isEmpty())
    }

    @Test fun tokenIsRandomAndPersistsForMacroDroid() {
        val token = SecretStore.token(app)
        assertTrue(token.matches(Regex("[0-9a-f]{48}")))
        assertEquals(token, SecretStore.token(app))
    }

    @Test fun manifestExposesBothMacroDroidActions() {
        for (action in listOf(RecorderService.ACTION_SNAPSHOT, RecorderService.ACTION_CLEAR)) {
            val receivers = app.packageManager.queryBroadcastReceivers(
                Intent(action).setPackage(app.packageName), PackageManager.GET_META_DATA)
            val receiver = receivers.single().activityInfo
            assertEquals(SnapshotReceiver::class.java.name, receiver.name)
            assertTrue(receiver.exported)
        }
    }

    @Test fun microphoneServiceRemainsPrivateAndHasTheRequiredType() {
        val info = app.packageManager.getServiceInfo(
            ComponentName(app, RecorderService::class.java), PackageManager.GET_META_DATA)
        assertFalse(info.exported)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE, info.foregroundServiceType)
    }
}
