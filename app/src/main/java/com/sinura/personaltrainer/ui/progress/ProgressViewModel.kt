package com.sinura.personaltrainer.ui.progress

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.InsightFailure
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.TrainingRecommendation
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
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
        container.preferencesRepository.heatWindow,
        container.trainingInsights.observe(
            window = container.preferencesRepository.heatWindow,
            refresh = refreshAt,
            includeWeekPlan = false,
        ),
    ) { stored, insights ->
        ProgressUiState(
            isLoading = false,
            window = insights.snapshot?.window ?: stored,
            snapshot = insights.snapshot,
            recommendations = insights.recommendations,
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
                today = com.sinura.personaltrainer.domain.CivilDate.fromEpochDay(LocalDate.now().toEpochDay()),
                weekStart = weekStart,
            )
            container.preferencesRepository.setLighterWeekStartEpochDay(start)
        }
    }
}
