package com.sinura.personaltrainer.domain

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StallSignalGymTest {
    private val now = 1_700_000_000_000L
    private val week = 7L * 24L * 60L * 60L * 1000L
    private val weston = "gym-af-weston"
    private val majorMac = "gym-af-major-mackenzie"
    private val pecDeck = "pec-deck"

    @Test
    fun threeSessionsAtSameGymAndWeightStall() {
        val finding = StallSignal.detect(
            listOf(
                machineSession("s1", weston, now - 3 * week, 45.0),
                machineSession("s2", weston, now - 2 * week, 45.0),
                machineSession("s3", weston, now - week, 45.0),
            ),
        )
        assertNotNull(finding)
    }

    @Test
    fun alternatingGymsAtDifferentWeightsDoesNotStall() {
        assertNull(
            StallSignal.detect(
                listOf(
                    machineSession("s1", weston, now - 3 * week, 45.0),
                    machineSession("s2", majorMac, now - 2 * week, 50.0),
                    machineSession("s3", weston, now - week, 55.0),
                ),
            ),
        )
    }

    private fun machineSession(
        id: String,
        gymId: String,
        at: Long,
        weightKg: Double,
    ): WorkoutSession {
        val lift = Exercise(
            id = pecDeck,
            name = "Pec Deck",
            muscleGroup = "Chest",
            notes = "",
            isCustom = false,
            equipment = EquipmentType.MACHINE,
        )
        return session(
            id = id,
            finishedAt = at,
            date = at,
            gymId = gymId,
            exercises = listOf(
                SessionExercise(
                    id = "se-$id",
                    sessionId = id,
                    exercise = lift,
                    sortOrder = 0,
                    targetSets = 3,
                    targetReps = 10,
                    targetWeightKg = weightKg,
                    restSeconds = 90,
                ),
            ),
            sets = listOf(
                set(
                    id = "$id-set",
                    sessionId = id,
                    exerciseId = pecDeck,
                    name = "Pec Deck",
                    weightKg = weightKg,
                    reps = 10,
                    at = at,
                ),
            ),
        )
    }
}

private fun session(
    id: String,
    finishedAt: Long?,
    date: Long,
    gymId: String?,
    exercises: List<SessionExercise>,
    sets: List<SetLog>,
): WorkoutSession = WorkoutSession(
    id = id,
    routineId = null,
    routineName = "Test",
    date = date,
    notes = "",
    durationMinutes = 30,
    startedAt = date,
    finishedAt = finishedAt,
    exercises = exercises,
    sets = sets,
    gymId = gymId,
)
