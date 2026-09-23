package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet F on the redesigned floor: the save receipt is the saved chip in the set history,
 * Next / Finish are named on the dock's two-line commit, Coach.decide feeds the Next-set
 * card, Why offers Use suggestion / Keep my numbers, and there is still no idle Start next.
 *
 * The two "Add set" controls and the saved chip are rendered and tapped in
 * SetHistoryStripRenderTest, WorkoutSetsSheetRenderTest, WorkoutDockRenderTest and
 * FloorScreenWiringRenderTest; W1a keeps one "Add set" and changes those on purpose.
 */
class FloorPacketFAdvanceTest {
    @Test
    fun extraSetIsAChipInTheHistoryAndTheSheetWithoutAnIdleStartNext() {
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.addSetHiddenOnFloor())
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.setHistoryOnFloor())
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.showIdleStartNext())
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(screen.contains("addSetHiddenOnFloor()"))
        assertFalse(screen.contains("onStartNextLift"))
        // Add set is the last chip of the set history once the plan is met, the full sheet
        // still offers Add another set at its foot, and the dock offers it beside Next:
        // rendered in SetHistoryStripRenderTest, WorkoutSetsSheetRenderTest and
        // WorkoutDockRenderTest, tapped through the screen in FloorScreenWiringRenderTest.
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertFalse(dock.contains("onStartNextLift"))
        assertFalse(dock.contains("RestIdleCopy.START_NEXT"))
        assertFalse(readOwned("ui/workout/RestTimerCard.kt").contains("START_NEXT"))
    }

    @Test
    fun productionMicroRecCallsCoachDecide() {
        val ui = readOwned("ui/workout/SetMicroRecUi.kt")
        assertFalse(ui.contains("SetMicroRecCalculator.suggest"))
        // The coach call itself is held in WorkoutMicroRecTest; the Next-set card that reads it
        // in NextSetRecommendationRenderTest; Apply filling the draft (and only the draft) through
        // the real screen in FloorRestAndCoachWiringRenderTest. The ViewModel's applyMicroRec
        // never calls logSet.
        val vm = readOwned("ui/workout/ActiveWorkoutViewModel.kt")
        val apply = vm.substring(vm.indexOf("fun applyMicroRec()"))
            .let { it.substring(0, it.indexOf("\n    }\n")) }
        assertFalse(apply.contains("logSet("))
        assertTrue(apply.contains("persistDraft()"))
    }

    @Test
    fun receiptLivesInTheHistoryAndNextFinishAreNamedOnTheDock() {
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.liftCompleteReplacesClock())
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertFalse(dock.contains("GymReceiptBanner("))
        assertFalse(dock.contains("LOG_RECEIPT"))
        // The commit is verb over payload; Next and Finish are named by the action itself.
        assertTrue(dock.contains("text = state.verb"))
        assertTrue(dock.contains("supporting = state.payload"))
        assertTrue(dock.contains("finishAct"))
        assertTrue(dock.contains("WorkoutTestTags.DOCK_FINISH"))
        assertTrue(dock.contains("WorkoutTestTags.NEXT"))
        assertTrue(dock.contains("completeDock"))
        val action = readOwned("ui/workout/WorkoutPrimaryAction.kt")
        assertTrue(action.contains("fun verb(includeNextName: Boolean = true): String"))
        assertTrue(action.contains("fun payload(unit: WeightUnit, loadClass: LoadClass): String?"))
        assertTrue(action.contains("WorkoutPrimaryKind.FINISH -> \"Finish workout\""))
        assertTrue(action.contains("\"Next exercise · \${nextName.orEmpty()}\""))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("verb = primaryAction.verb(includeNextName = false)"))
        assertTrue(screen.contains("?: primaryAction.nextName.takeIf { !landscape && primaryAction.kind == WorkoutPrimaryKind.NEXT_EXERCISE },"))
        assertTrue(screen.contains("spokenPayload = primaryAction.nextName.takeIf { primaryAction.kind == WorkoutPrimaryKind.NEXT_EXERCISE },"))
        assertTrue(screen.contains("payload = primaryAction.payload(unit = unit, loadClass = loadClass)"))
        assertTrue(screen.contains("showFinish"))
        // The "Latest saved" row is gone: the just-saved chip in the set history carries the
        // receipt, and the line is still announced once from the screen.
        assertFalse(screen.contains("workout-latest-saved"))
        assertFalse(screen.contains("LOG_RECEIPT"))
        assertFalse(readOwned("ui/workout/WorkoutSavedSets.kt").contains("LOG_RECEIPT"))
        // The just-saved chip reads "Saved · Set n of N" and says "saved":
        // SetHistoryStripRenderTest, and after a real save in FloorScreenWiringRenderTest.
        assertTrue(screen.contains("view.announceForAccessibility(receipt.line)"))
        assertTrue(screen.contains("Haptics.recordAccent(view)"))
        assertFalse(screen.contains("Haptics.celebrate"))
        val haptics = readOwned("ui/theme/Haptics.kt")
        assertTrue(haptics.contains("fun recordAccent("))
        assertTrue(haptics.contains("PR_ACCENT_DELAY_MS") || readOwned("ui/theme/Motion.kt").contains("PR_ACCENT_DELAY_MS"))
    }

    @Test
    fun whySheetNamesUseAndKeepMyNumbersAndApplyOnlyFillsTheEntry() {
        // The Why sheet, its trace and evidence, Use suggestion / Keep my numbers and Applied are
        // rendered in NextSetRecommendationRenderTest.
        val card = readOwned("ui/workout/NextSetRecommendation.kt")
        // Apply and Use suggestion are a detent, never the commit haptic: nothing was saved.
        assertTrue(card.contains("Haptics.tick(view)"))
        assertFalse(card.contains("Haptics.warn(view)"))
        assertFalse(card.contains("Haptics.commit"))
        assertFalse(card.contains("logSet"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
