package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-02: start-sheet routine cards picture the first three lifts and name
 * the kit. They are not a grouped text list.
 */
class StartOptionsRoutineCardTest {
    @Test
    fun fromRoutineCardsPictureTheFirstThreeLiftsAndNameTheKit() {
        val sheet = readOwned("ui/workout/StartOptionsSheet.kt")
        assertTrue(sheet.contains("RoutineCardCopy.mix"))
        assertTrue(sheet.contains("RoutineCardCopy.STILL_LIMIT"))
        assertTrue(sheet.contains("ExerciseThumb("))
        assertTrue(sheet.contains("GymCard("))
        assertTrue(sheet.contains("SessionOrderCopy.numberedPreview"))
        assertTrue(sheet.contains("private fun RoutineRow"))
        val routineRow = sheet.substringAfter("private fun RoutineRow")
            .substringBefore("\n@Composable\nprivate fun LogAndCardioActions")
        assertTrue(routineRow.contains("GymCard("))
        assertTrue(routineRow.contains("ExerciseThumb("))
        assertFalse(routineRow.contains("InstrumentRow("))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
