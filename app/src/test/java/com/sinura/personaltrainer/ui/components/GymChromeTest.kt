package com.sinura.personaltrainer.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GymChromeTest {
    @Test
    fun fifteenSitesShareScreenHeaderAndPinnedDock() {
        val headerSites = listOf(
            "library/ExerciseLibraryScreen.kt",
            "history/SessionDetailScreen.kt",
            "activity/ActivityDetailScreen.kt",
            "routines/CustomWeekScreen.kt",
            "plan/PlanDayScreen.kt",
            "routines/RoutineEditorScreen.kt",
            "exercise/ExerciseDetailScreen.kt",
            "onboarding/OnboardingScreen.kt",
            "workout/ActiveWorkoutScreen.kt",
            "workout/RestTimerScreen.kt",
        )
        val dockSites = listOf(
            "routines/RoutineEditorScreen.kt",
            "activity/ActivityComposerScreen.kt",
            "summary/WorkoutSummaryScreen.kt",
            "activity/LiveCardioScreen.kt",
            "activity/ActivityDetailScreen.kt",
            "workout/ActiveWorkoutScreen.kt",
        )
        headerSites.forEach { path ->
            assertTrue(path, readUi(path).contains("ScreenHeader("))
        }
        dockSites.forEach { path ->
            assertTrue(path, readUi(path).contains("PinnedDock("))
        }
        assertEquals(10, headerSites.size)
        assertEquals(6, dockSites.size)
        assertTrue(readOwned("ScreenHeader.kt").contains("fun ScreenHeader("))
        assertTrue(readOwned("PinnedDock.kt").contains("fun PinnedDock("))
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
