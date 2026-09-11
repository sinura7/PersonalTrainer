package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.domain.AuxiliaryPack
import com.sinura.personaltrainer.domain.AuxiliaryPacks
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.ScheduleKind
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.SlotRuleImport
import kotlinx.coroutines.flow.first

/**
 * Mints an auxiliary / warm-up pack as its own day block.
 *
 * Plan add is recurring. Home add is once: the generator writes this
 * week's occurrence, then the rule is disabled so next week stays empty.
 */
object AuxiliaryBlocks {
    suspend fun add(
        planner: PlannerRepository,
        routines: RoutineRepository,
        exercises: ExerciseRepository,
        preferences: PreferencesRepository,
        epochDay: Long,
        packId: String,
        once: Boolean,
        todayEpochDay: Long,
        nowMinutes: Int = 0,
    ) {
        val pack = AuxiliaryPacks.byId(packId) ?: return
        val weekday = CivilDate.fromEpochDay(epochDay).dayOfWeek
        val tag = ScheduleKind.aux(pack.id)
        val rules = planner.rules()
        val dayOccs = planner.occurrencesBetween(epochDay, epochDay)
        val alreadyOnDay = dayOccs.any { occ ->
            val rule = rules.firstOrNull { it.id == occ.ruleId }
            ScheduleKind.auxPackId(rule?.templateId) == pack.id
        }
        if (alreadyOnDay) return
        val hours = rules.filter { it.weekday == weekday }.map { it.hour }
        val hour = SlotRuleImport.hourOnDay(
            preferredHour = SlotRuleImport.nextLaterHour(hours),
            epochDay = epochDay,
            todayEpochDay = todayEpochDay,
            nowMinutes = nowMinutes,
        )
        val dormant = rules.firstOrNull {
            !it.enabled && it.weekday == weekday && it.templateId == tag
        }
        val live = rules.firstOrNull {
            it.enabled && it.weekday == weekday && it.templateId == tag
        }
        if (live != null) return
        val ruleId = if (dormant != null) {
            planner.setRuleEnabled(dormant.id, true)
            if (dormant.hour != hour) planner.setRuleHour(dormant.id, hour)
            dormant.id
        } else {
            val routineId = ensureRoutine(pack, routines, exercises)
            planner.addTimedRule(
                weekday = weekday,
                hour = hour,
                minute = 0,
                modality = ScheduleModality.STRENGTH,
                routineId = routineId,
                templateId = tag,
            ).id
        }
        val weekStart = preferences.schedulePreferences.first().weekStart
        planner.publishPinnedWeek(weekStart, todayEpochDay)
        if (once) planner.setRuleEnabled(ruleId, false)
    }

    suspend fun ensureRoutine(
        pack: AuxiliaryPack,
        routines: RoutineRepository,
        exercises: ExerciseRepository,
    ): String {
        val existing = routines.observeAll().first()
            .firstOrNull { it.name.equals(pack.title, ignoreCase = true) }
        if (existing != null) return existing.id
        val created = routines.create(pack.title, pack.caption)
        for (lift in pack.lifts) {
            val exercise = exercises.getById(lift.exerciseId) ?: continue
            routines.addExercise(
                routineId = created.id,
                exercise = exercise,
                targetSets = lift.sets,
                targetReps = lift.reps,
                targetWeightKg = null,
                restSeconds = lift.restSeconds,
            )
        }
        return created.id
    }
}
