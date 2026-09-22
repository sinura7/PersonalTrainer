package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.FloorCompactChrome
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The redesigned floor: overflow on the header row, weight beside reps as two
 * hero numerals, idle rest the same quiet dock card at rest, Log set the only
 * Volt. Primary controls stay in the dock.
 */
class FloorCompactPresentationTest {
    @Test
    fun portraitShowsOneIdentityWithoutASelectedLiftDock() {
        assertFalse(FloorCompactChrome.showSelectedLiftDock())
        assertTrue(FloorCompactChrome.oneCurrentLiftOnFloor())
        assertTrue(ownedExists("ui/workout/ActiveWorkoutScreen.kt"))
        assertFalse("the never-shown THIS LIFT strip is gone", ownedExists("ui/workout/SelectedLiftDock.kt"))
        assertFalse(ownedExists("ui/workout/CurrentLiftCard.kt"))
        val chrome = readOwned("ui/workout/LandscapeChrome.kt")
        assertFalse(chrome.contains("hideSelectedLiftDock"))
        assertTrue(chrome.contains("fun hideIdleRest(landscape: Boolean): Boolean = landscape"))
        assertTrue(chrome.contains("fun compactHeader(landscape: Boolean): Boolean = landscape"))
        assertTrue(LandscapeChrome.hideIdleRest(landscape = true))
        assertFalse(LandscapeChrome.hideIdleRest(landscape = false))
        val workout = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(workout.contains("SelectedLiftDock("))
        assertFalse(workout.contains("hideSelectedLiftDock"))
        assertFalse(workout.contains("WorkoutTestTags.SELECTED_LIFT"))
        assertEquals("one identity on the floor", 1, Regex("ExerciseHeader\\(").findAll(workout).count())
        assertTrue(workout.contains("selected?.let { currentLift ->"))
    }

    @Test
    fun derivedCommitStaysTheOnlyVoltAndIdleRestIsTheSameQuietCard() {
        val card = readOwned("ui/workout/RestTimerCard.kt")
        assertFalse("idle Start rest must not be a filled Volt", card.contains("PrimaryGymButton"))
        assertFalse(
            "idle Start next must not be composed",
            card.contains("RestIdleCopy.START_NEXT"),
        )
        assertFalse(FloorCompactChrome.showIdleStartNext())
        assertFalse(FloorCompactChrome.idleStartNextIsVolt())
        assertTrue(card.contains("spoken = RestIdleCopy.startSpoken(safeTotal)"))
        assertTrue(card.contains("tag = WorkoutTestTags.START_REST"))
        assertFalse("idle controls are quiet marks, not full-width rows", card.contains("TextButton("))
        // The three pills became one segmented track; the card names the segments, and
        // RestSegments itself lives beside RestControl so the card still names no Haptics.
        assertTrue(card.contains("RestSegments("))
        assertTrue(card.contains("val idle = !running && !justFinished"))
        assertTrue(card.contains("else -> PLANNED"))
        assertTrue(card.contains("else -> RestIdleCopy.dockSpoken(clock, afterWarmup)"))
        assertTrue(card.contains("onClick = if (idle) onEditDuration else onOpenRest"))
        assertFalse(FloorCompactChrome.idleRestIsInstrumentBar())
        assertTrue(FloorCompactChrome.restIsDockCard())
        assertTrue(FloorCompactChrome.oneClockTwoModes())

        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertTrue(dock.contains("PrimaryGymButton("))
        assertEquals("one filled Volt in the dock", 1, Regex("PrimaryGymButton\\(").findAll(dock).count())
        assertTrue(dock.contains("height = Metrics.commit"))
        assertTrue(dock.contains("val canLog: Boolean"))
        assertTrue(dock.contains("val showAnother: Boolean"))
        assertTrue(dock.contains("val nextAct = action.kind == WorkoutPrimaryKind.NEXT_EXERCISE && !state.editing"))
        assertTrue(dock.contains("val finishAct = action.kind == WorkoutPrimaryKind.FINISH && !state.editing"))
        assertTrue(dock.contains("Add another set"))
        assertTrue(dock.contains("LogCommitCopy.disabledReason"))
        assertTrue(dock.contains("key(action.identity)"))
        assertTrue(dock.contains("else -> WorkoutTestTags.LOG_SET"))
        assertTrue(dock.contains("RestTimerCard("))
        assertTrue(dock.contains("SetWorkDock("))
        assertTrue(FloorCompactChrome.logButtonStaysAnchored())
        assertFalse("effort left the dock for its own row", dock.contains("RpeCopy"))
        assertFalse(dock.contains("RPE_TRACK"))
        assertFalse(dock.contains("LazyRow("))
        val rpe = readOwned("ui/workout/RpeSelector.kt")
        assertTrue(rpe.contains("WorkoutTestTags.RPE_TRACK"))
        assertTrue(rpe.contains("WorkoutTestTags.RPE_WARMUP_REASON"))
        assertTrue(FloorCompactChrome.showOptionalLogOptions(isWarmup = false))
        assertFalse(FloorCompactChrome.showOptionalLogOptions(isWarmup = true))
        assertTrue(FloorCompactChrome.warmupOutsideRpeTrack())
    }

