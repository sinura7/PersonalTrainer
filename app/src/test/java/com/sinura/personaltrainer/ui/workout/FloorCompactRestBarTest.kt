package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.ui.theme.Metrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The dock's clocks after the redesign. HOLD and SET keep the one 56 dp
 * instrument bar (countdown fill, mode controls). Rest is its own quiet
 * card ([RestTimerCard]): a 48 dp countdown ring, REST, the time, the planned length
 * and −15 / +15 / Skip. Honesty stays in the dock companion so
 * neither clock grows to hold it and rest cannot collide with the coach
 * line at 360×800.
 *
 * Which clock takes the dock's slot, what each says and does, the rest card's moods and
 * controls, and the companion's order and compact clock are rendered in
 * RestTimerCardRenderTest, SetWorkDockRenderTest and WorkoutDockTimerRenderTest. The drawing
 * facts no semantics can see (the ring's size, stroke and sweep, the bar's cyan fill, a
 * hold's bar draining against its own target) are read from the drawn pixels in
 * RestCardDrawingRenderTest, and the commit's lines in DockCommitRenderTest (audit T1c-1).
 * The bans stay here, with the metrics the floor is measured in.
 */
class FloorCompactRestBarTest {
    @Test
    fun restHoldAndSetShareOneInstrumentBar() {
        assertEquals(56, Metrics.logTimerRow.value.toInt())
        assertEquals(48, Metrics.touchMin.value.toInt())
        assertEquals(72, Metrics.commit.value.toInt())
        assertEquals(48, Metrics.restRingSmall.value.toInt())
        assertEquals(4, Metrics.ringStroke.value.toInt())

        val bar = ownedSource("ui/components/RestTimerUi.kt")
        assertFalse(bar.contains("SnapValueWheel("))
        val hold = sourceBetween(bar, "fun SetWorkDock", "@Composable")
        assertFalse("the hold bar must not stack a second track under the clock", hold.contains("RestLinearTrack("))

        val card = ownedSource("ui/workout/RestTimerCard.kt")
        assertFalse("running rest must not stack a second track under the clock", card.contains("RestLinearTrack("))
        assertFalse("honesty must not grow the rest card", card.contains("RestHonestyRow("))
        assertFalse("the rest card never pulses", card.contains("rememberInfiniteTransition"))
        assertFalse(card.contains("\"10 seconds\""))
        assertFalse("idle must not expand presets inline", card.contains("picking"))
        assertFalse("idle must not host preset chips", card.contains("RestPresetChips("))
        assertFalse("idle must not grow a Time set row", card.contains("TextButton("))
        assertFalse("idle rest must not use a filled Volt", card.contains("PrimaryGymButton"))
        assertFalse(card.contains("SnapValueWheel("))

        val dock = ownedSource("ui/workout/WorkoutDock.kt")
        assertFalse("the dock composes one clock, never the legacy rest bar", dock.contains("RestDock("))
        assertFalse(dock.contains("FloorTimerSlot("))
    }

    @Test
    fun honestySharesTheCompanionWithoutAContextRail() {
        val dock = ownedSource("ui/workout/WorkoutDock.kt")
        assertFalse(dock.contains("CONTEXT_RAIL"))
    }
}
