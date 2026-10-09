package com.sinura.personaltrainer.insights

import com.sinura.personaltrainer.data.repository.ActivityRepository
import com.sinura.personaltrainer.data.repository.ExerciseRepository
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import com.sinura.personaltrainer.data.repository.ScheduleRepository
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.data.repository.SharedReadRecovery
import com.sinura.personaltrainer.data.repository.combineHealth
import com.sinura.personaltrainer.data.repository.mapHealthCatching
import com.sinura.personaltrainer.data.repository.presentValues
import com.sinura.personaltrainer.domain.DataHealth
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
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.TrainingInsightsCalculator
import com.sinura.personaltrainer.domain.TrainingInsightsInput
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.JvmTime
import com.sinura.personaltrainer.util.runCatchingCancellable
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

private const val TAG = "PT/InsightsSource"

/**
 * Where a failed analytics stage gets written down.
 *
 * [TrainingInsightsCalculator] degrades each stage independently and records which one gave
 * way in [TrainingInsights.failures], but it no longer logs: it used to call
 * `util.recoverWith`, which reaches `android.util.Log` through `AppLog`, and that put an
 * Android hop behind a `domain/` file. The catch stayed in the domain; the log moved here,
 * to the layer that already owns one.
 */
private fun logStageFailure(what: String, error: Throwable) {
    AppLog.w(TAG, "$what failed; using fallback", error)
}

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
    private val time: TimePort = JvmTime,
    private val compute: (TrainingInsightsInput) -> TrainingInsights = { input ->
        TrainingInsightsCalculator.compute(input, ::logStageFailure)
    },
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
        observeSharedHealth(includeWeekPlan).presentValues()

    override fun observeSharedHealth(includeWeekPlan: Boolean): Flow<DataHealth<TrainingInsights>> =
        assembled(includeWeekPlan).observe()
            .mapHealthCatching("the training summary") { retarget(it, HeatWindow.CURRENT_WEEK) }

    override fun retrySharedHealth(includeWeekPlan: Boolean): Flow<DataHealth<TrainingInsights>> =
        assembled(includeWeekPlan).retry()
            .mapHealthCatching("the training summary") { retarget(it, HeatWindow.CURRENT_WEEK) }

    /**
     * Logs, as the application scope does. Without a handler a throw that escaped the
     * pipeline ended the process, and Home subscribes on its first frame, so it closed again
     * on every open (audit AR-1). The inputs degrade and [shareAssembled] catches first; this
     * is the backstop.
     */
    private val sharedScope = CoroutineScope(
        SupervisorJob() + computeDispatcher + CoroutineExceptionHandler { _, thrown ->
            AppLog.e(TAG, "The shared training summary stopped", thrown)
        },
    )
    private val coreNudge = MutableStateFlow(0L)
    private val hintLock = Any()
    private var hintCache: Pair<HintCacheKey, List<ProgressionHint>?>? = null

    private val assembledWithPlan by lazy { shareAssembled(includeWeekPlan = true) }
    private val assembledWithoutPlan by lazy { shareAssembled(includeWeekPlan = false) }

    private fun assembled(includeWeekPlan: Boolean): SharedReadRecovery<Assembled> =
        if (includeWeekPlan) assembledWithPlan else assembledWithoutPlan

    private fun shareAssembled(includeWeekPlan: Boolean) = SharedReadRecovery(
        scope = sharedScope,
        graceMs = shareGraceMs,
        what = "the training summary",
    ) {
        assemble(includeWeekPlan)
    }

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
        assembled(includeWeekPlan).observe(),
        window,
        refreshNudgesCore(refresh),
    ) { health, heatWindow, _ ->
        when (health) {
            is DataHealth.Available -> DataHealth.Available(health.value to heatWindow)
            is DataHealth.Degraded -> DataHealth.Degraded(health.lastValue to heatWindow, health.what)
            is DataHealth.Unavailable -> health
        }
    }.mapHealthCatching("the training summary") { (assembled, heatWindow) ->
        retarget(assembled, heatWindow)
    }.presentValues().flowOn(computeDispatcher)

    private fun refreshNudgesCore(refresh: Flow<Any?>): Flow<Any?> = flow {
        var first = true
        refresh.collect { value ->
            if (!first) synchronized(hintLock) { coreNudge.value += 1L }
            first = false
            emit(value)
        }
    }

    private fun assemble(includeWeekPlan: Boolean): Flow<DataHealth<Assembled>> {
        val activitySummaries = activityRepository?.observeCompletedSummariesHealth()
            ?: flowOf(DataHealth.Available(emptyList()))
        val windowStart = nowMs() - WINDOW_MS
        val summaries = combineHealth(
            workoutRepository.observeSessionSummariesHealth(),
            activitySummaries,
        ) { workouts, activities -> workouts + activities }
        val history = combineHealth(
            workoutRepository.observeFinishedSinceHealth(windowStart),
            activityRepository?.observeCompletedGraphsSinceHealth(windowStart)
                ?: flowOf(DataHealth.Available(emptyList())),
        ) { sessions, activities ->
            windowedInsightHistory(
                sessions = sessions,
                activities = activities,
                minPerformedAtMs = nowMs() - WINDOW_MS,
            )
        }
        val historyAndRoutines = combineHealth(
            combineHealth(summaries, history) { totals, recent -> totals to recent },
            routineRepository.observeAllHealth(),
        ) { historyAndSummaries, routines -> historyAndSummaries to routines }
        val catalogAndRecency = combineHealth(
            exerciseRepository.observeAllHealth(),
            workoutRepository.observeFinishedLastLoggedHealth(),
        ) { exercises, recency -> exercises.associateBy { it.id } to recency }
        val preferencesHealth = preferencesRepository.observeHomePreferencesHealth()
        val core = combineHealth(
            historyAndRoutines,
            combineHealth(catalogAndRecency, preferencesHealth) { catalog, preferences ->
                catalog to preferences
            },
        ) { historyAndRoutinesValue, catalogAndPreferences ->
            val (historyAndSummaries, routines) = historyAndRoutinesValue
            val (catalog, preferences) = catalogAndPreferences
            Sources(
                history = historyAndSummaries.second,
                summaries = historyAndSummaries.first,
                routines = routines,
                exercises = catalog.first,
                lastLoggedAtByExerciseId = catalog.second,
                preferences = preferences.schedulePreferences,
                unit = preferences.weightUnit,
                coachPrefs = preferences.coachPreferences,
                lighterWeekStartEpochDay = preferences.lighterWeekStartEpochDay,
            )
        }
        val sources = combineHealth(core, scheduleRepository.observeSlotsHealth()) { value, slots ->
            value.copy(slots = slots)
        }.mapHealthCatching("the training summary") { value ->
            val today = Instant.ofEpochMilli(nowMs()).atZone(zone()).toLocalDate()
            val thisWeek = LighterWeek.weekStartEpochDay(
                com.sinura.personaltrainer.domain.CivilDate.fromEpochDay(today.toEpochDay()),
                value.preferences.weekStart,
            )
            value.copy(
                lighterWeek = LighterWeek.isCurrent(value.lighterWeekStartEpochDay, thisWeek),
                lighterWeekStartEpochDay = null,
            )
        }
        return sources.distinctUntilChanged()
            .combine(coreNudge) { value, _ -> value }
            .mapHealthCatching("the training summary") { value ->
                val hints = cachedHints(value)
                val insights = compute(
                    TrainingInsightsInput(
                        history = value.history,
                        summaries = value.summaries,
                        routines = value.routines,
                        exerciseCatalog = value.exercises,
                        lastLoggedAtByExerciseId = value.lastLoggedAtByExerciseId,
                        hints = hints,
                        preferences = value.preferences,
                        unit = value.unit,
                        slots = value.slots,
                        coachPrefs = value.coachPrefs,
                        window = HeatWindow.CURRENT_WEEK,
                        nowMs = nowMs(),
                        time = time,
                        zoneId = zone().id,
                        includeWeekPlan = includeWeekPlan,
                    ),
                )
                Assembled(value, insights)
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
            time = time,
            onStageFailure = ::logStageFailure,
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
        val lighterWeekStartEpochDay: Long? = null,
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
