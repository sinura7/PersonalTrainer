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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
     * Seeded from the stored preference, so the map opens on the window you last chose rather
     * than resetting to a default every time the process dies.
     */
    private val window = MutableStateFlow(HeatWindow.CURRENT_WEEK)

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

    val uiState: StateFlow<ProgressUiState> = container.trainingInsights
        .observe(window = window, refresh = refreshAt, includeWeekPlan = false)
        .map { insights ->
            ProgressUiState(
                isLoading = false,
                // Read back from the insights rather than from `window`: the body map and the
                // chip that labels it must never describe different windows mid-switch.
                window = insights.snapshot?.window ?: window.value,
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

    init {
        viewModelScope.launch {
            window.value = container.preferencesRepository.heatWindow.first()
        }
    }

    fun setWindow(value: HeatWindow) {
        window.value = value
        viewModelScope.launch { container.preferencesRepository.setHeatWindow(value) }
    }

    fun retry() {
        refreshAt.value = System.currentTimeMillis()
    }

    /**
     * Marks this calendar week lighter. The same write as Plan's Tune chip.
     *
     * Body observes insights without a week plan, so the start day is computed from
     * today and the stored week-start — not from `insights.weekPlan`.
     */
    fun markLighterWeek() {
        viewModelScope.launch {
            val weekStart = container.preferencesRepository.schedulePreferences.first().weekStart
            val start = LighterWeek.weekStartEpochDay(today = LocalDate.now(), weekStart = weekStart)
            container.preferencesRepository.setLighterWeekStartEpochDay(start)
        }
    }
}
