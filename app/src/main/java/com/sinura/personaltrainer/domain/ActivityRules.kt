package com.sinura.personaltrainer.domain

/**
 * Pure authoring and concurrency rules for [ActivitySession] (ADR-007).
 *
 * Persistence and UI call these; they do not invent a second policy.
 */
object ActivityRules {
    fun derivedRate(block: CardioBlock): DerivedCardioRate {
        val seconds = (block.movingSeconds ?: block.elapsedSeconds).takeIf { it > 0L }
        val meters = block.distanceMeters?.takeIf { it > 0.0 }
        if (seconds == null || meters == null) {
            return DerivedCardioRate(secondsPerMeter = null, metersPerSecond = null)
        }
        return DerivedCardioRate(
            secondsPerMeter = seconds.toDouble() / meters,
            metersPerSecond = meters / seconds.toDouble(),
        )
    }

    /**
     * Reject independently contradictory pace/speed/distance/time triples.
     *
     * [claimedSecondsPerMeter] and [claimedMetersPerSecond] are optional
     * authoring inputs. When present they must match the derived rate
     * within [RATE_TOLERANCE].
     */
    fun cardioMetricsAgree(
        block: CardioBlock,
        claimedSecondsPerMeter: Double? = null,
        claimedMetersPerSecond: Double? = null,
    ): Boolean {
        val derived = derivedRate(block)
        if (claimedSecondsPerMeter != null) {
            val expected = derived.secondsPerMeter ?: return false
            if (kotlin.math.abs(claimedSecondsPerMeter - expected) > RATE_TOLERANCE) return false
        }
        if (claimedMetersPerSecond != null) {
            val expected = derived.metersPerSecond ?: return false
            if (kotlin.math.abs(claimedMetersPerSecond - expected) > RATE_TOLERANCE) return false
        }
        return true
    }

    fun canStartLive(existing: List<ActivitySession>): Boolean =
        existing.none { it.status == ActivityStatus.ACTIVE }

    fun rejectFuture(
        performed: CapturedCivilTime,
        now: CapturedCivilTime,
    ): Boolean = performed.localEpochDay > now.localEpochDay

    /**
     * A confirmed write. A canceled editor never calls this — drafts
     * that are discarded produce no [ActivitySession].
     */
    fun confirm(
        draft: ActivityDraft,
        existing: List<ActivitySession>,
        now: CapturedCivilTime,
        ids: IdPort,
        clock: TimePort,
    ): ActivityWrite {
        if (draft.blocks.isEmpty()) return ActivityWrite.Rejected("Nothing to save.")
        if (rejectFuture(draft.performedStart, now)) {
            return ActivityWrite.Rejected("Future dates are not saved.")
        }
        if (draft.origin == ActivityOrigin.LIVE && !canStartLive(existing)) {
            return ActivityWrite.Rejected("One live activity at a time.")
        }
        if (draft.cardioClaims.any { !cardioMetricsAgree(it.block, it.secondsPerMeter, it.metersPerSecond) }) {
            return ActivityWrite.Rejected("Pace and speed must match distance and time.")
        }
        val nowMs = clock.nowMillis()
        val session = ActivitySession(
            id = draft.id ?: ids.newId(),
            status = draft.status,
            origin = draft.origin,
            source = draft.source,
            title = draft.title,
            notes = draft.notes,
            performedStart = draft.performedStart,
            performedEnd = draft.performedEnd,
            templateId = draft.templateId,
            occurrenceId = draft.occurrenceId,
            blocks = draft.blocks,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
            revision = 1L,
        )
        return ActivityWrite.Accepted(session)
    }

    const val RATE_TOLERANCE = 0.000_001
}

/**
 * In-memory editor state. Discarding it writes nothing.
 */
data class ActivityDraft(
    val id: String? = null,
    val status: ActivityStatus = ActivityStatus.COMPLETED,
    val origin: ActivityOrigin,
    val source: ActivitySource = ActivitySource.TEMPER,
    val title: String,
    val notes: String = "",
    val performedStart: CapturedCivilTime,
    val performedEnd: CapturedCivilTime? = null,
    val templateId: String? = null,
    val occurrenceId: String? = null,
    val blocks: List<ActivityBlock>,
    val cardioClaims: List<CardioRateClaim> = emptyList(),
)

data class CardioRateClaim(
    val block: CardioBlock,
    val secondsPerMeter: Double? = null,
    val metersPerSecond: Double? = null,
)

sealed class ActivityWrite {
    data class Accepted(val session: ActivitySession) : ActivityWrite()
    data class Rejected(val reason: String) : ActivityWrite()
}

/**
 * Read models over a list of activities. Persistence later implements
 * the same questions against the shadow database.
 */
object ActivityQueries {
    fun live(sessions: List<ActivitySession>): ActivitySession? =
        sessions.firstOrNull { it.status == ActivityStatus.ACTIVE }

    fun onLocalDate(sessions: List<ActivitySession>, localEpochDay: Long): List<ActivitySession> =
        sessions.filter { it.localEpochDay == localEpochDay }

    fun completedOn(sessions: List<ActivitySession>, localEpochDay: Long): List<ActivitySession> =
        onLocalDate(sessions, localEpochDay).filter { it.isCompleted }

    fun cardioOnlyDays(sessions: List<ActivitySession>): Set<Long> =
        sessions.filter { it.isCompleted }
            .groupBy { it.localEpochDay }
            .filter { (_, day) -> day.isNotEmpty() && day.all { it.isCardioOnly } }
            .keys
}
