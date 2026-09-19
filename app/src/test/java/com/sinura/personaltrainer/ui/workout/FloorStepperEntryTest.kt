package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet B on the redesigned floor: the weight / reps / hold draft are two hero
 * numerals with round plates and tap-to-type ([WeightRepsEditor]), never wheels.
 * Extra / paste typing wells, reminder wheels, and the rest sheet stay as they were.
 */
class FloorStepperEntryTest {
    @Test
    fun floorCompactPathLeavesWheelsAndKeepsLabels() {
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.weightAndRepsAreWheels())
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.floorFieldGlyphsReplaceLabels())
        val editor = readOwned("ui/workout/WeightRepsEditor.kt")
        assertTrue(editor.contains("internal fun WeightRepsEditor("))
        assertTrue(editor.contains("private fun HeroNumeral("))
        assertTrue(editor.contains("plateWidth = Metrics.stepperRound"))
        assertTrue(editor.contains("plateHeight = Metrics.stepperRound"))
        assertTrue(
            "plates sit beside the numeral only when the widest sample fits with them",
            editor.contains("val inline = sampleWidth + (Metrics.stepperRound + Metrics.space2) * 2 <= availableWidth"),
        )
        assertTrue(editor.contains("rememberTextMeasurer"))
        assertTrue(editor.contains("private const val WEIGHT_SAMPLE = \"888.8\""))
        assertTrue(editor.contains("private const val REPS_SAMPLE = \"888\""))
        assertTrue(editor.contains("private const val TIME_SAMPLE = \"88:88\""))
        assertTrue(editor.contains("NumberEntryDialog("))
        assertTrue(editor.contains("NumericEntry.parseWeightKg"))
        assertTrue(editor.contains("SetCopy.weightWellSpoken"))
        assertTrue(editor.contains("UnloadedLoad.allowsZeroWorkingWeight"))
        assertTrue(editor.contains("NumericEntry.parseReps"))
        assertTrue(editor.contains("NumericEntry.parseHoldSeconds"))
        assertTrue(editor.contains("IncrementTable.displayStep"))
        assertTrue(editor.contains("FloorStepper.nextWeightKg"))
        assertTrue(editor.contains("FloorStepper.nextReps"))
        assertTrue(editor.contains("FloorStepper.nextHoldSeconds"))
        assertTrue(editor.contains("WeightMeaning.NONE"))
        assertTrue(
            "word kickers, not glyphs",
            editor.contains("label = \"\${meaning.fieldLabel} (\${unit.suffix})\""),
        )
        assertTrue(editor.contains("label = \"Reps\""))
        assertTrue(editor.contains("label = if (holdRunning) HoldWork.HOLD_KICKER else \"Time\""))
        assertTrue(editor.contains("CustomAccessibilityAction(decrementSpoken)"))
        assertTrue(editor.contains("CustomAccessibilityAction(incrementSpoken)"))
        assertTrue(editor.contains("CustomAccessibilityAction(typeLabel)"))
        assertTrue(editor.contains("\"Type a rep count\""))
        assertTrue(editor.contains("\"Type hold seconds\""))
        assertTrue(editor.contains("WorkoutTestTags.WEIGHT_STEPPER"))
        assertTrue(editor.contains("WorkoutTestTags.REPS_STEPPER"))
        assertTrue(editor.contains("WorkoutTestTags.HOLD_STEPPER"))
        assertFalse(editor.contains("SnapValueWheel("))
        assertFalse("floor entry must not keep the live wheel tags", editor.contains("workout-weight-wheel"))
        assertFalse("the floor draws its own hero numerals", editor.contains("SetEntryPanel("))
        assertFalse(editor.contains("CompactFloorEntry("))
        assertFalse(editor.contains("FloorNumeralRow("))
        assertFalse(editor.contains("NumeralWell("))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("WeightRepsEditor("))
        assertFalse(screen.contains("SetEntryPanel("))
        assertTrue(screen.contains("loadType = currentLift.exercise.loadType"))
        assertTrue(screen.contains("equipment = currentLift.exercise.equipment"))
        assertTrue(screen.contains("movementKey = currentLift.exercise.movementKey"))
        assertTrue(screen.contains("plannedKg = currentLift.targetWeightKg"))
        assertTrue(screen.contains("plated = currentLift.exercise.equipment == EquipmentType.BARBELL"))
        assertTrue(screen.contains("onWeightKgChange = viewModel::setWeight"))
        assertTrue(screen.contains("onRepsChange = viewModel::setReps"))
        assertTrue(screen.contains("onSecondsChange = viewModel::setHoldSeconds"))
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
        val editor = readOwned("ui/workout/WeightRepsEditor.kt")
        assertTrue(editor.contains("StepperButton("))
        assertTrue(editor.contains("shape = Radius.full"))
        assertTrue(editor.contains("textStyle = InstrumentType.numeralMd"))
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
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.stackWeightAboveReps())
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.heroNumeralsSideBySide())
        val editor = readOwned("ui/workout/WeightRepsEditor.kt")
        assertTrue(editor.contains("val stack = LogLoopScale.stackEntryWells(LocalDensity.current.fontScale)"))
        val sideBySide = editor.indexOf("if (showWeight && !stack) {")
        val stacked = editor.indexOf("} else {", sideBySide)
        val typing = editor.indexOf("if (typingWeight) {")
        assertTrue(sideBySide in 0 until stacked)
        assertTrue(stacked in 0 until typing)
        val row = editor.substring(sideBySide, stacked)
        assertTrue(row.contains("Row("))
        assertTrue("no intrinsic pass over the lazy parent", row.contains(".drawBehind {"))
        assertTrue(row.contains("weightColumn(Modifier.weight(1f).padding(end = Metrics.space2), columnWidth)"))
        assertTrue("a hairline splits the two numerals", row.contains("strokeWidth = Metrics.hairline.toPx()"))
        assertTrue(row.contains("workColumn(Modifier.weight(1f).padding(start = Metrics.space2), columnWidth)"))
        val column = editor.substring(stacked, typing)
        assertTrue(column.contains("Column("))
        assertTrue(column.contains("weightColumn(Modifier.fillMaxWidth())"))
        assertTrue(column.contains("HairlineDivider(startIndent = Metrics.space7)"))
        assertTrue(column.contains("workColumn(Modifier.fillMaxWidth())"))
        assertTrue(
            "both layouts anchor the log loop",
            row.contains(".testTag(WorkoutTestTags.SET_ENTRY)") && column.contains(".testTag(WorkoutTestTags.SET_ENTRY)"),
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
