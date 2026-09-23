package com.sinura.personaltrainer.ui.workout

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Packet E: one dock clock, rest presets in the duration sheet, and Compose
 * (the SET bar and the rest card alike) stays visual-only for RestTick.
 *
 * The finish flash, its dwell and its one announcement, the presets and ±15 of the sheet and
 * the card, and the dock's wiring of them are rendered in RestTimerCardRenderTest,
 * RestDurationSheetRenderTest and WorkoutDockTimerRenderTest, and tapped through the ViewModel
 * in FloorRestAndCoachWiringRenderTest. Skip's commit and ±15's tick are felt in
 * RestPagesRenderTest and RestCardDrawingRenderTest; the service's own tick pulses and the
 * hold's beep in RestTimerServiceTest and RestAndHoldAlertsTest; starting the set clock or a
 * hold cancelling rest in ActiveWorkoutViewModelTest; switching lift while timing asks first
 * in LiftOptionsRenderTest (audit T1c-1). The bans stay here.
 */
class FloorPacketEClockTest {
    @Test
    fun restTimerUiDoesNotCallHapticsForRestTick() {
        val rest = ownedSource("ui/components/RestTimerUi.kt")
        assertFalse(rest.contains("RestTick.isWarn"))
        assertFalse(
            "Compose must not pulse RestTick haptics",
            rest.contains("Haptics.warn(view)") && rest.contains("safeRemaining"),
        )
        assertFalse(rest.contains("Haptics.warn(view)"))
        val card = ownedSource("ui/workout/RestTimerCard.kt")
        assertFalse("the rest card reads the service's clock and never pulses on its own", card.contains("Haptics"))
        assertFalse(card.contains("RestTick"))
        assertFalse(card.contains("rememberInfiniteTransition"))
    }

    @Test
    fun floorRestUsesPresetsNotAWheel() {
        val src = ownedSource("ui/components/RestTimerUi.kt")
        assertFalse(src.contains("SnapValueWheel("))
        val card = ownedSource("ui/workout/RestTimerCard.kt")
        assertFalse(card.contains("SnapValueWheel("))
        assertFalse("presets live in the sheet, not on the card", card.contains("RestPresetChips("))
    }
}
