package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordsCalculatorTest {
    private val now = 1_700_000_000_000L

    @Test
    fun standingMatchesTheLifetimeBestNotALaterWeakerSet() {
        val sets = listOf(
            loaded("old", "s-old", "ex-squat", "Squat", 140.0, 5, now - 90 * DAY),
            loaded("new", "s-new", "ex-squat", "Squat", 100.0, 5, now - DAY),
        )
        val rows = RecordsCalculator.standing(sets)
        val squat = rows.single()
        assertEquals(140.0, squat.valueKg, 1e-9)
        assertEquals(now - 90 * DAY, squat.achievedAt)
        assertEquals(standingRecords(sets), rows)
    }

    @Test
    fun aWeakerLaterSetDoesNotCountAsBroken() {
        val prior = listOf(loaded("a", "s1", "ex-squat", "Squat", 110.0, 5, now - DAY))
        val later = listOf(loaded("b", "s2", "ex-squat", "Squat", 90.0, 5, now))
        assertEquals(0, RecordsCalculator.countBroken(inRange = later, before = prior))
        assertTrue(
            RecordsCalculator.detect(
                candidate = later.single().set,
                priorHistory = prior.map { it.set },
                loadClass = LoadClass.LOADED,
            ).isEmpty(),
        )
    }

    @Test
    fun aStrongerLaterSetCountsOnceAgainstThePrior() {
        val prior = listOf(loaded("a", "s1", "ex-squat", "Squat", 100.0, 5, now - DAY))
        val later = listOf(loaded("b", "s2", "ex-squat", "Squat", 110.0, 5, now))
        assertTrue(
            RecordsCalculator.countBroken(inRange = later, before = prior) > 0,
        )
        assertTrue(
            RecordsCalculator.detect(
                candidate = later.single().set,
                priorHistory = prior.map { it.set },
                loadClass = LoadClass.LOADED,
            ).isNotEmpty(),
        )
    }

    @Test
    fun theFirstSetAgainstAnEmptyPriorIsABaselineNotABrokenRecord() {
        val first = listOf(loaded("a", "s1", "ex-squat", "Squat", 100.0, 5, now))
        assertEquals(0, RecordsCalculator.countBroken(inRange = first, before = emptyList()))
    }

    @Test
    fun countingOverBlocksMatchesTheSameSets() {
        val prior = listOf(loaded("a", "s1", "ex-squat", "Squat", 100.0, 5, now - DAY))
        val later = listOf(loaded("b", "s2", "ex-squat", "Squat", 110.0, 5, now))
        val before = listOf(training("before", now - DAY, prior))
        val inBlock = listOf(training("block", now, later))
        assertEquals(
            RecordsCalculator.countBroken(inRange = later, before = prior),
            RecordsCalculator.countBrokenTraining(inBlock = inBlock, beforeBlock = before),
        )
    }

    private fun loaded(
        setId: String,
        sessionId: String,
        exerciseId: String,
        name: String,
        weightKg: Double,
        reps: Int,
        at: Long,
    ): RecordSet = RecordSet(
        exerciseId = exerciseId,
        exerciseName = name,
        loadClass = LoadClass.LOADED,
        set = ExerciseSetRecord(
            setId = setId,
            sessionId = sessionId,
            weightKg = weightKg,
            reps = reps,
            completedAt = at,
        ),
    )

    private fun training(
        id: String,
        at: Long,
        sets: List<RecordSet>,
    ): CompletedTraining = CompletedTraining(
        id = id,
        kind = CompletedTraining.Kind.STRENGTH_SESSION,
        title = id,
        performedAtMs = at,
        localEpochDay = at / DAY,
        finishedAtMs = at,
        summary = SessionSummary(
            id = id,
            routineId = null,
            routineName = id,
            date = at,
            finishedAt = at,
            durationMinutes = 40,
            workingSets = sets.size,
            volumeKg = sets.sumOf { it.set.weightKg * it.set.reps },
            localEpochDay = at / DAY,
        ),
        strength = sets,
        cardioSeconds = 0L,
        cardioDistanceMeters = null,
    )

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
