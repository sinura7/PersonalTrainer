package com.sinura.personaltrainer.insights

import com.sinura.personaltrainer.data.repository.ActivityRepository
import com.sinura.personaltrainer.data.repository.ExerciseRepository
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import com.sinura.personaltrainer.data.repository.ScheduleRepository
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.toSummary
import com.sinura.personaltrainer.domain.windowedInsightHistory
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.ProgressionHint
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
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn

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
    private val activityRepository: ActivityRepository? = null,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val compute: (TrainingInsightsInput) -> TrainingInsights =
        TrainingInsightsCalculator::compute,
    private val loadHints: suspend (
        routines: List<Routine>,
        unit: WeightUnit,
        lighterWeek: Boolean,
    ) -> List<ProgressionHint> = { routines, unit, lighterWeek ->
        workoutRepository.readyForProgression(routines, unit, lighterWeek = lighterWeek)
    },
    private val shareGraceMs: Long = SHARE_GRACE_MS,
) : TrainingInsightsPublisher {
    /**
     * One computation for every screen that wants the default view of it.
     *
     * [observe] returns a cold flow, so each collector used to run the whole pipeline for
     * itself: the full history query, a heat snapshot over every session ever logged, the
     * coach, and the planner. Four screens collect it — Home, Plan, the active workout and the
     * start sheet — and two or three of them are alive at once routinely, each recomputing the
     * same answer from the same rows on every emission.
     *
     * Shared by [includeWeekPlan] because that is the only parameter these four vary, and the
     * plan is genuinely extra work the workout screens have no use for. Progress keeps a cold
     * flow of its own: it drives the heat window from a chip the user taps, so its input is not
     * the same input.
     *
     * `WhileSubscribed` rather than `Eagerly`: with nothing on screen there is nothing to
     * compute, and the five-second grace covers a rotation or a tab switch without a recompute.
     * The replayed value also means a screen opening beside an existing one renders immediately
     * with the same numbers, instead of waiting on a pipeline to tell it something the screen
     * next to it already knew.
     */
    override fun observeShared(includeWeekPlan: Boolean): Flow<TrainingInsights> =
        if (includeWeekPlan) sharedWithPlan else sharedWithoutPlan

    private val sharedScope = CoroutineScope(SupervisorJob() + computeDispatcher)

    private val sharedWithPlan: Flow<TrainingInsights> by lazy { share(includeWeekPlan = true) }

    private val sharedWithoutPlan: Flow<TrainingInsights> by lazy { share(includeWeekPlan = false) }

    private fun share(includeWeekPlan: Boolean): Flow<TrainingInsights> =
        observe(includeWeekPlan = includeWeekPlan)
            .shareIn(
                scope = sharedScope,
                started = SharingStarted.WhileSubscribed(shareGraceMs),
                replay = 1,
            )

    /**
     * @param window which heat window to summarise; Progress lets the user change it.
     * @param refresh any extra signal that should force a recompute. Nothing regenerates a
     *   week any more — the plan is stored — so this is now only a manual recompute nudge.
     * @param includeWeekPlan false on surfaces that never render a plan.
     */
    override fun observe(
        window: Flow<HeatWindow>,
        refresh: Flow<Any?>,
        includeWeekPlan: Boolean,
    ): Flow<TrainingInsights> = combine(
        // Six sources, five at a time: combine's typed overloads stop at five, so the slot flow
        // is folded in around the original group rather than the group being re-shaped.
        combine(
            combine(
                combine(
                    combine(
                        workoutRepository.observeSessionSummaries(),
                        activityRepository?.observeCompleted() ?: flowOf(emptyList()),
                    ) { summaries, activities ->
                        summaries + activities.filter { it.isCompleted }.map { it.toSummary() }
                    },
                    combine(
                        workoutRepository.observeFinishedSince(nowMs() - WINDOW_MS),
                        activityRepository?.observeCompleted() ?: flowOf(emptyList()),
                    ) { sessions, activities ->
                        windowedInsightHistory(
                            sessions = sessions,
                            activities = activities,
                            minPerformedAtMs = nowMs() - WINDOW_MS,
                        )
                    },
                ) { summaries, windowed -> summaries to windowed },
                routineRepository.observeAll(),
                exerciseRepository.observeAll(),
                preferencesRepository.schedulePreferences,
                preferencesRepository.weightUnit,
            ) { historyAndSummaries, routines, exercises, preferences, unit ->
                Sources(
                    history = historyAndSummaries.second,
                    summaries = historyAndSummaries.first,
                    routines = routines,
                    exercises = exercises.associateBy { it.id },
                    preferences = preferences,
                    unit = unit,
                )
            },
            scheduleRepository.observeSlots(),
            combine(
                preferencesRepository.coachPreferences,
                preferencesRepository.lighterWeekStartEpochDay,
            ) { coachPrefs, marked -> coachPrefs to marked },
        ) { sources, slots, coachAndMarked ->
            val today = Instant.ofEpochMilli(nowMs()).atZone(zone()).toLocalDate()
            val thisWeek = LighterWeek.weekStartEpochDay(
                com.sinura.personaltrainer.domain.CivilDate.fromEpochDay(today.toEpochDay()),
                sources.preferences.weekStart,
            )
            sources.copy(
                slots = slots,
                coachPrefs = coachAndMarked.first,
                lighterWeek = LighterWeek.isCurrent(coachAndMarked.second, thisWeek),
            )
        },
        window,
        refresh,
    ) { sources, heatWindow, _ ->
        sources to heatWindow
    }.mapLatest { (sources, heatWindow) ->
        // Null, not emptyList: "the query failed" and "nothing is ready to progress" render
        // very differently, and the old code collapsed them into the same empty section.
        val hints = runCatchingCancellable {
            loadHints(sources.routines, sources.unit, sources.lighterWeek)
        }.getOrElse { thrown ->
            AppLog.w(TAG, "Reading the progression hints failed", thrown)
            null
        }
        compute(
            TrainingInsightsInput(
                history = sources.history,
                summaries = sources.summaries,
                routines = sources.routines,
                exerciseCatalog = sources.exercises,
                hints = hints,
                preferences = sources.preferences,
                unit = sources.unit,
                slots = sources.slots,
                coachPrefs = sources.coachPrefs,
                window = heatWindow,
                nowMs = nowMs(),
                zoneId = zone().id,
                includeWeekPlan = includeWeekPlan,
            ),
        )
    }.flowOn(computeDispatcher)

    internal companion object {
        /** Long enough to survive a rotation or a tab switch, short enough not to hold work. */
        const val SHARE_GRACE_MS = 5_000L
        /** Body heat's widest window. Coach is 14 days inside this. */
        const val WINDOW_MS = 30L * 24 * 60 * 60 * 1000
    }

    private data class Sources(
        val history: List<WorkoutSession>,
        val summaries: List<SessionSummary> = emptyList(),
        val routines: List<Routine>,
        val exercises: Map<String, Exercise>,
        val preferences: SchedulePreferences,
        val unit: WeightUnit,
        /** Defaulted so the inner five-way combine keeps constructing this unchanged. */
        val slots: List<ScheduleSlot> = emptyList(),
        val coachPrefs: CoachPreferences = CoachPreferences.DEFAULT,
        val lighterWeek: Boolean = false,
    )
}
