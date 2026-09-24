package com.sinura.personaltrainer.ui.workout

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Packet 3: manual set stopwatch in the dock timer slot. Time set is a
 * quiet control on the idle rest card; Stop rides the SET instrument bar.
 *
 * Time set on the card and in the sheet, Stop on the SET bar, the dock's "Set time" clock
 * and the screen's wiring to the ViewModel's stopwatch are rendered in
 * RestTimerCardRenderTest, RestDurationSheetRenderTest, SetWorkDockRenderTest,
 * WorkoutDockTimerRenderTest and FloorRestAndCoachWiringRenderTest; the device journey's
 * tags on the real Stop and sheet in RestCardDrawingRenderTest; a lift switch while timing
 * in LiftOptionsRenderTest; starting the clock cancelling rest in ActiveWorkoutViewModelTest
 * (audit T1c-1). The no-second-Volt bans stay here.
 */
class FloorPacket3StopwatchTest {
    @Test
    fun dockOffersStartAndStopWithoutASecondVolt() {
        val card = ownedSource("ui/workout/RestTimerCard.kt")
        assertFalse("Time set must not be a filled Volt", card.contains("PrimaryGymButton"))
        val bar = ownedSource("ui/components/RestTimerUi.kt")
        val hold = sourceBetween(bar, "fun SetWorkDock", "@Composable")
        assertFalse(hold.contains("PrimaryGymButton"))
    }
}
