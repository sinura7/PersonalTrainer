package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.WeightMeaning
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W-06 on the redesigned floor: the current exercise's identity carries a still and
 * its working-set progress (the ordinal is spoken there and shown on the header's
 * progress line); switcher rows carry a still, set progress, and a rest badge.
 */
class WorkoutLiftChipTest {
    @Test
    fun liveLiftCardsCarrySetProgressAndARestBadge() {
        val identity = readOwned("ui/workout/ExerciseHeader.kt")
        assertTrue(identity.contains("CurrentLiftCopy.cardSpoken("))
        assertTrue(identity.contains("number = number,"))
        assertTrue(identity.contains("total = total,"))
        assertTrue(identity.contains("WorkoutTestTags.SET_CONTEXT"))
        assertFalse(identity.contains("WorkoutTestTags.liftSets"))
        assertTrue(identity.contains("ExerciseThumb("))
        assertTrue(identity.contains("Metrics.exerciseHeroImage"))
        assertTrue(identity.contains("Metrics.workoutIdentityImage"))
        assertFalse(identity.contains("numeralMd"))
        val spoken = CurrentLiftCopy.cardSpoken(
            name = "Leg Curl",
            number = 2,
            total = 5,
            workingLogged = 1,
            targetSets = 4,
            equipmentLabel = "Machine",
            meaning = WeightMeaning.LIFTED,
        )
        assertTrue("the ordinal is spoken on the identity", spoken.contains(CurrentLiftCopy.heroOrdinal(2, 5)))
        assertTrue(spoken.contains(CurrentLiftCopy.heroProgress(1, 4)))
        val chrome = readOwned("ui/workout/WorkoutHeader.kt")
        assertTrue(
            "the ordinal is shown on the header's progress line",
            chrome.contains("WorkoutProgressCalculator.headline(progress)"),
        )
        assertTrue(chrome.contains("WorkoutTestTags.PROGRESS_LINE"))

        val switcher = readOwned("ui/workout/LiftSwitcherSheet.kt")
        assertTrue(switcher.contains("ExerciseThumb("))
        assertTrue(switcher.contains("ThumbSize.header"))
        assertTrue(switcher.contains("LiftChipCopy.marks("))
        assertTrue(switcher.contains("CurrentLiftCopy.switcherSpoken"))
        assertTrue(switcher.contains("Rest remaining"))
        assertTrue(switcher.contains("WorkoutTestTags.liftRest"))
        assertTrue(switcher.contains("RestCyan"))

        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("ExerciseHeader("))
        assertFalse(screen.contains("CurrentLiftCard("))
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
