package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.WeightMeaning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W-06 on the redesigned floor: the current exercise's identity carries a still and one
 * set-position line from [SetOrdinalCopy] (no duplicate x/y working sets); switcher rows carry
 * a still, set progress, and a rest badge.
 *
 * The identity's visible "Lift n of N" switch and its still are rendered in
 * ExerciseHeaderRenderTest, the header's plan line in LandscapeChromeRenderTest, and the screen
 * composing the identity and the switcher is tapped in FloorScreenWiringRenderTest. Since audit
 * T1c-2 the switcher's rows are rendered in LiftSwitcherSheetRenderTest (their still, their set
 * progress, "Rest remaining" in cyan, and the one sentence each is spoken as) and a lift card's
 * still in LiftCardAndDangerButtonRenderTest. What stays is the copy and the bans.
 */
class WorkoutLiftChipTest {
    @Test
    fun liveLiftCardsCarrySetProgressAndARestBadge() {
        val identity = ownedSource("ui/workout/ExerciseHeader.kt")
        assertFalse(identity.contains("working sets"))
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
        assertTrue(spoken.contains(CurrentLiftCopy.heroOrdinal(2, 5)))
        assertTrue(spoken.contains(CurrentLiftCopy.heroProgress(1, 4)))
        // Since W1a the floor shows the ordinal on its switch rather than speaking it on the
        // identity (ExerciseHeaderRenderTest); the two say the same words.
        assertEquals(CurrentLiftCopy.heroOrdinal(2, 5), CurrentLiftCopy.switchLabel(2, 5))
        val screen = ownedSource("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(screen.contains("CurrentLiftCard("))
        assertFalse(
            "the log loop must not map every session lift to a card",
            screen.contains("itemsIndexed("),
        )
    }
}
