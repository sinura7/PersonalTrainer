package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Existing equal-stamp behavior, retained for a separately specified calculation decision. */
class RecordsTieCharacterizationTest {
    private val at = 1_700_000_000_000L

    @Test
    fun equalStampSetsRetainInputOrderRatherThanInventingAnOrder() {
        val lower = loaded("lower", "same-session", 100.0)
        val higher = loaded("higher", "same-session", 110.0)
        assertEquals(2, RecordsCalculator.countBroken(listOf(lower, higher), emptyList()))
        assertEquals(0, RecordsCalculator.countBroken(listOf(higher, lower), emptyList()))
    }

    @Test
    fun equalStampMixedSourcesRetainTheSameKnownOrderDependency() {
        val workout = training("same-id", CompletedTraining.Kind.STRENGTH_SESSION, loaded("workout-set", "same-id", 100.0))
        val activity = training("same-id", CompletedTraining.Kind.ACTIVITY, loaded("activity-set", "same-id", 110.0))
        assertEquals(2, RecordsCalculator.countBrokenTraining(listOf(workout, activity), emptyList()))
        assertEquals(0, RecordsCalculator.countBrokenTraining(listOf(activity, workout), emptyList()))
    }

    private fun loaded(id: String, sessionId: String, weight: Double) = RecordSet(
        exerciseId = "ex-squat", exerciseName = "Squat", loadClass = LoadClass.LOADED,
        set = ExerciseSetRecord(setId = id, sessionId = sessionId, weightKg = weight, reps = 5, completedAt = at),
    )

    private fun training(id: String, kind: CompletedTraining.Kind, row: RecordSet) = CompletedTraining(
        id = id, kind = kind, title = id, performedAtMs = at, localEpochDay = at / 86_400_000L,
        finishedAtMs = at, summary = SessionSummary(
            id = id, routineId = null, routineName = id, date = at, finishedAt = at,
            durationMinutes = 40, workingSets = 1, volumeKg = row.set.weightKg * row.set.reps,
            localEpochDay = at / 86_400_000L,
            kind = if (kind == CompletedTraining.Kind.ACTIVITY) HistoryKind.ACTIVITY else HistoryKind.WORKOUT,
        ), strength = listOf(row), cardioSeconds = 0L, cardioDistanceMeters = null,
    )
}
