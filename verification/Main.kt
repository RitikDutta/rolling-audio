package dev.ritik.rollingaudio

import java.io.ByteArrayOutputStream

fun main() {
    val buffer = AudioRingBuffer(6)
    check(buffer.snapshotOrNull() == null)
    buffer.append(shortArrayOf(1, 2, 3), 3)
    check(buffer.snapshotOrNull() == null)
    buffer.append(shortArrayOf(4, 5, 6), 3)
    check(buffer.snapshotOrNull()!!.contentEquals(shortArrayOf(1, 2, 3, 4, 5, 6)))
    buffer.append(shortArrayOf(7, 8), 2)
    check(buffer.snapshotOrNull()!!.contentEquals(shortArrayOf(3, 4, 5, 6, 7, 8)))
    // Push longer than the buffer: retain only the latest samples.
    buffer.append(shortArrayOf(9, 10, 11, 12, 13, 14, 15), 7)
    check(buffer.snapshotOrNull()!!.contentEquals(shortArrayOf(10, 11, 12, 13, 14, 15)))

    val out = ByteArrayOutputStream()
    WavWriter.write(out, shortArrayOf(0, 1, -1, 32767, -32768), 16_000)
    val wav = out.toByteArray()
    check(wav.size == 44 + 2 * 5)
    check(String(wav, 0, 4) == "RIFF")
    check(String(wav, 8, 4) == "WAVE")
    check(String(wav, 36, 4) == "data")
    check(wav[44].toInt() == 0 && wav[45].toInt() == 0)
    check(wav[46].toInt() == 1 && wav[47].toInt() == 0)
    check((wav[48].toInt() and 255) == 255 && (wav[49].toInt() and 255) == 255)
    println("PASS: circular buffer and PCM16 WAV format checks")
}
