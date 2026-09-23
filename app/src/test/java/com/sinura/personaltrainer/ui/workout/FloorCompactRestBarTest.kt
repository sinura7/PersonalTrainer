package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.FloorCompactChrome
import com.sinura.personaltrainer.ui.theme.Metrics
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dock's clocks after the redesign. HOLD and SET keep the one 56 dp
 * instrument bar (countdown fill, mode controls). Rest is its own quiet
 * card ([RestTimerCard]): a 48 dp countdown ring, REST, the time, the
 * target and −15 / +15 / Skip. Honesty stays in the dock companion so
 * neither clock grows to hold it and rest cannot collide with the coach
 * line at 360×800.
 *
 * Which clock takes the dock's slot, what each says and does, the rest card's moods and
 * controls, and the companion's order and compact clock are rendered in
 * RestTimerCardRenderTest, SetWorkDockRenderTest and WorkoutDockTimerRenderTest. The bans
 * stay here, with the drawing facts no semantics can see (the ring's size and stroke, the
 * bar's cyan fill) and the commit's own lines.
 */
class FloorCompactRestBarTest {
    @Test
    fun restHoldAndSetShareOneInstrumentBar() {
        assertTrue(FloorCompactChrome.timerIsCompactInstrumentBar())
        assertTrue(FloorCompactChrome.oneClockTwoModes())
        assertTrue(FloorCompactChrome.restIsDockCard())
        assertFalse(FloorCompactChrome.idleRestIsInstrumentBar())
        assertFalse(FloorCompactChrome.restLengthIsInlineWheel())
        assertEquals(56, Metrics.logTimerRow.value.toInt())
        assertEquals(48, Metrics.touchMin.value.toInt())
        assertEquals(72, Metrics.commit.value.toInt())
        assertEquals(48, Metrics.restRingSmall.value.toInt())
        assertEquals(4, Metrics.ringStroke.value.toInt())

        val bar = readOwned("ui/components/RestTimerUi.kt")
        assertFalse(bar.contains("SnapValueWheel("))

        val barStart = bar.indexOf("fun FloorInstrumentBar")
        val holdStart = bar.indexOf("fun SetWorkDock")
        assertTrue(barStart >= 0 && holdStart > barStart)
        val holdEnd = bar.indexOf("@Composable", holdStart)
        assertTrue(holdEnd > holdStart)
        val instrument = bar.substring(barStart, holdStart)
        assertTrue(instrument.contains("RestCyanDim"))

        val hold = bar.substring(holdStart, holdEnd)
        assertFalse("the hold bar must not stack a second track under the clock", hold.contains("RestLinearTrack("))

        val card = readOwned("ui/workout/RestTimerCard.kt")
        assertTrue(card.contains("RestMiniRing(progress = progress, accent = accent, finished = justFinished)"))
        assertTrue(card.contains("Canvas(modifier = Modifier.size(Metrics.restRingSmall))"))
        assertTrue(card.contains("Metrics.ringStroke.toPx()"))
        assertFalse("running rest must not stack a second track under the clock", card.contains("RestLinearTrack("))
        assertFalse("honesty must not grow the rest card", card.contains("RestHonestyRow("))
        assertFalse("the rest card never pulses", card.contains("rememberInfiniteTransition"))
        assertFalse(card.contains("\"10 seconds\""))
        assertFalse("idle must not expand presets inline", card.contains("picking"))
        assertFalse("idle must not host preset chips", card.contains("RestPresetChips("))
        assertFalse("idle must not grow a Time set row", card.contains("TextButton("))
        assertFalse("idle rest must not use a filled Volt", card.contains("PrimaryGymButton"))
        assertFalse(card.contains("SnapValueWheel("))

        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertFalse("the dock composes one clock, never the legacy rest bar", dock.contains("RestDock("))
        assertFalse(dock.contains("FloorTimerSlot("))
    }

    @Test
    fun honestySharesTheCompanionWithAccessibleActiveTimerAndAnchoredCommit() {
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertFalse(dock.contains("CONTEXT_RAIL"))
        assertTrue(dock.contains("height = Metrics.commit"))
        assertTrue(dock.contains("text = state.verb"))
        assertTrue(dock.contains("supporting = state.payload"))
        assertTrue(dock.contains("textStyle = InstrumentType.commit"))
        assertFalse(FloorCompactChrome.liftCompleteReplacesClock())
        assertTrue(FloorCompactChrome.logButtonStaysAnchored())

        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        // Kept as text (T1b review): the hold's target only shows when the clock would run
        // past it, a state the ViewModel's timer never produces, so no render can see this
        // wire. The dock's own use of it is rendered in WorkoutDockTimerRenderTest.
        assertTrue(screen.contains("holdTotalSeconds = holdTimer.totalSeconds"))
        assertTrue(screen.contains("exactBestEffort = rest.exactAlarmBestEffort"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
