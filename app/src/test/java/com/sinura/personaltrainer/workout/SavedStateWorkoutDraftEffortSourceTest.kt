package com.sinura.personaltrainer.workout

import androidx.lifecycle.SavedStateHandle
import com.sinura.personaltrainer.domain.EffortSource
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedStateWorkoutDraftEffortSourceTest {
    @Test
    fun missingEffortSourceKeyDecodesFromRpe() {
        val handle = SavedStateHandle()
        val store = SavedStateWorkoutDraft(handle)
        val sessionId = "session-1"
        val exerciseId = "ex-squat"
        store.writeSelection(sessionId = sessionId, exerciseId = exerciseId, notes = "", editingSetId = null)
        store.write(
            WorkoutDraft(
                sessionId = sessionId,
                exerciseId = exerciseId,
                weightKg = 100.0,
                reps = 5,
                rpe = 8,
                isWarmup = false,
                notes = "",
                effortSource = EffortSource.USER,
            ),
        )
        handle.remove<String>(effortSourceKey(exerciseId))
        assertEquals(EffortSource.USER, store.readLift(sessionId, exerciseId)?.effortSource)
    }

    @Test
    fun missingEffortSourceKeyWithNoRpeIsNone() {
        val handle = SavedStateHandle()
        val store = SavedStateWorkoutDraft(handle)
        val sessionId = "session-1"
        val exerciseId = "ex-squat"
        store.writeSelection(sessionId = sessionId, exerciseId = exerciseId, notes = "", editingSetId = null)
        store.write(
            WorkoutDraft(
                sessionId = sessionId,
                exerciseId = exerciseId,
                weightKg = 100.0,
                reps = 5,
                rpe = null,
                isWarmup = false,
                notes = "",
            ),
        )
        handle.remove<String>(effortSourceKey(exerciseId))
        assertEquals(EffortSource.NONE, store.readLift(sessionId, exerciseId)?.effortSource)
    }

    @Test
    fun storedPrefillLastRoundTrips() {
        val handle = SavedStateHandle()
        val store = SavedStateWorkoutDraft(handle)
        val sessionId = "session-1"
        val exerciseId = "ex-squat"
        store.writeSelection(sessionId = sessionId, exerciseId = exerciseId, notes = "", editingSetId = null)
        store.write(
            WorkoutDraft(
                sessionId = sessionId,
                exerciseId = exerciseId,
                weightKg = 100.0,
                reps = 5,
                rpe = 7,
                isWarmup = false,
                notes = "",
                effortSource = EffortSource.PREFILL_LAST,
            ),
        )
        assertEquals(EffortSource.PREFILL_LAST, store.readLift(sessionId, exerciseId)?.effortSource)
    }

    private fun effortSourceKey(exerciseId: String): String = "draft.lift.$exerciseId.effortSource"
}
