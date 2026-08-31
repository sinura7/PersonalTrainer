package com.sinura.personaltrainer.data.repository

import androidx.room.withTransaction
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.data.mapper.toEntity
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilDateTime
import com.sinura.personaltrainer.domain.DayBlockOrder
import com.sinura.personaltrainer.domain.MissedWorkChoice
import com.sinura.personaltrainer.domain.MissedWorkDecision
import com.sinura.personaltrainer.domain.MissedWorkPolicy
import com.sinura.personaltrainer.domain.MoveToToday
import com.sinura.personaltrainer.domain.OccurrenceGenerator
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ReminderDecision
import com.sinura.personaltrainer.domain.ReminderDelivery
import com.sinura.personaltrainer.domain.ReminderDeliveryStatus
import com.sinura.personaltrainer.domain.ReminderPolicy
import com.sinura.personaltrainer.domain.ReminderPreferences
import com.sinura.personaltrainer.domain.ReminderRebuildAction
import com.sinura.personaltrainer.domain.ReminderScheduler
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.ScheduleRule
import com.sinura.personaltrainer.domain.SlotRuleImport
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.ZonePolicy
import com.sinura.personaltrainer.util.JvmTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlannerRepository(
    private val database: TemperDatabase,
    private val scheduler: ReminderScheduler,
    private val time: TimePort = JvmTime,
) {
    private val dao = database.plannerDao()

    fun observeRules(): Flow<List<ScheduleRule>> =
        dao.observeRules().map { rows -> rows.map { it.toDomain() } }

    fun observeOccurrences(): Flow<List<ScheduleOccurrence>> =
        dao.observeOccurrences().map { rows -> rows.map { it.toDomain() } }

    fun observeDecisions(): Flow<List<MissedWorkDecision>> =
        dao.observeDecisions().map { rows -> rows.map { it.toDomain() } }

    suspend fun rules(): List<ScheduleRule> = dao.getRules().map { it.toDomain() }

    suspend fun occurrencesBetween(startEpochDay: Long, endEpochDay: Long): List<ScheduleOccurrence> =
        dao.getOccurrencesBetween(startEpochDay, endEpochDay).map { it.toDomain() }

    suspend fun decisionFor(weekStartEpochDay: Long): MissedWorkDecision? =
        dao.getDecision(weekStartEpochDay)?.toDomain()

    suspend fun getRule(id: String): ScheduleRule? = dao.getRule(id)?.toDomain()

    suspend fun getOccurrence(id: String): ScheduleOccurrence? = dao.getOccurrence(id)?.toDomain()

    /**
     * One-shot: empty rule table plus existing pins becomes evening-strength rules.
     * Later pins sync through [syncSlotsToRules].
     */
    suspend fun importSlotsIfNeeded() {
        if (dao.ruleCount() > 0) return
        val slots = database.scheduleDao().getAll().mapNotNull { it.toDomain() }
        val now = time.nowMillis()
        val imported = SlotRuleImport.rulesFromSlots(slots, now)
        if (imported.isNotEmpty()) dao.upsertRules(imported.map { it.toEntity() })
    }

    suspend fun syncSlotsToRules() {
        val slots = database.scheduleDao().getAll().mapNotNull { it.toDomain() }
        val rules = dao.getRules().map { it.toDomain() }
        val now = time.nowMillis()
        val slotIds = slots.map { it.id }.toSet()
        val byId = rules.associateBy { it.id }
        val toUpsert = slots.mapNotNull { slot ->
            SlotRuleImport.upsertFromSlot(byId[SlotRuleImport.ruleIdForSlot(slot.id)], slot, now)
        }
        if (toUpsert.isNotEmpty()) dao.upsertRules(toUpsert.map { it.toEntity() })
        val importedIds = rules.map { it.id }.filter { SlotRuleImport.isImportedSlotRule(it) }.toSet()
        for (ruleId in importedIds) {
            val slotId = ruleId.removePrefix("rule-")
            if (slotId !in slotIds && rules.firstOrNull { it.id == ruleId }?.modality == ScheduleModality.STRENGTH) {
                dao.deleteRule(ruleId)
            }
        }
    }

    suspend fun upsertRule(rule: ScheduleRule) {
        dao.upsertRule(rule.toEntity())
    }

    /**
     * Hour on an existing block. Planned occurrences for the rule move with
     * it so Home and reminders do not keep the old clock.
     */
    suspend fun setRuleHour(ruleId: String, hour: Int) {
        val nextHour = hour.coerceIn(0, 23)
        val nowMs = time.nowMillis()
        database.withTransaction {
            val current = dao.getRule(ruleId)?.toDomain() ?: return@withTransaction
            val rule = current.copy(hour = nextHour, updatedAtMs = nowMs)
            dao.upsertRule(rule.toEntity())
            val zoneId = rule.resolveZoneId(time.defaultZoneId())
            val updated = mutableListOf<ScheduleOccurrence>()
            for (row in dao.getOccurrencesForRule(ruleId)) {
                if (row.status != OccurrenceStatus.PLANNED.name) continue
                val captured = time.resolveLocal(
                    com.sinura.personaltrainer.domain.CivilDateTime(
                        CivilDate.fromEpochDay(row.localEpochDay),
                        nextHour,
                        row.minute,
                    ),
                    zoneId,
                )
                val next = row.toDomain().copy(
                    hour = nextHour,
                    captured = captured,
                    updatedAtMs = nowMs,
                )
                cancelReminders(row.id)
                dao.upsertOccurrence(next.toEntity())
                updated.add(next)
            }
            scheduleRemindersLocked(updated, listOf(rule), nowMs)
        }
    }

    /**
     * Permute stored hours so Home/Plan order matches [moves].
     * Hours remain the sort key; they are not shown (ADR-020).
     */
    suspend fun applyDayOrder(moves: List<DayBlockOrder.HourMove>) {
        if (moves.isEmpty()) return
        val nowMs = time.nowMillis()
        database.withTransaction {
            val updated = mutableListOf<ScheduleOccurrence>()
            val touchedRules = mutableListOf<ScheduleRule>()
            for (move in moves) {
                val nextHour = move.hour.coerceIn(0, 23)
                val occRow = dao.getOccurrence(move.occurrenceId) ?: continue
                val ruleRow = dao.getRule(move.ruleId) ?: continue
                var rule = ruleRow.toDomain()
                if (rule.hour != nextHour) {
                    rule = rule.copy(hour = nextHour, updatedAtMs = nowMs)
                    dao.upsertRule(rule.toEntity())
                }
                touchedRules.add(rule)
                val zoneId = rule.resolveZoneId(time.defaultZoneId())
                val captured = time.resolveLocal(
                    CivilDateTime(
                        CivilDate.fromEpochDay(occRow.localEpochDay),
                        nextHour,
                        occRow.minute,
                    ),
                    zoneId,
                )
                val next = occRow.toDomain().copy(
                    hour = nextHour,
                    captured = captured,
                    updatedAtMs = nowMs,
                )
                if (occRow.status == OccurrenceStatus.PLANNED.name) {
                    cancelReminders(occRow.id)
                }
                dao.upsertOccurrence(next.toEntity())
                if (next.status == OccurrenceStatus.PLANNED) updated.add(next)
            }
            scheduleRemindersLocked(updated, touchedRules.distinctBy { it.id }, nowMs)
        }
    }

    suspend fun setRuleEnabled(ruleId: String, enabled: Boolean) {
        val current = dao.getRule(ruleId)?.toDomain() ?: return
        if (current.enabled == enabled) return
        dao.upsertRule(
            current.copy(enabled = enabled, updatedAtMs = time.nowMillis()).toEntity(),
        )
    }

    suspend fun addTimedRule(
        weekday: Weekday,
        hour: Int,
        minute: Int,
        modality: ScheduleModality,
        routineId: String? = null,
        templateId: String? = null,
        nowMs: Long = time.nowMillis(),
    ): ScheduleRule {
        val rule = ScheduleRule(
            id = "rule-${modality.name.lowercase()}-${weekday.isoValue}-${UUID.randomUUID()}",
            weekday = weekday,
            hour = hour,
            minute = minute,
            modality = modality,
            zonePolicy = ZonePolicy.FOLLOW_DEVICE,
            routineId = routineId,
            templateId = templateId,
            reminderOffsetMinutes = 0,
            enabled = true,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )
        dao.upsertRule(rule.toEntity())
        return rule
    }

    /**
     * Drops a user-added timed rule (morning cardio, later strength).
     * Imported evening pins stay on Unpin. Planned occurrences for the
     * rule go with it; DONE rows stay as history.
     */
    suspend fun removeTimedRule(ruleId: String) {
        if (!SlotRuleImport.isUserTimedRule(ruleId)) return
        database.withTransaction {
            val occs = dao.getOccurrencesForRule(ruleId)
            for (row in occs) {
                if (row.status == OccurrenceStatus.PLANNED.name) {
                    cancelReminders(row.id)
                    dao.deleteOccurrence(row.id)
                }
            }
            dao.deleteRule(ruleId)
        }
    }

    suspend fun ensureWeek(
        weekStart: CivilDate,
        deviceZoneId: String = time.defaultZoneId(),
        nowMs: Long = time.nowMillis(),
    ): List<ScheduleOccurrence> {
        return database.withTransaction {
            val rules = dao.getRules().map { it.toDomain() }
            val existing = dao.getOccurrencesBetween(weekStart.epochDay, weekStart.epochDay + 6)
                .map { it.toDomain() }
            val week = OccurrenceGenerator.generateWeek(
                weekStart = weekStart,
                rules = rules,
                existing = existing,
                time = time,
                deviceZoneId = deviceZoneId,
                nowMs = nowMs,
            )
            dao.upsertOccurrences(week.map { it.toEntity() })
            scheduleRemindersLocked(week, rules, nowMs)
            week
        }
    }

    suspend fun publishPinnedWeek(weekStart: Weekday, todayEpochDay: Long) {
        syncSlotsToRules()
        ensureWeek(CivilDate.fromEpochDay(todayEpochDay).previousOrSame(weekStart))
    }

    suspend fun applyMissedWork(
        choice: MissedWorkChoice,
        weekStart: CivilDate,
        todayEpochDay: Long,
        nowMinutesOfDay: Int,
        deviceZoneId: String = time.defaultZoneId(),
        nowMs: Long = time.nowMillis(),
    ) {
        database.withTransaction {
            val rules = dao.getRules().map { it.toDomain() }
            val existing = dao.getOccurrencesBetween(weekStart.epochDay, weekStart.epochDay + 6)
                .map { it.toDomain() }
            val result = MissedWorkPolicy.apply(
                choice = choice,
                occurrences = existing,
                weekStart = weekStart,
                todayEpochDay = todayEpochDay,
                nowMinutesOfDay = nowMinutesOfDay,
                nowMs = nowMs,
                time = time,
                deviceZoneId = deviceZoneId,
                rules = rules,
            )
            // Adapt can retire rows (a removed rule's future PLANNED days); their
            // reminder deliveries cascade with them.
            result.removed.forEach { dao.deleteOccurrence(it) }
            dao.upsertOccurrences(result.occurrences.map { it.toEntity() })
            dao.upsertDecision(
                MissedWorkDecision(
                    weekStartEpochDay = weekStart.epochDay,
                    choice = choice,
                    decidedAtMs = nowMs,
                ).toEntity(),
            )
            scheduleRemindersLocked(result.occurrences, rules, nowMs)
        }
    }

    suspend fun markOccurrenceDone(occurrenceId: String, activityId: String, nowMs: Long = time.nowMillis()) {
        val current = dao.getOccurrence(occurrenceId) ?: return
        dao.upsertOccurrence(
            current.copy(
                status = OccurrenceStatus.DONE.name,
                completedActivityId = activityId,
                updatedAtMs = nowMs,
            ),
        )
        cancelReminders(occurrenceId)
    }

    suspend fun skipOccurrence(occurrenceId: String, nowMs: Long = time.nowMillis()) {
        val current = dao.getOccurrence(occurrenceId) ?: return
        dao.upsertOccurrence(
            current.copy(status = OccurrenceStatus.SKIPPED.name, updatedAtMs = nowMs),
        )
        cancelReminders(occurrenceId)
    }

    suspend fun getDelivery(id: String): ReminderDelivery? = dao.getDelivery(id)?.toDomain()

    suspend fun processDueDelivery(
        deliveryId: String,
        prefs: ReminderPreferences,
        nowLocalMinutes: Int,
        nowMs: Long = time.nowMillis(),
        onDeliver: (ScheduleOccurrence, ReminderDelivery) -> Unit,
    ) {
        val delivery = dao.getDelivery(deliveryId)?.toDomain() ?: return
        val occurrence = dao.getOccurrence(delivery.occurrenceId)?.toDomain()
        when (
            val decision = ReminderPolicy.decide(
                delivery,
                occurrence,
                nowMs,
                prefs,
                nowLocalMinutes,
            )
        ) {
            ReminderDecision.DELIVER -> {
                if (occurrence != null) {
                    dao.upsertDelivery(
                        delivery.toEntity().copy(
                            status = ReminderDeliveryStatus.DELIVERED.name,
                            updatedAtMs = nowMs,
                        ),
                    )
                    onDeliver(occurrence, delivery)
                }
            }
            ReminderDecision.STALE -> dao.upsertDelivery(
                delivery.toEntity().copy(
                    status = ReminderDeliveryStatus.STALE.name,
                    updatedAtMs = nowMs,
                ),
            )
            ReminderDecision.SKIP -> Unit
            ReminderDecision.TOO_EARLY -> scheduler.schedule(delivery)
            ReminderDecision.DEFER_QUIET -> {
                val delayMin = ReminderPolicy.minutesUntilQuietEnd(nowLocalMinutes, prefs.quietEndHour)
                val deferred = delivery.copy(
                    scheduledAtMs = nowMs + delayMin * 60_000L,
                    updatedAtMs = nowMs,
                )
                dao.upsertDelivery(deferred.toEntity())
                scheduler.schedule(deferred)
            }
        }
    }

    suspend fun snoozeDelivery(deliveryId: String, nowMs: Long = time.nowMillis()) {
        val current = dao.getDelivery(deliveryId)?.toDomain() ?: return
        dao.upsertDelivery(
            current.toEntity().copy(
                status = ReminderDeliveryStatus.SNOOZED.name,
                updatedAtMs = nowMs,
            ),
        )
        scheduler.cancel(deliveryId)
        val snoozed = ReminderDelivery(
            id = "rem-${current.occurrenceId}-${nowMs}",
            occurrenceId = current.occurrenceId,
            scheduledAtMs = nowMs + ReminderPolicy.SNOOZE_MINUTES * 60_000L,
            status = ReminderDeliveryStatus.PENDING,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )
        dao.upsertDelivery(snoozed.toEntity())
        scheduler.schedule(snoozed)
    }

    suspend fun markDeliveryStatus(
        deliveryId: String,
        status: ReminderDeliveryStatus,
        nowMs: Long = time.nowMillis(),
    ) {
        val current = dao.getDelivery(deliveryId) ?: return
        dao.upsertDelivery(current.copy(status = status.name, updatedAtMs = nowMs))
        if (status != ReminderDeliveryStatus.PENDING) scheduler.cancel(deliveryId)
    }

    suspend fun moveOccurrenceForward(occurrenceId: String, nowMs: Long = time.nowMillis()) {
        val current = getOccurrence(occurrenceId) ?: return
        val rule = getRule(current.ruleId)
        var day = current.localEpochDay + 1
        val existing = dao.getOccurrencesBetween(day, day + 13).map { it.toDomain() }
        val occupied = existing.map { it.ruleId to it.localEpochDay }.toSet()
        while ((current.ruleId to day) in occupied && day < current.localEpochDay + 14) {
            day += 1
        }
        dao.upsertOccurrence(
            current.copy(status = OccurrenceStatus.MOVED, updatedAtMs = nowMs).toEntity(),
        )
        cancelReminders(occurrenceId)
        val zoneId = rule?.resolveZoneId(time.defaultZoneId()) ?: current.captured.zoneId
        val captured = time.resolveLocal(
            com.sinura.personaltrainer.domain.CivilDateTime(
                CivilDate.fromEpochDay(day),
                current.hour,
                current.minute,
            ),
            zoneId,
        )
        val moved = ScheduleOccurrence(
            id = OccurrenceGenerator.occurrenceId(current.ruleId, day),
            ruleId = current.ruleId,
            status = OccurrenceStatus.PLANNED,
            captured = captured,
            hour = current.hour,
            minute = current.minute,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )
        dao.upsertOccurrence(moved.toEntity())
        scheduleIfPending(moved, rule, nowMs)
    }

    /**
     * Put one leftover occurrence on [todayEpochDay] (ADR-019). Recurrence
     * does not change. Returns null when the id is gone.
     */
    suspend fun moveOccurrenceToDay(
        occurrenceId: String,
        todayEpochDay: Long,
        nowMs: Long = time.nowMillis(),
    ): MoveToToday.Outcome? {
        val current = getOccurrence(occurrenceId) ?: return null
        val rule = getRule(current.ruleId)
        val onToday = dao.getOccurrencesBetween(todayEpochDay, todayEpochDay).map { it.toDomain() }
        val outcome = MoveToToday.decide(
            current = current,
            todayEpochDay = todayEpochDay,
            existingOnToday = onToday,
            rule = rule,
            nowMs = nowMs,
            time = time,
            deviceZoneId = time.defaultZoneId(),
        )
        if (outcome is MoveToToday.Outcome.Relocate) {
            dao.upsertOccurrence(outcome.vacated.toEntity())
            cancelReminders(current.id)
            dao.upsertOccurrence(outcome.created.toEntity())
            scheduleIfPending(outcome.created, rule, nowMs)
        }
        return outcome
    }

    private suspend fun scheduleIfPending(
        occurrence: ScheduleOccurrence,
        rule: ScheduleRule?,
        nowMs: Long,
    ) {
        val delivery = ReminderDelivery(
            id = "rem-${occurrence.id}",
            occurrenceId = occurrence.id,
            scheduledAtMs = ReminderPolicy.scheduledAtMillis(
                occurrence,
                rule?.reminderOffsetMinutes ?: 0,
            ),
            status = ReminderDeliveryStatus.PENDING,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )
        if (delivery.scheduledAtMs >= nowMs) {
            dao.upsertDelivery(delivery.toEntity())
            scheduler.schedule(delivery)
        }
    }

    suspend fun rebuildReminders(nowMs: Long = time.nowMillis()) {
        val deliveries = dao.getDeliveries()
        for (row in deliveries) {
            when (ReminderPolicy.rebuildAction(row.toDomain(), nowMs)) {
                ReminderRebuildAction.RESCHEDULE -> scheduler.schedule(row.toDomain())
                ReminderRebuildAction.MARK_STALE -> {
                    dao.upsertDelivery(
                        row.copy(status = ReminderDeliveryStatus.STALE.name, updatedAtMs = nowMs),
                    )
                    scheduler.cancel(row.id)
                }
                ReminderRebuildAction.IGNORE -> Unit
            }
        }
    }

    private suspend fun scheduleRemindersLocked(
        occurrences: List<ScheduleOccurrence>,
        rules: List<ScheduleRule>,
        nowMs: Long,
    ) {
        val rulesById = rules.associateBy { it.id }
        for (occurrence in occurrences) {
            val existing = dao.getDeliveriesForOccurrence(occurrence.id)
            if (occurrence.status != OccurrenceStatus.PLANNED) {
                for (row in existing) {
                    dao.upsertDelivery(
                        row.copy(status = ReminderDeliveryStatus.CANCELLED.name, updatedAtMs = nowMs),
                    )
                    scheduler.cancel(row.id)
                }
                continue
            }
            if (existing.any { it.status == ReminderDeliveryStatus.PENDING.name }) continue
            val rule = rulesById[occurrence.ruleId]
            val scheduledAt = ReminderPolicy.scheduledAtMillis(
                occurrence,
                rule?.reminderOffsetMinutes ?: 0,
            )
            if (scheduledAt < nowMs) continue
            val delivery = ReminderDelivery(
                id = "rem-${occurrence.id}",
                occurrenceId = occurrence.id,
                scheduledAtMs = scheduledAt,
                status = ReminderDeliveryStatus.PENDING,
                createdAtMs = nowMs,
                updatedAtMs = nowMs,
            )
            dao.upsertDelivery(delivery.toEntity())
            scheduler.schedule(delivery)
        }
    }

    private suspend fun cancelReminders(occurrenceId: String) {
        val existing = dao.getDeliveriesForOccurrence(occurrenceId)
        val now = time.nowMillis()
        for (row in existing) {
            if (row.status == ReminderDeliveryStatus.PENDING.name) {
                dao.upsertDelivery(
                    row.copy(status = ReminderDeliveryStatus.CANCELLED.name, updatedAtMs = now),
                )
            }
            scheduler.cancel(row.id)
        }
        scheduler.cancelForOccurrence(occurrenceId)
    }
}
