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
        val card = readOwned("ui/workout/CurrentLiftCard.kt")
        assertTrue(card.contains("CurrentLiftCopy.heroOrdinal") || card.contains("CurrentLiftCopy.liftOrdinal"))
        assertTrue(card.contains("CurrentLiftCopy.heroProgress") || card.contains("CurrentLiftCopy.workingProgress"))
        assertTrue(card.contains("WorkoutTestTags.liftSets"))
        assertTrue(card.contains("ThumbSize.hero"))
        assertFalse(card.contains("numeralMd"))

        val switcher = readOwned("ui/workout/LiftSwitcherSheet.kt")
        assertTrue(switcher.contains("LiftChipCopy.marks("))
        assertTrue(switcher.contains("CurrentLiftCopy.switcherSpoken"))
        assertTrue(switcher.contains("LiftChipCopy.REST"))
        assertTrue(switcher.contains("WorkoutTestTags.liftRest"))
        assertTrue(switcher.contains("RestCyan"))

        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("CurrentLiftCard("))
        assertTrue(screen.contains("LiftSwitcherSheet("))
        assertTrue(screen.contains("fun liftSets"))
        assertTrue(screen.contains("fun liftRest"))
        assertFalse(
            "the log loop must not map every session lift to a card",
            screen.contains("itemsIndexed("),
        )

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
