package com.sinura.personaltrainer.ui.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v4 blueprint 3.1 supersedes S-02: start-sheet routines are plain rows of rowMin height that
 * name the routine and its lift count. Lifts, kit and stills move to the preview a tap opens.
 *
 * StartOptionsRoutineCardRenderTest renders the row from a seeded routine: its name, its lift
 * count, the preview listing every lift in order and pictured, and Begin starting it.
 */
class StartOptionsRoutineCardTest {
    @Test
    fun aRoutineIsAPlainRowWithItsLiftCount() {
        val routineRow = sourceBetween(
            ownedSource("ui/start/StartChooser.kt"),
            "routines.take(StartGroups.ROUTINE_LIMIT)",
            "StartTags.routine(routine.id)",
        )
        assertTrue(routineRow.contains("InstrumentRow("))
        assertTrue(routineRow.contains("R.plurals.start_routine_sub"))
        assertTrue(routineRow.contains("Metrics.rowMin"))
        assertFalse("a routine is not a picture card here", routineRow.contains("GymCard("))
    }
}
