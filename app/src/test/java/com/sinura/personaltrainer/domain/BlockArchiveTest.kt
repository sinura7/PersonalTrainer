package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finished blocks are kept as boundaries, not as summaries.
 *
 * Two numbers each, and every session they contained is still in the database — so a past
 * block's review is rebuilt from those two numbers rather than stored. A stored summary would
 * be a second source of truth about work the database already holds, and it would go stale the
 * moment an old session was edited.
 */
class BlockArchiveTest {
    private fun block(start: Long, weeks: Int = 12) = TrainingBlock(start, weeks)

    @Test
    fun aBlockSurvivesTheRoundTrip() {
        val blocks = listOf(block(20_000), block(20_084))
        assertEquals(blocks, BlockArchive.decode(BlockArchive.encode(blocks)))
    }

    @Test
    fun nothingStoredIsNoBlocks() {
        assertEquals(emptyList<TrainingBlock>(), BlockArchive.decode(null))
        assertEquals(emptyList<TrainingBlock>(), BlockArchive.decode(""))
        assertEquals(emptyList<TrainingBlock>(), BlockArchive.decode("   "))
    }

    @Test
    fun aCorruptEntryIsDroppedRatherThanThrown() {
        // A corrupted archive must not be able to stop the app knowing what week it is on.
        val decoded = BlockArchive.decode("20000:12,rubbish,20084:not-a-number,,20168:12")
        assertEquals(listOf(block(20_000), block(20_168)), decoded)
    }

    @Test
    fun anImpossibleLengthIsNotABlock() {
        assertTrue(BlockArchive.decode("20000:0").isEmpty())
        assertTrue(BlockArchive.decode("20000:900").isEmpty())
    }

    @Test
    fun archivingIsIdempotent() {
        // A double tap, or a restore landing on top of a local archive, must not list it twice.
        val once = BlockArchive.archive(emptyList(), block(20_000))
        val twice = BlockArchive.archive(once, block(20_000))
        assertEquals(1, twice.size)
    }

    @Test
    fun blocksAreKeptOldestFirst() {
        val archived = BlockArchive.archive(listOf(block(20_084)), block(20_000))
        assertEquals(listOf(20_000L, 20_084L), archived.map { it.startEpochDay })
    }

    @Test
    fun theArchiveIsBoundedAndDropsTheOldest() {
        var archive = emptyList<TrainingBlock>()
        repeat(TrainingBlock.MAX_KEPT + 5) { n ->
            archive = BlockArchive.archive(archive, block(20_000L + n * 84L))
        }
        assertEquals(TrainingBlock.MAX_KEPT, archive.size)
        // The five oldest are the ones gone.
        assertEquals(20_000L + 5 * 84L, archive.first().startEpochDay)
    }

    @Test
    fun encodingAlsoRespectsTheCap() {
        val many = (0 until TrainingBlock.MAX_KEPT + 4).map { block(20_000L + it * 84L) }
        assertEquals(TrainingBlock.MAX_KEPT, BlockArchive.decode(BlockArchive.encode(many)).size)
    }
}
