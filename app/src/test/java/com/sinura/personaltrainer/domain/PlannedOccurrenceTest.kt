package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlannedOccurrenceTest {

    @Test
    fun prefersTheRoutinePinOverAnotherStrengthRow() {
        val push = item(id = "occ-push", hour = 18, routineId = "r-push", focus = SessionFocusKind.PUSH)
        val pull = item(id = "occ-pull", hour = 19, routineId = "r-pull", focus = SessionFocusKind.PULL)
        val match = PlannedOccurrence.matching(
            day = day(routineId = "r-push", focus = SessionFocusKind.PUSH),
            items = listOf(pull, push),
        )
        assertEquals("occ-push", match?.occurrence?.id)
    }

    @Test
    fun ignoresCardioWhenMatchingAStrengthDay() {
        val cardio = item(
            id = "occ-run",
            hour = 7,
            routineId = null,
            focus = null,
            modality = ScheduleModality.CARDIO,
        )
        val strength = item(id = "occ-push", hour = 18, routineId = "r-push", focus = SessionFocusKind.PUSH)
        val match = PlannedOccurrence.matching(
            day = day(routineId = null, focus = SessionFocusKind.PUSH),
            items = listOf(cardio, strength),
        )
        assertEquals("occ-push", match?.occurrence?.id)
    }

    @Test
    fun restDayMatchesNothing() {
        assertNull(
            PlannedOccurrence.matching(
                day = day(routineId = "r-push", focus = SessionFocusKind.PUSH, rest = true),
                items = listOf(item(id = "occ-push", hour = 18, routineId = "r-push", focus = SessionFocusKind.PUSH)),
            ),
        )
    }

    @Test
    fun twoUnrelatedStrengthRowsDoNotGuess() {
        val first = item(id = "occ-a", hour = 17, routineId = "r-a", focus = SessionFocusKind.PUSH)
        val second = item(id = "occ-b", hour = 19, routineId = "r-b", focus = SessionFocusKind.PULL)
        assertNull(
            PlannedOccurrence.matching(
                day = day(routineId = "r-missing", focus = SessionFocusKind.LEGS),
                items = listOf(first, second),
            ),
        )
    }

    private fun day(
        routineId: String?,
        focus: SessionFocusKind,
        rest: Boolean = false,
    ) = SuggestedTrainingDay(
        epochDay = DAY,
        dayOfWeek = Weekday.TUESDAY,
        isRest = rest,
        focusKind = focus,
        focusTitle = focus.label,
        routineId = routineId,
        routineName = routineId,
        reason = "Planned.",
        emphasisMuscles = emptyList(),
        confidence = ScheduleConfidence.HIGH,
    )

    private fun item(
        id: String,
        hour: Int,
        routineId: String?,
        focus: SessionFocusKind?,
        modality: ScheduleModality = ScheduleModality.STRENGTH,
    ): AgendaItem {
        val rule = ScheduleRule(
            id = "rule-$id",
            weekday = Weekday.TUESDAY,
            hour = hour,
            minute = 0,
            modality = modality,
            focusKind = focus,
            routineId = routineId,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        )
        val occurrence = ScheduleOccurrence(
            id = id,
            ruleId = rule.id,
            status = OccurrenceStatus.PLANNED,
            captured = CapturedCivilTime(
                instantMillis = 1L,
                zoneId = "UTC",
                offsetSeconds = 0,
                localEpochDay = DAY,
            ),
            hour = hour,
            minute = 0,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        )
        return AgendaItem(occurrence, rule)
    }

    private companion object {
        const val DAY = 20_000L
    }
}
