package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayBlockCopyTest {
    private val lifts = listOf("Squat", "Row", "Bench", "Curl", "Fly", "Dip")

    @Test
    fun orderNamesOnlyTheLiftsWithAStillAndCountsTheRest() {
        assertEquals("1 Squat · 2 Row", DayBlockCopy.names(listOf("Squat", "Row")))
        assertEquals("1 Squat", DayBlockCopy.names(listOf("Squat")))
        assertEquals("1 Squat · 2 Row · 3 Bench · 4 Curl · +2", DayBlockCopy.names(lifts))
        assertEquals("1 Squat · +5", DayBlockCopy.names(lifts, limit = 0))
        assertNull(DayBlockCopy.names(emptyList()))
    }

    @Test
    fun metaCountsLiftsAndAddsTheEstimateOnlyWhenThereIsOne() {
        assertEquals("1 lift", DayBlockCopy.meta(1, null))
        assertEquals("2 lifts", DayBlockCopy.meta(2, null))
        assertEquals("2 lifts · about 13 min", DayBlockCopy.meta(2, 13))
    }

    @Test
    fun aPlannedSessionHasNoStatusWordAndFullInk() {
        val lines = DayBlockCopy.lines(listOf("Squat", "Row"), OccurrenceStatus.PLANNED, minutes = 13)
        assertEquals("1 Squat · 2 Row", lines.names)
        assertEquals("2 lifts · about 13 min", lines.meta)
        assertNull(lines.status)
        assertFalse(lines.settled)
    }

    /**
     * Done and moved cannot be started from the block, so they go quiet.
     * Skipped and missed still can (`DailyAgenda.canOpenStart`), so they
     * keep their ink and say why the foot reads differently.
     */
    @Test
    fun onlyDoneAndMovedSettleTheBlock() {
        val done = DayBlockCopy.lines(listOf("Squat"), OccurrenceStatus.DONE)
        assertEquals("Done", done.status)
        assertTrue(done.settled)
        assertEquals("1 Squat", done.names)
        assertEquals("1 lift", done.meta)

        val moved = DayBlockCopy.lines(listOf("Squat"), OccurrenceStatus.MOVED)
        assertEquals("Moved", moved.status)
        assertTrue(moved.settled)

        val skipped = DayBlockCopy.lines(listOf("Squat"), OccurrenceStatus.SKIPPED)
        assertEquals("Skipped", skipped.status)
        assertFalse(skipped.settled)

        val missed = DayBlockCopy.lines(listOf("Squat"), OccurrenceStatus.MISSED)
        assertEquals("Missed", missed.status)
        assertFalse(missed.settled)
    }

    @Test
    fun anEmptyPlannedBlockSaysWhatItIsAndASettledOneSaysNothingMore() {
        val strength = DayBlockCopy.lines(emptyList(), OccurrenceStatus.PLANNED)
        assertNull(strength.names)
        assertEquals(SessionOrderCopy.EMPTY_PREVIEW, strength.meta)

        val cardio = DayBlockCopy.lines(emptyList(), OccurrenceStatus.PLANNED, ScheduleModality.CARDIO)
        assertEquals(SessionOrderCopy.READY, cardio.meta)

        val doneCardio = DayBlockCopy.lines(emptyList(), OccurrenceStatus.DONE, ScheduleModality.CARDIO)
        assertNull(doneCardio.meta)
        assertEquals("Done", doneCardio.status)
    }

    @Test
    fun theLeftoverPreviewIsNeverSettledAndAFocusWithoutARoutineSaysNothing() {
        val focus = DayBlockCopy.preview(emptyList())
        assertNull(focus.names)
        assertNull(focus.meta)
        assertNull(focus.status)
        assertFalse(focus.settled)

        val pinned = DayBlockCopy.preview(listOf("Squat", "Row"), minutes = 13)
        assertEquals("1 Squat · 2 Row", pinned.names)
        assertEquals("2 lifts · about 13 min", pinned.meta)
    }
}
