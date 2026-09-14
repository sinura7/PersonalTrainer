package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayBlockCopyTest {
    private val lifts = listOf("Squat", "Row", "Bench", "Curl", "Fly", "Dip")
    private val upper = listOf(
        "Barbell Bench Press",
        "Pull-Up",
        "Overhead Press",
        "Chest-Supported Dumbbell Row",
        "Lat Pulldown",
        "Skull Crusher",
        "Face Pull",
    )

    @Test
    fun aTypicalSessionNamesEveryLiftAndALongOneStopsAtEight() {
        assertEquals(8, DayBlockCopy.ROW_LIMIT)
        assertEquals(listOf("1 Squat", "2 Row"), DayBlockCopy.names(listOf("Squat", "Row")))
        assertEquals(listOf("1 Squat"), DayBlockCopy.names(listOf("Squat")))
        assertEquals(
            listOf("1 Squat", "2 Row", "3 Bench", "4 Curl", "5 Fly", "6 Dip"),
            DayBlockCopy.names(lifts),
        )
        assertNull(DayBlockCopy.extra(lifts))
        assertEquals(listOf("1 Squat", "+5"), DayBlockCopy.names(lifts, limit = 0))
        assertEquals("+5", DayBlockCopy.extra(lifts, limit = 0))
        assertEquals(emptyList<String>(), DayBlockCopy.names(emptyList()))
        assertNull(DayBlockCopy.extra(emptyList()))
        assertFalse(DayBlockCopy.isExtra("1 Squat"))
        assertTrue(DayBlockCopy.isExtra("+3"))
    }

    @Test
    fun upperANamesAllSevenLiftsAsAListNotAMiddotSentence() {
        assertEquals(
            listOf(
                "1 Barbell Bench Press",
                "2 Pull-Up",
                "3 Overhead Press",
                "4 Chest-Supported Dumbbell Row",
                "5 Lat Pulldown",
                "6 Skull Crusher",
                "7 Face Pull",
            ),
            DayBlockCopy.names(upper),
        )
        assertNull(DayBlockCopy.extra(upper))
        assertTrue(DayBlockCopy.names(upper).none { it.contains(" · ") })
    }

    @Test
    fun nineLiftsPictureEightAndCountTheNinth() {
        val nine = upper + "Band Pull-Apart"
        assertEquals(
            listOf(
                "1 Barbell Bench Press",
                "2 Pull-Up",
                "3 Overhead Press",
                "4 Chest-Supported Dumbbell Row",
                "5 Lat Pulldown",
                "6 Skull Crusher",
                "7 Face Pull",
                "8 Band Pull-Apart",
                "+1",
            ),
            DayBlockCopy.names(nine),
        )
        assertEquals("+1", DayBlockCopy.extra(nine))
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
        assertEquals(listOf("1 Squat", "2 Row"), lines.names)
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
        assertEquals(listOf("1 Squat"), done.names)
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
        assertEquals(listOf("1 Elephant walk", "2 Woodchop"), pack.names)

        val blank = DayBlockCopy.lines(listOf("Squat"), OccurrenceStatus.PLANNED, minutes = 3, caption = " ")
        assertEquals("1 lift · about 3 min", blank.meta)
    }

    @Test
    fun anEmptyPlannedBlockSaysWhatItIsAndASettledOneSaysNothingMore() {
        val strength = DayBlockCopy.lines(emptyList(), OccurrenceStatus.PLANNED)
        assertEquals(emptyList<String>(), strength.names)
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
        assertEquals(emptyList<String>(), focus.names)
        assertNull(focus.meta)
        assertNull(focus.status)
        assertFalse(focus.settled)

        val pinned = DayBlockCopy.preview(listOf("Squat", "Row"), minutes = 13)
        assertEquals(listOf("1 Squat", "2 Row"), pinned.names)
        assertEquals("2 lifts · about 13 min", pinned.meta)
    }
}
