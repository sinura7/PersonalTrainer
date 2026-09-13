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
        assertTrue(agenda.contains("HomeTags.START"))
        assertTrue(agenda.contains("SessionOrderCopy.FREE_WORKOUT"))
        assertTrue(agenda.contains("HomeStartCopy.EMPTY_BODY"))
        assertFalse(agenda.contains("AddUnderTodayRow"))
        assertFalse(agenda.contains("DayAddPicker"))
        assertFalse(agenda.contains("HomeTags.ADD"))
        assertFalse(agenda.contains("PlanDayCopy.ADD"))

        val home = readOwned("ui/home/HomeScreen.kt")
        assertTrue(home.contains("HomeStartSheet("))
        assertTrue(home.contains("startSheet = true"))
        assertFalse(home.contains("HomeTags.ADD"))
        assertFalse(home.contains("GetStartedSheet"))
        assertFalse(home.contains("Get started"))
    }

    @Test
    fun sheetOffersFreeRoutinesCardioAndExtraAndStartsFree() {
        val sheet = readOwned("ui/home/HomeStartSheet.kt")
        assertTrue(sheet.contains("HomeStartCopy.FREE"))
        assertTrue(sheet.contains("HomeStartCopy.ROUTINE"))
        assertTrue(sheet.contains("HomeStartCopy.CARDIO"))
        assertTrue(sheet.contains("HomeStartCopy.EXTRA"))
        assertTrue(sheet.contains("onStartFree"))
        assertTrue(sheet.contains("HomeStart.namedRoutines"))
        assertTrue(sheet.contains("CardioPickCard("))
        assertTrue(sheet.contains("AuxiliaryPackList("))
        assertTrue(sheet.contains("ScheduleKind.planCardioTypes"))
        assertFalse(sheet.contains("Get started"))
        assertFalse(sheet.contains("StartOptionsSheet("))
        assertFalse(sheet.contains("askKeep"))
        assertFalse(sheet.contains("PlanDayCopy.JUST_TODAY"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
