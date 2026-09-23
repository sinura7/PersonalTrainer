package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.RestIdleCopy
import com.sinura.personaltrainer.ui.theme.Metrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Idle rest on the redesigned dock is the same [RestTimerCard] at rest: dim
 * kicker, dim planned clock, an empty ring, a chevron into the duration
 * sheet, and quiet Start rest / Time set controls. Never a bright countdown,
 * never a filled Volt, never inline presets.
 *
 * What the idle card, its length sheet and the dock's battery sentence show, say and do is
 * rendered in RestTimerCardRenderTest, RestDurationSheetRenderTest and
 * WorkoutDockTimerRenderTest, and tapped through the screen in
 * FloorRestAndCoachWiringRenderTest. The empty ring and the chevron are read from the drawn
 * pixels in RestCardDrawingRenderTest; the rest page's ring, room and honesty lines in
 * RestPageFitRenderTest (audit T1c-1). The bans stay here, with the metrics and the spoken
 * words the idle card is built from.
 */
class RestIdlePresentationTest {
    @Test
    fun idleDockWearsADimInstrumentFaceNotABrightClock() {
        val card = ownedSource("ui/workout/RestTimerCard.kt")
        assertFalse(
            "idle Start next must not be composed",
            card.contains("RestIdleCopy.START_NEXT"),
        )
        assertFalse("idle must not expand presets inline", card.contains("picking"))
        assertFalse("idle must not grow a Time set row", card.contains("TextButton("))
        assertFalse("idle must not host preset chips", card.contains("RestPresetChips("))
        assertFalse("idle rest must not use a filled Volt", card.contains("PrimaryGymButton"))
        assertFalse(card.contains("SnapValueWheel("))
        assertFalse("the rest card never pulses", card.contains("rememberInfiniteTransition"))
        assertEquals(72, Metrics.commit.value.toInt())
        assertEquals(48, Metrics.restRingSmall.value.toInt())
    }

    @Test
    fun theDurationSheetHostsNoVolt() {
        val src = ownedSource("ui/components/RestTimerUi.kt")
        val sheet = sourceBetween(src, "fun RestDurationSheet", "fun RestSweepRing")
        assertFalse(sheet.contains("PrimaryGymButton"))
    }

    @Test
    fun theRestPageNeverPromisesPrecisionOrANextRest() {
        val floor = ownedSource("ui/workout/RestTimerScreen.kt")
        assertFalse(floor.contains("precise", ignoreCase = true))
        assertFalse(floor.contains("\"Next rest\""))
    }

    @Test
    fun talkBackIdleClockAndStartMatchTheInstrument() {
        assertEquals(
            "Rest is not running. Planned rest 2:30. Tap to change duration.",
            RestIdleCopy.dockSpoken(clock = "2:30", afterWarmup = false),
        )
        assertEquals(
            "Start rest, 2 minutes 30 seconds",
            RestIdleCopy.startSpoken(150),
        )
        assertEquals("Time this set", com.sinura.personaltrainer.domain.SetStopwatchCopy.START_SPOKEN)
        val notes = com.sinura.personaltrainer.domain.AccessibilityMatrix.page("active-strength").talkBackNotes
        assertTrue(notes.contains("Time this set"))
        assertTrue(notes.contains("Start rest"))
    }
}