    @Test
    fun overflowLivesOnTheHeaderRowNotItsOwnRow() {
        assertTrue(FloorCompactChrome.overflowOnHeaderRow())
        assertTrue(FloorCompactChrome.headerIsReadOnlyInstrumentStrip())
        val menu = readOwned("ui/workout/WorkoutOverflowMenu.kt")
        assertTrue(menu.contains("fun LiftOverflowMenu("))
        assertTrue(menu.contains("WorkoutTestTags.LIFT_OPTIONS"))
        assertTrue(menu.contains("contentDescription = \"Workout options\""))
        assertTrue(menu.contains("CurrentLiftCopy.SESSION_NOTES"))
        assertTrue(menu.contains("CurrentLiftCopy.SWITCH"))
        assertTrue(menu.contains("CurrentLiftCopy.SKIP"))
        assertTrue(menu.contains("CurrentLiftCopy.SWAP"))
        assertTrue(menu.contains("CurrentLiftCopy.REMOVE"))
        assertTrue(menu.contains("\"Session summary\""))
        assertTrue(menu.contains(".size(Metrics.touchMin)"))
        assertTrue(menu.contains("onSwitch: (() -> Unit)? = null"))
        assertTrue(menu.contains("if (onSwitch != null) {"))
        val header = readOwned("ui/workout/WorkoutHeader.kt")
        assertTrue(header.contains("overflow: (@Composable () -> Unit)? = null"))
        val trailing = header.substring(header.indexOf("trailing = {"), header.indexOf("if (headline.isNotBlank() && !planAsTitle)"))
        assertTrue(trailing.contains("WorkoutTestTags.FINISH"))
        assertTrue(trailing.contains("WorkoutTestTags.DISCARD"))
        assertTrue(trailing.contains("overflow?.invoke()"))
        val workout = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        val topBar = workout.indexOf("topBar = {")
        val overflow = workout.indexOf("LiftOverflowMenu(")
        val bottomBar = workout.indexOf("bottomBar = {")
        assertTrue("the overflow is composed in the header's trailing slot", overflow in topBar until bottomBar)
        assertTrue(workout.contains("onSwitch = { liftSwitcherOpen = true }"))
        val hero = readOwned("ui/workout/ExerciseHeader.kt")
        assertFalse(hero.contains("LiftOverflowMenu("))
        assertFalse(hero.contains("CurrentLiftHeader("))
        assertFalse(hero.contains("SetDots("))
        assertTrue(hero.contains("Metrics.exerciseHeroImage"))
        val liftCard = readOwned("ui/components/LiftCard.kt")
        val row = liftCard.substring(liftCard.indexOf("Row("), liftCard.indexOf("content()"))
        assertTrue(row.contains("trailing()"))
        assertTrue(row.contains("menu()"))
        assertTrue(liftCard.contains("verticalAlignment = Alignment.Top"))
    }

