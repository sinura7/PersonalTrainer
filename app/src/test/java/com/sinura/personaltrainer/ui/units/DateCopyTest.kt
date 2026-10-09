package com.sinura.personaltrainer.ui.units

import com.sinura.personaltrainer.domain.ClockFormat
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateCopyTest {
    @Test
    fun fullCivilDatesDisambiguateYearWithoutChangingHistoryCopy() {
        val december = LocalDate.of(2026, 12, 31)
        val january = LocalDate.of(2027, 1, 1)
        assertEquals("Thursday 31 December 2026", DateCopy.weekdayFullDate(december))
        assertEquals("Friday 1 January 2027", DateCopy.weekdayFullDate(january))
        assertEquals("Thursday 31 December", DateCopy.weekdayLong(december))
        assertEquals("Thu 31 Dec 2026", DateCopy.weekdayShort(december))
    }

    @Test
    fun hoursChoicePrintsEnglishDayMonthNotUsMonthFirst() {
        val millis = Instant.parse("2026-01-02T18:30:00Z").toEpochMilli()
        val utc = ZoneId.of("UTC")
        assertEquals(
            "2 Jan 2026, 6:30 PM",
            DateCopy.dateTime(millis, ClockFormat.TWELVE, utc),
        )
        assertEquals(
            "2 Jan 2026, 18:30",
            DateCopy.dateTime(millis, ClockFormat.TWENTY_FOUR, utc),
        )
        assertEquals("Fri 2 Jan 2026", DateCopy.weekdayShort(LocalDate.of(2026, 1, 2)))
        assertFalse(DateCopy.dateTime(millis, ClockFormat.TWELVE, utc).startsWith("Jan 2"))
    }

    @Test
    fun ownedStampsUseDateCopyAndComposerDropsUsLocale() {
        val dateCopy = readMain("ui/units/DateCopy.kt")
        assertTrue(dateCopy.contains("Locale.ENGLISH"))
        assertFalse(dateCopy.contains("Locale.US"))

        val composer = readMain("ui/activity/ActivityComposerScreen.kt")
        assertFalse(composer.contains("Locale.US"))
        assertTrue(composer.contains("DateCopy.weekdayShort"))
        assertTrue(composer.contains("ComposerCopy.cardioEntry"))

        val history = readMain("ui/history/HistoryScreen.kt")
        assertTrue(history.contains("DateCopy.dateTime"))
        assertTrue(history.contains("LocalClockFormat"))

        val session = readMain("ui/history/SessionDetailScreen.kt")
        assertTrue(session.contains("DateCopy.dateTime"))

        val backupSection = readMain("ui/settings/BackupRestoreSection.kt")
        assertTrue(backupSection.contains("DateCopy.dateTime"))
        assertFalse(backupSection.contains("DataStore"))

        val aboutSections = readMain("ui/settings/AboutSections.kt")
        assertTrue(aboutSections.contains("AppLog.redactMessages"))
        assertTrue(aboutSections.contains("SettingsTags.REDACT_LOGS"))
        assertFalse(aboutSections.contains("setRedact"))

        val settings = readMain("ui/settings/SettingsScreen.kt")
        assertTrue(settings.contains("DateCopy.dateTime"))
        assertFalse(settings.contains("DataStore"))
    }

    private fun readMain(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
