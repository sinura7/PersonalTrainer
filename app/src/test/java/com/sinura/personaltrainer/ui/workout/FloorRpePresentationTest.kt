package com.sinura.personaltrainer.ui.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Packet D: RPE always on working drafts, ramp chips, set context — on the redesigned floor,
 * where the track is [RpeSelector] under the hero numerals and the set type is the toggle in
 * [ExerciseHeader].
 *
 * The track itself (five radio chips, the named ends, help, a warm-up's reason) is rendered in
 * RpeSelectorRenderTest, and the screen's wiring (a chip writes the draft, a warm-up hides the
 * track, the coach's effort is recommended but not chosen) is tapped through the ViewModel in
 * FloorRestAndCoachWiringRenderTest. How a chip is drawn is RpeChipDrawingRenderTest (audit
 * T1c-2): the Volt edge means chosen, and a recommended value wears a Volt dot in its top-end
 * corner instead, the non-colour signal ADR-023 asks for, which costs the label no width.
 *
 * The old RPE helper's preference and its ViewModel flag have no reader any more; W2a removes
 * them. Until then the bans below keep it off the screen and out of the backup.
 */
class FloorRpePresentationTest {
    @Test
    fun rpeIsAlwaysOnAWorkingDraftAndHiddenForWarmup() {
        // The draft's set type alone decides; rest state never reaches the track.
        assertFalse(ownedSource("ui/workout/RpeSelector.kt").contains("restRunning"))
        assertEquals("Effort (RPE)", com.sinura.personaltrainer.domain.RpeCopy.HELP_TITLE)
        assertFalse(
            "a recommended chip must not share the selected chip's Volt edge",
            ownedSource("ui/components/InstrumentChip.kt").contains("focused || selected || recommended"),
        )
        // Scoped to the screen: the ViewModel still declares both until W2a removes them.
        val screen = ownedSource("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(screen.contains("rpeHelperVisible"))
        assertFalse(screen.contains("viewModel::dismissRpeHelper"))
    }

    @Test
    fun legacyHelperPreferenceRemainsDeviceLocalForCompatibility() {
        val prefs = ownedSource("data/repository/prefs/SettingsStore.kt")
        assertFalse(prefs.contains("entity") && prefs.contains("rpe_helper"))
        assertFalse(
            "helper dismiss is device-local",
            ownedSource("data/repository/PreferencesRepository.kt").contains("RPE_HELPER_DISMISSED"),
        )
    }
}
