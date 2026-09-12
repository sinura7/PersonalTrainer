package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phone-check 12 Sep 2026: X is go-Home, Finish owns save/discard,
 * Start next is rest-dock primary.
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
    fun idleDockSplitsStartNextFromStartRest() {
        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("onStartNext"))
        assertTrue(dock.contains("RestIdleCopy.START_NEXT"))
        assertTrue(dock.contains("RestIdleCopy.START"))
        val workout = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(workout.contains("onStartNext = viewModel::startNextLift"))
        assertTrue(workout.contains("onStart = viewModel::startSelectedRest"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
