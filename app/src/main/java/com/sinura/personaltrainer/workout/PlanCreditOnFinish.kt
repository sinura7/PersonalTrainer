package com.sinura.personaltrainer.workout

import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.AuxiliarySessions
import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.PlanCreditResult
import com.sinura.personaltrainer.domain.WorkoutSession

import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable

private const val TAG = "PT/PlanCredit"

/**
 * Whether finishing this session should count for today's planned strength day.
 *
 * A Today-row start still binds [PendingOccurrence] and credits on finish as before.
 * Other starts forget that link, so credit is decided here from routine match instead.
 */
object PlanCreditOnFinish {
    /**
     * Runs after [com.sinura.personaltrainer.workout.FinishWorkout] succeeds.
     *
     * @param boundBeforeFinish true when [PendingOccurrence.complete] already ran for this session.
     */
    suspend fun afterFinish(
        deps: AppDependencies,
        session: WorkoutSession,
        boundOccurrenceId: String?,
        todayEpochDay: Long,
    ): PlanCreditResult {
        if (boundOccurrenceId != null) {
            val title = dayTitle(deps, boundOccurrenceId) ?: return PlanCreditResult.None
            return PlanCreditResult.Credited(occurrenceId = boundOccurrenceId, dayTitle = title)
        }
        val open = openStrengthOccurrence(deps, todayEpochDay) ?: return PlanCreditResult.None
        val title = open.title
        val plannedRoutine = open.rule?.routineId
        if (plannedRoutine != null && session.routineId == plannedRoutine) {
            runCatchingCancellable {
                deps.plannerRepository.markOccurrenceDone(open.occurrence.id, session.id)
            }
                .onFailure { thrown ->
                    AppLog.e(TAG, "Auto plan credit on finish failed", thrown)
                    return PlanCreditResult.None
                }
            return PlanCreditResult.Credited(occurrenceId = open.occurrence.id, dayTitle = title)
        }
        if (AuxiliarySessions.isAuxiliarySession(session)) {
            return PlanCreditResult.None
        }
        return PlanCreditResult.NeedsChoice(occurrenceId = open.occurrence.id, dayTitle = title)
    }

    suspend fun applyChoice(
        deps: AppDependencies,
        occurrenceId: String,
        sessionId: String,
        countForPlan: Boolean,
    ) {
        if (!countForPlan) return
        runCatchingCancellable { deps.plannerRepository.markOccurrenceDone(occurrenceId, sessionId) }
            .onFailure { thrown -> AppLog.e(TAG, "Applying plan credit choice failed", thrown) }
    }

    suspend fun undoCredit(deps: AppDependencies, occurrenceId: String, sessionId: String) {
        runCatchingCancellable { deps.plannerRepository.undoOccurrenceDone(occurrenceId, sessionId) }
            .onFailure { thrown -> AppLog.e(TAG, "Undoing plan credit failed", thrown) }
    }

    private suspend fun openStrengthOccurrence(
        deps: AppDependencies,
        todayEpochDay: Long,
    ): AgendaItem? {
        val occurrences = deps.plannerRepository.occurrencesBetween(todayEpochDay, todayEpochDay)
        val items = DailyAgenda.forDay(todayEpochDay, occurrences, deps.plannerRepository.rules())
        return items.firstOrNull { item ->
            item.occurrence.status == OccurrenceStatus.PLANNED &&
                (item.rule?.modality ?: ScheduleModality.STRENGTH) == ScheduleModality.STRENGTH
        }
    }

    private suspend fun dayTitle(deps: AppDependencies, occurrenceId: String): String? {
        val occurrence = deps.plannerRepository.getOccurrence(occurrenceId) ?: return null
        val rule = deps.plannerRepository.rules().firstOrNull { it.id == occurrence.ruleId }
        val routineName = rule?.routineId?.let { deps.routineRepository.getById(it)?.name }
        return routineName ?: rule?.focusKind?.label
    }
}
