package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.FloorCompactChrome
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phone-check 12 Sep 2026: X is go-Home, Finish owns save/discard.
 * Start next is gone from the idle rest card; Log set is the Volt.
 */
class FloorPhoneCheckPresentationTest {
    @Test
    fun xGoesHomeWithoutTheLeavePopup() {
        val workout = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(workout.contains("fun keepAndExit()"))
        assertTrue(workout.contains("BackHandler(enabled = state.session != null) { keepAndExit() }"))
        assertTrue(workout.contains("onExit = { keepAndExit() }"))
        assertTrue(workout.contains("EndWorkoutDialog("))
        assertFalse(workout.contains("LeaveWorkoutDialog("))
        assertFalse(workout.contains("confirmLeave"))
        assertTrue(workout.contains("onFinish = { confirmEnd = true }"))
    }

    @Test
    fun idleDockKeepsStartRestWithoutStartNext() {
        val card = readOwned("ui/workout/RestTimerCard.kt")
        assertFalse(card.contains("onStartNext"))
        assertFalse(
            "idle Start next must not be composed",
            card.contains("RestIdleCopy.START_NEXT"),
        )
        assertFalse(card.contains("START_NEXT"))
        assertTrue(card.contains("RestIdleCopy.startSpoken"))
        assertTrue(card.contains("private const val START_REST = \"Start rest\""))
        assertTrue(card.contains("onClick = onStart,"))
        assertFalse(FloorCompactChrome.showIdleStartNext())
        val legacy = readOwned("ui/components/RestTimerUi.kt")
        assertFalse(legacy.contains("onStartNext"))
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertFalse(dock.contains("onStartNextLift"))
        assertTrue(dock.contains("val onStartRest: () -> Unit"))
        assertTrue(dock.contains("onStart = events.onStartRest"))
        val workout = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(workout.contains("onStartNextLift = viewModel::startNextLift"))
        assertFalse(workout.contains("startNextLift"))
        assertTrue(workout.contains("onStartRest = viewModel::startSelectedRest"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
