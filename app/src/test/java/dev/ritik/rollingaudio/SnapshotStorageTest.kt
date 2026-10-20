package dev.ritik.rollingaudio

import android.app.Application
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35])
class SnapshotStorageTest {
    private lateinit var app: Application
    private lateinit var provider: DownloadsProvider

    @Before fun prepare() {
        app = RuntimeEnvironment.getApplication()
        provider = DownloadsProvider(File.createTempFile("snapshot", ".wav", app.cacheDir))
        provider.attachInfo(app, ProviderInfo().apply { authority = MediaStore.AUTHORITY })
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, provider)
    }

    @Test fun exportsCompleteWavToDownloadsAndReusesTheSameUri() {
        val storage = SnapshotStorage(app)
        val first = storage.save(ShortArray(160_000) { 42 })
        assertEquals(320_044L, provider.file.length())
        assertEquals("voice.wav", provider.values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
        assertEquals("audio/wav", provider.values.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        assertEquals("Download/Sarvam/", provider.values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
        assertEquals(0, provider.pending)
        val second = SnapshotStorage(app).save(shortArrayOf(1, 2, 3))
        assertEquals(first, second)
        assertEquals(1, provider.inserts)
        assertEquals(50L, provider.file.length()) // old content is truncated
        assertEquals("RIFF", provider.file.readBytes().take(4).toByteArray().toString(Charsets.US_ASCII))
        assertEquals(0, provider.pending)
    }

    @Test fun cleanupEmptiesAudioWithoutChangingTheDocumentUri() {
        val storage = SnapshotStorage(app)
        val uri = storage.save(shortArrayOf(1, 2, 3))
        storage.clearAudio()
        assertEquals(0L, provider.file.length())
        assertEquals(uri, storage.save(shortArrayOf(4)))
        assertEquals(1, provider.inserts)
    }

    @Test fun failedExportIsNotPublishedAndCanBeRetried() {
        val storage = SnapshotStorage(app)
        val uri = storage.save(shortArrayOf(1, 2, 3))
        provider.failNextWrite = true
        try {
            storage.save(shortArrayOf(4, 5, 6))
            fail("Expected the write error to reach the caller")
        } catch (_: FileNotFoundException) { }
        assertEquals(1, provider.pending)
        assertEquals(0L, provider.file.length())
        assertEquals(uri, storage.save(shortArrayOf(7, 8, 9)))
        assertEquals(0, provider.pending)
        assertEquals(1, provider.inserts)
    }

    @Test fun cleanupBeforeAnyExportDoesNotCreateAFileEntry() {
        SnapshotStorage(app).clearAudio()
        assertEquals(0, provider.inserts)
    }

    /** File-backed MediaStore stand-in: exercises real ContentResolver streams. */
    private class DownloadsProvider(val file: File) : ContentProvider() {
        var values = ContentValues()
        var inserts = 0
        var pending = 0
        var failNextWrite = false
        override fun onCreate() = true
        override fun getType(uri: Uri) = "audio/wav"
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            this.values = ContentValues(requireNotNull(values))
            pending = values.getAsInteger(MediaStore.MediaColumns.IS_PENDING)
            inserts++
            return ContentUris.withAppendedId(uri, inserts.toLong())
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?,
                            selectionArgs: Array<out String>?): Int {
            pending = requireNotNull(values).getAsInteger(MediaStore.MediaColumns.IS_PENDING)
            return 1
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (mode.contains('w') && failNextWrite) {
                failNextWrite = false
                throw FileNotFoundException("Simulated export failure")
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                           selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
