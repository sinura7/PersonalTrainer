package com.sinura.personaltrainer.insights

import com.sinura.personaltrainer.data.repository.ActivityRepository
import com.sinura.personaltrainer.data.repository.ExerciseRepository
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import com.sinura.personaltrainer.data.repository.ScheduleRepository
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.SessionSummary
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
     * Home, Plan, the active workout and the start sheet share one pipeline per
     * [includeWeekPlan]. Progress used to run a cold copy of the whole chain so
     * its heat-window chip could move independently; the chip now retargets only
     * the snapshot stage of this same shared assembly.
     *
     * `WhileSubscribed` rather than `Eagerly`: with nothing on screen there is nothing to
     * compute, and the five-second grace covers a rotation or a tab switch without a recompute.
     * The replayed value also means a screen opening beside an existing one renders immediately
     * with the same numbers, instead of waiting on a pipeline to tell it something the screen
     * next to it already knew.
     */
    override fun observeShared(includeWeekPlan: Boolean): Flow<TrainingInsights> =
        assembled(includeWeekPlan).map { retarget(it, HeatWindow.CURRENT_WEEK) }

    private val sharedScope = CoroutineScope(SupervisorJob() + computeDispatcher)
    private val coreNudge = MutableStateFlow(0L)
    private val hintLock = Any()
    private var hintCache: Pair<HintCacheKey, List<ProgressionHint>?>? = null

    private val assembledWithPlan: Flow<Assembled> by lazy { shareAssembled(includeWeekPlan = true) }
    private val assembledWithoutPlan: Flow<Assembled> by lazy { shareAssembled(includeWeekPlan = false) }

    private fun assembled(includeWeekPlan: Boolean): Flow<Assembled> =
        if (includeWeekPlan) assembledWithPlan else assembledWithoutPlan

    private fun shareAssembled(includeWeekPlan: Boolean): Flow<Assembled> =
        assemble(includeWeekPlan)
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
        assembled(includeWeekPlan),
        window,
        refreshNudgesCore(refresh),
    ) { assembled, heatWindow, _ ->
        retarget(assembled, heatWindow)
    }.flowOn(computeDispatcher)

    private fun refreshNudgesCore(refresh: Flow<Any?>): Flow<Any?> = flow {
        var first = true
        refresh.collect { value ->
            if (!first) coreNudge.value = nowMs()
            first = false
            emit(value)
        }
    }

    private fun assemble(includeWeekPlan: Boolean): Flow<Assembled> {
        val activitySummaries = activityRepository?.observeCompletedSummaries()
            ?: flowOf(emptyList())
        val windowStart = nowMs() - WINDOW_MS
        // Slot flow and retry nudge sit outside the five-way combine: typed overloads stop at five.
        return combine(
            combine(
                combine(
                    combine(
                        workoutRepository.observeSessionSummaries(),
                        activitySummaries,
                    ) { summaries, activities ->
                        summaries + activities
                    },
                    combine(
                        workoutRepository.observeFinishedSince(windowStart),
                        activityRepository?.observeCompletedGraphsSince(windowStart)
                            ?: flowOf(emptyList()),
                    ) { sessions, activities ->
                        windowedInsightHistory(
                            sessions = sessions,
                            activities = activities,
                            minPerformedAtMs = nowMs() - WINDOW_MS,
                        )
                    },
                ) { summaries, windowed -> summaries to windowed },
                routineRepository.observeAll(),
                combine(
                    exerciseRepository.observeAll(),
                    workoutRepository.observeLastLogged(),
                ) { exercises, lastLogged ->
                    exercises.associateBy { it.id } to lastLogged
                },
                preferencesRepository.schedulePreferences,
                preferencesRepository.weightUnit,
            ) { historyAndSummaries, routines, catalogAndRecency, preferences, unit ->
                Sources(
                    history = historyAndSummaries.second,
                    summaries = historyAndSummaries.first,
                    routines = routines,
                    exercises = catalogAndRecency.first,
                    lastLoggedAtByExerciseId = catalogAndRecency.second,
                    preferences = preferences,
                    unit = unit,
                )
            },
            scheduleRepository.observeSlots(),
            combine(
                preferencesRepository.coachPreferences,
                preferencesRepository.lighterWeekStartEpochDay,
            ) { coachPrefs, marked -> coachPrefs to marked },
            coreNudge,
        ) { sources, slots, coachAndMarked, _ ->
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
        }.mapLatest { sources ->
            val hints = cachedHints(sources)
            val insights = compute(
                TrainingInsightsInput(
                    history = sources.history,
                    summaries = sources.summaries,
                    routines = sources.routines,
                    exerciseCatalog = sources.exercises,
                    lastLoggedAtByExerciseId = sources.lastLoggedAtByExerciseId,
                    hints = hints,
                    preferences = sources.preferences,
                    unit = sources.unit,
                    slots = sources.slots,
                    coachPrefs = sources.coachPrefs,
                    window = HeatWindow.CURRENT_WEEK,
                    nowMs = nowMs(),
                    zoneId = zone().id,
                    includeWeekPlan = includeWeekPlan,
                ),
            )
            Assembled(sources, insights)
        }.flowOn(computeDispatcher)
    }

    private suspend fun cachedHints(sources: Sources): List<ProgressionHint>? {
        val key = HintCacheKey(
            unit = sources.unit,
            lighterWeek = sources.lighterWeek,
            routines = sources.routines,
            summaries = sources.summaries,
        )
        synchronized(hintLock) {
            hintCache?.let { cached ->
                if (cached.first == key) return cached.second
            }
        }
        val hints = runCatchingCancellable {
            loadHints(sources.routines, sources.unit, sources.lighterWeek)
        }.getOrElse { thrown ->
            AppLog.w(TAG, "Reading the progression hints failed", thrown)
            null
        }
        if (hints != null) {
            synchronized(hintLock) { hintCache = key to hints }
        }
        return hints
    }

    private fun retarget(assembled: Assembled, window: HeatWindow): TrainingInsights =
        TrainingInsightsCalculator.retargetWindow(
            insights = assembled.insights,
            window = window,
            nowMs = nowMs(),
            zoneId = zone().id,
            weekStart = assembled.sources.preferences.weekStart,
            exerciseCatalog = assembled.sources.exercises,
            lastLoggedAtByExerciseId = assembled.sources.lastLoggedAtByExerciseId,
        )

    internal companion object {
        /** Long enough to survive a rotation or a tab switch, short enough not to hold work. */
        const val SHARE_GRACE_MS = 5_000L
        /**
         * Wide enough for Body heat's widest window: "this month" starts at civil
         * midnight on the 1st, which on the 31st of a 31-day month sits more than
         * 30 rolling days back. 32 covers the longest month plus DST slack; every
         * consumer (heat, coach's 14 days, deload's three weeks) re-filters against
         * its own window, so the extra fetch width changes no displayed number.
         */
        const val WINDOW_MS = 32L * 24 * 60 * 60 * 1000
    }

    private data class Sources(
        val history: List<WorkoutSession>,
        val summaries: List<SessionSummary> = emptyList(),
        val routines: List<Routine>,
        val exercises: Map<String, Exercise>,
        val lastLoggedAtByExerciseId: Map<String, Long> = emptyMap(),
        val preferences: SchedulePreferences,
        val unit: WeightUnit,
        /** Defaulted so the inner five-way combine keeps constructing this unchanged. */
        val slots: List<ScheduleSlot> = emptyList(),
        val coachPrefs: CoachPreferences = CoachPreferences.DEFAULT,
        val lighterWeek: Boolean = false,
    )

    private data class Assembled(
        val sources: Sources,
        val insights: TrainingInsights,
    )

    private data class HintCacheKey(
        val unit: WeightUnit,
        val lighterWeek: Boolean,
        val routines: List<Routine>,
        val summaries: List<SessionSummary>,
    )
}
