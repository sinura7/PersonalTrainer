package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.SetMicroRecInputs
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.coach.AddASetMuscleStats
import com.sinura.personaltrainer.domain.coach.AddASetPolicy
import com.sinura.personaltrainer.domain.coach.TempoCoach
import com.sinura.personaltrainer.domain.coach.TempoCoachTip

internal data class TempoCoachSnapshot(
    val microRec: SetMicroRec?,
    val coachPrefs: CoachPreferences,
    val unit: WeightUnit,
    val session: WorkoutSession?,
    val selectedExerciseId: String?,
    val history: List<WorkoutSession>,
    val exerciseCatalog: Map<String, Exercise>,
    val trainingBlock: TrainingBlock?,
    val schedule: SchedulePreferences,
    val lighterWeek: Boolean,
    val time: TimePort,
    val nowMs: Long,
    val todayEpochDay: Long,
    val addASetDismissed: Set<String>,
    val addASetAccepted: Set<String>,
    val tempoDismissed: Boolean,
    val inputs: SetMicroRecInputs?,
) {
    fun tip(): TempoCoachTip? {
        if (tempoDismissed) return null
        val ctx = buildAddASetContext() ?: return TempoCoach.resolve(
            microRec = microRec,
            prefs = coachPrefs,
            addASet = null,
            extraSetRec = null,
        )
        return TempoCoach.resolve(
            microRec = microRec,
            prefs = coachPrefs,
            addASet = ctx,
            extraSetRec = extraSetRec(),
        )
    }

    private fun buildAddASetContext(): AddASetPolicy.Context? {
        val asked = inputs ?: return null
        val session = session ?: return null
        val liftId = session.resolveSelectedExerciseId(selectedExerciseId) ?: return null
        val lift = session.exercises.firstOrNull { it.exercise.id == liftId } ?: return null
        val exercise = lift.exercise
        val muscle = AddASetPolicy.primaryMuscleFor(exercise.muscles, exercise.muscleGroup) ?: return null
        val working = asked.thisSessionWorking
        val last = working.lastOrNull() ?: return null
        val extra = extraSetRec(asked) ?: return null
        val holdCodes = setOf(
            SetMicroRecCalculator.RPE_HOLD,
            SetMicroRecCalculator.CLOSE_HOLD,
            SetMicroRecCalculator.LIGHTER_HOLD,
            SetMicroRecCalculator.BW_HOLD,
            SetMicroRecCalculator.SKIP_RPE_HOLD,
            SetMicroRecCalculator.TOP_SET,
            SetMicroRecCalculator.QUALITY,
        )
        val zone = time.defaultZoneId()
        val weekStart = schedule.weekStart
        return AddASetPolicy.Context(
            goal = coachPrefs.goal,
            primaryMuscle = muscle,
            targetSets = asked.targetSets,
            workingLogged = asked.workingLogged,
            todayWorking = working,
            lighterWeek = lighterWeek,
            extraSetAlreadyAccepted = liftId in addASetAccepted,
            addASetDismissedForExercise = liftId in addASetDismissed,
            weeklyHardSetsForMuscle = AddASetMuscleStats.weeklyHardSetsThisWeek(
                muscle = muscle,
                finishedHistory = history,
                liveSession = session,
                nowMs = nowMs,
                time = time,
                zoneId = zone,
                weekStart = weekStart,
                exerciseCatalog = exerciseCatalog,
            ),
            muscleWeeklySetsByWeek = AddASetMuscleStats.muscleWeeklySetsLastThreeWeeks(
                muscle = muscle,
                finishedHistory = history,
                liveSession = session,
                nowMs = nowMs,
                time = time,
                zoneId = zone,
                weekStart = weekStart,
                exerciseCatalog = exerciseCatalog,
            ),
            blockWeekIndex = AddASetPolicy.blockWeekIndex(trainingBlock, todayEpochDay),
            blockOpenerSetsForLift = lift.targetSets,
            lastSessionMatchingSets = asked.historyWorking,
            lastWorkingSet = last,
            targetReps = asked.targetReps,
            extraSetReasonCode = extra.reasonCode,
            loadProgressionBlocked = extra.reasonCode in holdCodes,
        )
    }

    private fun extraSetRec(asked: SetMicroRecInputs? = inputs): SetMicroRec? {
        val base = asked ?: return null
        return SetMicroRecCalculator.suggest(base.copy(allowExtra = true))
    }
}
