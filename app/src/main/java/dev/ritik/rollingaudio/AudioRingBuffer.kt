package dev.ritik.rollingaudio

/** Keeps exactly the latest capacity samples. No files are written until requested. */
class AudioRingBuffer(private val capacity: Int) {
    init { require(capacity > 0) }
    private val samples = ShortArray(capacity)
    private var next = 0
    private var count = 0

    @Synchronized fun append(source: ShortArray, length: Int) {
        require(length in 0..source.size)
        for (i in 0 until length) {
            samples[next] = source[i]
            next = (next + 1) % capacity
        }
        count = minOf(capacity, count + length)
    }

    /** Returns chronological 10-second copy only when the buffer is fully primed. */
    @Synchronized fun snapshotOrNull(): ShortArray? {
        if (count != capacity) return null
        return ShortArray(capacity) { samples[(next + it) % capacity] }
    }

    @Synchronized fun availableSamples(): Int = count
}
