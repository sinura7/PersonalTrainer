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
            "QuietButton.kt" to "fun QuietButton(",
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
    fun workoutHistoryIsASecondarySheetAndHistoryKeepsItsReusableTable() {
        // The floor shows today's sets as chips; the full labelled Edit/Delete list stays a
        // secondary sheet the strip's Edit button opens. Neither is a second table.
        val strip = readUi("workout/SetHistoryStrip.kt")
        assertTrue(strip.contains("fun SetHistoryStrip("))
        assertTrue(strip.contains("WorkoutTestTags.VIEW_SETS"))
        assertTrue(strip.contains("onClick = onOpenAll"))
        assertTrue(strip.contains("InstrumentMenu("))
        assertTrue(strip.contains("SetRowCopy.revise(ordinal)"))
        assertTrue(strip.contains("SetRowCopy.delete(ordinal)"))
        assertTrue(strip.contains("FloorStatCopy.compactSet("))
        assertFalse("the floor strip is chips, not a second table", strip.contains("SetTable("))
        assertFalse(strip.contains("ModalBottomSheet("))
        val screen = readUi("workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("SetHistoryStrip("))
        assertTrue(screen.contains("WorkoutSetsSheet("))
        assertFalse("the Latest saved receipt row is gone", screen.contains("LatestWorkoutSet("))
        val logged = readUi("workout/WorkoutSavedSets.kt")
        assertTrue(logged.contains("fun WorkoutSetsSheet("))
        assertTrue(logged.contains("ModalBottomSheet("))
        assertTrue(logged.contains("SetCopy.setLine"))
        assertTrue(logged.contains("Edit set"))
        assertTrue(logged.contains("Delete set"))
        assertFalse(logged.contains("LatestWorkoutSet("))
        val header = readUi("workout/ExerciseHeader.kt")
        assertTrue(header.contains("fun ExerciseHeader("))
        assertTrue(header.contains("ExerciseThumb("))
        assertTrue(header.contains("Metrics.exerciseHeroImage"))
        val overflow = readUi("workout/WorkoutOverflowMenu.kt")
        assertTrue(overflow.contains("fun LiftOverflowMenu("))
        assertTrue(overflow.contains("InstrumentMenu("))
        assertTrue(screen.contains("LiftOverflowMenu("))
        val retired = listOf(
            "WorkoutLiftCard.kt",
            "CurrentLiftCard.kt",
            "WorkoutLogBar.kt",
            "LoggedSetsPanel.kt",
            "SelectedLiftDock.kt",
        )
        retired.forEach { name ->
            assertFalse("$name was retired by the floor redesign", exists("ui/workout/$name"))
        }
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
        assertTrue(type.contains("val heroTitle"))
        assertTrue(type.contains("val commit"))
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
        assertTrue(metrics.contains("val exerciseHeroImage"))
        assertTrue(metrics.contains("val stepperRound"))
        assertTrue(metrics.contains("val restRingSmall"))
        assertTrue(metrics.contains("val ringStroke"))
        assertTrue(metrics.contains("val progressTrack"))
        assertTrue(metrics.contains("val setMarker"))
        assertTrue(metrics.contains("val helpMark"))
    }

    private fun readOwned(name: String): String = read("ui/components/$name")

    private fun readUi(relative: String): String = read("ui/$relative")

    private fun read(relative: String): String =
        roots.map { File(it, relative) }.first { it.isFile }.readText()

    private fun exists(relative: String): Boolean = roots.any { File(it, relative).isFile }

    private val roots = listOf(
        File("app/src/main/java/com/sinura/personaltrainer"),
        File("../app/src/main/java/com/sinura/personaltrainer"),
    )
}
