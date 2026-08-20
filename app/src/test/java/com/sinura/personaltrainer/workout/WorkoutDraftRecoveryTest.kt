package com.sinura.personaltrainer.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rules that decide what a rebuilt Active Workout screen restores. The process-death case
 * is the one that matters on the gym floor: the phone sits in a pocket through a rest, the OS
 * reclaims the app, and the lifter must come back to the numbers they dialed in.
 */
class WorkoutDraftRecoveryTest {
    private val sessionId = "session-1"

    private fun draft(
        session: String = sessionId,
        exerciseId: String? = "ex-squat",
        weightKg: Double = 100.0,
        reps: Int = 5,
        rpe: Int? = 8,
        isWarmup: Boolean = false,
        notes: String = "felt good",
    ) = WorkoutDraft(session, exerciseId, weightKg, reps, rpe, isWarmup, notes)

    @Test
    fun persistedDraftIsUsedWhenTheProcessDied() {
        // The in-memory cache is empty after a process kill; saved state is all that is left.
        val restored = WorkoutDraftRecovery.resolve(
            sessionId = sessionId,
            inMemory = null,
            persisted = draft(weightKg = 102.5, reps = 3),
        )
        assertEquals(102.5, restored!!.weightKg, 0.001)
        assertEquals(3, restored.reps)
        assertEquals("ex-squat", restored.exerciseId)
        assertEquals("felt good", restored.notes)
    }

    @Test
    fun inMemoryWinsWhileTheProcessIsAlive() {
        val restored = WorkoutDraftRecovery.resolve(
            sessionId = sessionId,
            inMemory = draft(weightKg = 110.0),
            persisted = draft(weightKg = 100.0),
        )
        assertEquals(110.0, restored!!.weightKg, 0.001)
    }

    @Test
    fun aDraftFromAnotherSessionNeverLeaksIn() {
        assertNull(
            WorkoutDraftRecovery.resolve(
                sessionId = sessionId,
                inMemory = draft(session = "other-session"),
                persisted = null,
            ),
        )
        assertNull(
            WorkoutDraftRecovery.resolve(
                sessionId = sessionId,
                inMemory = null,
                persisted = draft(session = "other-session"),
            ),
        )
    }

    @Test
    fun aStaleInMemoryDraftFallsThroughToTheMatchingPersistedOne() {
        val restored = WorkoutDraftRecovery.resolve(
            sessionId = sessionId,
            inMemory = draft(session = "other-session", weightKg = 60.0),
            persisted = draft(weightKg = 100.0),
        )
        assertEquals(100.0, restored!!.weightKg, 0.001)
    }

    @Test
    fun nothingToRestoreYieldsNull() {
        assertNull(WorkoutDraftRecovery.resolve(sessionId, null, null))
        assertNull(WorkoutDraftRecovery.resolve("", draft(), draft()))
    }

    @Test
    fun outOfRangeValuesAreRepairedNotTrusted() {
        // A hand-edited saved state, or a field whose meaning changed between versions,
        // must not produce an unloggable screen.
        val restored = WorkoutDraftRecovery.resolve(
            sessionId = sessionId,
            inMemory = null,
            persisted = draft(weightKg = -5.0, reps = 0, rpe = 42),
        )!!
        assertEquals(0.0, restored.weightKg, 0.001)
        assertEquals(1, restored.reps)
        assertNull(restored.rpe)
    }

    @Test
    fun nonFiniteWeightsAreRepaired() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { bad ->
            val restored = WorkoutDraftRecovery.resolve(sessionId, null, draft(weightKg = bad))!!
            assertEquals("weight $bad survived", 0.0, restored.weightKg, 0.001)
        }
    }

    @Test
    fun validValuesArePreservedExactly() {
        val original = draft(weightKg = 87.5, reps = 12, rpe = 10, isWarmup = true)
        assertEquals(original, WorkoutDraftRecovery.resolve(sessionId, original, null))
    }

    @Test
    fun anAbsurdRepCountIsClampedRatherThanKept() {
        val restored = WorkoutDraftRecovery.resolve(sessionId, null, draft(reps = 100_000))!!
        assertEquals(500, restored.reps)
    }
}
