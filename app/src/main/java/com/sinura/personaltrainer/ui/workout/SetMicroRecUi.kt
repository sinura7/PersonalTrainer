package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.ExerciseSetRecord
import com.sinura.personaltrainer.domain.coach.CoachEngine
import com.sinura.personaltrainer.domain.coach.CoachSuggestion
import com.sinura.personaltrainer.domain.LoggedSetView
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.setMicroRecInputs
/**
 * The coach's call for the next set. The Log's Next card and the rest page's Next line reach it
 * through [NextSetInputs], which has no defaults, so the two ask with the same inputs (W2b-4).
 * The defaults here serve calls about fewer inputs: the tests', and the Log's rest seed when a
 * lift is chosen, which has never counted Another set.
 */
internal fun workoutCoachSuggestion(
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
    coachPrefs: CoachPreferences = CoachPreferences.DEFAULT,
): CoachSuggestion? {
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
    return CoachEngine.suggest(
        setMicroRecInputs(
            editing = editingSetId != null,
            loadType = planned?.exercise?.loadType,
            unit = unit,
            equipment = planned?.exercise?.equipment,
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
        prefs = coachPrefs,
    )
}

/** Back-compat for call sites that only need [SetMicroRec] numbers. */
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
    coachPrefs: CoachPreferences = CoachPreferences.DEFAULT,
): SetMicroRec? = workoutCoachSuggestion(
    session = session,
    selectedExerciseId = selectedExerciseId,
    draft = draft,
    hint = hint,
    editingSetId = editingSetId,
    lighterWeek = lighterWeek,
    unit = unit,
    nowMs = nowMs,
    todayEpochDay = todayEpochDay,
    wantAnotherSet = wantAnotherSet,
    historySets = historySets,
    coachPrefs = coachPrefs,
)?.toMicroRec()
