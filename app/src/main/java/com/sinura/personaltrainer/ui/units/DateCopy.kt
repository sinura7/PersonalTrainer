package com.sinura.personaltrainer.ui.units

import com.sinura.personaltrainer.domain.ClockCopy
import com.sinura.personaltrainer.domain.ClockFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One stamp grammar for History, session detail, the composer, and backup.
 *
 * Display Hours (Regular / Military) used to relabel four quiet-hours chips
 * and nothing else. These formatters read [ClockFormat] so a 24-hour choice
 * is visible on every timestamp the owned surfaces print. Patterns are
 * English day-month, not month-first American stamps: the APK ships `en` only
 * (J3), and a US locale was printing American stamps for everyone.
 *
 * Lives here, not in `domain/`: the domain seam policy bans `java.time`
 * and `Locale` on the shared-target seam (ADR-003, ADR-011).
 */
object DateCopy {
    private val LOCALE: Locale = Locale.ENGLISH
    private val MONTH_YEAR: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM yyyy", LOCALE)
    private val MONTH_YEAR_SHORT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM yyyy", LOCALE)
    private val WEEKDAY_LONG: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE d MMMM", LOCALE)
    private val WEEKDAY_SHORT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d MMM yyyy", LOCALE)
    private val DAY_MONTH_YEAR: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy", LOCALE)

    fun dateTime(
        millis: Long,
        clock: ClockFormat,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val local = Instant.ofEpochMilli(millis).atZone(zoneId)
        val date = DAY_MONTH_YEAR.format(local.toLocalDate())
        val time = ClockCopy.format(local.hour, local.minute, clock)
        return "$date, $time"
    }

    fun monthYear(month: YearMonth): String = MONTH_YEAR.format(month)

    fun monthYearShort(month: YearMonth): String = MONTH_YEAR_SHORT.format(month)

    fun monthYearShort(day: LocalDate): String = MONTH_YEAR_SHORT.format(day)

    fun weekdayLong(day: LocalDate): String = WEEKDAY_LONG.format(day)

    fun weekdayShort(day: LocalDate): String = WEEKDAY_SHORT.format(day)
}
