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
        assertTrue("Warm-up chip", secondary.contains("label = \"Warm-up\""))
        assertTrue("RPE glyph", secondary.contains("TemperIcons.FloorRpe"))
        assertFalse("RPE text kicker is replaced", secondary.contains("Kicker(\"RPE\")"))
        assertTrue("RPE track tag", secondary.contains("WorkoutTestTags.RPE_TRACK"))
        assertTrue("RPE values", secondary.contains("(6..10)"))
        assertTrue("compact chips", secondary.contains("compact = true"))
        assertTrue("equal weight chips", secondary.contains(".weight(1f)"))
        val warmupAt = secondary.indexOf("label = \"Warm-up\"")
        val trackAt = secondary.indexOf("WorkoutTestTags.RPE_TRACK")
        assertTrue("Warm-up chip must be composed before the RPE track", warmupAt in 0 until trackAt)
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.warmupOutsideRpeTrack())
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.rpeTrackFitsWithoutScroll())
    }

    @Test
    fun advanceIsAStandingDockChoiceNotADwellAutoMove() {
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertTrue(bar.contains("advanceChoice: Boolean"))
        assertTrue(bar.contains("LogBarCopy.ANOTHER_SET"))
        assertTrue(bar.contains("WorkoutTestTags.ANOTHER_SET"))
        assertTrue(bar.contains("onAnotherSet"))

        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("advanceChoice = pendingAdvance != null"))
        assertTrue(screen.contains("onAnotherSet = viewModel::stayOnCurrentExercise"))
        assertTrue(screen.contains("viewModel.advanceNow()"))
        assertFalse(
            "dwell must not call advanceNow",
            screen.contains("onDismissed = { viewModel.advanceNow() }"),
        )
        assertFalse(screen.contains("Stay here"))
        assertTrue(screen.contains("Motion.STATUS_DWELL_MS"))

        val copy = readOwned("domain/LogBarCopy.kt")
        assertTrue(copy.contains("const val NEXT = \"Next lift\""))
        assertTrue(copy.contains("const val ANOTHER_SET = \"Another set\""))
        assertTrue(copy.contains("const val LOGGING"))
        assertTrue(bar.contains("canLog"))
        assertTrue(bar.contains("LogCommitCopy.disabledReason"))
        assertTrue(bar.contains("logging = logging && !nextAct"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
