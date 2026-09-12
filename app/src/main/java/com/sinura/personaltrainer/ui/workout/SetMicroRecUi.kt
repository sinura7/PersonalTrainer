package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.LoggedSetView
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.ExerciseSetRecord
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.setMicroRecInputs

/** Same `suggest()` inputs on the log and the rest floor. */
internal fun workoutMicroRec(
    session: WorkoutSession?,
    selectedExerciseId: String?,
    draft: ActiveExerciseDraft,
    hint: ProgressionHint?,
    editingSetId: String?,
    lighterWeek: Boolean,
    unit: WeightUnit,
    nowMs: Long,
    todayEpochDay: Long,
    wantAnotherSet: Boolean = false,
    historySets: List<ExerciseSetRecord> = emptyList(),
): SetMicroRec? {
    if (session == null) return null
    val exerciseId = session.resolveSelectedExerciseId(selectedExerciseId) ?: return null
    val planned = session.exercises.firstOrNull { it.exercise.id == exerciseId }
    val sets = session.setsFor(exerciseId)
    val working = sets.filter { !it.isWarmup }.map {
        LoggedSetView(
            weightKg = it.weightKg,
            reps = it.reps,
            rpe = it.rpe,
            isWarmup = false,
        )
    }
    val lastAny = sets.maxByOrNull { it.completedAt }
    // Warm-up drafts are not a preview of the next working set.
    val draftRpe = draft.rpe.takeUnless { draft.isWarmup }
    return SetMicroRecCalculator.suggest(
        setMicroRecInputs(
            editing = editingSetId != null,
            loadType = planned?.exercise?.loadType,
            unit = unit,
            targetSets = planned?.targetSets ?: 0,
            targetReps = planned?.targetReps ?: 5,
            targetWeightKg = planned?.targetWeightKg,
            working = working,
            lastAnySetWasWarmup = lastAny?.isWarmup == true,
            hint = hint,
            lighterWeek = lighterWeek,
            draftWeightKg = if (draftRpe != null) draft.weightKg else 0.0,
            draftReps = if (draftRpe != null) draft.reps else 0,
            draftRpe = draftRpe,
            nowMs = nowMs,
            todayEpochDay = todayEpochDay,
            allowExtra = wantAnotherSet,
            rpeIntent = true,
            historyWorking = historySets.map { set ->
                LoggedSetView(
                    weightKg = set.weightKg,
                    reps = set.reps,
                    rpe = set.rpe,
                    isWarmup = false,
                )
            },
        ),
    )
}
