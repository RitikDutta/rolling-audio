package dev.ritik.rollingaudio

import org.junit.Assert.*
import org.junit.Test

class AudioRingBufferTest {
    @Test fun waitsForAnEntireTenSecondWindow() {
        val ring = AudioRingBuffer(160_000)
        assertNull(ring.snapshotOrNull())
        ring.append(ShortArray(159_999) { it.toShort() }, 159_999)
        assertEquals(159_999, ring.availableSamples())
        assertNull(ring.snapshotOrNull())
        ring.append(shortArrayOf(42), 1)
        assertEquals(160_000, ring.snapshotOrNull()!!.size)
        assertEquals(42.toShort(), ring.snapshotOrNull()!!.last())
    }

    @Test fun wrapsInChronologicalOrderAndHonorsReadLength() {
        val ring = AudioRingBuffer(6)
        ring.append(shortArrayOf(1, 2, 3, 4, 5, 6), 6)
        ring.append(shortArrayOf(7, 8, 99), 2)
        assertArrayEquals(shortArrayOf(3, 4, 5, 6, 7, 8), ring.snapshotOrNull())
        assertEquals(6, ring.availableSamples())
    }

    @Test fun retainsOnlyLatestSamplesWhenAppendExceedsCapacity() {
        val ring = AudioRingBuffer(4)
        ring.append(shortArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9), 9)
        assertArrayEquals(shortArrayOf(6, 7, 8, 9), ring.snapshotOrNull())
    }

    @Test fun snapshotIsIndependentOfLaterRecordingAndCallerChanges() {
        val ring = AudioRingBuffer(3)
        ring.append(shortArrayOf(1, 2, 3), 3)
        val frozen = ring.snapshotOrNull()!!
        ring.append(shortArrayOf(4, 5, 6), 3)
        assertArrayEquals(shortArrayOf(1, 2, 3), frozen)
        frozen[0] = 99
        assertArrayEquals(shortArrayOf(4, 5, 6), ring.snapshotOrNull())
    }

    @Test fun zeroLengthReadDoesNotChangeTheBuffer() {
        val ring = AudioRingBuffer(1)
        ring.append(shortArrayOf(42), 0)
        assertNull(ring.snapshotOrNull())
        assertEquals(0, ring.availableSamples())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidCapacity() { AudioRingBuffer(0) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsReadLengthLargerThanInput() {
        AudioRingBuffer(4).append(shortArrayOf(1), 2)
    }
}
