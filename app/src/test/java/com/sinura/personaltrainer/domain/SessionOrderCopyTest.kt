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

    /**
     * The order used to win outright, so DONE, SKIPPED and MOVED rows printed
     * the same subtitle as a live planned one and the status words below were
     * unreachable for any session that had lifts. On Home that made a finished
     * day indistinguishable from a waiting one, with no explanation for why the
     * row would not open.
     */
    @Test
    fun settledStatusIsNamedInFrontOfTheOrder() {
        val lifts = listOf("Squat", "Row")
        assertEquals(
            "Done · 1 Squat · 2 Row",
            SessionOrderCopy.occurrenceLine(OccurrenceStatus.DONE, lifts),
        )
        assertEquals(
            "Skipped · 1 Squat · 2 Row",
            SessionOrderCopy.occurrenceLine(OccurrenceStatus.SKIPPED, lifts),
        )
        assertEquals(
            "Moved · 1 Squat · 2 Row",
            SessionOrderCopy.occurrenceLine(OccurrenceStatus.MOVED, lifts),
        )
        // Startable states carry no prefix: the row opens, so it needs no excuse.
        assertEquals(
            "1 Squat · 2 Row",
            SessionOrderCopy.occurrenceLine(OccurrenceStatus.PLANNED, lifts),
        )
        assertEquals(
            "1 Squat · 2 Row",
            SessionOrderCopy.occurrenceLine(OccurrenceStatus.MISSED, lifts),
        )
        assertEquals("Done", SessionOrderCopy.settledLabel(OccurrenceStatus.DONE))
        assertEquals(null, SessionOrderCopy.settledLabel(OccurrenceStatus.PLANNED))
        assertEquals(null, SessionOrderCopy.settledLabel(OccurrenceStatus.MISSED))
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
        assertEquals(SessionOrderCopy.FREE_WORKOUT, "Start a workout")
        assertEquals(SessionOrderCopy.START_ROW, "Start")
        assertEquals(SessionOrderCopy.SAVE_ROUTINE, "Save")
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
            "Tap lifts in the order you'll do them. Each tap adds one to this routine.",
        )
        // The picker writes as it goes, and the hint is where the owner is told so.
        assertEquals(
            SessionOrderCopy.PICKER_HINT,
            "Tap in the order you'll lift. 1 is first, and each tap is saved.",
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

    @Test
    fun filledSpokenSeparatesRecordedWorkingSetsFromPlannedTargets() {
        assertEquals("3/3", SessionOrderCopy.filledCount(3, 3))
        assertEquals("4", SessionOrderCopy.filledCount(4, 0))
        assertEquals("3 × 5", SessionOrderCopy.workValue(3, 5))
        assertEquals(
            "1. Squat. Quads. Recorded: 1 working set. Planned: Work 3 × 5. Rest 1:30. Load 100 kg",
            SessionOrderCopy.filledSpoken(
                number = 1,
                name = "Squat",
                muscleGroup = "Quads",
                workingLogged = 1,
                targetSets = 3,
                targetReps = 5,
                restClock = "1:30",
                load = "100 kg",
            ),
        )
        assertEquals(
            "2. Hang. Recorded: 4 working sets",
            SessionOrderCopy.filledSpoken(
                number = 2,
                name = "Hang",
                muscleGroup = "",
                workingLogged = 4,
                targetSets = 0,
                targetReps = 0,
                restClock = null,
                load = null,
            ),
        )
        assertEquals(
            "3. Curl. Recorded: 1 working set",
            SessionOrderCopy.filledSpoken(
                number = 3,
                name = "Curl",
                muscleGroup = "",
                workingLogged = 1,
                targetSets = 0,
                targetReps = 0,
                restClock = null,
                load = null,
            ),
        )
    }

    @Test
    fun recordedZeroDoesNotClaimAnUntouchedPrescriptionWasPerformed() {
        assertEquals(
            "1. Squat. Recorded: 0 working sets. Planned: Work 3 × 5. Rest 2:00. Load 140 kg",
            SessionOrderCopy.filledSpoken(
                number = 1, name = "Squat", muscleGroup = "", workingLogged = 0,
                targetSets = 3, targetReps = 5, restClock = "2:00", load = "140 kg",
            ),
        )
    }

    @Test
    fun filledHoldTargetsUseTheirSecondsAndRangeInsteadOfPlaceholderReps() {
        assertEquals("3 × 30–45s", SessionOrderCopy.workValue(3, 1, 30, 45))
        assertEquals("3 × 30s", SessionOrderCopy.workValue(3, 1, 30))
        assertEquals(
            "2. Dead Hang. Recorded: 1 working set. Planned: Work 3 × 30–45s. Rest 1:00",
            SessionOrderCopy.filledSpoken(
                number = 2, name = "Dead Hang", muscleGroup = "", workingLogged = 1,
                targetSets = 3, targetReps = 1, restClock = "1:00", load = null,
                holdSeconds = 30, holdSecondsMax = 45,
            ),
        )
    }

    @Test
    fun partialPrescriptionStillQualifiesItsRestAndLoadWithoutInventingWork() {
        assertEquals(
            "1. Curl. Recorded: 2 working sets. Planned: Rest 1:00. Load 10 kg",
            SessionOrderCopy.filledSpoken(
                number = 1, name = "Curl", muscleGroup = "", workingLogged = 2,
                targetSets = 0, targetReps = 0, restClock = "1:00", load = "10 kg",
            ),
        )
    }

    @Test
    fun repeatedPrescriptionsSpeakOriginalPositionsAndTargetsWithRecordedCountOnce() {
        val planned = listOf(
            SessionOrderCopy.filledPrescriptionSpoken(1, 3, 5, "1:30", "100 kg"),
            SessionOrderCopy.filledPrescriptionSpoken(3, 2, 8, "2:00", "80 kg"),
        )
        assertEquals(
            "1. Squat. Quads. Recorded: 1 working set. Planned 1: Work 3 × 5. Rest 1:30. Load 100 kg" +
                ". Planned 3: Work 2 × 8. Rest 2:00. Load 80 kg",
            SessionOrderCopy.filledGroupSpoken(1, "Squat", "Quads", 1, planned),
        )
        // No single target or summed target is used as a recorded completion denominator.
        assertEquals("1", SessionOrderCopy.filledCount(1, 0))
    }

    @Test
    fun repeatedHoldAndEmptyTargetsAreNotReplacedWithAnotherPrescription() {
        assertEquals(
            "Planned 3: Work 2 × 20–30s. Rest 0:45. Load 0 kg",
            SessionOrderCopy.filledPrescriptionSpoken(3, 2, 1, "0:45", "0 kg", 20, 30),
        )
        val empty = SessionOrderCopy.filledPrescriptionSpoken(4, 0, 0, "0:00", null)
        assertEquals("Planned 4: Work 0 × 0. Rest 0:00", empty)
        assertEquals(
            "2. Hold. Recorded: 0 working sets. Planned 4: Work 0 × 0. Rest 0:00",
            SessionOrderCopy.filledGroupSpoken(2, "Hold", "", 0, listOf(empty)),
        )
    }
}
