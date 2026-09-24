package com.sinura.personaltrainer.ui.workout

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Phone-check 12 Sep 2026: X is go-Home, Finish owns save/discard. Start next is gone from the
 * idle rest card; Log set is the Volt.
 *
 * The bans below guard those findings and stay. The same findings are held as behaviour in
 * FloorRestAndCoachWiringRenderTest: the header's X and system Back leave with the session kept
 * and no popup, Finish opens "End workout?", Start rest runs the ViewModel's clock, and no
 * "Start next" is shown or spoken anywhere on the floor, with the plan met or not.
 * RestTimerCardRenderTest and WorkoutDockTimerRenderTest hold the card's Start rest.
 *
 * `startNextLift` is read in ActiveWorkoutScreen.kt only: W2a removed it from the ViewModel,
 * where it had no production caller; the bans keep it off the screen.
 */
class FloorPhoneCheckPresentationTest {
    @Test
    fun xGoesHomeWithoutTheLeavePopup() {
        val workout = ownedSource("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(workout.contains("LeaveWorkoutDialog("))
        assertFalse(workout.contains("confirmLeave"))
    }

    @Test
    fun idleDockKeepsStartRestWithoutStartNext() {
        val card = ownedSource("ui/workout/RestTimerCard.kt")
        assertFalse(card.contains("onStartNext"))
        assertFalse(
            "idle Start next must not be composed",
            card.contains("RestIdleCopy.START_NEXT"),
        )
        assertFalse(card.contains("START_NEXT"))
        assertFalse(ownedSource("ui/components/RestTimerUi.kt").contains("onStartNext"))
        assertFalse(ownedSource("ui/workout/WorkoutDock.kt").contains("onStartNextLift"))
        val workout = ownedSource("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(workout.contains("onStartNextLift = viewModel::startNextLift"))
        assertFalse(workout.contains("startNextLift"))
    }
}
