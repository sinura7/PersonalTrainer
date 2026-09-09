package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.JvmTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompletedTrainingTest {
    @Test
    fun aLiveSessionThatIsNotFinishedIsNotCompletedTraining() {
        val open = session(
            id = "live",
            finishedAt = null,
            date = 1_000L,
            sets = emptyList(),
            exercises = emptyList(),
        )
        assertNull(open.toCompletedTraining(JvmTime, "UTC"))
    }

    @Test
    fun aFinishedWorkoutKeepsItsIdAndWorkingSets() {
        val at = 1_700_000_000_000L
        val finished = session(
            id = "s1",
            finishedAt = at,
            date = at,
            sets = listOf(set("set-1", "s1", "ex-squat", "Squat", 100.0, 5, at = at)),
            exercises = listOf(sessionExercise("ex-squat", "Squat", "Quads")),
        )
        val item = finished.toCompletedTraining(JvmTime, "UTC")!!
        assertEquals("s1", item.id)
        assertEquals(CompletedTraining.Kind.STRENGTH_SESSION, item.kind)
        assertEquals(1, item.strength.size)
        assertEquals(100.0, item.strength.single().set.weightKg, 0.0)
        assertEquals(0L, item.cardioSeconds)
    }
}
