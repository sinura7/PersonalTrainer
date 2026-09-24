package com.sinura.personaltrainer.ui.workout

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * One current lift on the floor, and a session switcher (never the library) to change it;
 * session notes live behind the options, not in the set loop.
 *
 * The visible "Lift n of N" switch, the header overflow's Switch exercise and the switcher's
 * Add exercise are tapped through the screen in FloorScreenWiringRenderTest; what the switch
 * and the identity announce is rendered in ExerciseHeaderRenderTest. Since audit T1c-2 the
 * sheet itself is rendered in LiftSwitcherSheetRenderTest (its "Lifts" title, its rows and
 * "Add exercise" at its foot), and Session notes, from the options and in End workout, is
 * tapped through the screen in SessionNotesRenderTest. The bans stay here.
 */
class LiftSwitcherPresentationTest {
    @Test
    fun theSetLoopMapsNoLiftToACardAndOffersNoAddLift() {
        val screen = ownedSource("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(screen.contains("CurrentLiftCard("))
        assertFalse(screen.contains("itemsIndexed("))
        assertFalse(screen.contains("items = session.exercises"))
        assertFalse(
            "Add a lift must not sit in the set loop once a lift exists",
            sourceBetween(screen, "LazyColumn(", "if (liftSwitcherOpen").contains("SecondaryGymButton"),
        )
        assertFalse(
            "the identity is not a library row",
            ownedSource("ui/workout/ExerciseHeader.kt").contains("ExercisePickerSheet"),
        )
    }

    @Test
    fun notesLeaveTheLogLoop() {
        val screen = ownedSource("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(
            "NotesBlock must not sit in the set loop",
            sourceBetween(screen, "LazyColumn(", "if (liftSwitcherOpen").contains("NotesBlock("),
        )
        val identity = ownedSource("ui/workout/ExerciseHeader.kt")
        assertFalse("notes are overflow, not identity", identity.contains("NotesBlock("))
        assertFalse(identity.contains("SESSION_NOTES"))
    }
}
