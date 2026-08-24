package com.sinura.personaltrainer.util

import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilDateTime
import com.sinura.personaltrainer.domain.DstGapPolicy
import com.sinura.personaltrainer.domain.DstOverlapChoice
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.domain.UnresolvableLocalTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * JVM / Android adapter for [TimePort].
 *
 * `java.time` stays here, outside `domain/`. Production and the plain-JVM
 * test lane share this object. Tests that need a frozen clock wrap it.
 */
object JvmTime : TimePort {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeMillis(): Long = try {
        android.os.SystemClock.elapsedRealtime()
    } catch (_: Throwable) {
        // Plain-JVM lane has no SystemClock. Rest math injects a fake.
        System.nanoTime() / 1_000_000L
    }

    override fun defaultZoneId(): String = ZoneId.systemDefault().id

    override fun capture(instantMillis: Long, zoneId: String): CapturedCivilTime {
        val zone = ZoneId.of(zoneId)
        val zoned = Instant.ofEpochMilli(instantMillis).atZone(zone)
        return CapturedCivilTime(
            instantMillis = instantMillis,
            zoneId = zone.id,
            offsetSeconds = zoned.offset.totalSeconds,
            localEpochDay = zoned.toLocalDate().toEpochDay(),
        )
    }

    override fun startOfDayMillis(date: CivilDate, zoneId: String): Long {
        val zone = ZoneId.of(zoneId)
        return date.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    }

    override fun minusCivilDays(instantMillis: Long, zoneId: String, days: Long): Long {
        val zone = ZoneId.of(zoneId)
        return Instant.ofEpochMilli(instantMillis)
            .atZone(zone)
            .minusDays(days)
            .toInstant()
            .toEpochMilli()
    }

    override fun resolveLocal(
        local: CivilDateTime,
        zoneId: String,
        overlap: DstOverlapChoice,
        gap: DstGapPolicy,
    ): CapturedCivilTime {
        val zone = ZoneId.of(zoneId)
        val localDateTime = local.toLocalDateTime()
        val rules = zone.rules
        val valid = rules.getValidOffsets(localDateTime)
        if (valid.isEmpty() && gap == DstGapPolicy.REJECT) {
            throw UnresolvableLocalTimeException(local, zone.id)
        }
        val preferred: ZoneOffset? = when {
            valid.size <= 1 -> valid.firstOrNull()
            overlap == DstOverlapChoice.EARLIER -> valid.maxBy { it.totalSeconds }
            else -> valid.minBy { it.totalSeconds }
        }
        // ofLocal shifts a gap forward by the transition duration and honours
        // a preferred offset on overlap. The chosen offset is what we persist.
        val zoned = ZonedDateTime.ofLocal(localDateTime, zone, preferred)
        return CapturedCivilTime(
            instantMillis = zoned.toInstant().toEpochMilli(),
            zoneId = zone.id,
            offsetSeconds = zoned.offset.totalSeconds,
            localEpochDay = zoned.toLocalDate().toEpochDay(),
        )
    }
}

fun CivilDate.toLocalDate(): LocalDate = LocalDate.ofEpochDay(epochDay)

fun LocalDate.toCivilDate(): CivilDate = CivilDate.fromEpochDay(toEpochDay())

fun CivilDateTime.toLocalDateTime(): LocalDateTime =
    LocalDateTime.of(date.year, date.month, date.day, hour, minute, second, nanoOfSecond)

fun com.sinura.personaltrainer.domain.CivilYearMonth.toYearMonth(): java.time.YearMonth =
    java.time.YearMonth.of(year, month)

fun java.time.YearMonth.toCivilYearMonth(): com.sinura.personaltrainer.domain.CivilYearMonth =
    com.sinura.personaltrainer.domain.CivilYearMonth(year, monthValue)

fun com.sinura.personaltrainer.domain.Weekday.toJavaDayOfWeek(): java.time.DayOfWeek =
    java.time.DayOfWeek.of(isoValue)

fun java.time.DayOfWeek.toWeekday(): com.sinura.personaltrainer.domain.Weekday =
    com.sinura.personaltrainer.domain.Weekday.fromIso(value)
