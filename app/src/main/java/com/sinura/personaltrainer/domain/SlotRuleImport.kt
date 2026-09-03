package com.sinura.personaltrainer.domain

/**
 * One-shot mapping from the v2 pinned cycle onto weekday rules.
 *
 * Imported slots become evening strength at 18:00. User-added cardio
 * defaults to 07:00. A later strength session on the same weekday lands
 * two hours after the latest existing row (20:00 after a typical 18:00
 * pin). The slot table stays; this does not rewrite it.
 *
 * Extra timed rules use ids like `rule-strength-…` and `rule-cardio-…`.
 * Pin / swap / unpin must not treat those as imported slot rules or they
 * vanish on the next sync.
 */
object SlotRuleImport {
    const val DEFAULT_STRENGTH_HOUR = 18
    const val DEFAULT_CARDIO_HOUR = 7
    const val LATER_GAP_HOURS = 2

    fun ruleIdForSlot(slotId: String): String = "rule-$slotId"

    fun isUserTimedRule(ruleId: String): Boolean =
        ruleId.startsWith("rule-strength-") ||
            ruleId.startsWith("rule-cardio-") ||
            ruleId.startsWith("rule-mixed-")

    fun isImportedSlotRule(ruleId: String): Boolean =
        ruleId.startsWith("rule-") && !isUserTimedRule(ruleId)

    /**
     * Next free hour for another session on a weekday that already has
     * rows. Stays after the evening pin so accessory work is later, not
     * a second 18:00.
     */
    fun nextLaterHour(existingHours: Iterable<Int>): Int {
        val occupied = existingHours.map { it.coerceIn(0, 23) }.toSet()
        val floor = maxOf(occupied.maxOrNull() ?: 0, DEFAULT_STRENGTH_HOUR)
        val preferred = floor + LATER_GAP_HOURS
        if (preferred <= 23 && preferred !in occupied) return preferred
        for (hour in (floor + 1)..23) {
            if (hour !in occupied) return hour
        }
        for (hour in (DEFAULT_STRENGTH_HOUR - 1) downTo 12) {
            if (hour !in occupied) return hour
        }
        for (hour in 0..23) {
            if (hour !in occupied) return hour
        }
        return DEFAULT_STRENGTH_HOUR
    }

    /**
     * Same-day add: never land at or before the current hour. A 21:00
     * cardio must not persist 07:00; a later session must not persist 18:00.
     */
    fun clampSameDayHour(preferredHour: Int, nowMinutes: Int): Int {
        val preferred = preferredHour.coerceIn(0, 23)
        val nextHour = (nowMinutes / 60) + 1
        return maxOf(preferred, nextHour.coerceIn(0, 23))
    }

    fun hourOnDay(
        preferredHour: Int,
        epochDay: Long,
        todayEpochDay: Long,
        nowMinutes: Int,
    ): Int {
        if (epochDay != todayEpochDay) return preferredHour.coerceIn(0, 23)
        return clampSameDayHour(preferredHour, nowMinutes)
    }

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
