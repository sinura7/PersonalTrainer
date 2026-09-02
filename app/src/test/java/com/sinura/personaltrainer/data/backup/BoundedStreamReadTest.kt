package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * [readAtMost] replaced `InputStream.readNBytes`, which is API 33 while this app ships
 * to API 26 — the call did not exist on any device below Android 13 and took the backup
 * import down with `NoSuchMethodError`. Lint named it the first time it ran.
 *
 * The replacement has to hold two properties the callers depend on: it stops exactly at
 * the limit, because they ask for budget + 1 and treat a bigger result as "too big to
 * import"; and it keeps reading across short reads, because a socket that hands back
 * 1 byte at a time must not look like a 1-byte file.
 */
class BoundedStreamReadTest {
    @Test
    fun stopsAtTheLimitWhenTheStreamIsLonger() {
        val source = ByteArray(50) { it.toByte() }

        val read = ByteArrayInputStream(source).readAtMost(10)

        assertEquals(10, read.size)
        assertArrayEquals(source.copyOfRange(0, 10), read)
    }

    @Test
    fun returnsEverythingWhenTheStreamIsShorterThanTheLimit() {
        val source = ByteArray(7) { it.toByte() }

        val read = ByteArrayInputStream(source).readAtMost(4096)

        assertArrayEquals(source, read)
    }

    @Test
    fun assemblesAcrossShortReads() {
        val source = ByteArray(300) { (it % 251).toByte() }

        // The whole reason this is a loop and not one read(): a single read on a stream
        // that dribbles would return 1 byte, and the caller would decide a 300-byte
        // backup was a 1-byte one.
        val read = OneByteAtATime(source).readAtMost(source.size)

        assertArrayEquals(source, read)
    }

    @Test
    fun aStreamThatDribblesStillStopsAtTheLimit() {
        val source = ByteArray(300) { (it % 251).toByte() }

        val read = OneByteAtATime(source).readAtMost(100)

        assertEquals(100, read.size)
        assertArrayEquals(source.copyOfRange(0, 100), read)
    }

    @Test
    fun anEmptyStreamReadsEmpty() {
        assertEquals(0, ByteArrayInputStream(ByteArray(0)).readAtMost(4096).size)
    }

    @Test
    fun aZeroLimitReadsNothingAndConsumesNothing() {
        val stream = ByteArrayInputStream(ByteArray(10) { it.toByte() })

        assertEquals(0, stream.readAtMost(0).size)
        assertEquals(10, stream.available())
    }

    /**
     * The over-budget check the callers actually make: ask for the budget plus one, and
     * a result bigger than the budget means refuse. Pinned here so the +1 is not tidied
     * away by someone who reads it as an off-by-one.
     */
    @Test
    fun theBudgetPlusOneProbeDistinguishesAtBudgetFromOverBudget() {
        val atBudget = ByteArray(64)
        val overBudget = ByteArray(65)

        assertEquals(64, ByteArrayInputStream(atBudget).readAtMost(64 + 1).size)
        assertEquals(65, ByteArrayInputStream(overBudget).readAtMost(64 + 1).size)
    }

    /** Hands back one byte per call, like a socket under load. */
    private class OneByteAtATime(private val source: ByteArray) : InputStream() {
        private var position = 0

        override fun read(): Int =
            if (position >= source.size) -1 else source[position++].toInt() and 0xFF

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (position >= source.size) return -1
            b[off] = source[position++]
            return 1
        }
    }
}
