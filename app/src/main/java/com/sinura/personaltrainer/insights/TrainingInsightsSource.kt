package com.sinura.personaltrainer.insights

import com.sinura.personaltrainer.data.repository.ExerciseRepository
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import com.sinura.personaltrainer.data.repository.ScheduleRepository
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.ScheduleSlot
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.TrainingInsightsCalculator
import com.sinura.personaltrainer.domain.TrainingInsightsInput
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

private const val TAG = "PT/InsightsSource"

/**
 * The single producer of [TrainingInsights].
 *
 * Home, Schedule and Progress each carried a near-identical copy of this chain. Beyond the
 * duplication, all three ran the whole analytics pass — normalising every set in the history
 * into muscle buckets, then planning a week — inside `mapLatest` on the main thread, on every
 * emission of any of five upstream flows. This assembles the inputs once and computes them on
 * [computeDispatcher].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainingInsightsSource(
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    private val exerciseRepository: ExerciseRepository,
    private val preferencesRepository: PreferencesRepository,
    private val scheduleRepository: ScheduleRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    /**
     * @param window which heat window to summarise; Progress lets the user change it.
     * @param refresh any extra signal that should force a recompute. Nothing regenerates a
     *   week any more — the plan is stored — so this is now only a manual recompute nudge.
     * @param includeWeekPlan false on surfaces that never render a plan.
     */
    fun observe(
        window: Flow<HeatWindow> = flowOf(HeatWindow.CURRENT_WEEK),
        refresh: Flow<Any?> = flowOf(Unit),
        includeWeekPlan: Boolean = true,
    ): Flow<TrainingInsights> = combine(
        // Six sources, five at a time: combine's typed overloads stop at five, so the slot flow
        // is folded in around the original group rather than the group being re-shaped.
        combine(
            combine(
                workoutRepository.observeHistory(),
                routineRepository.observeAll(),
                exerciseRepository.observeAll(),
                preferencesRepository.schedulePreferences,
                preferencesRepository.weightUnit,
            ) { history, routines, exercises, preferences, unit ->
                Sources(history, routines, exercises.associateBy { it.id }, preferences, unit)
            },
            scheduleRepository.observeSlots(),
            preferencesRepository.coachPreferences,
        ) { sources, slots, coachPrefs -> sources.copy(slots = slots, coachPrefs = coachPrefs) },
        window,
        refresh,
    ) { sources, heatWindow, _ ->
        sources to heatWindow
    }.mapLatest { (sources, heatWindow) ->
        // Null, not emptyList: "the query failed" and "nothing is ready to progress" render
        // very differently, and the old code collapsed them into the same empty section.
        val hints = runCatchingCancellable {
            workoutRepository.readyForProgression(sources.routines, sources.unit)
        }.getOrElse { thrown ->
            AppLog.w(TAG, "Reading the progression hints failed", thrown)
            null
        }
        TrainingInsightsCalculator.compute(
            TrainingInsightsInput(
                history = sources.history,
                routines = sources.routines,
                exerciseCatalog = sources.exercises,
                hints = hints,
                preferences = sources.preferences,
                unit = sources.unit,
                slots = sources.slots,
                coachPrefs = sources.coachPrefs,
                window = heatWindow,
                nowMs = nowMs(),
                zone = zone(),
                includeWeekPlan = includeWeekPlan,
            ),
        )
    }.flowOn(computeDispatcher)

    private data class Sources(
        val history: List<WorkoutSession>,
        val routines: List<Routine>,
        val exercises: Map<String, Exercise>,
        val preferences: SchedulePreferences,
        val unit: WeightUnit,
        /** Defaulted so the inner five-way combine keeps constructing this unchanged. */
        val slots: List<ScheduleSlot> = emptyList(),
        val coachPrefs: CoachPreferences = CoachPreferences.DEFAULT,
    )
}
