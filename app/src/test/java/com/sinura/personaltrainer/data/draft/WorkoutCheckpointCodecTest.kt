package com.sinura.personaltrainer.data.draft

import com.sinura.personaltrainer.domain.EffortSource
import com.sinura.personaltrainer.workout.WorkoutDraft
import com.sinura.personaltrainer.workout.WorkoutEntryCheckpoint
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutCheckpointCodecTest {
    @Test
    fun missingEffortSourceDecodesAsUserWhenRpePresent() {
        val encoded = WorkoutCheckpointCodec.encodeDraft(entry(rpe = 8, effortSource = EffortSource.USER))
        val lift = encoded.asJsonObject.getAsJsonArray("lifts").get(0).asJsonObject
        lift.remove("effortSource")
        val decoded = WorkoutCheckpointCodec.decodeDraft(encoded)
        assertEquals(EffortSource.USER, decoded.lifts.values.single().effortSource)
    }

    @Test
    fun missingEffortSourceDecodesAsNoneWhenRpeAbsent() {
        val encoded = WorkoutCheckpointCodec.encodeDraft(entry(rpe = null, effortSource = EffortSource.NONE))
        val lift = encoded.asJsonObject.getAsJsonArray("lifts").get(0).asJsonObject
        lift.remove("effortSource")
        val decoded = WorkoutCheckpointCodec.decodeDraft(encoded)
        assertEquals(EffortSource.NONE, decoded.lifts.values.single().effortSource)
    }

    @Test
    fun storedPrefillLastRoundTrips() {
        val encoded = WorkoutCheckpointCodec.encodeDraft(entry(rpe = 7, effortSource = EffortSource.PREFILL_LAST))
        val decoded = WorkoutCheckpointCodec.decodeDraft(encoded)
        assertEquals(EffortSource.PREFILL_LAST, decoded.lifts.values.single().effortSource)
        assertEquals(7, decoded.lifts.values.single().rpe)
    }

    private fun entry(rpe: Int?, effortSource: EffortSource) = WorkoutEntryCheckpoint(
        sessionId = "session-1",
        startedAt = 1L,
        authoredRevision = 0L,
        lifts = mapOf(
            "ex-squat" to WorkoutDraft(
                sessionId = "session-1",
                exerciseId = "ex-squat",
                weightKg = 100.0,
                reps = 5,
                rpe = rpe,
                isWarmup = false,
                notes = "",
                effortSource = effortSource,
            ),
        ),
        selectedExerciseId = "ex-squat",
        notes = null,
        notesBase = null,
        editingOriginal = null,
    )
}
