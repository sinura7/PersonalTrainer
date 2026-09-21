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
        assertTrue(home.contains("SettingsHomeCopy.ACCOUNT"))
        assertTrue(home.contains("SettingsHomeCopy.BACKUP"))
        assertTrue(home.contains("TemperIcons.Display"))
        assertTrue(home.contains("TemperIcons.Reminders"))
        assertTrue(home.contains("TemperIcons.Generator"))
        assertTrue(home.contains("TemperIcons.Rest"))
        assertTrue(home.contains("TemperIcons.Bodyweight"))
        assertTrue(home.contains("TemperIcons.Backup"))
        assertTrue(home.contains("TemperIcons.YourPlan"))
        assertFalse(home.contains("TemperIcons.Plan"))
        assertTrue(home.contains("TemperIcons.Diagnostics"))
        assertTrue(home.contains("TemperIcons.About"))
        assertTrue(home.contains("TemperIcons.Check"))
        assertTrue(home.contains("TemperIcons.Chevron"))
        assertTrue(home.contains("SettingsHomeCopy.UPDATE"))
        assertTrue(home.contains("SettingsTags.ROW_UPDATE"))
        assertFalse(home.contains("SchedulePrefsSection"))
        assertFalse(home.contains("CoachingSection"))
        assertFalse(home.contains("BackupRestoreSection"))
        assertFalse(home.contains("Generate a week"))
        assertFalse(home.contains("ReminderPrefsSection"))
        assertFalse(home.contains("ReminderTimeWheel"))
        assertFalse(home.contains("SettingsHomeCopy.LOG"))
        assertFalse(home.contains("SettingsHomeCopy.FOUNDATION"))
        assertFalse(home.contains("SettingsTags.ROW_LOG"))
        assertFalse(home.contains("SettingsTags.ROW_FOUNDATION"))

        val screen = readOwned("ui/settings/SettingsScreen.kt")
        assertTrue(screen.contains("SettingsHome("))
        assertTrue(screen.contains("SettingsPage.ACCOUNT"))
        assertTrue(screen.contains("AccountSection("))
        assertTrue(screen.contains("SettingsPage.HOME"))
        assertTrue(screen.contains("SettingsPage.GENERATOR"))
        assertTrue(screen.contains("SettingsPage.REMINDERS"))
        assertTrue(screen.contains("SchedulePrefsSection("))
        assertTrue(screen.contains("BackupRestoreSection("))
        assertTrue(screen.contains("Generate a week"))
        assertTrue(screen.contains("viewModel::generateWeek"))
        assertTrue(screen.contains("SecondaryGymButton("))
        val homeBranch = screen.substringAfter("SettingsPage.HOME").substringBefore("SettingsPage.DISPLAY")
        assertFalse(homeBranch.contains("SchedulePrefsSection"))
        assertFalse(homeBranch.contains("BackupRestoreSection"))
        assertFalse(homeBranch.contains("Generate a week"))
        val generatePane = screen.substringAfter("private fun SettingsGeneratorPane")
            .substringBefore("private fun SettingsBackupPane")
        assertTrue(generatePane.contains("SecondaryGymButton("))
        assertFalse(generatePane.contains("PrimaryGymButton("))
    }

    @Test
    fun debugLogAndFoundationSitUnderAboutNotTheRoot() {
        val home = readOwned("ui/settings/SettingsHome.kt")
        assertFalse(home.contains("BuildConfig.DEBUG"))
        val about = readOwned("ui/settings/AboutSections.kt")
        assertTrue(about.contains("SettingsHomeCopy.DEVELOPER"))
        assertTrue(about.contains("SettingsHomeCopy.LOG"))
        assertTrue(about.contains("SettingsHomeCopy.FOUNDATION"))
        assertTrue(about.contains("SettingsTags.ROW_LOG"))
        assertTrue(about.contains("SettingsTags.ROW_FOUNDATION"))
        assertTrue(about.contains("BuildConfig.DEBUG"))
        val screen = readOwned("ui/settings/SettingsScreen.kt")
        assertTrue(screen.contains("onOpenLog"))
        assertTrue(screen.contains("onOpenFoundation"))
        assertTrue(screen.contains("SettingsPage.LOG"))
        assertTrue(screen.contains("SettingsPage.FOUNDATION"))
    }

    @Test
    fun weekGeneratorIsSectionedNotChipSoup() {
        val schedule = readOwned("ui/settings/SchedulePrefsSection.kt")
        assertTrue(schedule.contains("Training days"))
        assertTrue(schedule.contains("Training weekdays"))
        assertTrue(schedule.contains("Training age"))
        assertTrue(schedule.contains("Where you train"))
        assertTrue(schedule.contains("SettingsRadioList("))
        assertTrue(schedule.contains("onTrainingAge"))
        assertTrue(schedule.contains("onTrainingPlace"))
        assertFalse(schedule.contains("FlowRow"))
        assertFalse(schedule.contains("PreferenceBlock"))
        val screen = readOwned("ui/settings/SettingsScreen.kt")
        assertTrue(screen.contains("viewModel::generateWeek"))
        assertTrue(screen.contains("viewModel::setTrainingAge"))
        assertTrue(screen.contains("viewModel::setTrainingPlace"))
    }

    @Test
    fun reminderTimeIsAScrollWheelNotHourChips() {
        val reminders = readOwned("ui/reminders/ReminderPrefsSection.kt")
        assertTrue(reminders.contains("ReminderTimeWheel"))
        assertTrue(reminders.contains("GymCard"))
        assertFalse(reminders.contains("(1..12).forEach"))
        assertFalse(reminders.contains("ReminderCopy.minuteChoices"))
        assertFalse(reminders.contains("FlowRow"))

        val wheel = readOwned("ui/reminders/ReminderTimeWheel.kt")
        assertTrue(wheel.contains("SnapWheelColumn"))
        assertTrue(wheel.contains("periodLabels"))
        assertFalse(wheel.contains("InstrumentChip"))
        val snap = readOwned("ui/components/SnapWheel.kt")
        assertTrue(snap.contains("VerticalPager"))
        assertTrue(snap.contains("PageSize.Fixed"))
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
        assertTrue(home.contains("DebugUpdateBanner"))
        assertTrue(home.contains("rememberDebugUpdatePort"))
        assertTrue(home.contains("debugUpdate::install"))
        val screen = readOwned("ui/settings/SettingsScreen.kt")
        assertTrue(screen.contains("DebugUpdateBanner"))
        assertTrue(screen.contains("BuildConfig.DEBUG"))
        assertTrue(screen.contains("debugUpdate::install"))
        assertFalse(screen.contains("Get started"))
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
