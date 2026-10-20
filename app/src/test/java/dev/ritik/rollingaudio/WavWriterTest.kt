package dev.ritik.rollingaudio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class WavWriterTest {
    @Test fun exportsExactlyTenSecondsAsMono16kHzPcm16() {
        val samples = ShortArray(160_000) { (it % 65536 - 32768).toShort() }
        val out = ByteArrayOutputStream()
        WavWriter.write(out, samples)
        val wav = out.toByteArray()
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(320_044, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals(320_036, header.getInt(4))
        assertEquals("WAVEfmt ", String(wav, 8, 8, Charsets.US_ASCII))
        assertEquals(16, header.getInt(16))
        assertEquals(1, header.getShort(20).toInt()) // PCM
        assertEquals(1, header.getShort(22).toInt()) // mono
        assertEquals(16_000, header.getInt(24))
        assertEquals(32_000, header.getInt(28))
        assertEquals(2, header.getShort(32).toInt())
        assertEquals(16, header.getShort(34).toInt())
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        assertEquals(320_000, header.getInt(40))
        val decoded = ShortArray(samples.size)
        header.position(44)
        header.asShortBuffer().get(decoded)
        assertArrayEquals(samples, decoded)
    }

    @Test fun preservesSignedSamplesAcrossChunkBoundaries() {
        val samples = ShortArray(4099) { shortArrayOf(0, 1, -1, 32767, -32768)[it % 5] }
        val out = ByteArrayOutputStream()
        WavWriter.write(out, samples, 8_000)
        val wav = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(8_000, wav.getInt(24))
        assertEquals(16_000, wav.getInt(28))
        wav.position(44)
        val decoded = ShortArray(samples.size)
        wav.asShortBuffer().get(decoded)
        assertArrayEquals(samples, decoded)
    }

    @Test fun emptyInputHasAValidEmptyWavHeader() {
        val out = ByteArrayOutputStream()
        WavWriter.write(out, shortArrayOf())
        assertEquals(44, out.size())
        val header = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36, header.getInt(4))
        assertEquals(0, header.getInt(40))
    }
}
