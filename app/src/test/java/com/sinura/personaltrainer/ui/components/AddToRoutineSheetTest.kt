package com.sinura.personaltrainer.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L-05: add-to-routine is a landing preview and destination cards, not a
 * list of text buttons.
 */
class AddToRoutineSheetTest {
    @Test
    fun addToRoutineSheetPicturesTheLandingAndTheDestination() {
        val sheet = readOwned("ui/components/AddToRoutineSheet.kt")
        assertTrue(sheet.contains("fun AddToRoutineSheet("))
        assertTrue(sheet.contains("LandingCard("))
        assertTrue(sheet.contains("DestinationCard("))
        assertTrue(sheet.contains("ExerciseThumb("))
        assertTrue(sheet.contains("ThumbSize.header"))
        assertTrue(sheet.contains("GymCard("))
        assertTrue(sheet.contains("AddToRoutineCopy.landing("))
        assertTrue(sheet.contains("SessionOrderCopy.WORK"))
        assertTrue(sheet.contains("SessionOrderCopy.REST"))
        assertTrue(sheet.contains("AddToRoutineCopy.stills("))
        assertTrue(sheet.contains("AddToRoutineCopy.mix("))
        assertTrue(sheet.contains("alreadyHolds"))
        assertFalse(sheet.contains("InstrumentRow("))

        val library = readOwned("ui/library/ExerciseLibraryScreen.kt")
        assertTrue(library.contains("AddToRoutineSheet("))
        assertTrue(library.contains("AddToRoutineCopy.destinations("))
        assertFalse(library.contains("private fun AddToRoutineSheet"))

        val detail = readOwned("ui/exercise/ExerciseDetailScreen.kt")
        assertTrue(detail.contains("AddToRoutineSheet("))
        assertTrue(detail.contains("AddToRoutineCopy.destination("))
        assertFalse(detail.contains("private fun RoutinePickerSheet"))
        assertFalse(detail.contains("private fun AddToRoutineSheet"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