    @Test
    fun heroNumeralsSitSideBySideAndTheHoldClockStaysInTheDock() {
        assertFalse(FloorCompactChrome.stackWeightAboveReps())
        assertTrue(FloorCompactChrome.heroNumeralsSideBySide())
        assertFalse(FloorCompactChrome.weightAndRepsAreWheels())
        assertFalse(FloorCompactChrome.floorFieldGlyphsReplaceLabels())
        assertFalse(ownedExists("ui/workout/WorkoutLiftCard.kt"))
        val editor = readOwned("ui/workout/WeightRepsEditor.kt")
        assertTrue(editor.contains("fun WeightRepsEditor("))
        assertTrue(editor.contains("val stack = LogLoopScale.stackEntryWells(LocalDensity.current.fontScale)"))
        assertTrue(editor.contains("if (showWeight && !stack) {"))
        assertTrue(editor.contains(".testTag(WorkoutTestTags.SET_ENTRY)"))
        assertTrue(editor.contains("NumberEntryDialog("))
        assertTrue(editor.contains("NumericEntry.parseWeightKg"))
        assertTrue(editor.contains("NumericEntry.parseReps"))
        assertTrue(editor.contains("NumericEntry.parseHoldSeconds"))
        assertTrue(editor.contains("tag = WorkoutTestTags.WEIGHT_STEPPER"))
        assertTrue(editor.contains("tag = WorkoutTestTags.REPS_STEPPER"))
        assertTrue(editor.contains("tag = WorkoutTestTags.HOLD_STEPPER"))
        assertTrue(editor.contains("HoldWork.clock"))
        // The wells no longer carry a visible heading; each still names its field to a
        // screen reader through its own spoken form.
        assertTrue(editor.contains("SetCopy.weightEntryHero(meaning, weightKg, unit)"))
        assertTrue(editor.contains("spoken = \"Reps \$reps\""))
        assertTrue(editor.contains("else \"Time \${HoldWork.clock(seconds)}\""))
        assertTrue(editor.contains("RoundPlate(label = \"−\""))
        assertTrue(editor.contains("RoundPlate(label = \"+\""))
        assertTrue(editor.contains("plateWidth = Metrics.stepperRound"))
        assertTrue(editor.contains("shape = Radius.full"))
        assertFalse(editor.contains("SnapValueWheel("))
        assertFalse(editor.contains("FloorEntryWheels"))
        assertFalse(editor.contains("workout-weight-wheel"))
        assertFalse(editor.contains("FloorFieldGlyph("))
        assertFalse(editor.contains("SetEntryPanel("))
        assertFalse(editor.contains("ExerciseThumb("))
        assertFalse(editor.contains("WorkoutTestTags.HOLD_CLOCK"))
        val hero = readOwned("ui/workout/ExerciseHeader.kt")
        assertTrue(hero.contains("ExerciseThumb("))
        assertFalse(hero.contains("SessionTelemetryCopy"))
        val header = readOwned("ui/workout/WorkoutHeader.kt")
        assertTrue(header.contains("ScreenHeader("))
        assertFalse(header.contains("INSTRUMENT_STRIP"))
        assertFalse(header.contains("SessionTelemetryCopy"))
        val menu = readOwned("ui/workout/WorkoutOverflowMenu.kt")
        assertTrue(menu.contains("Session summary"))
        val tags = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(tags.contains("WEIGHT_STEPPER"))
        assertTrue(tags.contains("REPS_STEPPER"))
        assertTrue(tags.contains("HOLD_STEPPER"))
        assertFalse(tags.contains("SetEntryPanel("))
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertTrue(dock.contains("-> SetWorkDock("))
        val restUi = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(restUi.contains("workout-hold-clock"))
        val entry = readOwned("ui/components/SetEntryPanel.kt")
        val weightStepper = entry.indexOf("fun WeightStepper")
        assertTrue(weightStepper >= 0)
        val tall = entry.substring(weightStepper)
        assertTrue(tall.contains("NumeralWell("))
        assertTrue(tall.contains("NumberEntryDialog"))
        val extra = readOwned("ui/routines/SessionLiftStrip.kt")
        assertTrue(extra.contains("NumeralWell("))
        assertTrue(extra.contains("NumberEntryDialog("))
        assertFalse(extra.contains("SnapValueWheel("))
        assertFalse(extra.contains("CompactFloorEntry("))
        assertFalse(extra.contains("FloorNumeralRow("))
    }

    @Test
    fun primaryDockControlsStayUnclippedInBottomBar() {
        val workout = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        val bottomBar = workout.indexOf("bottomBar = {")
        val dock = workout.indexOf("WorkoutDock(")
        val lazy = workout.indexOf("LazyColumn(")
        assertTrue(bottomBar >= 0 && dock > bottomBar)
        assertTrue(lazy > dock)
        val dockFile = readOwned("ui/workout/WorkoutDock.kt")
        assertTrue(dockFile.contains("RestTimerCard("))
        assertTrue(dockFile.contains("PinnedDock("))
        assertFalse(workout.substring(lazy).contains("WorkoutDock("))
        assertFalse(workout.substring(lazy).contains("RestTimerCard("))
        assertFalse(workout.substring(lazy).contains("PinnedDock("))
        assertFalse(workout.contains("FloorTimerSlot("))
        assertTrue(workout.contains("WorkoutHeader("))
        assertFalse(
            "Home session cards are not this packet",
            workout.contains("SessionLiftStrip("),
        )
        val homeCards = readOwned("ui/routines/SessionLiftStrip.kt")
        assertFalse(homeCards.contains("compact = true"))
        val leftover = readOwned("ui/home/ThisWeekCard.kt")
        assertFalse(leftover.contains("SetEntryPanel("))
    }

    @Test
    fun emptySessionDockIsAddALiftWithoutATimer() {
        assertTrue(FloorCompactChrome.emptySessionHidesTimerDock())
        val workout = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(workout.contains("text = \"Add exercise\""))
        assertTrue(workout.contains("WorkoutTestTags.DOCK_ADD_LIFT"))
        assertTrue(workout.contains("emptySession"))
        assertTrue(workout.contains("showDiscard = state.showDiscard"))
        assertTrue(workout.contains("canFinish = state.canFinish"))
        val bottom = workout.substring(workout.indexOf("bottomBar = {"))
        val emptyDock = bottom.substring(bottom.indexOf("if (emptySession) {"), bottom.indexOf("} else if (logBarVisible) {"))
        assertTrue(emptyDock.contains("PinnedDock("))
        assertFalse(
            "empty free workout must not mount the workout dock or its clocks",
            emptyDock.contains("WorkoutDock("),
        )
        assertFalse(emptyDock.contains("RestTimerCard("))
        assertFalse(emptyDock.contains("WorkoutDockTimer("))
        assertFalse(emptyDock.contains("prelude"))
    }

    private fun ownedExists(relative: String): Boolean {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.any { File(it, relative).isFile }
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
