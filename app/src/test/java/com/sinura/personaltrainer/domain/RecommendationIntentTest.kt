package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationIntentTest {
    @Test
    fun eachActionMapsToATypedIntent() {
        assertEquals(
            RecommendationIntent.OpenLibrary(CanonicalMuscle.BACK),
            RecommendationIntents.from(rec(RecommendationAction.OPEN_LIBRARY_MUSCLE, muscle = CanonicalMuscle.BACK)),
        )
        assertEquals(
            RecommendationIntent.OpenExercise("ex-1"),
            RecommendationIntents.from(
                rec(RecommendationAction.OPEN_EXERCISE, exerciseId = "ex-1", exerciseName = "Squat"),
            ),
        )
        assertEquals(
            RecommendationIntent.OpenBodyMap,
            RecommendationIntents.from(rec(RecommendationAction.OPEN_EXERCISE, muscle = CanonicalMuscle.CHEST)),
        )
        assertEquals(
            RecommendationIntent.StartWorkout,
            RecommendationIntents.from(rec(RecommendationAction.START_WORKOUT)),
        )
        assertEquals(
            RecommendationIntent.OpenRoutines,
            RecommendationIntents.from(rec(RecommendationAction.OPEN_ROUTINES)),
        )
        assertEquals(
            RecommendationIntent.OpenBodyMap,
            RecommendationIntents.from(rec(RecommendationAction.OPEN_BODY_MAP, muscle = CanonicalMuscle.CHEST)),
        )
        assertEquals(
            RecommendationIntent.MarkLighterWeek,
            RecommendationIntents.from(rec(RecommendationAction.MARK_LIGHTER_WEEK)),
        )
    }

    @Test
    fun noDestinationEmitsNoIntentAndTheLabelStaysHonest() {
        val idle = rec(action = null, muscle = null)
        assertNull(RecommendationIntents.from(idle))
        assertEquals("Show on the map", RecommendationIntents.actionLabel(idle))
        val named = rec(RecommendationAction.OPEN_EXERCISE, exerciseId = "ex-1", exerciseName = "Squat")
        assertTrue(RecommendationIntents.actionLabel(named).contains("Squat"))
        val muscle = rec(RecommendationAction.OPEN_LIBRARY_MUSCLE, muscle = CanonicalMuscle.BACK)
        assertTrue(RecommendationIntents.actionLabel(muscle).contains("back"))
        assertEquals("Start a workout", RecommendationIntents.actionLabel(rec(RecommendationAction.START_WORKOUT)))
        assertEquals("Open routines", RecommendationIntents.actionLabel(rec(RecommendationAction.OPEN_ROUTINES)))
        assertEquals(
            "Mark this week lighter",
            RecommendationIntents.actionLabel(rec(RecommendationAction.MARK_LIGHTER_WEEK)),
        )
    }

    private fun rec(
        action: RecommendationAction?,
        muscle: CanonicalMuscle? = null,
        exerciseId: String? = null,
        exerciseName: String? = null,
    ) = TrainingRecommendation(
        id = "rec-${action?.name ?: "none"}",
        kicker = RecommendationEngine.KICKER_BALANCE,
        title = "Title",
        reason = "reason",
        priority = RecommendationPriority.INFO,
        action = action,
        actionMuscle = muscle,
        actionExerciseId = exerciseId,
        actionExerciseName = exerciseName,
        rankScore = 1,
    )
}
