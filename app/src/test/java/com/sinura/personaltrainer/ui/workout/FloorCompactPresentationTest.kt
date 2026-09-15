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
        assertTrue(idle.contains("RestIdleCopy.START_NEXT"))
        assertTrue(idle.contains("RestIdleCopy.START"))
        assertTrue(idle.contains("TextButton("))
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.idleStartNextIsVolt())

        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertTrue(bar.contains("PrimaryGymButton("))
        assertTrue(bar.contains("height = Metrics.commit"))
        assertTrue(bar.contains("showRpe: Boolean"))
        assertTrue(bar.contains("advanceChoice: Boolean"))
        assertTrue(bar.contains("LogBarCopy.ANOTHER_SET"))
        assertFalse(bar.contains("RpeCopy.blurb"))
        assertFalse(bar.contains("LazyRow("))
    }

    @Test
    fun overflowLivesOnTheHeaderRowNotItsOwnRow() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.overflowOnHeaderRow())
        val card = readOwned("ui/workout/WorkoutLiftCard.kt")
        assertTrue(card.contains("menu = {"))
        assertTrue(card.contains("LiftOverflowMenu("))
        assertTrue(card.contains("WorkoutTestTags.LIFT_OPTIONS"))
        assertFalse(card.contains("CurrentLiftHeader("))
        assertFalse(card.contains("SetDots("))
        val headerCall = card.substring(card.indexOf("\n    LiftCard("), card.indexOf("if (!selected)"))
        assertTrue(headerCall.contains("trailing = headerTrailing"))
        assertTrue(headerCall.contains("menu = {"))
        val liftCard = readOwned("ui/components/LiftCard.kt")
        val header = liftCard.substring(liftCard.indexOf("Row("), liftCard.indexOf("content()"))
        assertTrue(header.contains("trailing()"))
        assertTrue(header.contains("menu()"))
        assertTrue(liftCard.contains("verticalAlignment = Alignment.Top"))
    }

    @Test
    fun compactFloorStacksWeightAboveRepsAndKeepsHoldClock() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.stackWeightAboveReps())
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.weightAndRepsAreWheels())
        val card = readOwned("ui/workout/WorkoutLiftCard.kt")
        assertTrue(card.contains("compact = true"))
        assertTrue(card.contains("FloorCompactChrome.showOptionalLogOptions(restRunning)"))
        assertTrue(card.contains("WorkoutTestTags.HOLD_CLOCK"))
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
        val wheels = entry.substring(compactFn, weightStepper)
        assertTrue(wheels.contains("SnapValueWheel("))
        assertTrue(wheels.contains("FloorEntryWheels.weightDisplays"))
        assertTrue(wheels.contains("FloorEntryWheels.repsValues"))
        assertTrue(wheels.contains("FloorEntryWheels.holdSecondsValues"))
        assertTrue(wheels.contains("workout-weight-wheel"))
        assertTrue(wheels.contains("workout-reps-wheel"))
        assertTrue(wheels.contains("workout-hold-wheel"))
        assertTrue(wheels.contains("HoldWork.clock"))
        assertTrue(wheels.contains("Metrics.wheelRow"))
        assertFalse(wheels.contains("NumeralWell("))
        assertFalse(wheels.contains("NumberEntryDialog"))
        assertFalse(wheels.contains("Type a weight"))
        val header = readOwned("ui/workout/WorkoutHeader.kt")
        assertTrue(header.contains("ELAPSED") || header.contains("elapsed") || header.contains("Elapsed"))
        val tags = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(tags.contains("WEIGHT_WHEEL"))
        assertTrue(tags.contains("REPS_WHEEL"))
        assertTrue(tags.contains("HOLD_WHEEL"))
        val tall = entry.substring(weightStepper)
        assertTrue(tall.contains("NumeralWell("))
        assertTrue(tall.contains("NumberEntryDialog"))
        val extra = readOwned("ui/routines/SessionLiftStrip.kt")
        assertTrue(extra.contains("NumeralWell("))
        assertFalse(extra.contains("SnapValueWheel("))
        assertFalse(extra.contains("CompactFloorEntry("))
    }

    @Test
    fun primaryDockControlsStayUnclippedInBottomBar() {
        val workout = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        val bottomBar = workout.indexOf("bottomBar")
        val restDock = workout.indexOf("RestDock(")
        val logBar = workout.indexOf("LogBar(")
        val lazy = workout.indexOf("LazyColumn(")
        assertTrue(bottomBar >= 0 && restDock > bottomBar && logBar > restDock)
        assertTrue(lazy > logBar)
        assertFalse(workout.substring(lazy).contains("RestDock("))
        assertFalse(workout.substring(lazy).contains("LogBar("))
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

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
