package com.sinura.personaltrainer.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-08: the shared gym pieces exist, and the two set histories are one table.
 */
class ComponentInventoryTest {
    @Test
    fun namedInventoryLivesInComponents() {
        val owned = listOf(
            "ExerciseThumb.kt" to "fun ExerciseThumb(",
            "ExercisePickerSheet.kt" to "fun ExerciseRow(",
            "LiftCard.kt" to "fun LiftCard(",
            "EquipmentChip.kt" to "fun EquipmentChip(",
            "SetTable.kt" to "fun SetTable(",
            "ConfirmActionDialog.kt" to "fun GymDialog(",
            "GymSurfaces.kt" to "fun SectionHeader(",
            "NumberEntryDialog.kt" to "fun <T> NumberEntryDialog(",
            "ScreenSkeleton.kt" to "fun ScreenSkeleton(",
        )
        owned.forEach { (file, signature) ->
            assertTrue("$file missing $signature", readOwned(file).contains(signature))
        }
        val confirm = readOwned("ConfirmActionDialog.kt")
        assertTrue(confirm.contains("fun ConfirmActionDialog("))
        assertTrue(confirm.contains("GymDialog("))
        val loading = readOwned("EmptyState.kt")
        assertTrue(loading.contains("ScreenSkeleton("))
        assertFalse(loading.contains("CircularProgressIndicator"))
        val overlay = readOwned("RestTimerUi.kt")
        assertFalse(overlay.contains("SYSTEM_ALERT_WINDOW"))
        assertFalse(overlay.contains("TYPE_APPLICATION_OVERLAY"))
    }

    @Test
    fun workoutAndHistoryShareLiftCardAndSetTable() {
        val workout = readUi("workout/WorkoutLiftCard.kt")
        assertTrue(workout.contains("LiftCard("))
        assertTrue(workout.contains("LoggedSetsPanel("))
        val logged = readUi("workout/LoggedSetsPanel.kt")
        assertTrue(logged.contains("SetTable("))
        assertTrue(logged.contains("SetTableLine.fromLog"))
        val filled = readUi("history/FilledLiftCard.kt")
        assertTrue(filled.contains("LiftCard("))
        assertTrue(filled.contains("SetTable("))
        val picker = readOwned("ExercisePickerSheet.kt")
        assertTrue(picker.contains("EquipmentChip("))
        assertFalse(picker.contains("private fun InstrumentTag("))
        val library = readUi("library/ExerciseLibraryScreen.kt")
        assertTrue(library.contains("ExerciseRow("))
        val editor = readUi("routines/RoutineEditorScreen.kt")
        assertTrue(editor.contains("ExerciseRow("))
    }

    @Test
    fun instrumentTokensAlreadyCloseTypeColorAndShape() {
        val type = read("ui/theme/Type.kt")
        assertTrue(type.contains("val numeralHero"))
        assertTrue(type.contains("val kicker"))
        assertTrue(type.contains("TABULAR"))
        val color = read("ui/theme/Color.kt")
        assertTrue(color.contains("val Volt"))
        assertTrue(color.contains("val RestCyan"))
        assertTrue(color.contains("val PrGold"))
        assertTrue(color.contains("val Danger"))
        val shape = read("ui/theme/Shape.kt")
        assertTrue(shape.contains("val xs"))
        assertTrue(shape.contains("val lg"))
        val metrics = read("ui/theme/Metrics.kt")
        assertTrue(metrics.contains("val space1"))
        assertTrue(metrics.contains("val space7"))
        assertTrue(metrics.contains("val hairline"))
        assertTrue(metrics.contains("val gutter"))
    }

    private fun readOwned(name: String): String = read("ui/components/$name")

    private fun readUi(relative: String): String = read("ui/$relative")

    private fun read(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
