package com.sinura.personaltrainer.domain

/**
 * One-shot mapping from the v2 pinned cycle onto weekday rules.
 *
 * Imported slots become evening strength at 18:00. User-added cardio
 * defaults to 07:00. The slot table stays; this does not rewrite it.
 */
object SlotRuleImport {
    const val DEFAULT_STRENGTH_HOUR = 18
    const val DEFAULT_CARDIO_HOUR = 7

    fun ruleIdForSlot(slotId: String): String = "rule-$slotId"

    fun ruleFromSlot(slot: ScheduleSlot, nowMs: Long): ScheduleRule? {
        val weekday = slot.anchorDay ?: return null
        return ScheduleRule(
            id = ruleIdForSlot(slot.id),
            weekday = weekday,
            hour = DEFAULT_STRENGTH_HOUR,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
            zonePolicy = ZonePolicy.FOLLOW_DEVICE,
            fixedZoneId = null,
            routineId = slot.routineId,
            templateId = null,
            focusKind = slot.focusKind,
            reminderOffsetMinutes = 0,
            enabled = true,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )
    }

    fun rulesFromSlots(slots: List<ScheduleSlot>, nowMs: Long): List<ScheduleRule> =
        slots.mapNotNull { ruleFromSlot(it, nowMs) }

    /**
     * Slot pin fields that a later swap or re-pin must copy onto the
     * imported rule. Hour, modality and reminder stay as the rule already
     * has them — those are not slot columns.
     */
    fun upsertFromSlot(existing: ScheduleRule?, slot: ScheduleSlot, nowMs: Long): ScheduleRule? {
        val desired = ruleFromSlot(slot, nowMs) ?: return null
        if (existing == null) return desired
        if (
            existing.weekday == desired.weekday &&
            existing.routineId == desired.routineId &&
            existing.focusKind == desired.focusKind
        ) {
            return null
        }
        return existing.copy(
            weekday = desired.weekday,
            routineId = desired.routineId,
            focusKind = desired.focusKind,
            updatedAtMs = nowMs,
        )
    }
}
