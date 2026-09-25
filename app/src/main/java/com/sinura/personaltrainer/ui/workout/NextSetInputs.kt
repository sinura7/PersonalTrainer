package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.ExerciseSetRecord
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.SetMicroRecInputs
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.coach.CoachEngine
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.workout.WorkoutDraft

/**
 * Everything the coach is asked about the next set, beside the clock. The Log
 * ([ActiveWorkoutViewModel.microRec]) and the rest page's Next line ([RestTimerViewModel]) each
 * fill one of these and ask through [coachKey], so both ask one question. The rest page's planned
 * length still asks the question it asked before, with three of these set aside (see there).
 *
 * Nothing here has a default, so neither page can leave an input out. The rest page used to
 * leave out three: Another set, last session's sets, and the set open for correction. Its Next
 * line then said "100 kg × 5" after Another set where the Log said "102.5 kg × 5", and lost last
 * session's "RPE 8" on a lift's first set (W2b-4, owner decision of 24 September 2026).
 *
 * Where each input comes from stays with the page. The Log holds them; the rest page reads what
 * the Log last left in the draft cache, and loads the history the way the Log does
 * ([ProgressionHintLoader]).
 */
internal data class NextSetInputs(
    val session: WorkoutSession?,
    val selectedExerciseId: String?,
    val draft: ActiveExerciseDraft,
    val hint: ProgressionHint?,
    /** The set open for correction on the Log, or null. The coach makes no call while one is. */
    val editingSetId: String?,
    /** Another set was asked for past the lift's planned sets. */
    val wantAnotherSet: Boolean,
    /** The lift's last finished session's sets. A lift's first set takes its RPE from them. */
    val historySets: List<ExerciseSetRecord>,
    val lighterWeek: Boolean,
    val unit: WeightUnit,
    val coachPrefs: CoachPreferences,
) {
    fun rec(nowMs: Long, todayEpochDay: Long): SetMicroRec? =
        coachKey().rec(nowMs = nowMs, todayEpochDay = todayEpochDay)

    /** What the coach is asked, less the clock ([CoachKey]). */
    fun coachKey(): CoachKey = CoachKey(
        inputs = workoutCoachInputs(
            session = session,
            selectedExerciseId = selectedExerciseId,
            draft = draft,
            hint = hint,
            editingSetId = editingSetId,
            lighterWeek = lighterWeek,
            unit = unit,
            nowMs = 0L,
            todayEpochDay = 0L,
            wantAnotherSet = wantAnotherSet,
            historySets = historySets,
        ),
        prefs = coachPrefs,
    )
}

/**
 * Everything the coach reads to make its call, with the clock left at zero, and its settings
 * beside it. Two equal keys get the same call; only its trace's time and day can differ, and no
 * rule reads those (ADR-029). So the Log's card and the rest page's Next line ask the coach
 * again only when a key changes, not on every redraw: a weight step with no RPE, a note, a
 * second of rest (W2c, audit C-2). The call is then made with the clock as it is at the ask,
 * so its trace still says when it was made (ADR-008).
 */
internal data class CoachKey(
    /** Null where the coach makes no call: no session, or no lift to ask about. */
    val inputs: SetMicroRecInputs?,
    val prefs: CoachPreferences,
) {
    fun rec(nowMs: Long, todayEpochDay: Long): SetMicroRec? = inputs
        ?.copy(nowMs = nowMs, todayEpochDay = todayEpochDay)
        ?.let { asked -> CoachEngine.suggest(inputs = asked, prefs = prefs) }
        ?.toMicroRec()
}

/**
 * [rec] as the Log's Next card shows it, or null where the card is hidden: the lift's planned
 * sets are done and Another set was not asked for, the entry is a warm-up (the Log shows the
 * ramp instead), there is no call (a set is open for correction), or the lift is a hold. The
 * Log's card and the rest page's Next line both go through here, so the page shows the Log's
 * line or none (W2b-4).
 *
 * A hold (plank, dead hang, a stretch) is logged in seconds with no reps, and the coach counts
 * reps: it offered "1 rep" before the first hold and said "Hold 0 reps" after it (audit DM-1).
 * The call is still made, for the rest it sets and the effort it suggests; only its line, in
 * reps, is not shown.
 *
 * The Log also hides its card while its entry is locked. The rest page follows the part of
 * that lock it can see, a save the Log holds in the draft cache: a failed save waiting for Retry
 * is held for as long as it waits, a set being written only for a moment. The Log's other locks
 * (a set being deleted or opened for correction, the session still loading) are its own.
 */
internal fun shownNextSet(rec: SetMicroRec?, draftIsWarmup: Boolean, liftIsHold: Boolean): SetMicroRec? =
    rec?.takeIf { !draftIsWarmup && !liftIsHold && SetMicroRecCopy.visibleOnEntry(it) }

/**
 * The Log's entry as the draft cache holds it: a timed hold keeps its 0 reps, any other lift
 * has at least one. The Log recovers its entry this way and the rest page reads it this way, so
 * the two coach calls start from one entry (W2b-4 review: the page gave a hold one rep).
 */
internal fun WorkoutDraft.entryDraft(): ActiveExerciseDraft = ActiveExerciseDraft(
    weightKg = weightKg,
    reps = if (durationSeconds != null) reps.coerceAtLeast(0) else reps.coerceAtLeast(1),
    rpe = rpe,
    isWarmup = isWarmup,
    durationSeconds = durationSeconds,
)
