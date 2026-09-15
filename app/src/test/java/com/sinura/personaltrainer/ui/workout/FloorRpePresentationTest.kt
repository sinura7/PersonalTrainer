package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet D: RPE always on working drafts, ramp chips, set context.
 */
class FloorRpePresentationTest {
    @Test
    fun rpeIsAlwaysOnAWorkingDraftAndHiddenForWarmup() {
        assertTrue(
            com.sinura.personaltrainer.domain.FloorCompactChrome.showOptionalLogOptions(
                isWarmup = false,
            ),
        )
        assertFalse(
            com.sinura.personaltrainer.domain.FloorCompactChrome.showOptionalLogOptions(
                isWarmup = true,
            ),
        )
        val card = readOwned("ui/workout/WorkoutLiftCard.kt")
        assertTrue(card.contains("showOptionalLogOptions(isWarmup = draftWarmup)"))
        assertFalse(card.contains("showOptionalLogOptions(restRunning)"))
        assertTrue(card.contains("SetOrdinalCopy.draftLine"))
        assertTrue(card.contains("WorkoutTestTags.SET_CONTEXT"))
        assertTrue(card.contains("WarmupRamp.sets"))
        assertTrue(card.contains("onApplyWarmupRamp"))
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertTrue(bar.contains("RpeCopy.LABEL"))
        assertTrue(bar.contains("RpeCopy.HELPER"))
        assertTrue(bar.contains("RpeCopy.WARMUP_REASON"))
        assertTrue(bar.contains("Role.RadioButton"))
        assertTrue(bar.contains("selectableGroup()"))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("rpeHelperVisible"))
        assertTrue(screen.contains("viewModel::applyWarmupRamp"))
        assertTrue(screen.contains("viewModel::dismissRpeHelper"))
        val logged = readOwned("ui/workout/LoggedSetsPanel.kt")
        assertTrue(logged.contains("SetOrdinalCopy.loggedLines"))
        assertTrue(logged.contains("targetSets"))
    }

    @Test
    fun rpeHelperLivesInDatastoreNotRoom() {
        val prefs = readOwned("data/repository/prefs/SettingsStore.kt")
        assertTrue(prefs.contains("rpe_helper_dismissed"))
        assertFalse(prefs.contains("entity") && prefs.contains("rpe_helper"))
        val display = readOwned("data/repository/prefs/DisplayPrefsStore.kt")
        assertTrue(display.contains("rpeHelperDismissed"))
        assertTrue(display.contains("dismissRpeHelper"))
        val restore = readOwned("data/repository/PreferencesRepository.kt")
        assertFalse(
            "helper dismiss is device-local",
            restore.contains("RPE_HELPER_DISMISSED"),
        )
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
