package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet B: gym-floor weight / reps / hold draft are plates + keypad.
 * Extra / paste typing wells, reminder wheels, and rest wheels stay.
 */
class FloorStepperEntryTest {
    @Test
    fun floorCompactPathLeavesWheelsAndKeepsLabels() {
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.weightAndRepsAreWheels())
        val entry = readOwned("ui/components/SetEntryPanel.kt")
        val compactFn = entry.indexOf("private fun CompactFloorEntry")
        val weightStepper = entry.indexOf("fun WeightStepper")
        val floor = entry.substring(compactFn, weightStepper)
        assertTrue(floor.contains("FloorNumeralRow("))
        assertTrue(floor.contains("Metrics.stepperPlateWidth"))
        assertTrue(floor.contains("Metrics.stepperWeightHeight"))
        assertTrue(floor.contains("Metrics.stepperRepsHeight"))
        assertTrue(floor.contains("Metrics.stepperNumeralMinWidth"))
        assertTrue(floor.contains("NumberEntryDialog("))
        assertTrue(floor.contains("NumericEntry.parseWeightKg"))
        assertTrue(floor.contains("SetCopy.weightWellSpoken"))
        assertTrue(floor.contains("UnloadedLoad.allowsZeroWorkingWeight"))
        assertTrue(floor.contains("NumericEntry.parseReps"))
        assertTrue(floor.contains("NumericEntry.parseHoldSeconds"))
        assertTrue(floor.contains("IncrementTable.displayStep"))
        assertTrue(floor.contains("FloorStepper.nextWeightKg"))
        assertTrue(floor.contains("FloorStepper.nextReps"))
        assertTrue(floor.contains("FloorStepper.nextHoldSeconds"))
        assertTrue(floor.contains("WeightMeaning.NONE"))
        assertTrue(floor.contains("meaning.fieldLabel"))
        assertTrue(floor.contains("CustomAccessibilityAction"))
        assertFalse(floor.contains("SnapValueWheel("))
        assertFalse("floor compact must not keep the live wheel tags", floor.contains("workout-weight-wheel"))
        val card = readOwned("ui/workout/WorkoutLiftCard.kt")
        assertTrue(card.contains("loadType = lift.exercise.loadType"))
        assertTrue(card.contains("equipment = lift.exercise.equipment"))
        assertTrue(card.contains("movementKey = lift.exercise.movementKey"))
        assertTrue(card.contains("plannedKg = lift.targetWeightKg"))
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
        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertFalse(dock.contains("SnapValueWheel("))
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.restLengthIsInlineWheel())
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
        val dialog = readOwned("ui/components/NumberEntryDialog.kt")
        assertTrue(dialog.contains("Haptics.tick(view)"))
        assertFalse(
            "typed confirm is not Log success",
            dialog.contains("Haptics.commit"),
        )
    }

    @Test
    fun noHorizontalWeightRepsPairOnTheFloor() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.stackWeightAboveReps())
        val entry = readOwned("ui/components/SetEntryPanel.kt")
        val compactFn = entry.indexOf("private fun CompactFloorEntry")
        val weightStepper = entry.indexOf("fun WeightStepper")
        val floor = entry.substring(compactFn, weightStepper)
        assertTrue(floor.contains("Column("))
        assertFalse(floor.contains("weight(1f)") && floor.contains("RepsStepper"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
