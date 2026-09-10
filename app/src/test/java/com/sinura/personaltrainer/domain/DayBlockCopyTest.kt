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
     * The ink follows whether the block can be started from where it sits,
     * which the caller knows (`DailyAgenda.canOpenStart`): done and moved
     * never can; skipped can today and cannot on an earlier day. The
     * status word is the status either way.
     */
    @Test
    fun theInkFollowsStartabilityAndTheWordFollowsTheStatus() {
        val done = DayBlockCopy.lines(listOf("Squat"), OccurrenceStatus.DONE, startable = false)
        assertEquals("Done", done.status)
        assertTrue(done.settled)
        assertEquals("1 Squat", done.names)
        assertEquals("1 lift", done.meta)

        val moved = DayBlockCopy.lines(listOf("Squat"), OccurrenceStatus.MOVED, startable = false)
        assertEquals("Moved", moved.status)
        assertTrue(moved.settled)

        val skippedToday = DayBlockCopy.lines(listOf("Squat"), OccurrenceStatus.SKIPPED, startable = true)
        assertEquals("Skipped", skippedToday.status)
        assertFalse(skippedToday.settled)

        val skippedEarlier = DayBlockCopy.lines(listOf("Squat"), OccurrenceStatus.SKIPPED, startable = false)
        assertEquals("Skipped", skippedEarlier.status)
        assertTrue(skippedEarlier.settled)

        val missed = DayBlockCopy.lines(listOf("Squat"), OccurrenceStatus.MISSED, startable = true)
        assertEquals("Missed", missed.status)
        assertFalse(missed.settled)
    }

    /** A pack's caption is what the confirm shows instead of a count line, so the block agrees. */
    @Test
    fun aPackCaptionReplacesTheCountAndEstimate() {
        val pack = DayBlockCopy.lines(
            names = listOf("Elephant walk", "Woodchop"),
            status = OccurrenceStatus.PLANNED,
            minutes = 3,
            caption = "About ten minutes. Hips, calves, a walk-out and a brace. After a round.",
        )
        assertEquals("About ten minutes. Hips, calves, a walk-out and a brace. After a round.", pack.meta)
        assertEquals("1 Elephant walk · 2 Woodchop", pack.names)

        val blank = DayBlockCopy.lines(listOf("Squat"), OccurrenceStatus.PLANNED, minutes = 3, caption = " ")
        assertEquals("1 lift · about 3 min", blank.meta)
    }

    @Test
    fun anEmptyPlannedBlockSaysWhatItIsAndASettledOneSaysNothingMore() {
        val strength = DayBlockCopy.lines(emptyList(), OccurrenceStatus.PLANNED)
        assertNull(strength.names)
        assertEquals(SessionOrderCopy.EMPTY_PREVIEW, strength.meta)

        val cardio = DayBlockCopy.lines(emptyList(), OccurrenceStatus.PLANNED, ScheduleModality.CARDIO)
        assertEquals(SessionOrderCopy.READY, cardio.meta)

        val doneCardio = DayBlockCopy.lines(emptyList(), OccurrenceStatus.DONE, ScheduleModality.CARDIO, startable = false)
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
