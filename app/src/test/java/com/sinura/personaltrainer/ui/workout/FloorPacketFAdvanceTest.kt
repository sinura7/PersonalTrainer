package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet F on the redesigned floor: the save receipt is the saved chip in the set history,
 * Next / Finish are named on the dock's two-line commit, Coach.decide feeds the Next-set
 * card, Why offers Use suggestion / Keep my numbers, and there is still no idle Start next.
 */
class FloorPacketFAdvanceTest {
    @Test
    fun extraSetIsAChipInTheHistoryAndTheSheetWithoutAnIdleStartNext() {
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.addSetHiddenOnFloor())
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.setHistoryOnFloor())
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.showIdleStartNext())
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(screen.contains("addSetHiddenOnFloor()"))
        assertTrue(screen.contains("showAddSet = WorkoutAdvance.cardOffersAnotherSet("))
        assertFalse(screen.contains("onStartNextLift"))
        // Add set is the last chip of the set history once the plan is met, and the full
        // sheet still offers Add another set at its foot.
        val strip = readOwned("ui/workout/SetHistoryStrip.kt")
        assertTrue(strip.contains("showAddSet: Boolean"))
        assertTrue(strip.contains("WorkoutTestTags.ADD_SET"))
        assertTrue(strip.contains("private const val ADD_SET = \"Add set\""))
        val sheet = readOwned("ui/workout/WorkoutSavedSets.kt")
        assertTrue(sheet.contains("if (showAddSet) item(key = \"add-set\")"))
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertFalse(dock.contains("onStartNextLift"))
        assertFalse(dock.contains("RestIdleCopy.START_NEXT"))
        assertTrue(dock.contains("WorkoutTestTags.ANOTHER_SET"))
        assertTrue(dock.contains("\"Add another set\""))
        assertFalse(readOwned("ui/workout/RestTimerCard.kt").contains("START_NEXT"))
    }

    @Test
    fun productionMicroRecCallsCoachDecide() {
        val ui = readOwned("ui/workout/SetMicroRecUi.kt")
        assertTrue(ui.contains("CoachEngine.suggest("))
        assertFalse(ui.contains("SetMicroRecCalculator.suggest"))
        assertTrue(ui.contains("toMicroRec()"))
        // The Next-set card is the floor's one reader of the decision, and Apply only
        // fills the entry: the ViewModel's applyMicroRec never calls logSet.
        val card = readOwned("ui/workout/NextSetRecommendation.kt")
        assertTrue(card.contains("if (!SetMicroRecCopy.visibleOnEntry(rec)) return"))
        assertTrue(card.contains("WorkoutTestTags.NEXT_SET"))
        assertTrue(card.contains("WorkoutTestTags.NEXT_SET_COMPACT"))
        assertTrue(card.contains("WorkoutTestTags.MICRO_REC_APPLY"))
        assertTrue(card.contains("SetMicroRecCopy.deltaLine(rec, loadClass, unit)"))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("FloorCompactChrome.coachUsesCompactStrip("))
        assertTrue(screen.contains("compact = coachCompact"))
        assertTrue(screen.contains("onApply = viewModel::applyMicroRec"))
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
        assertTrue(screen.contains("receiptSetId = logReceipt?.setId"))
        assertTrue(screen.contains("view.announceForAccessibility(receipt.line)"))
        val strip = readOwned("ui/workout/SetHistoryStrip.kt")
        assertTrue(strip.contains("saved = set.id == receiptSetId"))
        assertTrue(strip.contains("saved -> \"\$SAVED · \$ordinal\""))
        assertTrue(screen.contains("Haptics.recordAccent(view)"))
        assertFalse(screen.contains("Haptics.celebrate"))
        val haptics = readOwned("ui/theme/Haptics.kt")
        assertTrue(haptics.contains("fun recordAccent("))
        assertTrue(haptics.contains("PR_ACCENT_DELAY_MS") || readOwned("ui/theme/Motion.kt").contains("PR_ACCENT_DELAY_MS"))
    }

    @Test
    fun whySheetNamesUseAndKeepMyNumbersAndApplyOnlyFillsTheEntry() {
        val card = readOwned("ui/workout/NextSetRecommendation.kt")
        assertTrue(card.contains("SetMicroRecCopy.USE_SUGGESTION"))
        assertTrue(card.contains("SetMicroRecCopy.KEEP_MY_NUMBERS"))
        assertTrue(card.contains("title = \"Why this set\""))
        assertTrue(card.contains("SetMicroRecCopy.whyLines(rec)"))
        assertTrue(card.contains("CoachEvidenceCopy.whySheetAppendix"))
        assertTrue(card.contains("EvidenceCitationChip"))
        assertTrue(card.contains("WorkoutTestTags.MICRO_REC_WHY"))
        // Apply and Use suggestion are a detent, never the commit haptic: nothing was saved.
        assertTrue(card.contains("Haptics.tick(view)"))
        assertFalse(card.contains("Haptics.warn(view)"))
        assertFalse(card.contains("Haptics.commit"))
        assertFalse(card.contains("logSet"))
        assertTrue(card.contains("text = if (applied) \"Applied\" else \"Apply\""))
        assertTrue(card.contains("enabled = enabled && !applied"))
        val copy = readOwned("domain/SetMicroRec.kt")
        assertTrue(copy.contains("const val USE_SUGGESTION = \"Use suggestion\""))
        assertTrue(copy.contains("const val KEEP_MY_NUMBERS = \"Keep my numbers\""))
        assertTrue(copy.contains("fun whyLines"))
        assertTrue(copy.contains("fun deltaLine"))
        assertTrue(readOwned("domain/RuleTraceCopy.kt").contains("fun whySheet"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
