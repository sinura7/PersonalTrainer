package com.sinura.personaltrainer.ui.workout

import androidx.annotation.VisibleForTesting
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.domain.ExerciseSessionSummary
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.Weekday
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * A lift's progression hint and its last session, read the same way by the Log's prefill
 * ([ActiveWorkoutViewModel]) and the rest page ([RestTimerViewModel]), so the two coach calls
 * start from the same history ([NextSetInputs]).
 *
 * The hint is read under [HintSettings], which both screens watch through [settings] and pass
 * in, so a change of the unit or the week's mark reads the open lift's hint again (W2e). Nothing
 * here is written. The Log checks between reads that the lift is still the one it is loading
 * for and drops a stale answer. Nothing is caught either: the Log degrades its load on a failure
 * and only logs a failed re-read; the rest page only logs.
 */
internal class ProgressionHintLoader(
    private val container: AppDependencies,
    private val sessionId: String,
) {
    /**
     * Today's week, as the epoch day it starts on by the schedule's first weekday. Tests mark this
     * week lighter with it; the screens read the week through [settings].
     */
    @VisibleForTesting
    suspend fun thisWeekStart(): Long =
        weekStartOf(container.preferencesRepository.schedulePreferences.first().weekStart)

    /**
     * The unit and whether this week is the one marked lighter, as they change. A write of any
     * other setting, a mark for another week, or the same unit again emits nothing new.
     */
    fun settings(): Flow<HintSettings> = combine(
        container.preferencesRepository.weightUnit,
        container.preferencesRepository.lighterWeekStartEpochDay,
        container.preferencesRepository.schedulePreferences.map { it.weekStart },
    ) { unit, mark, weekStart ->
        HintSettings(unit = unit, lighterWeek = LighterWeek.isCurrent(mark, weekStartOf(weekStart)))
    }.distinctUntilChanged()

    /** The hint for [exerciseId] from finished sessions, this one left out, read under [settings]. */
    suspend fun progression(
        exerciseId: String,
        planned: SessionExercise?,
        settings: HintSettings,
    ): ProgressionHint? = container.workoutRepository.progressionFor(
        exerciseId = exerciseId,
        exerciseName = planned?.exercise?.name ?: "",
        targetReps = planned?.targetReps ?: 5,
        excludeSessionId = sessionId,
        loadType = planned?.exercise?.loadType,
        unit = settings.unit,
        lighterWeek = settings.lighterWeek,
        equipment = planned?.exercise?.equipment,
    )

    /**
     * [exerciseId]'s last finished session, this one left out. A lift's first set takes its RPE
     * from these sets (W2b-4: the rest page did not read them, and lost last time's "RPE 8").
     */
    suspend fun lastPerformance(exerciseId: String): ExerciseSessionSummary? =
        container.workoutRepository.lastPerformance(exerciseId, sessionId)

    private fun weekStartOf(weekStart: Weekday): Long =
        LighterWeek.weekStartEpochDay(container.time.civilDate(container.time.nowMillis()), weekStart)
}

/** The two settings a lift's hint is read under (W2e): the unit it steps in, and whether this week is marked lighter. */
internal data class HintSettings(val unit: WeightUnit, val lighterWeek: Boolean)
