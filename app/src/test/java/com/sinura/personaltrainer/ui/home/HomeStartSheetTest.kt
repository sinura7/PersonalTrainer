package com.sinura.personaltrainer.ui.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home empty state is Start a workout, not Add. The Volt opens the
 * start sheet (free / routines / cardio / Extra), not two Home buttons.
 */
class HomeStartSheetTest {
    @Test
    fun emptyHomeHasStartAWorkoutAndNoAddRow() {
        val agenda = readOwned("ui/home/DailyAgendaCard.kt")
        val action = readOwned("ui/home/HomeBoardLayout.kt")
        assertTrue(agenda.contains("HomeStartAction("))
        assertTrue(action.contains("HomeTags.START"))
        assertTrue(action.contains("SessionOrderCopy.FREE_WORKOUT"))
        assertTrue(agenda.contains("HomeStartCopy.EMPTY_BODY"))
        assertFalse(agenda.contains("AddUnderTodayRow"))
        assertFalse(agenda.contains("DayAddPicker"))
        assertFalse(agenda.contains("HomeTags.ADD"))
        assertFalse(agenda.contains("PlanDayCopy.ADD"))

        val home = readOwned("ui/home/HomeScreen.kt")
        // V2: Home's Start opens the one shared StartChooser through AppNav.
        assertTrue(home.contains("onOpenStartSheet()"))
        assertFalse(home.contains("HomeStartSheet("))
        assertFalse(home.contains("HomeTags.ADD"))
        assertFalse(home.contains("GetStartedSheet"))
        assertFalse(home.contains("Get started"))
    }

    @Test
    fun sheetOffersFreeRoutinesCardioAndExtraAndStartsFree() {
        val sheet = readOwned("ui/start/StartChooser.kt")
        assertTrue(sheet.contains("R.string.start_empty"))
        assertTrue(sheet.contains("R.string.start_group_routine"))
        assertTrue(sheet.contains("HomeStartCopy.CARDIO"))
        assertTrue(sheet.contains("HomeStartCopy.EXTRA"))
        assertTrue(sheet.contains("gate.startFreeWorkout()"))
        assertTrue(sheet.contains("StartGroups.routines"))
        assertTrue(readOwned("ui/start/StartGroups.kt").contains("HomeStart.namedRoutines"))
        assertTrue(sheet.contains("CardioPickCard("))
        assertTrue(sheet.contains("AuxiliaryPackList("))
        assertTrue(sheet.contains("suggestedKit"))
        assertTrue(sheet.contains("ExtraEquipment"))
        assertTrue(sheet.contains("ScheduleKind.planCardioTypes"))
        assertFalse(sheet.contains("Get started"))
        assertFalse(sheet.contains("StartOptionsSheet("))
        assertFalse(sheet.contains("askKeep"))
        assertFalse(sheet.contains("PlanDayCopy.JUST_TODAY"))
        val extra = readOwned("ui/plan/AuxiliaryPackList.kt")
        assertTrue(extra.contains("ExtraEquipment.PICK"))
        assertTrue(extra.contains("suggestedKit"))
        assertTrue(extra.contains("PickerStill"))
        assertTrue(extra.contains("AuxiliaryPacks.visibleFor"))
        val picker = readOwned("ui/plan/DayAddPicker.kt")
        assertTrue(picker.contains("suggestedKit = suggestedKit"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
