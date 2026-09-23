package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet B on the redesigned floor: the weight / reps / hold draft are two hero
 * numerals with round plates and tap-to-type ([WeightRepsEditor]), never wheels.
 * Extra / paste typing wells, reminder wheels, and the rest sheet stay as they were.
 *
 * What the numerals do — their TalkBack actions, the plates, tap-to-type, side by side
 * until large text — is rendered in WeightRepsEditorRenderTest, and the screen's wiring
 * into the ViewModel is tapped in FloorScreenWiringRenderTest. W1a's numeric-entry cue
 * edits exactly the lines those checks used to pin. The bans stay here.
 */
class FloorStepperEntryTest {
    @Test
    fun floorCompactPathLeavesWheelsAndKeepsLabels() {
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.weightAndRepsAreWheels())
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.floorFieldGlyphsReplaceLabels())
        val editor = readOwned("ui/workout/WeightRepsEditor.kt")
        assertFalse(editor.contains("SnapValueWheel("))
        assertFalse("floor entry must not keep the live wheel tags", editor.contains("workout-weight-wheel"))
        assertFalse("the floor draws its own hero numerals", editor.contains("SetEntryPanel("))
        assertFalse(editor.contains("CompactFloorEntry("))
        assertFalse(editor.contains("FloorNumeralRow("))
        assertFalse(editor.contains("NumeralWell("))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(screen.contains("SetEntryPanel("))
    }

    @Test
    fun extraPasteAndHistoryKeepTypingWells() {
        val extra = readOwned("ui/routines/SessionLiftStrip.kt")
        assertTrue(extra.contains("NumeralWell("))
        assertTrue(extra.contains("NumberEntryDialog("))
        assertFalse(extra.contains("CompactFloorEntry("))
        assertFalse(extra.contains("FloorNumeralRow("))
        assertFalse(extra.contains("SnapValueWheel("))
        val history = readOwned("ui/history/SetEditSheet.kt")
        assertTrue(history.contains("SetEntryPanel("))
        assertFalse(history.contains("compact = true"))
        val tall = readOwned("ui/components/SetEntryPanel.kt")
        val weightStepper = tall.indexOf("fun WeightStepper")
        val wells = tall.substring(weightStepper)
        assertTrue(wells.contains("NumeralWell("))
        assertTrue(wells.contains("NumberEntryDialog("))
        assertTrue(wells.contains("Type a weight") || wells.contains("Type \${if"))
    }

    @Test
    fun reminderAndOnboardingWheelsStayAndFloorRestWheelIsGone() {
        val reminder = readOwned("ui/reminders/ReminderTimeWheel.kt")
        assertTrue(reminder.contains("SnapWheelColumn("))
        val onboarding = readOwned("ui/onboarding/BodyweightWheel.kt")
        assertTrue(onboarding.contains("VerticalPager("))
        assertTrue(onboarding.contains("NumberEntryDialog("))
        val timers = readOwned("ui/components/RestTimerUi.kt")
        assertFalse(timers.contains("SnapValueWheel("))
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.restLengthIsInlineWheel())
        val restCard = readOwned("ui/workout/RestTimerCard.kt")
        assertFalse(restCard.contains("SnapValueWheel("))
        assertFalse("duration editing stays in the sheet", restCard.contains("RestPresetChips("))
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertTrue(dock.contains("RestDurationSheet("))
        assertFalse(dock.contains("SnapValueWheel("))
    }

    @Test
    fun stepperHoldRepeatUsesTheCappedConstantsAndTickLight() {
        val stepper = readOwned("ui/components/StepperButton.kt")
        assertTrue(stepper.contains("StepperRepeat.HOLD_BEFORE_REPEAT_MS"))
        assertTrue(stepper.contains("StepperRepeat.REPEAT_MS"))
        assertTrue(stepper.contains("Haptics.tick(view)"))
        assertTrue(stepper.contains("Haptics.tickLight(view)"))
        assertFalse(stepper.contains("FAST_REPEAT_MS"))
        assertFalse(stepper.contains("60L"))
        assertTrue("round plates share the one repeat loop", stepper.contains("shape: Shape = RoundedCornerShape(Radius.sm)"))
        assertTrue(stepper.contains("textStyle: TextStyle? = null"))
        // The plates share this repeat loop and keep a full 48 dp target: both rendered in
        // WeightRepsEditorRenderTest (a held plate steps; each plate is at least touchMin).
        val editor = readOwned("ui/workout/WeightRepsEditor.kt")
        assertFalse("the plates never carry their own repeat loop", editor.contains("StepperRepeat"))
        val dialog = readOwned("ui/components/NumberEntryDialog.kt")
        assertTrue(dialog.contains("Haptics.tick(view)"))
        assertFalse(
            "typed confirm is not Log success",
            dialog.contains("Haptics.commit"),
        )
    }

    @Test
    fun heroNumeralsSitSideBySideAndStackOnlyForLargeText() {
        // Side by side at normal text, stacked from LogLoopScale.STACK_WELLS_FROM, with
        // SET_ENTRY holding both numerals either way: WeightRepsEditorRenderTest.
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.stackWeightAboveReps())
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.heroNumeralsSideBySide())
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
