package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.workout.WorkoutDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The question the Log and the rest page both ask the coach (W2b-4).
 *
 * The two ask the same question only because [NextSetInputs] has no defaults: a page that leaves
 * an input out does not compile. A default would let one page drop an input silently, and some
 * inputs cannot be seen on the rest page when dropped. The coach's goal and emphasis change only
 * the call's wording, which the page does not show; a line compared on screen passes without
 * them. So the declaration itself is held here.
 */
class NextSetInputsTest {
    @Test
    fun theSharedQuestionHasNoDefaults() {
        val source = ownedSource("ui/workout/NextSetInputs.kt")
        val start = source.indexOf("internal data class NextSetInputs(")
        assertTrue("NextSetInputs is declared in NextSetInputs.kt", start >= 0)
        val end = source.indexOf(") {", start)
        assertTrue("its parameter list closes", end > start)
        val parameters = source.substring(start, end).lines()
            .map { it.substringBefore("//").trim() }
            .filter { it.startsWith("val ") }
        assertEquals(
            "every input the coach reads, none of them with a default",
            listOf(
                "session", "selectedExerciseId", "draft", "hint", "editingSetId", "wantAnotherSet",
                "historySets", "lighterWeek", "unit", "coachPrefs",
            ),
            parameters.map { it.removePrefix("val ").substringBefore(":").trim() },
        )
        val defaulted = parameters.filter { it.contains("=") }
        assertTrue("no input has a default, found $defaulted", defaulted.isEmpty())
        // However the declaration is laid out: Kotlin compiles any default into a constructor
        // that takes a DefaultConstructorMarker.
        val withDefaults = NextSetInputs::class.java.declaredConstructors.filter { constructor ->
            constructor.parameterTypes.any { it.name == "kotlin.jvm.internal.DefaultConstructorMarker" }
        }
        assertTrue("NextSetInputs compiles with no default, found $withDefaults", withDefaults.isEmpty())
    }

    @Test
    fun aTimedHoldKeepsItsZeroRepsAndAnyOtherLiftHasAtLeastOne() {
        // The Log recovers its entry this way and the rest page reads it this way (W2b-4 review).
        val hold = draft(reps = 0, durationSeconds = 30).entryDraft()
        assertEquals("a hold counts no reps", 0, hold.reps)
        assertEquals(30, hold.durationSeconds)
        assertEquals("a lift counted in reps has at least one", 1, draft(reps = 0, durationSeconds = null).entryDraft().reps)
        val entry = draft(reps = 5, durationSeconds = null, rpe = 8, isWarmup = true).entryDraft()
        assertEquals(ActiveExerciseDraft(weightKg = 100.0, reps = 5, rpe = 8, isWarmup = true, durationSeconds = null), entry)
    }

    private fun draft(reps: Int, durationSeconds: Int?, rpe: Int? = null, isWarmup: Boolean = false) = WorkoutDraft(
        sessionId = "session",
        exerciseId = "lift",
        weightKg = 100.0,
        reps = reps,
        rpe = rpe,
        isWarmup = isWarmup,
        notes = "",
        durationSeconds = durationSeconds,
    )
}
