package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionOrderCopyTest {
    @Test
    fun numberedPreviewKeepsTapOrderAndEmptyCopy() {
        assertEquals(SessionOrderCopy.EMPTY_PREVIEW, SessionOrderCopy.numberedPreview(emptyList()))
        assertEquals(
            "4 lifts · 1 Squat · 2 Row · 3 Bench",
            SessionOrderCopy.numberedPreview(listOf("Squat", "Row", "Bench", "Curl")),
        )
        assertEquals(
            "1 Squat · 2 Row · 3 Bench",
            SessionOrderCopy.numberedPreview(listOf("Squat", "Row", "Bench")),
        )
        assertEquals("1 Squat", SessionOrderCopy.numberedPreview(listOf("Squat")))
        assertEquals(
            "4 lifts · 1 Squat",
            SessionOrderCopy.numberedPreview(listOf("Squat", "Row", "Bench", "Curl"), limit = 0),
        )
    }

    @Test
    fun occurrenceLinePrefersSessionOrderOverStatus() {
        assertEquals(
            "1 Squat · 2 Row",
            SessionOrderCopy.occurrenceLine(OccurrenceStatus.PLANNED, listOf("Squat", "Row")),
        )
        assertEquals(
            "Done",
            SessionOrderCopy.occurrenceLine(OccurrenceStatus.DONE, emptyList()),
        )
        assertEquals(
            SessionOrderCopy.EMPTY_PREVIEW,
            SessionOrderCopy.occurrenceLine(OccurrenceStatus.PLANNED, emptyList()),
        )
        assertEquals(
            SessionOrderCopy.READY,
            SessionOrderCopy.occurrenceLine(
                OccurrenceStatus.PLANNED,
                emptyList(),
                ScheduleModality.CARDIO,
            ),
        )
        assertEquals(
            SessionOrderCopy.READY,
            SessionOrderCopy.occurrenceLine(
                OccurrenceStatus.PLANNED,
                emptyList(),
                ScheduleModality.MIXED,
            ),
        )
        assertEquals(
            SessionOrderCopy.EMPTY_PREVIEW,
            SessionOrderCopy.occurrenceLine(
                OccurrenceStatus.PLANNED,
                emptyList(),
                ScheduleModality.STRENGTH,
            ),
        )
        assertEquals(SessionOrderCopy.FREE_WORKOUT, "Start a free workout")
        assertEquals(SessionOrderCopy.ADD_EXTRA, "Add extra")
        assertEquals(SessionOrderCopy.NEED_A_LIFT, "Add at least one lift before starting this routine.")
        assertEquals(
            SessionOrderCopy.AGENDA_SEPARATE,
            "Each session stays its own. Finish one, then start the next.",
        )
        assertEquals(
            SessionOrderCopy.LATER_SESSION,
            "Another session this weekday. Pick a routine. Does not replace the others.",
        )
        assertEquals(
            SessionOrderCopy.EMPTY_EDITOR_BODY,
            "Tap lifts in the order you'll do them. Add puts them on this day.",
        )
    }

    @Test
    fun sectionAndLiftIndexNameTheJob() {
        assertEquals("Session · 1 lift", SessionOrderCopy.sectionLabel(1))
        assertEquals("Session · 4 lifts", SessionOrderCopy.sectionLabel(4))
        assertEquals("Mon · 3", SessionOrderCopy.daySection("Mon", 3))
        assertEquals("Lift 2 of 4", SessionOrderCopy.liftIndex(2, 4))
    }

    @Test
    fun cardSpokenNamesEveryField() {
        assertEquals(
            "1. Squat. Quads. 4 by 6. Rest 2:00. 80 kg",
            SessionOrderCopy.cardSpoken(
                number = 1,
                name = "Squat",
                muscleGroup = "Quads",
                sets = 4,
                reps = 6,
                restClock = "2:00",
                load = "80 kg",
            ),
        )
        assertEquals(
            "2. Hang. 3 by 8. Rest 1:30",
            SessionOrderCopy.cardSpoken(
                number = 2,
                name = "Hang",
                muscleGroup = "",
                sets = 3,
                reps = 8,
                restClock = "1:30",
                load = null,
            ),
        )
    }
}
