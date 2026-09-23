package com.sinura.personaltrainer.ui.workout

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Packet F on the redesigned floor: the save receipt is the saved chip in the set history,
 * Next / Finish are named on the dock's two-line commit, Coach.decide feeds the Next-set
 * card, Why offers Use suggestion / Keep my numbers, and there is still no idle Start next.
 *
 * What those do is held where it can be seen: the saved chip and "Add another set" in
 * SetHistoryStripRenderTest, WorkoutSetsSheetRenderTest, WorkoutDockRenderTest and
 * FloorScreenWiringRenderTest; the commit's verb, payload and Finish in DockCommitRenderTest
 * and WorkoutFloorRenderTest; the one announcement of a save, a record's accent and Apply's
 * detent in FloorFeedbackRenderTest; Apply keeping the coach's numbers through a process
 * death in FloorVmContractTest; no idle Start next in FloorRestAndCoachWiringRenderTest
 * (audit T1c-1). The bans stay here. Apply's never-logs ban reads `applyMicroRec` wherever
 * it is declared in the workout packages (`ui/workout` and `workout`), since W2d moves it out
 * of the ViewModel, and fails if it becomes a one-line delegate instead of a body.
 */
class FloorPacketFAdvanceTest {
    @Test
    fun noIdleStartNextAnywhereOnTheFloor() {
        val screen = ownedSource("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(screen.contains("addSetHiddenOnFloor()"))
        assertFalse(screen.contains("onStartNextLift"))
        val dock = ownedSource("ui/workout/WorkoutDock.kt")
        assertFalse(dock.contains("onStartNextLift"))
        assertFalse(dock.contains("RestIdleCopy.START_NEXT"))
        assertFalse(ownedSource("ui/workout/RestTimerCard.kt").contains("START_NEXT"))
    }

    @Test
    fun productionMicroRecCallsCoachDecideAndApplyNeverLogs() {
        val ui = ownedSource("ui/workout/SetMicroRecUi.kt")
        assertFalse(ui.contains("SetMicroRecCalculator.suggest"))
        assertFalse("Apply fills the entry; it never logs", workoutFunctionBody("applyMicroRec").contains("logSet("))
    }

    @Test
    fun theReceiptLivesInTheHistoryNotInABanner() {
        val dock = ownedSource("ui/workout/WorkoutDock.kt")
        assertFalse(dock.contains("GymReceiptBanner("))
        assertFalse(dock.contains("LOG_RECEIPT"))
        // The "Latest saved" row is gone: the just-saved chip in the set history carries the receipt.
        val screen = ownedSource("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(screen.contains("workout-latest-saved"))
        assertFalse(screen.contains("LOG_RECEIPT"))
        assertFalse(ownedSource("ui/workout/WorkoutSavedSets.kt").contains("LOG_RECEIPT"))
        assertFalse(screen.contains("Haptics.celebrate"))
    }

    @Test
    fun theNextSetCardNeverCommitsOrLogs() {
        // Apply and Use suggestion are a detent, never the commit haptic: nothing was saved.
        val card = ownedSource("ui/workout/NextSetRecommendation.kt")
        assertFalse(card.contains("Haptics.warn(view)"))
        assertFalse(card.contains("Haptics.commit"))
        assertFalse(card.contains("logSet"))
    }
}
