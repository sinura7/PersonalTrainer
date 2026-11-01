package com.sinura.personaltrainer.ui.progress

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.BodyExplorer
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.InsightFailure
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.RecordsCalculator
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
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

    /**
     * v4 V7: Body's trend cards. Re-read whenever finished work changes; each source that fails
     * leaves its card empty rather than blanking the map above.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val trends: StateFlow<BodyTrends> = combine(
        container.workoutRepository.observeFinishedWorkRevisionHealth(),
        container.exerciseRepository.observeAll(),
        container.preferencesRepository.bodyweightLog.catch { emit(emptyList()) },
        container.workoutRepository.observeRecordSetsHealth(),
    ) { _, catalog, bodyweight, recordSets -> Triple(catalog, bodyweight, recordSets) }
        .mapLatest { (catalog, bodyweight, recordSets) ->
            val today = todayEpochDay()
            val first = BodyTrendsMath.firstWeekStart(today)
            val sinceMs = time.startOfDayMillis(CivilDate.fromEpochDay(first), time.defaultZoneId())
            val rows = runCatchingCancellable { container.workoutRepository.workingSetCountsSince(sinceMs) }
                .getOrElse { emptyList() }
            BodyTrends(
                bodyweight = bodyweight.filter { it.epochDay >= first },
                records = RecordsCalculator.standing(recordSets.presentValue().orEmpty(), limit = Int.MAX_VALUE),
                weeklySets = BodyTrendsMath.weeklySets(rows, catalog, first) { ms ->
                    time.capture(ms, time.defaultZoneId()).localEpochDay
                },
                firstWeekStartEpochDay = first,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyTrends())

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
