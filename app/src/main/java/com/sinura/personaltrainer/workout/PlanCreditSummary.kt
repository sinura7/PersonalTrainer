package com.sinura.personaltrainer.workout

import com.sinura.personaltrainer.domain.AuxiliarySessions
import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.PlanCreditUiState
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.WorkoutSession

import com.sinura.personaltrainer.AppDependencies

object PlanCreditSummary {
    suspend fun resolve(
        deps: AppDependencies,
        session: WorkoutSession,
        todayEpochDay: Long,
    ): PlanCreditUiState? {
        val occurrences = deps.plannerRepository.occurrencesBetween(todayEpochDay, todayEpochDay)
        val credited = occurrences.firstOrNull { occ ->
            occ.status == OccurrenceStatus.DONE && occ.completedActivityId == session.id
        }
        if (credited != null) {
            val title = titleFor(deps, credited) ?: return null
            return PlanCreditUiState.Credited(credited.id, title)
        }
        if (AuxiliarySessions.isAuxiliarySession(session)) return null
        val items = DailyAgenda.forDay(todayEpochDay, occurrences, deps.plannerRepository.rules())
        val open = items.firstOrNull { item ->
            item.occurrence.status == OccurrenceStatus.PLANNED &&
                (item.rule?.modality ?: ScheduleModality.STRENGTH) == ScheduleModality.STRENGTH
        } ?: return null
        val plannedRoutine = open.rule?.routineId
        if (plannedRoutine != null && session.routineId == plannedRoutine) return null
        return PlanCreditUiState.NeedsChoice(open.occurrence.id, open.title)
    }

    private suspend fun titleFor(deps: AppDependencies, occurrence: ScheduleOccurrence): String? {
        val rule = deps.plannerRepository.rules().firstOrNull { it.id == occurrence.ruleId }
        val routineName = rule?.routineId?.let { deps.routineRepository.getById(it)?.name }
        return routineName ?: rule?.focusKind?.label
    }
}
