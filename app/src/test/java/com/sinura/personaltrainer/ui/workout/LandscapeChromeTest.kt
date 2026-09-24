package com.sinura.personaltrainer.ui.workout

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The floor's chrome, by what must never come back. What it does on a phone on its side, and
 * where the dock and header sit in any orientation, is rendered in LandscapeChromeRenderTest;
 * the rest page in a short window in RestPageFitRenderTest; the dock's landscape "Timer
 * controls ›" and its clocks in WorkoutDockTimerRenderTest; the lift card's number badge and
 * the danger button in LiftCardAndDangerButtonRenderTest (audit T1c-1). LandscapeChrome's
 * budget arithmetic (`logBudgetDp`, `logVisibleInLandscape`, `ringSizeDp`, the `*_DP`
 * constants) had no reader but this file; W2a took it out.
 */
class LandscapeChromeTest {
    @Test
    fun retiredLiftDocksAndCardsStayRetired() {
        // ADR-027: there is one identity on the floor and the recommendation is its
        // own card, so the old selected-lift dock and fold-into-card switches are gone.
        val chrome = ownedSource("ui/workout/LandscapeChrome.kt")
        assertFalse(chrome.contains("hideSelectedLiftDock"))
        assertFalse(chrome.contains("foldMicroRecIntoCard"))
        val workout = ownedSource("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(workout.contains("SelectedLiftDock("))
        listOf(
            "ui/workout/SelectedLiftDock.kt",
            "ui/workout/WorkoutLogBar.kt",
            "ui/workout/WorkoutLiftCard.kt",
            "ui/workout/CurrentLiftCard.kt",
            "ui/workout/LoggedSetsPanel.kt",
        ).forEach { retired -> assertFalse(retired, ownedSourceExists(retired)) }
        assertFalse(ownedSource("ui/workout/WorkoutDock.kt").contains("CartBadge("))
    }

    @Test
    fun theScrollingListNeverHostsTheDocksClocks() {
        // G-02 / Packet 2 / ADR-027: the timer surface and Log set share WorkoutDock in
        // Scaffold.bottomBar, rendered in LandscapeChromeRenderTest.
        val workout = ownedSource("ui/workout/ActiveWorkoutScreen.kt")
        val list = sourceFrom(workout, "LazyColumn(")
        assertFalse(
            "RestTimerCard must not be composed in the scrolling column",
            list.contains("RestTimerCard("),
        )
        assertFalse(
            "SetWorkDock must not be composed in the scrolling column",
            list.contains("SetWorkDock("),
        )
        assertFalse("the dock is the only timer host", workout.contains("FloorTimerSlot("))
        assertFalse("RestDock is owned inside the dock", workout.contains("RestDock("))
    }
}
