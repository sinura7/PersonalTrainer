package com.sinura.personaltrainer.data.mapper

import com.sinura.personaltrainer.data.local.entity.MissedWorkDecisionEntity
import com.sinura.personaltrainer.data.local.entity.ReminderDeliveryEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleOccurrenceEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleRuleEntity
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.MissedWorkChoice
import com.sinura.personaltrainer.domain.MissedWorkDecision
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ReminderDelivery
import com.sinura.personaltrainer.domain.ReminderDeliveryStatus
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.ScheduleRule
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.ZonePolicy

fun ScheduleRuleEntity.toDomain(): ScheduleRule = ScheduleRule(
    id = id,
    weekday = Weekday.fromIso(weekday),
    hour = hour,
    minute = minute,
    modality = enumValueOfOr(modality, ScheduleModality.STRENGTH),
    zonePolicy = enumValueOfOr(zonePolicy, ZonePolicy.FOLLOW_DEVICE),
    fixedZoneId = fixedZoneId,
    routineId = routineId,
    templateId = templateId,
    focusKind = focusKind?.let { raw -> SessionFocusKind.entries.firstOrNull { it.name == raw } },
    reminderOffsetMinutes = reminderOffsetMinutes,
    enabled = enabled != 0,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)

fun ScheduleRule.toEntity(): ScheduleRuleEntity = ScheduleRuleEntity(
    id = id,
    weekday = weekday.isoValue,
    hour = hour,
    minute = minute,
    modality = modality.name,
    zonePolicy = zonePolicy.name,
    fixedZoneId = fixedZoneId,
    routineId = routineId,
    templateId = templateId,
    focusKind = focusKind?.name,
    reminderOffsetMinutes = reminderOffsetMinutes,
    enabled = if (enabled) 1 else 0,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)

fun ScheduleOccurrenceEntity.toDomain(): ScheduleOccurrence = ScheduleOccurrence(
    id = id,
    ruleId = ruleId,
    status = enumValueOfOr(status, OccurrenceStatus.PLANNED),
    captured = CapturedCivilTime(
        instantMillis = instantMs,
        zoneId = zoneId,
        offsetSeconds = offsetSeconds,
        localEpochDay = localEpochDay,
    ),
    hour = hour,
    minute = minute,
    completedActivityId = completedActivityId,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)

fun ScheduleOccurrence.toEntity(): ScheduleOccurrenceEntity = ScheduleOccurrenceEntity(
    id = id,
    ruleId = ruleId,
    status = status.name,
    instantMs = captured.instantMillis,
    zoneId = captured.zoneId,
    offsetSeconds = captured.offsetSeconds,
    localEpochDay = captured.localEpochDay,
    hour = hour,
    minute = minute,
    completedActivityId = completedActivityId,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)

fun MissedWorkDecisionEntity.toDomain(): MissedWorkDecision = MissedWorkDecision(
    weekStartEpochDay = weekStartEpochDay,
    choice = enumValueOfOr(choice, MissedWorkChoice.KEEP_DATES),
    decidedAtMs = decidedAtMs,
)

fun MissedWorkDecision.toEntity(): MissedWorkDecisionEntity = MissedWorkDecisionEntity(
    weekStartEpochDay = weekStartEpochDay,
    choice = choice.name,
    decidedAtMs = decidedAtMs,
)

fun ReminderDeliveryEntity.toDomain(): ReminderDelivery = ReminderDelivery(
    id = id,
    occurrenceId = occurrenceId,
    scheduledAtMs = scheduledAtMs,
    status = enumValueOfOr(status, ReminderDeliveryStatus.PENDING),
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)

fun ReminderDelivery.toEntity(): ReminderDeliveryEntity = ReminderDeliveryEntity(
    id = id,
    occurrenceId = occurrenceId,
    scheduledAtMs = scheduledAtMs,
    status = status.name,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)

private inline fun <reified T : Enum<T>> enumValueOfOr(raw: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback
