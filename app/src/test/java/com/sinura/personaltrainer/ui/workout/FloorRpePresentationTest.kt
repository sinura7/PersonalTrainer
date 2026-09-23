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
 *
 * The set context, the warm-up ramp and the saved-set ordinals are rendered in
 * ExerciseHeaderRenderTest, WeightRepsEditorRenderTest, SetHistoryStripRenderTest and
 * WorkoutSetsSheetRenderTest, and tapped through the screen in FloorScreenWiringRenderTest.
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
        // The track itself (five radio chips, the named ends, help, a warm-up's reason) is
        // rendered in RpeSelectorRenderTest, and the screen's wiring (a chip writes the draft,
        // a warm-up hides the track, the coach's effort is recommended but not chosen) is tapped
        // through the ViewModel in FloorRestAndCoachWiringRenderTest.
        // The draft's set type alone decides; rest state never reaches the track.
        val selector = readOwned("ui/workout/RpeSelector.kt")
        assertFalse(selector.contains("restRunning"))
        assertEquals("Effort (RPE)", com.sinura.personaltrainer.domain.RpeCopy.HELP_TITLE)
        // The chip publishes its selected state to TalkBack (ExerciseHeaderRenderTest reads it
        // on the set-type radio).
        val chip = readOwned("ui/components/InstrumentChip.kt")
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
        assertFalse(screen.contains("rpeHelperVisible"))
        assertFalse(screen.contains("viewModel::dismissRpeHelper"))
        // Set context and the warm-up ramp moved with the entry: the header names the set,
        // the ramp presets sit under the numerals, and the ramp still only sets the draft —
        // all tapped through the screen in FloorScreenWiringRenderTest.
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
