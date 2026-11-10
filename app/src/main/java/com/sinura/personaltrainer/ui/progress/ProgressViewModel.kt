package com.sinura.personaltrainer.ui.progress

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.BodyExplorer
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.InsightFailure
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.BodyWeekActivity
import com.sinura.personaltrainer.domain.BodyWeekActivityMath
import com.sinura.personaltrainer.domain.RecordsCalculator
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import com.sinura.personaltrainer.data.repository.observeHealth
import com.sinura.personaltrainer.domain.DataHealth
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProgressUiState(
    val isLoading: Boolean = true,
    val window: HeatWindow = HeatWindow.CURRENT_WEEK,
    val snapshot: BodyHeatSnapshot? = null,
    val recommendations: List<TrainingRecommendation> = emptyList(),
    /**
     * Catalog lifts Body can name before any set is logged. Empty once there is
     * history — the coach cards take this slot — and empty if the catalog has not
     * loaded yet.
     */
    val firstLifts: List<Exercise> = emptyList(),
    val catalog: List<Exercise> = emptyList(),
    val coachPrefs: CoachPreferences = CoachPreferences.DEFAULT,
    /** Fatal: there is no map to draw. The screen replaces its content with a way out. */
    val error: String? = null,
    /**
     * Non-fatal: the map is real, something beside it is not. Kept separate because the screen's
     * error branch is terminal — folding a failed progression query into [error] would blank a
     * body map that had computed perfectly well.
     */
    val notice: String? = null,
)

class ProgressViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    /**
     * Forces a recompute without changing the window.
     *
     * Retry cannot be expressed as `setWindow(currentWindow)`: a StateFlow conflates a write
     * of the value it already holds, so nothing downstream ever re-runs. On the fatal-error
     * branch the window shown *is* the current one by construction, which made the only
     * action on that screen a button that could never succeed. Schedule's regenerate already
     * uses this seam the same way.
     */
    private val refreshAt = MutableStateFlow(0L)

    /**
     * The stored chip is the input, not a later correction. Seeding
     * [HeatWindow.CURRENT_WEEK] and overwriting in `init` flashed the
     * wrong window on every Body open.
     */
    val uiState: StateFlow<ProgressUiState> = combine(
        combine(
            container.preferencesRepository.heatWindow,
            container.trainingInsights.observe(
                window = container.preferencesRepository.heatWindow,
                refresh = refreshAt,
                includeWeekPlan = false,
            ),
        ) { stored, insights -> stored to insights },
        combine(
            container.exerciseRepository.observeAll(),
            container.preferencesRepository.coachPreferences,
        ) { catalog, coach -> catalog to coach },
    ) { windowAndInsights, catalogAndCoach ->
        val (stored, insights) = windowAndInsights
        val (catalog, coach) = catalogAndCoach
        val hasWork = insights.snapshot?.hasAnyWorkingSets == true
        ProgressUiState(
            isLoading = false,
            window = insights.snapshot?.window ?: stored,
            snapshot = insights.snapshot,
            recommendations = insights.recommendations,
            firstLifts = if (hasWork) emptyList() else BodyExplorer.coverage(catalog, coach),
            catalog = catalog,
            coachPrefs = coach,
            error = "Couldn’t load the body map. Try switching the window."
                .takeIf { insights.failed(InsightFailure.HEAT) },
            notice = when {
                insights.failed(InsightFailure.PROGRESSION) ->
                    "Couldn’t check which lifts are ready to progress."
                insights.failed(InsightFailure.RECOMMENDATIONS) ->
                    "Couldn’t work out this week’s suggestions."
                else -> null
            },
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProgressUiState(),
        )

    /** Civil today for the 8 week window. The screen feeds LocalTodayEpochDay so midnight moves it. */
    private val trendsToday = MutableStateFlow(todayEpochDay())
    private val trendsRetry = MutableStateFlow(0)

    /** The last volume that was read, kept so a later failed read shows it as stale. */
    @Volatile private var lastVolume: List<MuscleWeeks>? = null

    fun setTrendsToday(epochDay: Long) {
        trendsToday.value = epochDay
    }

    /** The weekly check-in's write, offered from Body's empty bodyweight card. */
    private val _weighIn = MutableStateFlow(WeighIn())

    /** Body's Log weight, on Home's check-in path: range check, pending, a visible error. */
    val weighIn: StateFlow<WeighIn> = _weighIn

    /**
     * Same admission as Home's weekly check-in. The repository silently drops an out of range
     * value, so the range is checked here and refused with a message; the dialog stays open
     * with the typed value until the write actually returns.
     */
    fun recordBodyweight(kg: Double): Boolean {
        val current = _weighIn.value
        if (current.pending || current.saved) return false
        if (!kg.isFinite() || kg !in OnboardingAnswers.MIN_BODYWEIGHT_KG..OnboardingAnswers.MAX_BODYWEIGHT_KG) {
            _weighIn.value = WeighIn(error = getApplication<Application>().getString(R.string.body_weight_out_of_range))
            return false
        }
        _weighIn.value = WeighIn(pending = true)
        val day = trendsToday.value
        viewModelScope.launch {
            runCatchingCancellable { container.preferencesRepository.recordBodyweight(kg, day) }
                .onSuccess { _weighIn.value = WeighIn(saved = true) }
                .onFailure { thrown ->
                    AppLog.w(TAG, "Saving the weigh-in from Body failed", thrown)
                    _weighIn.value = WeighIn(error = getApplication<Application>().getString(R.string.body_weight_save_failed))
                }
        }
        return true
    }

    /** The dialog closed: after a save, or a dismissal while nothing is in flight. */
    fun onWeighInClosed(): Boolean {
        if (_weighIn.value.pending) return false
        _weighIn.value = WeighIn()
        return true
    }

    fun retryTrends() {
        trendsRetry.value += 1
    }

    /**
     * v4 V7: Body's trend cards. Each card carries its own read health, so one failed source
     * shows Retry on its card rather than "nothing yet", and never blanks the map above.
     * Records are the union of workout and activity sets, like History's.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val trends: StateFlow<BodyTrends> = combine(trendsToday, trendsRetry) { today, _ -> today }
        .flatMapLatest { today ->
            val first = BodyTrendsMath.firstWeekStart(today)
            val records = combine(
                container.workoutRepository.observeRecordSetsHealth(),
                container.activityRepository.observeRecordSetsHealth(),
            ) { workouts, activities ->
                RecordsCalculator.standing(
                    workouts.presentValue().orEmpty() + activities.presentValue().orEmpty(),
                    limit = Int.MAX_VALUE,
                ) to cardRead(workouts, activities)
            }
            val bodyweight = container.preferencesRepository.bodyweightLog.observeHealth("the bodyweight log")
            val volume = combine(
                container.workoutRepository.observeFinishedWorkRevisionHealth(),
                container.exerciseRepository.observeAllHealth(),
                container.activityRepository.observeCompletedSummariesHealth(),
                container.workoutRepository.observeSessionSummariesHealth(),
            ) { revision, catalog, activities, workouts ->
                Quad(revision, catalog, activities, workouts)
            }.mapLatest { (revision, catalog, activities, workouts) ->
                readVolume(first, today, revision, catalog, activities, workouts)
            }
            combine(records, bodyweight, volume) { (recordRows, recordsRead), weights, volumeResult ->
                BodyTrends(
                    bodyweight = weights.presentValue().orEmpty(),
                    bodyweightRead = cardRead(weights),
                    records = recordRows,
                    recordsRead = recordsRead,
                    weeklySets = volumeResult.weeks,
                    volumeRead = volumeResult.read,
                    weekActivity = volumeResult.weekActivity,
                    firstWeekStartEpochDay = first,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyTrends())

    private data class VolumeRead(
        val weeks: List<MuscleWeeks>,
        val read: CardRead,
        val weekActivity: BodyWeekActivity,
    )

    private var lastWeekActivity: BodyWeekActivity? = null

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private suspend fun readVolume(
        first: Long,
        todayEpochDay: Long,
        revision: DataHealth<*>,
        catalog: DataHealth<List<Exercise>>,
        activities: DataHealth<List<com.sinura.personaltrainer.domain.SessionSummary>>,
        workouts: DataHealth<List<com.sinura.personaltrainer.domain.SessionSummary>>,
    ): VolumeRead {
        val lifts = catalog.presentValue()
        val failedSource = revision.isUnavailable || lifts == null
        val activityFailed = activities.isUnavailable
        if (!failedSource) {
            val sinceMs = time.startOfDayMillis(CivilDate.fromEpochDay(first), time.defaultZoneId())
            val rows = runCatchingCancellable {
                container.workoutRepository.workingSetCountsSince(sinceMs) +
                    container.activityRepository.workingSetCountsSince(sinceMs)
            }
            rows.getOrNull()?.let { read ->
                val weeks = BodyTrendsMath.weeklySets(read, lifts.orEmpty(), first) { ms ->
                    time.capture(ms, time.defaultZoneId()).localEpochDay
                }
                lastVolume = weeks
                val weekStart = java.time.LocalDate.ofEpochDay(todayEpochDay)
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    .toEpochDay()
                val weekEnd = weekStart + 6
                var auxMinutes = 0
                workouts.presentValue().orEmpty()
                    .filter { it.localEpochDay in weekStart..weekEnd }
                    .forEach { summary ->
                        if (container.workoutRepository.excludeAuxiliaryVolume(
                                summary.routineId,
                                summary.routineName,
                            )
                        ) {
                            auxMinutes += summary.durationMinutes
                        }
                    }
                val weekActivity = if (!activityFailed) {
                    BodyWeekActivityMath.forWeek(
                        weekStartEpochDay = weekStart,
                        weekEndEpochDay = weekEnd,
                        activitySummaries = activities.presentValue().orEmpty(),
                        auxiliaryWorkoutMinutes = auxMinutes,
                    ).also { lastWeekActivity = it }
                } else {
                    lastWeekActivity ?: BodyWeekActivity()
                }
                return VolumeRead(weeks, cardRead(revision, catalog, activities), weekActivity)
            }
        }
        val held = lastVolume
        val activityHeld = lastWeekActivity ?: BodyWeekActivity()
        return if (held != null) {
            VolumeRead(held, CardRead.STALE, activityHeld)
        } else {
            VolumeRead(emptyList(), CardRead.FAILED, activityHeld)
        }
    }

    fun setWindow(value: HeatWindow) {
        viewModelScope.launch { container.preferencesRepository.setHeatWindow(value) }
    }

    fun retry() {
        refreshAt.value = System.currentTimeMillis()
    }

    /**
     * Marks this calendar week lighter. The same write as Plan's lighter chip.
     *
     * Body observes insights without a week plan, so the start day is computed from
     * today and the stored week-start — not from `insights.weekPlan`.
     */
    fun markLighterWeek() {
        viewModelScope.launch {
            val weekStart = container.preferencesRepository.schedulePreferences.first().weekStart
            val start = LighterWeek.weekStartEpochDay(
                today = civilToday(),
                weekStart = weekStart,
            )
            container.preferencesRepository.setLighterWeekStartEpochDay(start)
        }
    }
}

/** FAILED when any source never read, STALE when any holds an old value, else OK. */
internal fun cardRead(vararg reads: DataHealth<*>): CardRead = when {
    reads.any { it is DataHealth.Unavailable } -> CardRead.FAILED
    reads.any { it is DataHealth.Degraded } -> CardRead.STALE
    else -> CardRead.OK
}

/** Body's Log weight write: [pending] while the repository runs, [saved] until the dialog closes. */
data class WeighIn(val pending: Boolean = false, val error: String? = null, val saved: Boolean = false)

private const val TAG = "PT/ProgressViewModel"
