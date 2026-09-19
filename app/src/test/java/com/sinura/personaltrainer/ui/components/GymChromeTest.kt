package com.sinura.personaltrainer.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GymChromeTest {
    @Test
    fun seventeenSitesShareScreenHeaderAndPinnedDock() {
        val headerSites = listOf(
            "library/ExerciseLibraryScreen.kt",
            "history/SessionDetailScreen.kt",
            "activity/ActivityDetailScreen.kt",
            "routines/CustomWeekScreen.kt",
            "plan/PlanDayScreen.kt",
            "routines/RoutineEditorScreen.kt",
            "exercise/ExerciseDetailScreen.kt",
            "onboarding/OnboardingScreen.kt",
            "workout/WorkoutHeader.kt",
            "workout/RestTimerScreen.kt",
        )
        // The workout has two docks on the shared chrome: WorkoutDock while a lift is
        // selected, and the screen's own Add exercise dock for an empty session.
        val dockSites = listOf(
            "routines/RoutineEditorScreen.kt",
            "activity/ActivityComposerScreen.kt",
            "summary/WorkoutSummaryScreen.kt",
            "activity/LiveCardioScreen.kt",
            "activity/ActivityDetailScreen.kt",
            "workout/WorkoutDock.kt",
            "workout/ActiveWorkoutScreen.kt",
        )
        headerSites.forEach { path ->
            assertTrue(path, readUi(path).contains("ScreenHeader("))
        }
        dockSites.forEach { path ->
            assertTrue(path, readUi(path).contains("PinnedDock("))
        }
        assertEquals(10, headerSites.size)
        assertEquals(7, dockSites.size)
        assertTrue(readOwned("ScreenHeader.kt").contains("fun ScreenHeader("))
        val pinned = readOwned("PinnedDock.kt")
        assertTrue(pinned.contains("fun PinnedDock("))
        assertTrue(pinned.contains("prelude"))
        val dock = readUi("workout/WorkoutDock.kt")
        assertTrue(dock.contains("prelude = {"))
        assertTrue(dock.contains("WorkoutTestTags.TIMER_ROW"))
        assertTrue(dock.contains("volt = {"))
        assertTrue(dock.contains("PrimaryGymButton("))
    }

    private fun readUi(relative: String): String = read("ui/$relative")

    private fun readOwned(name: String): String = read("ui/components/$name")

    private fun read(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
