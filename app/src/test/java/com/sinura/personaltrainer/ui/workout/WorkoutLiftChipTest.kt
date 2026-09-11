package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W-06: live lift chips show a still, set progress, and a rest badge.
 */
class WorkoutLiftChipTest {
    @Test
    fun liveLiftCardsCarrySetProgressAndARestBadge() {
        val card = readOwned("ui/workout/WorkoutLiftCard.kt")
        assertTrue(card.contains("LiftCard("))
        assertTrue(card.contains("LiftChipCopy.marks("))
        assertTrue(card.contains("LiftChipCopy.spoken("))
        assertTrue(card.contains("LiftChipCopy.REST"))
        assertTrue(card.contains("WorkoutTestTags.liftSets"))
        assertTrue(card.contains("WorkoutTestTags.liftRest"))
        assertTrue(card.contains("RestCyan"))
        assertFalse(card.contains("numeralMd"))
        assertFalse(
            card.contains("if (targetSets > 0) \"\$workingLogged/\$targetSets\""),
        )

        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("restRunning ="))
        assertTrue(screen.contains("restRemainingSeconds ="))
        assertTrue(screen.contains("fun liftSets"))
        assertTrue(screen.contains("fun liftRest"))

        val liftCard = readOwned("ui/components/LiftCard.kt")
        assertTrue(liftCard.contains("ExerciseThumb("))
        assertTrue(liftCard.contains("ThumbSize.header"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
