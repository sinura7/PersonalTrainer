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
        assertFalse(bar.contains("RpeCopy.blurb"))
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
        assertTrue(entry.contains("if (compact || stack)"))
        val wellStart = entry.indexOf("internal fun NumeralWell")
        val compactStart = entry.indexOf("if (compact)", wellStart)
        val tallStart = entry.indexOf("clip(RoundedCornerShape(Radius.md))", compactStart)
        assertTrue(compactStart >= 0 && tallStart > compactStart)
        val compactWell = entry.substring(compactStart, tallStart)
        assertTrue(compactWell.contains("Kicker(label, asHeading = false)"))
        assertTrue(compactWell.contains("InstrumentType.numeralMd"))
        assertTrue(compactWell.contains("compact = true"))
        assertTrue(compactWell.contains("padding(horizontal = Metrics.space2, vertical = Metrics.space1)"))
        assertFalse(compactWell.contains("numeralLg"))
        assertFalse(compactWell.contains("padding(Metrics.space3)"))
        val header = readOwned("ui/workout/WorkoutHeader.kt")
        assertTrue(header.contains("ELAPSED") || header.contains("elapsed") || header.contains("Elapsed"))
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
