package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet 1 floor defects: Warm-up outside RPE 6–10; standing Next lift /
 * Another set (no dwell auto-advance).
 */
class WorkoutLogBarTest {
    @Test
    fun logBarCommitCopyFollowsTheWarmupSwitch() {
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertTrue(bar.contains("LogBarCopy.commit("))
        assertTrue(bar.contains("warmup = warmup"))
        assertTrue(bar.contains("warmup: Boolean"))
        assertFalse(bar.contains("\"Log \$draftLabel\""))
        assertFalse(bar.contains("\"Save \$draftLabel\""))

        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("warmup = state.draft.isWarmup"))
        assertTrue(screen.contains("LogBar("))
    }

    @Test
    fun warmupSitsOutsideTheEqualWeightRpeTrack() {
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        val start = bar.indexOf("fun SecondaryLogOptions")
        assertTrue("SecondaryLogOptions missing", start >= 0)
        val secondary = bar.substring(start)
        assertFalse(
            "Warm-up must not share a scrolling row with RPE",
            secondary.contains("LazyRow("),
        )
        assertTrue("persistent help", secondary.contains("RPE help"))
        assertTrue("RPE label", secondary.contains("Effort · Optional"))
        assertTrue("RPE track tag", secondary.contains("WorkoutTestTags.RPE_TRACK"))
        assertTrue("RPE values", secondary.contains("RpeCopy.VALUES"))
        assertTrue("compact chips", secondary.contains("compact = true"))
        assertTrue("equal weight chips", secondary.contains(".weight(1f)"))
        assertTrue("radio semantics", secondary.contains("InstrumentChoiceChip("))
        assertTrue("TalkBack meaning", secondary.contains("RpeCopy.meaning"))
        assertTrue("warmup reason", secondary.contains("Warm-ups leave RPE blank"))
        assertFalse("Warm-up chip moved above weight", secondary.contains("label = \"Warm-up\""))
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.warmupOutsideRpeTrack())
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.rpeTrackFitsWithoutScroll())
        val card = readOwned("ui/workout/WorkoutLiftCard.kt")
        val warmupAt = card.indexOf("WARMUP_CHIP")
        val weightAt = card.indexOf("SetEntryPanel(")
        val rpeAt = card.indexOf("SecondaryLogOptions(")
        assertTrue("Warm-up chip must sit above the weight well", warmupAt in 0 until weightAt)
        assertTrue("RPE must sit below the weight well", rpeAt > weightAt)
        assertTrue(card.contains("label = \"Warm-up\""))
        assertTrue(card.contains("WorkoutTestTags.WARMUP_CHIP"))
        assertTrue(card.contains("WarmupRamp.sets"))
    }

    @Test
    fun advanceIsAStandingDockChoiceNotADwellAutoMove() {
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertTrue(bar.contains("showNext: Boolean"))
        assertTrue(bar.contains("showFinish: Boolean"))
        assertTrue(bar.contains("showAnother: Boolean"))
        assertTrue(bar.contains("LogBarCopy.ANOTHER_SET"))
        assertTrue(bar.contains("WorkoutTestTags.ANOTHER_SET"))
        assertTrue(bar.contains("onAnotherSet"))
        assertTrue(bar.contains("finishAct"))
        assertTrue(bar.contains("WorkoutTestTags.DOCK_FINISH"))
        assertTrue(bar.contains("NextLiftPreview("))

        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("showNext = advance.showNext"))
        assertTrue(screen.contains("showFinish = advance.showFinish"))
        assertTrue(screen.contains("viewModel.requestExtraSet()"))
        assertTrue(screen.contains("viewModel.advanceNow()"))
        assertTrue(screen.contains("onFinish = { confirmEnd = true }"))
        assertFalse(
            "dwell must not call advanceNow",
            screen.contains("onDismissed = { viewModel.advanceNow() }"),
        )
        assertFalse(screen.contains("Stay here"))
        assertFalse(screen.contains("onStartNextLift"))
        assertTrue(screen.contains("Haptics.recordAccent(view)"))
        assertFalse(screen.contains("Haptics.celebrate"))

        val copy = readOwned("domain/LogBarCopy.kt")
        assertTrue(copy.contains("const val NEXT = \"Next lift\""))
        assertTrue(copy.contains("const val ANOTHER_SET = \"Another set\""))
        assertTrue(copy.contains("const val FINISH_WORKOUT = \"Finish workout\""))
        assertTrue(copy.contains("const val LOGGING"))
        assertTrue(bar.contains("canLog"))
        assertTrue(bar.contains("LogCommitCopy.disabledReason"))
        assertTrue(bar.contains("testTag(WorkoutTestTags.LOG_SET)"))
        assertTrue(bar.contains("next = false"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
