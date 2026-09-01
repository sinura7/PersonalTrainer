package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CustomWeekPolicy
import com.sinura.personaltrainer.domain.ScheduleKind
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.SlotRuleImport
import com.sinura.personaltrainer.domain.todayEpochDay
import kotlinx.coroutines.flow.first

/**
 * Mints a workout or cardio block on a civil day.
 *
 * Plan add is recurring (enabled rule). Home add may be once: this week's
 * occurrence is generated, then the rule is disabled so later weeks stay
 * empty — the same once path [AuxiliaryBlocks] uses for extras.
 */
object DayBlocks {
    suspend fun addStrength(
        planner: PlannerRepository,
        schedule: ScheduleRepository,
        preferences: PreferencesRepository,
        epochDay: Long,
        routineId: String,
        once: Boolean,
        todayEpochDay: Long = todayEpochDay(),
    ) {
        val weekday = CivilDate.fromEpochDay(epochDay).dayOfWeek
        val rules = planner.rules()
        val dayOccs = planner.occurrencesBetween(epochDay, epochDay)
        val alreadyOnDay = dayOccs.any { occ ->
            val rule = rules.firstOrNull { it.id == occ.ruleId }
            rule?.routineId == routineId
        }
        if (alreadyOnDay) return
        if (once) {
            mintTimed(
                planner = planner,
                preferences = preferences,
                todayEpochDay = todayEpochDay,
                weekday = weekday,
                hour = SlotRuleImport.nextLaterHour(rules.filter { it.weekday == weekday }.map { it.hour }),
                modality = ScheduleModality.STRENGTH,
                routineId = routineId,
                once = true,
            )
            return
        }
        val hasStrength = hasStrengthOnWeekday(planner, epochDay, weekday)
        if (hasStrength) {
            mintTimed(
                planner = planner,
                preferences = preferences,
                todayEpochDay = todayEpochDay,
                weekday = weekday,
                hour = SlotRuleImport.nextLaterHour(rules.filter { it.weekday == weekday }.map { it.hour }),
                modality = ScheduleModality.STRENGTH,
                routineId = routineId,
                once = false,
            )
        } else {
            schedule.pin(routineId = routineId, focusKind = null, anchorDay = weekday)
            publish(planner, preferences, todayEpochDay)
        }
    }

    suspend fun addCardio(
        planner: PlannerRepository,
        preferences: PreferencesRepository,
        epochDay: Long,
        type: CardioType,
        once: Boolean,
        todayEpochDay: Long = todayEpochDay(),
    ) {
        val weekday = CivilDate.fromEpochDay(epochDay).dayOfWeek
        val rules = planner.rules()
        val dayOccs = planner.occurrencesBetween(epochDay, epochDay)
        val alreadyOnDay = dayOccs.any { occ ->
            rules.firstOrNull { it.id == occ.ruleId }?.modality == ScheduleModality.CARDIO
        }
        if (alreadyOnDay) return
        val live = rules.firstOrNull {
            it.enabled && it.weekday == weekday && it.modality == ScheduleModality.CARDIO
        }
        if (live != null) return
        val tag = ScheduleKind.cardio(type)
        val dormant = rules.firstOrNull {
            !it.enabled && it.weekday == weekday && it.templateId == tag
        }
        val hours = rules.filter { it.weekday == weekday }.map { it.hour }
        val hour = SlotRuleImport.DEFAULT_CARDIO_HOUR.takeIf { it !in hours }
            ?: SlotRuleImport.nextLaterHour(hours)
        val ruleId = if (dormant != null) {
            planner.setRuleEnabled(dormant.id, true)
            if (dormant.hour != hour) planner.setRuleHour(dormant.id, hour)
            dormant.id
        } else {
            planner.addTimedRule(
                weekday = weekday,
                hour = hour,
                minute = 0,
                modality = ScheduleModality.CARDIO,
                templateId = tag,
            ).id
        }
        publish(planner, preferences, todayEpochDay)
        if (once) planner.setRuleEnabled(ruleId, false)
    }

    /**
     * Mint (or reuse) a routine, attach it as this day's workout, return
     * the id so the editor can open before Home Start.
     */
    suspend fun composeWorkout(
        planner: PlannerRepository,
        schedule: ScheduleRepository,
        routines: RoutineRepository,
        preferences: PreferencesRepository,
        epochDay: Long,
        once: Boolean,
        todayEpochDay: Long = todayEpochDay(),
    ): String {
        val weekday = CivilDate.fromEpochDay(epochDay).dayOfWeek
        val hasStrength = hasStrengthOnWeekday(planner, epochDay, weekday)
        val name = if (once || hasStrength) {
            CustomWeekPolicy.extraRoutineName(weekday)
        } else {
            CustomWeekPolicy.routineName(weekday)
        }
        val used = planner.rules().mapNotNull { it.routineId }.toSet()
        val reusable = routines.observeAll().first().firstOrNull { routine ->
            routine.name.equals(name, ignoreCase = true) && routine.id !in used
        }
        val routineId = reusable?.id ?: routines.create(name).id
        addStrength(
            planner = planner,
            schedule = schedule,
            preferences = preferences,
            epochDay = epochDay,
            routineId = routineId,
            once = once,
            todayEpochDay = todayEpochDay,
        )
        return routineId
    }

    private suspend fun hasStrengthOnWeekday(
        planner: PlannerRepository,
        epochDay: Long,
        weekday: com.sinura.personaltrainer.domain.Weekday,
    ): Boolean {
        val rules = planner.rules()
        val dayOccs = planner.occurrencesBetween(epochDay, epochDay)
        val onDay = dayOccs.any { occ ->
            val rule = rules.firstOrNull { it.id == occ.ruleId } ?: return@any false
            rule.modality == ScheduleModality.STRENGTH && !ScheduleKind.isAux(rule.templateId)
        }
        if (onDay) return true
        return rules.any {
            it.enabled &&
                it.weekday == weekday &&
                it.modality == ScheduleModality.STRENGTH &&
                !ScheduleKind.isAux(it.templateId)
        }
    }

    private suspend fun mintTimed(
        planner: PlannerRepository,
        preferences: PreferencesRepository,
        todayEpochDay: Long,
        weekday: com.sinura.personaltrainer.domain.Weekday,
        hour: Int,
        modality: ScheduleModality,
        routineId: String?,
        once: Boolean,
        templateId: String? = null,
    ) {
        val ruleId = planner.addTimedRule(
            weekday = weekday,
            hour = hour.coerceIn(0, 23),
            minute = 0,
            modality = modality,
            routineId = routineId,
            templateId = templateId,
        ).id
        publish(planner, preferences, todayEpochDay)
        if (once) planner.setRuleEnabled(ruleId, false)
    }

    private suspend fun publish(
        planner: PlannerRepository,
        preferences: PreferencesRepository,
        todayEpochDay: Long,
    ) {
        val weekStart = preferences.schedulePreferences.first().weekStart
        planner.publishPinnedWeek(weekStart, todayEpochDay)
    }
}
