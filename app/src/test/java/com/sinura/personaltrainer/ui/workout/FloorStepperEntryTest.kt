package com.sinura.personaltrainer.ui.workout

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Packet B on the redesigned floor: the weight / reps / hold draft are two hero numerals with
 * round plates and tap-to-type ([WeightRepsEditor]), never wheels. Extra / paste typing wells,
 * reminder wheels, and the rest sheet stay as they were.
 *
 * What the numerals do is rendered in WeightRepsEditorRenderTest and tapped through the screen
 * in FloorScreenWiringRenderTest. Since audit T1c-2 the rest is held where it can be seen too: a
 * plate's detent, a held plate's paced light ticks and the keypad's Set as a detent, never a
 * commit, in PlateAndKeypadFeelRenderTest; the history sheet typing its weight and reps in
 * HistorySetEntryRenderTest; onboarding's bodyweight wheel in BodyweightWheelRenderTest. The
 * Home strip's wells and the reminder wheel are held by TargetStepperTest and
 * SettingsHomeLayoutTest. The bans stay here.
 */
class FloorStepperEntryTest {
    @Test
    fun theFloorEntryHasNoGlyphWheelOrSharedPanel() {
        val editor = ownedSource("ui/workout/WeightRepsEditor.kt")
        assertFalse("no glyph stands in for the unit or a heading", editor.contains("FloorFieldGlyph"))
        assertFalse(editor.contains("SnapValueWheel("))
        assertFalse("floor entry must not keep the live wheel tags", editor.contains("workout-weight-wheel"))
        assertFalse("the floor draws its own hero numerals", editor.contains("SetEntryPanel("))
        assertFalse(editor.contains("CompactFloorEntry("))
        assertFalse(editor.contains("FloorNumeralRow("))
        assertFalse(editor.contains("NumeralWell("))
        assertFalse(ownedSource("ui/workout/ActiveWorkoutScreen.kt").contains("SetEntryPanel("))
    }

    @Test
    fun theHomeStripAndTheHistorySheetKeepTheirWellsNotTheFloorsCompactPath() {
        val extra = ownedSource("ui/routines/SessionLiftStrip.kt")
        assertFalse(extra.contains("CompactFloorEntry("))
        assertFalse(extra.contains("FloorNumeralRow("))
        assertFalse(extra.contains("SnapValueWheel("))
        assertFalse(ownedSource("ui/history/SetEditSheet.kt").contains("compact = true"))
    }

    @Test
    fun theRestLengthIsNeverAWheelOnTheFloor() {
        assertFalse(ownedSource("ui/components/RestTimerUi.kt").contains("SnapValueWheel("))
        val restCard = ownedSource("ui/workout/RestTimerCard.kt")
        assertFalse(restCard.contains("SnapValueWheel("))
        assertFalse("duration editing stays in the sheet", restCard.contains("RestPresetChips("))
        assertFalse(ownedSource("ui/workout/WorkoutDock.kt").contains("SnapValueWheel("))
    }

    @Test
    fun thePlatesKeepOneCappedRepeatAndTypingNeverFeelsLikeACommit() {
        val stepper = ownedSource("ui/components/StepperButton.kt")
        assertFalse(stepper.contains("FAST_REPEAT_MS"))
        assertFalse(stepper.contains("60L"))
        assertFalse(
            "the plates never carry their own repeat loop",
            ownedSource("ui/workout/WeightRepsEditor.kt").contains("StepperRepeat"),
        )
        assertFalse(
            "typed confirm is not Log success",
            ownedSource("ui/components/NumberEntryDialog.kt").contains("Haptics.commit"),
        )
    }
}
