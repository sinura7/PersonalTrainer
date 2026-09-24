package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.SessionExercise
import kotlinx.coroutines.flow.first

/**
 * A lift's progression hint, read the same way by the Log's prefill ([ActiveWorkoutViewModel])
 * and the rest page ([RestTimerViewModel]), so the two coach calls start from the same history.
 *
 * Three steps, each one read, and nothing written. The Log checks between the steps that the
 * lift is still the one it is loading for and drops a stale answer, and it publishes the
 * lighter week before the history query, since the coach reads it meanwhile; a loader that
 * wrote at the end would do neither. Nothing is caught either: the Log degrades the entry on
 * a failure, and the rest page only logs it.
 */
internal class ProgressionHintLoader(
    private val container: AppDependencies,
    private val sessionId: String,
) {
    /** Today's week, as the epoch day it starts on by the schedule's first weekday. */
    suspend fun thisWeekStart(): Long {
        val schedule = container.preferencesRepository.schedulePreferences.first()
        return LighterWeek.weekStartEpochDay(
            container.time.civilDate(container.time.nowMillis()),
            schedule.weekStart,
        )
    }

    /** Whether [thisWeek] is the week marked lighter. */
    suspend fun isLighterWeek(thisWeek: Long): Boolean = LighterWeek.isCurrent(
        container.preferencesRepository.lighterWeekStartEpochDay.first(),
        thisWeek,
    )

    /** The hint for [exerciseId] from finished sessions, this one left out. */
    suspend fun progression(
        exerciseId: String,
        planned: SessionExercise?,
        lighterWeek: Boolean,
    ): ProgressionHint? = container.workoutRepository.progressionFor(
        exerciseId = exerciseId,
        exerciseName = planned?.exercise?.name ?: "",
        targetReps = planned?.targetReps ?: 5,
        excludeSessionId = sessionId,
        loadType = planned?.exercise?.loadType,
        unit = container.preferencesRepository.weightUnit.first(),
        lighterWeek = lighterWeek,
        equipment = planned?.exercise?.equipment,
    )
}
