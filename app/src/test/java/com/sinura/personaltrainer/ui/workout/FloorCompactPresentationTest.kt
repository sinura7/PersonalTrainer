package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live-58 floor restack: overflow on the identity row, weight then reps,
 * idle rest a quiet line, Log set the only Volt. Primary controls stay in
 * the dock.
 */
class FloorCompactPresentationTest {
    @Test
    fun portraitDoesNotStackThisLiftUnderTheExpandedCard() {
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.showSelectedLiftDock())
        assertTrue(LandscapeChrome.hideSelectedLiftDock(landscape = false))
        assertTrue(LandscapeChrome.hideSelectedLiftDock(landscape = true))
        val workout = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(workout.contains("LandscapeChrome.hideSelectedLiftDock"))
        assertTrue(workout.contains("SelectedLiftDock("))
        val selected = workout.indexOf("SelectedLiftDock(")
        val hide = workout.lastIndexOf("hideSelectedLiftDock", selected)
        assertTrue("SelectedLiftDock must sit behind hideSelectedLiftDock", hide in 0 until selected)
    }

    @Test
    fun logSetStaysTheOnlyVoltAndIdleRestIsOneLine() {
        val dock = readOwned("ui/components/RestTimerUi.kt")
        val idleStart = dock.indexOf("fun RestIdleRow")
        val idleEnd = dock.indexOf("fun RestLinearTrack")
        assertTrue(idleStart >= 0 && idleEnd > idleStart)
        val idle = dock.substring(idleStart, idleEnd)
        assertFalse("idle Start next must not be a filled Volt", idle.contains("PrimaryGymButton"))
        assertFalse(
            "idle Start next must not be composed",
            idle.contains("RestIdleCopy.START_NEXT"),
        )
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.showIdleStartNext())
        assertTrue(idle.contains("RestIdleCopy.START"))
        assertTrue(idle.contains("TextButton("))
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.idleStartNextIsVolt())

        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertTrue(bar.contains("PrimaryGymButton("))
        assertTrue(bar.contains("height = Metrics.commit"))
        assertTrue(bar.contains("showRpe: Boolean"))
        assertTrue(bar.contains("showNext: Boolean"))
        assertTrue(bar.contains("showFinish: Boolean"))
        assertTrue(bar.contains("LogBarCopy.ANOTHER_SET"))
        assertTrue(bar.contains("canLog: Boolean"))
        assertTrue(bar.contains("LogCommitCopy.disabledReason"))
        assertTrue(bar.contains("next = false"))
        assertTrue(bar.contains("testTag(WorkoutTestTags.LOG_SET)"))
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.logButtonStaysAnchored())
        assertFalse(bar.contains("RpeCopy.blurb"))
        assertFalse(bar.contains("LazyRow("))
    }

    @Test
    fun overflowLivesOnTheHeaderRowNotItsOwnRow() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.overflowOnHeaderRow())
        val card = readOwned("ui/workout/CurrentLiftCard.kt")
        assertTrue(card.contains("LiftOverflowMenu("))
        assertTrue(card.contains("WorkoutTestTags.LIFT_OPTIONS"))
        assertTrue(card.contains("CurrentLiftCopy.SESSION_NOTES"))
        assertTrue(card.contains("ExerciseHero(") || card.contains("fun ExerciseHero"))
        assertFalse(card.contains("CurrentLiftHeader("))
        assertFalse(card.contains("SetDots("))
        assertTrue(card.contains("size(Metrics.touchMin)"))
        val liftCard = readOwned("ui/components/LiftCard.kt")
        val header = liftCard.substring(liftCard.indexOf("Row("), liftCard.indexOf("content()"))
        assertTrue(header.contains("trailing()"))
        assertTrue(header.contains("menu()"))
        assertTrue(liftCard.contains("verticalAlignment = Alignment.Top"))
    }

    @Test
    fun compactFloorStacksWeightAboveRepsAndKeepsHoldClock() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.stackWeightAboveReps())
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.weightAndRepsAreWheels())
        val card = readOwned("ui/workout/WorkoutLiftCard.kt")
        assertTrue(card.contains("compact = true"))
        assertTrue(card.contains("FloorCompactChrome.showOptionalLogOptions(isWarmup = draftWarmup)"))
        assertFalse(card.contains("WorkoutTestTags.HOLD_CLOCK"))
        assertFalse(card.contains("ExerciseThumb("))
        val liftCard = readOwned("ui/components/LiftCard.kt")
        assertTrue(liftCard.contains("ExerciseThumb("))
        assertTrue(liftCard.contains("CountBadge("))
        val entry = readOwned("ui/components/SetEntryPanel.kt")
        assertTrue(entry.contains("compact: Boolean = false"))
        val panelStart = entry.indexOf("fun SetEntryPanel")
        val compactFn = entry.indexOf("private fun CompactFloorEntry")
        val weightStepper = entry.indexOf("fun WeightStepper")
        assertTrue(panelStart >= 0 && compactFn > panelStart && weightStepper > compactFn)
        val panel = entry.substring(panelStart, compactFn)
        assertTrue(panel.contains("if (compact)"))
        assertTrue(panel.contains("CompactFloorEntry("))
        assertFalse(panel.contains("NumberEntryDialog"))
        val floor = entry.substring(compactFn, weightStepper)
        assertTrue(floor.contains("FloorNumeralRow("))
        assertTrue(floor.contains("NumberEntryDialog("))
        assertTrue(floor.contains("NumericEntry.parseWeightKg"))
        assertTrue(floor.contains("NumericEntry.parseReps"))
        assertTrue(floor.contains("NumericEntry.parseHoldSeconds"))
        assertTrue(floor.contains("workout-weight-stepper"))
        assertTrue(floor.contains("workout-reps-stepper"))
        assertTrue(floor.contains("workout-hold-stepper"))
        assertTrue(floor.contains("HoldWork.clock"))
        assertTrue(floor.contains("Kicker("))
        assertTrue(floor.contains("meaning.fieldLabel"))
        assertFalse(floor.contains("SnapValueWheel("))
        assertFalse(floor.contains("FloorEntryWheels"))
        assertFalse(floor.contains("workout-weight-wheel"))
        val header = readOwned("ui/workout/WorkoutHeader.kt")
        assertTrue(header.contains("ScreenHeader("))
        assertFalse(header.contains("INSTRUMENT_STRIP"))
        val hero = readOwned("ui/workout/CurrentLiftCard.kt")
        assertTrue(hero.contains("INSTRUMENT_STRIP") || hero.contains("SessionTelemetryCopy.line"))
        val tags = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(tags.contains("WEIGHT_STEPPER"))
        assertTrue(tags.contains("REPS_STEPPER"))
        assertTrue(tags.contains("HOLD_STEPPER"))
        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("workout-hold-clock"))
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
        val bottomBar = workout.indexOf("bottomBar")
        val logBar = workout.indexOf("LogBar(")
        val lazy = workout.indexOf("LazyColumn(")
        assertTrue(bottomBar >= 0 && logBar > bottomBar)
        assertTrue(lazy > logBar)
        assertTrue(workout.contains("FloorTimerSlot(") || readOwned("ui/workout/WorkoutLogBar.kt").contains("FloorTimerSlot("))
        assertFalse(workout.substring(lazy).contains("LogBar("))
        assertFalse(workout.substring(lazy).contains("FloorTimerSlot("))
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
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.emptySessionHidesTimerDock())
        val workout = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(workout.contains("LogBarCopy.ADD_LIFT"))
        assertTrue(workout.contains("WorkoutTestTags.DOCK_ADD_LIFT"))
        assertTrue(workout.contains("emptySession"))
        assertTrue(workout.contains("showDiscard = state.showDiscard"))
        assertTrue(workout.contains("canFinish = state.canFinish"))
        val bottom = workout.substring(workout.indexOf("bottomBar"))
        val emptyDock = bottom.substring(0, bottom.indexOf("LazyColumn("))
        assertFalse(
            "empty free workout must not mount FloorTimerSlot",
            emptyDock.contains("FloorTimerSlot("),
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
