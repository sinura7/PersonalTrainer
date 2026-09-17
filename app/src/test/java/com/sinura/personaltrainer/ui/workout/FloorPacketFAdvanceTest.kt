package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet F: receipt, named Next / Finish, Coach.decide in the entry,
 * Why + Keep my numbers, Add set / Start next gone.
 */
class FloorPacketFAdvanceTest {
    @Test
    fun extraSetLivesInSavedSheetAndCompletionWithoutAnIdleStartNext() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.addSetHiddenOnFloor())
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.showIdleStartNext())
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(screen.contains("addSetHiddenOnFloor()"))
        assertTrue(screen.contains("showAddSet = WorkoutAdvance.cardOffersAnotherSet("))
        assertFalse(screen.contains("onStartNextLift"))
        val card = readOwned("ui/workout/WorkoutLiftCard.kt")
        assertTrue(card.contains("showAddSet = showAddSet"))
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertFalse(bar.contains("onStartNextLift"))
        assertFalse(bar.contains("RestIdleCopy.START_NEXT"))
    }

    @Test
    fun productionMicroRecCallsCoachDecide() {
        val ui = readOwned("ui/workout/SetMicroRecUi.kt")
        assertTrue(ui.contains("Coach.decide("))
        assertFalse(ui.contains("SetMicroRecCalculator.suggest"))
        assertTrue(ui.contains("toMicroRec()"))
    }

    @Test
    fun receiptAndLiftCompleteDockAreNamed() {
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.liftCompleteReplacesClock())
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertFalse(bar.contains("GymReceiptBanner("))
        assertTrue(readOwned("ui/workout/WorkoutSavedSets.kt").contains("WorkoutTestTags.LOG_RECEIPT"))
        assertTrue(bar.contains("primaryLabel ?: LogBarCopy.commit("))
        assertTrue(bar.contains("finishAct"))
        assertTrue(bar.contains("WorkoutTestTags.DOCK_FINISH"))
        assertTrue(bar.contains("nextName"))
        assertTrue(bar.contains("completeDock"))
        assertTrue(readOwned("domain/LogBarCopy.kt").contains("FINISH_WORKOUT"))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("receipt = logReceipt"))
        assertTrue(screen.contains("showFinish"))
        assertTrue(screen.contains("Haptics.recordAccent(view)"))
        assertFalse(screen.contains("Haptics.celebrate"))
        val haptics = readOwned("ui/theme/Haptics.kt")
        assertTrue(haptics.contains("fun recordAccent("))
        assertTrue(haptics.contains("PR_ACCENT_DELAY_MS") || readOwned("ui/theme/Motion.kt").contains("PR_ACCENT_DELAY_MS"))
    }

    @Test
    fun whySheetNamesUseAndKeepMyNumbers() {
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        val micro = bar.substring(bar.indexOf("fun MicroRecLine"))
        assertTrue(micro.contains("SetMicroRecCopy.USE_SUGGESTION"))
        assertTrue(micro.contains("SetMicroRecCopy.KEEP_MY_NUMBERS"))
        assertTrue(micro.contains("Haptics.tick(view)"))
        assertFalse(micro.contains("Haptics.commit"))
        val copy = readOwned("domain/SetMicroRec.kt")
        assertTrue(copy.contains("const val KEEP_MY_NUMBERS = \"Keep my numbers\""))
        assertTrue(copy.contains("fun whyLines"))
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
