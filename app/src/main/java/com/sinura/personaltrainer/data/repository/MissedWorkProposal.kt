package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.domain.MissedWorkChoice
import com.sinura.personaltrainer.domain.MissedWorkDecision
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.ScheduleRule
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.MissedWorkPolicy
import com.sinura.personaltrainer.domain.TimePort

/** One immutable preview. Its exact dates/identities, rather than a later clock, are accepted. */
internal data class MissedWorkProposal(
    val weekStartEpochDay: Long,
    val todayEpochDay: Long,
    val nowMinutesOfDay: Int,
    val deviceZoneId: String,
    val atMs: Long,
    val choice: MissedWorkChoice,
    val rulesBefore: List<ScheduleRule>,
    val occurrencesBefore: List<ScheduleOccurrence>,
    val occurrencesAfter: List<ScheduleOccurrence>,
    val removedIds: List<String>,
) {
    val decision: MissedWorkDecision get() = MissedWorkDecision(weekStartEpochDay, choice, atMs)
    override fun toString(): String = "MissedWorkProposal(redacted)"
}

/** A decoded private command must still be precisely the accepted policy's result. */
internal fun MissedWorkProposal.validate(time: TimePort) {
    require(todayEpochDay in weekStartEpochDay..Math.addExact(weekStartEpochDay, 6))
    require(nowMinutesOfDay in 0..1439 && atMs >= 0)
    java.time.ZoneId.of(deviceZoneId)
    require(rulesBefore.map { it.id }.distinct().size == rulesBefore.size)
    require(occurrencesBefore.map { it.id }.distinct().size == occurrencesBefore.size)
    require(occurrencesAfter.map { it.id }.distinct().size == occurrencesAfter.size)
    require(rulesBefore == rulesBefore.sortedBy { it.id })
    require(occurrencesBefore == occurrencesBefore.sortedBy { it.id })
    require(occurrencesAfter == occurrencesAfter.sortedBy { it.id })
    rulesBefore.forEach {
        require(it.id.isNotBlank() && it.hour in 0..23 && it.minute in 0..59)
        java.time.ZoneId.of(it.resolveZoneId(deviceZoneId))
    }
    (occurrencesBefore + occurrencesAfter).forEach {
        require(it.id.isNotBlank() && it.ruleId.isNotBlank() && it.hour in 0..23 && it.minute in 0..59)
        require(it.localEpochDay in weekStartEpochDay..weekStartEpochDay + 6)
        java.time.ZoneId.of(it.captured.zoneId)
    }
    require(MissedWorkPolicy.overdue(occurrencesBefore, todayEpochDay).isNotEmpty())
    val result = MissedWorkPolicy.apply(choice, occurrencesBefore, CivilDate.fromEpochDay(weekStartEpochDay),
        todayEpochDay, nowMinutesOfDay, atMs, time, deviceZoneId, rulesBefore)
    require(result.occurrences.sortedBy { it.id } == occurrencesAfter && result.removed.sorted() == removedIds)
}
