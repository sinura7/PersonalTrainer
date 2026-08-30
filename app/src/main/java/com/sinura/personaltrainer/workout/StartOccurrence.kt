package com.sinura.personaltrainer.workout

import com.sinura.personaltrainer.data.repository.PlannerRepository
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.ScheduleKind
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.ScheduleRule
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.JvmTime
import com.sinura.personaltrainer.util.runCatchingCancellable

private const val TAG = "PT/StartOccurrence"

sealed interface StartOccurrenceOutcome {
    data class OpenWorkout(
        val sessionId: String,
        val occurrenceId: String,
        val day: SuggestedTrainingDay,
    ) : StartOccurrenceOutcome

    data class OpenCardio(val sessionId: String) : StartOccurrenceOutcome

    data class OpenComposer(val occurrenceId: String) : StartOccurrenceOutcome

    data class Blocked(
        val inProgressSessionId: String,
        val day: SuggestedTrainingDay,
        val occurrenceId: String,
    ) : StartOccurrenceOutcome

    data class Failed(val message: String) : StartOccurrenceOutcome

    data object Missing : StartOccurrenceOutcome
}

/**
 * Turns a planned occurrence into an open session, live cardio, or composer.
 *
 * Home and the start sheet used to each reimplement this, including the
 * cardio-type lookup. A scheduled bike started as a run from the sheet.
 * Callers map the sealed outcome; leftover relocation stays on Home.
 */
class StartOccurrence(
    private val plannerRepository: PlannerRepository,
    private val routineRepository: RoutineRepository,
    private val startTrainingDay: StartTrainingDay,
    private val startLiveCardio: StartLiveCardio,
) {
    suspend operator fun invoke(occurrenceId: String): StartOccurrenceOutcome {
        return runCatchingCancellable {
            val occurrence = plannerRepository.getOccurrence(occurrenceId)
                ?: return@runCatchingCancellable StartOccurrenceOutcome.Missing
            val rule = plannerRepository.getRule(occurrence.ruleId)
            when (rule?.modality ?: ScheduleModality.STRENGTH) {
                ScheduleModality.CARDIO -> {
                    val type = ScheduleKind.cardioTypeOrRun(rule?.templateId)
                    when (
                        val cardio = startLiveCardio(
                            type = type,
                            now = JvmTime.captureNow(),
                            occurrenceId = occurrence.id,
                        )
                    ) {
                        is StartCardioOutcome.Open -> StartOccurrenceOutcome.OpenCardio(cardio.sessionId)
                        is StartCardioOutcome.Rejected -> StartOccurrenceOutcome.Failed(cardio.reason)
                    }
                }
                ScheduleModality.MIXED -> StartOccurrenceOutcome.OpenComposer(occurrence.id)
                ScheduleModality.STRENGTH -> {
                    val day = plannedDay(occurrence, rule)
                    when (val outcome = startTrainingDay(day)) {
                        is StartDayOutcome.Open -> StartOccurrenceOutcome.OpenWorkout(
                            sessionId = outcome.sessionId,
                            occurrenceId = occurrence.id,
                            day = day,
                        )
                        is StartDayOutcome.Blocked -> StartOccurrenceOutcome.Blocked(
                            inProgressSessionId = outcome.inProgressSessionId,
                            day = day,
                            occurrenceId = occurrence.id,
                        )
                        is StartDayOutcome.Failed -> StartOccurrenceOutcome.Failed(outcome.message)
                        StartDayOutcome.Ignored -> StartOccurrenceOutcome.Missing
                    }
                }
            }
        }.getOrElse { thrown ->
            AppLog.w(TAG, "Starting occurrence $occurrenceId failed", thrown)
            StartOccurrenceOutcome.Failed("Could not start that session. Try again.")
        }
    }

    private suspend fun plannedDay(
        occurrence: ScheduleOccurrence,
        rule: ScheduleRule?,
    ): SuggestedTrainingDay {
        val routineName = rule?.routineId?.let { routineRepository.getById(it)?.name }
        val item = AgendaItem(occurrence, rule, routineName)
        return SuggestedTrainingDay(
            epochDay = occurrence.localEpochDay,
            dayOfWeek = Weekday.fromEpochDay(occurrence.localEpochDay),
            isRest = false,
            focusKind = rule?.focusKind ?: SessionFocusKind.FULL_BODY,
            focusTitle = item.title,
            routineId = rule?.routineId,
            routineName = routineName,
            reason = "Planned.",
            emphasisMuscles = emptyList(),
            confidence = ScheduleConfidence.HIGH,
            slotId = null,
        )
    }
}
