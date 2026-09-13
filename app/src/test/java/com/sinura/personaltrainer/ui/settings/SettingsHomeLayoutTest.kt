package com.sinura.personaltrainer.ui.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHomeLayoutTest {
    @Test
    fun settingsRootIsAnIndexNotAFilingCabinet() {
        val home = readOwned("ui/settings/SettingsHome.kt")
        assertTrue(home.contains("SettingsHomeCopy.DISPLAY"))
        assertTrue(home.contains("SettingsHomeCopy.REMINDERS"))
        assertTrue(home.contains("SettingsHomeCopy.GENERATOR"))
        assertTrue(home.contains("SettingsHomeCopy.BACKUP"))
        assertFalse(home.contains("SchedulePrefsSection"))
        assertFalse(home.contains("CoachingSection"))
        assertFalse(home.contains("BackupRestoreSection"))
        assertFalse(home.contains("Generate a week"))
        assertFalse(home.contains("ReminderPrefsSection"))
        assertFalse(home.contains("ReminderTimeWheel"))

        val screen = readOwned("ui/settings/SettingsScreen.kt")
        assertTrue(screen.contains("SettingsHome("))
        assertTrue(screen.contains("SettingsPage.HOME"))
        assertTrue(screen.contains("SettingsPage.GENERATOR"))
        assertTrue(screen.contains("SettingsPage.REMINDERS"))
        assertTrue(screen.contains("SchedulePrefsSection("))
        assertTrue(screen.contains("BackupRestoreSection("))
        assertTrue(screen.contains("Generate a week"))
        assertTrue(screen.contains("viewModel::generateWeek"))
        val homeBranch = screen.substringAfter("SettingsPage.HOME").substringBefore("SettingsPage.DISPLAY")
        assertFalse(homeBranch.contains("SchedulePrefsSection"))
        assertFalse(homeBranch.contains("BackupRestoreSection"))
        assertFalse(homeBranch.contains("Generate a week"))
    }

    @Test
    fun reminderTimeIsAScrollWheelNotHourChips() {
        val reminders = readOwned("ui/reminders/ReminderPrefsSection.kt")
        assertTrue(reminders.contains("ReminderTimeWheel"))
        assertFalse(reminders.contains("(1..12).forEach"))
        assertFalse(reminders.contains("ReminderCopy.minuteChoices"))

        val wheel = readOwned("ui/reminders/ReminderTimeWheel.kt")
        assertTrue(wheel.contains("VerticalPager"))
        assertTrue(wheel.contains("periodLabels"))
        assertFalse(wheel.contains("InstrumentChip"))
    }

    @Test
    fun getStartedSheetStaysGone() {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer/ui/home/GetStartedSheet.kt"),
            File("../app/src/main/java/com/sinura/personaltrainer/ui/home/GetStartedSheet.kt"),
        )
        assertFalse(roots.any { it.isFile })
        val home = readOwned("ui/home/HomeScreen.kt")
        assertFalse(home.contains("GetStartedSheet"))
        assertFalse(home.contains("Get started"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        val file = roots.map { File(it, relative) }.first { it.isFile }
        return file.readText()
    }
}
