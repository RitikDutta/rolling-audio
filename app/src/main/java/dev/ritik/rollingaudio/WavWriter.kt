package dev.ritik.rollingaudio

import java.io.OutputStream

/** PCM16 little-endian WAV, 1 channel. Android-specific APIs are not required. */
object WavWriter {
    fun write(out: OutputStream, samples: ShortArray, sampleRate: Int = 16_000) {
        val dataSize = samples.size * 2
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        write32(out, 36 + dataSize)
        out.write("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        write32(out, 16) // PCM format chunk size
        write16(out, 1)  // uncompressed PCM
        write16(out, 1)  // mono
        write32(out, sampleRate)
        write32(out, sampleRate * 2)
        write16(out, 2)  // block alignment (bytes)
        write16(out, 16) // bits per sample
        out.write("data".toByteArray(Charsets.US_ASCII))
        write32(out, dataSize)
        val bytes = ByteArray(4096)
        var index = 0
        while (index < samples.size) {
            val length = minOf(bytes.size / 2, samples.size - index)
            for (i in 0 until length) {
                val value = samples[index + i].toInt()
                bytes[i * 2] = value.toByte()
                bytes[i * 2 + 1] = (value ushr 8).toByte()
            }
            out.write(bytes, 0, length * 2)
            index += length
        }
    }
    private fun write16(out: OutputStream, n: Int) {
        out.write(n and 255)
        out.write((n ushr 8) and 255)
    }
    private fun write32(out: OutputStream, n: Int) {
        write16(out, n and 0xffff)
        write16(out, (n ushr 16) and 0xffff)
    }
}
