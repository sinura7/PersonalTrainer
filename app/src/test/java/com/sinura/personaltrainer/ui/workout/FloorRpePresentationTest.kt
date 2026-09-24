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
 * W2a removed the old RPE helper's preference and its ViewModel flag, which nothing read. The
 * bans below keep a helper nag off the screen and its flag out of the settings that sync.
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
        // W2a removed both from the ViewModel (and the stored flag behind them); the screen
        // must not wire a helper nag back in.
        val screen = ownedSource("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(screen.contains("rpeHelperVisible"))
        assertFalse(screen.contains("viewModel::dismissRpeHelper"))
    }

    @Test
    fun theRemovedHelperFlagDoesNotComeBackIntoSettings() {
        val prefs = ownedSource("data/repository/prefs/SettingsStore.kt")
        assertFalse(prefs.contains("entity") && prefs.contains("rpe_helper"))
        assertFalse(
            "the removed helper flag must not come back",
            ownedSource("data/repository/PreferencesRepository.kt").contains("RPE_HELPER_DISMISSED"),
        )
    }
}
