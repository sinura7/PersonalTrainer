package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W-11: the pinned log button changes with the Warm-up chip.
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

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
