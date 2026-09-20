package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet D: RPE always on working drafts, ramp chips, set context — on the
 * redesigned floor, where the track is [RpeSelector] under the hero numerals
 * and the set type is the toggle in [ExerciseHeader].
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
        // The draft's set type alone decides; rest state never reaches the track.
        val selector = readOwned("ui/workout/RpeSelector.kt")
        assertTrue(selector.contains("warmup: Boolean"))
        assertTrue(selector.contains("if (warmup) {"))
        assertFalse(selector.contains("restRunning"))
        assertTrue(selector.contains("WorkoutTestTags.RPE_WARMUP_REASON"))
        assertTrue(selector.contains("Effort is recorded for working sets. Warm-ups leave RPE blank."))
        assertTrue(selector.contains("WorkoutTestTags.RPE_TRACK"))
        assertTrue(selector.contains("Kicker(\"RPE\")"))
        assertTrue(selector.contains("RPE help"))
        assertTrue(selector.contains("RpeCopy.HELP_TITLE"))
        assertTrue(selector.contains("RpeCopy.helpBody()"))
        assertEquals("Effort (RPE)", com.sinura.personaltrainer.domain.RpeCopy.HELP_TITLE)
        assertTrue(selector.contains("InstrumentChip("))
        assertTrue(selector.contains("role = Role.RadioButton"))
        assertTrue(selector.contains("selectableGroup()"))
        val chip = readOwned("ui/components/InstrumentChip.kt")
        assertTrue(chip.contains("Modifier.selectable("))
        // A suggestion is never rendered as a selection (ADR-027 §4). The Volt edge means
        // chosen; a recommended value wears a dot instead, which is also the non-colour
        // signal ADR-023 asks for — and, being corner-set, costs the label no width, so
        // five chips still fit across 360 dp.
        assertTrue(chip.contains("if (focused || selected) Volt else Hairline"))
        assertFalse(
            "a recommended chip must not share the selected chip's Volt edge",
            chip.contains("focused || selected || recommended"),
        )
        assertTrue(chip.contains("if (recommended && !selected) {"))
        assertTrue(chip.contains(".size(Metrics.markDot)"))
        assertTrue(chip.contains(".align(Alignment.TopEnd)"))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("RpeSelector("))
        assertTrue(screen.contains("warmup = state.draft.isWarmup"))
        assertTrue(screen.contains("onRpe = viewModel::setRpe"))
        assertTrue(screen.contains("recommendedRpe = microRec?.nextRpe"))
        assertFalse(screen.contains("rpeHelperVisible"))
        assertFalse(screen.contains("viewModel::dismissRpeHelper"))
        // Set context and the warm-up ramp moved with the entry: the header names the set,
        // the ramp presets sit under the numerals, and the ramp still only sets the draft.
        assertTrue(screen.contains("SetOrdinalCopy.draftLine("))
        assertTrue(screen.contains("setContext = setContext"))
        assertTrue(readOwned("ui/workout/ExerciseHeader.kt").contains("WorkoutTestTags.SET_CONTEXT"))
        assertTrue(screen.contains("WarmupRamp.sets("))
        assertTrue(screen.contains("onApplyRamp = viewModel::applyWarmupRamp"))
        val editor = readOwned("ui/workout/WeightRepsEditor.kt")
        assertTrue(editor.contains("fun WarmupRampRow("))
        assertTrue(editor.contains("WorkoutTestTags.WARMUP_RAMP"))
        val logged = readOwned("ui/workout/WorkoutSavedSets.kt")
        assertTrue(logged.contains("Warm-up"))
        assertTrue(logged.contains("Working set"))
        assertTrue(logged.contains("targetSets"))
        val history = readOwned("ui/workout/SetHistoryStrip.kt")
        assertTrue(history.contains("SetOrdinalCopy.loggedLines("))
        assertTrue(history.contains("SetOrdinalCopy.marks("))
    }

    @Test
    fun legacyHelperPreferenceRemainsDeviceLocalForCompatibility() {
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
