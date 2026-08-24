package com.sinura.personaltrainer.domain

/**
 * How a durable instant is captured (ADR-011).
 *
 * Display uses [localEpochDay], not the device zone of the later read.
 * Rest-timer deadlines stay on [elapsedRealtimeMillis] and never enter this type.
 */
data class CapturedCivilTime(
    val instantMillis: Long,
    val zoneId: String,
    val offsetSeconds: Int,
    val localEpochDay: Long,
) {
    val localDate: CivilDate get() = CivilDate.fromEpochDay(localEpochDay)
}

/** Fall-back overlap: the same local time occurs twice. */
enum class DstOverlapChoice {
    /** First occurrence (typically still on DST, the larger UTC offset). */
    EARLIER,
    /** Second occurrence (typically standard time, the smaller UTC offset). */
    LATER,
}

/** Spring-forward gap: the local time does not exist. */
enum class DstGapPolicy {
    /** Move forward to the first valid local time after the gap. */
    SHIFT_FORWARD,
    /** Refuse the write. Used when a backdated composer must not invent a time. */
    REJECT,
}

class UnresolvableLocalTimeException(
    val local: CivilDateTime,
    val zoneId: String,
    message: String = "Local time $local does not exist in $zoneId",
) : IllegalArgumentException(message)

/**
 * Injected clock, calendar, and zone rules (ADR-003, ADR-011).
 *
 * Shared-target domain code talks to this port. The JVM adapter may use
 * `java.time`. Android UI may still format with platform locale APIs.
 */
interface TimePort {
    fun nowMillis(): Long

    /**
     * Monotonic elapsed time. Rest timers use this, never a civil clock
     * (ADR-011 §8, ADR-012).
     */
    fun elapsedRealtimeMillis(): Long

    fun defaultZoneId(): String

    fun capture(instantMillis: Long, zoneId: String = defaultZoneId()): CapturedCivilTime

    fun captureNow(zoneId: String = defaultZoneId()): CapturedCivilTime =
        capture(nowMillis(), zoneId)

    fun civilDate(instantMillis: Long, zoneId: String = defaultZoneId()): CivilDate =
        capture(instantMillis, zoneId).localDate

    fun startOfDayMillis(date: CivilDate, zoneId: String): Long

    /** Subtract calendar days, keeping the local time-of-day, resolving DST as the zone rules do. */
    fun minusCivilDays(instantMillis: Long, zoneId: String, days: Long): Long

    /**
     * Resolve a local civil time in [zoneId].
     *
     * Gap and overlap are explicit. The chosen offset is part of the returned
     * four-tuple and is what later writes persist.
     */
    fun resolveLocal(
        local: CivilDateTime,
        zoneId: String,
        overlap: DstOverlapChoice = DstOverlapChoice.EARLIER,
        gap: DstGapPolicy = DstGapPolicy.SHIFT_FORWARD,
    ): CapturedCivilTime
}

/**
 * Stable ID source. Production uses random UUIDs. Tests use a sequential
 * factory so fixtures stay deterministic.
 */
fun interface IdPort {
    fun newId(): String
}
