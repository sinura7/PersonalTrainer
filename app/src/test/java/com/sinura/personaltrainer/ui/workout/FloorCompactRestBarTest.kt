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
        assertTrue(bar.contains("fun FloorInstrumentBar"))
        assertTrue(bar.contains("heightIn(min = Metrics.logTimerRow)"))
        assertFalse(bar.contains("SnapValueWheel("))

        val barStart = bar.indexOf("fun FloorInstrumentBar")
        val holdStart = bar.indexOf("fun SetWorkDock")
        assertTrue(barStart >= 0 && holdStart > barStart)
        val holdEnd = bar.indexOf("@Composable", holdStart)
        assertTrue(holdEnd > holdStart)
        val instrument = bar.substring(barStart, holdStart)
        assertTrue(instrument.contains("heightIn(min = Metrics.logTimerRow)"))
        assertTrue(instrument.contains("RestCyanDim"))
        assertTrue(instrument.contains("workout-stop-set-clock"))
        assertTrue(instrument.contains("SetStopwatchCopy.STOP"))

        val hold = bar.substring(holdStart, holdEnd)
        assertTrue(hold.contains("FloorInstrumentBar("))
        assertTrue(hold.contains("HoldWork.liveDockSeconds"))
        assertTrue(hold.contains("workout-hold-clock"))
        assertTrue(hold.contains("onStop = onStop"))
        assertFalse("the hold bar must not stack a second track under the clock", hold.contains("RestLinearTrack("))

        val card = readOwned("ui/workout/RestTimerCard.kt")
        assertTrue(card.contains("internal fun RestTimerCard("))
        assertTrue(card.contains(".heightIn(min = Metrics.commit)"))
        assertTrue(card.contains(".testTag(if (idle) WorkoutTestTags.REST_IDLE else WorkoutTestTags.REST_BAR)"))
        assertTrue(card.contains("RestMiniRing(progress = progress, accent = accent, finished = justFinished)"))
        assertTrue(card.contains("Canvas(modifier = Modifier.size(Metrics.restRingSmall))"))
        assertTrue(card.contains("Metrics.ringStroke.toPx()"))
        assertTrue(card.contains("justFinished -> TalkBackPolicy.REST_FINISHED_KICKER"))
        assertTrue(card.contains("else -> TalkBackPolicy.REST_RUNNING_KICKER"))
        assertTrue(card.contains("running -> listOf(MINUS, PLUS, SKIP)"))
        assertTrue(card.contains("WorkoutTestTags.REST_MINUS"))
        assertTrue(card.contains("WorkoutTestTags.REST_PLUS"))
        assertTrue(card.contains("WorkoutTestTags.REST_SKIP"))
        assertTrue(card.contains("spoken = \"Minus \${RestTimer.NUDGE_SECONDS} seconds\""))
        assertTrue(card.contains("spoken = \"Plus \${RestTimer.NUDGE_SECONDS} seconds\""))
        assertTrue(card.contains("onClick = if (idle) onEditDuration else onOpenRest"))
        assertTrue(card.contains("running -> \"\$TARGET \$targetClock\""))
        assertFalse("running rest must not stack a second track under the clock", card.contains("RestLinearTrack("))
        assertFalse("honesty must not grow the rest card", card.contains("RestHonestyRow("))
        assertFalse("the rest card never pulses", card.contains("rememberInfiniteTransition"))
        assertFalse(card.contains("\"10 seconds\""))
        assertTrue(card.contains("Last ten seconds"))
        assertFalse("idle must not expand presets inline", card.contains("picking"))
        assertFalse("idle must not host preset chips", card.contains("RestPresetChips("))
        assertFalse("idle must not grow a Time set row", card.contains("TextButton("))
        assertFalse("idle rest must not use a filled Volt", card.contains("PrimaryGymButton"))
        assertFalse(card.contains("SnapValueWheel("))

        val dock = readOwned("ui/workout/WorkoutDock.kt")
        val holdMode = dock.indexOf("-> SetWorkDock(")
        val restMode = dock.indexOf("-> RestTimerCard(")
        assertTrue("HOLD and SET keep the instrument bar", holdMode >= 0)
        assertTrue("rest is the card", restMode > holdMode)
        assertTrue(dock.contains("FloorTimerSurface.mode("))
        assertFalse("the dock composes one clock, never the legacy rest bar", dock.contains("RestDock("))
        assertFalse(dock.contains("FloorTimerSlot("))
        assertTrue(dock.contains("onStop = events.onStopSetClock.takeIf { timer.stopwatchRunning && !holdActive }"))
        assertTrue(dock.contains("onEditDuration = { durationSheet = true }"))
        assertTrue(dock.contains("onOpenRest = events.onOpenRest"))
    }

    @Test
    fun honestySharesTheCompanionWithAccessibleActiveTimerAndAnchoredCommit() {
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertTrue(dock.contains("RestHonestyRow("))
        assertTrue(dock.contains("RestHonestyCopy.pick("))
        assertFalse(dock.contains("CONTEXT_RAIL"))
        assertTrue(dock.contains("contextVisible"))
        assertTrue(dock.contains("WorkoutTestTags.COMPANION_CLOCK"))
        assertTrue(dock.contains("WorkoutTestTags.TIMER_ROW"))
        assertTrue(dock.contains(".heightIn(min = Metrics.logTimerRow)"))
        assertTrue(dock.contains("height = Metrics.commit"))
        assertTrue(dock.contains("text = state.verb"))
        assertTrue(dock.contains("supporting = state.payload"))
        assertTrue(dock.contains("textStyle = InstrumentType.commit"))
        assertTrue(dock.contains("holdRemainingSeconds"))
        assertTrue(dock.contains("holdTotalSeconds"))
        val errorAt = dock.indexOf("state.error != null -> TextButton(")
        val undoAt = dock.indexOf("!state.undoMessage.isNullOrBlank() -> GymUndoHost(")
        val honestyAt = dock.indexOf("honesty != null -> RestHonestyRow(")
        val editAt = dock.indexOf("state.editing -> TextButton(")
        val hintAt = dock.indexOf("state.suggestionUnavailable -> Text(")
        assertTrue(
            "companion priority is error, undo, Cancel edit, honesty, suggestion caption",
            errorAt >= 0 && undoAt > errorAt && editAt > undoAt && honestyAt > editAt && hintAt > honestyAt,
        )
        assertTrue("an active clock stays reachable beside the companion", dock.contains("if (timer.show) clockButton()"))
        assertTrue(dock.contains("timer.restRunning -> \"Rest \${RestTimer.formatClock(timer.restRemainingSeconds)}\""))
        assertTrue(dock.contains("holdActive -> \"Hold \${RestTimer.formatClock(timer.holdElapsedSeconds)}\""))
        assertTrue(dock.contains("else -> \"Timers\""))
        assertFalse(FloorCompactChrome.liftCompleteReplacesClock())
        assertTrue(FloorCompactChrome.logButtonStaysAnchored())

        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("const val COMPANION_CLOCK = \"workout-companion-clock\""))
        assertTrue(screen.contains("const val TIMER_ROW = \"workout-timer-row\""))
        assertTrue(screen.contains("holdRemainingSeconds = holdTimer.remainingSeconds"))
        assertTrue(screen.contains("holdTotalSeconds = holdTimer.totalSeconds"))
        assertTrue(screen.contains("notificationsEnabled = restNotificationsEnabled"))
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
