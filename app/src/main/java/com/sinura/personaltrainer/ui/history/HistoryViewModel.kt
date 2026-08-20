package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.TrainingCalendarBuilder
import com.sinura.personaltrainer.domain.TrainingMonth
import com.sinura.personaltrainer.domain.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZoneId

data class HistoryUiState(
    val isLoading: Boolean = true,
    val sessions: List<WorkoutSession> = emptyList(),
    val calendar: TrainingMonth = TrainingMonth(month = YearMonth.now()),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
)

class HistoryViewModel(application: Application) : AppViewModel(application) {
    /**
     * Which month the calendar is showing. Held in the ViewModel rather than the composition so
     * paging back through a year survives rotation and process death.
     */
    private val visibleMonth = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<HistoryUiState> = combine(
        container.workoutRepository.observeHistory(),
        container.preferencesRepository.schedulePreferences,
        visibleMonth,
    ) { sessions, preferences, month ->
        HistoryUiState(
            isLoading = false,
            sessions = sessions,
            calendar = TrainingCalendarBuilder.build(
                month = month,
                sessions = sessions,
                zone = ZoneId.systemDefault(),
                // The calendar's weeks start where the planner's and the heat map's do, or
                // "this week" would mean a third thing in the same app.
                weekStart = preferences.weekStart,
            ),
            weekStart = preferences.weekStart,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(),
        )

    fun showPreviousMonth() {
        visibleMonth.value = visibleMonth.value.minusMonths(1)
    }

    fun showNextMonth() {
        // Never past the current month: nothing is ever logged in the future.
        val next = visibleMonth.value.plusMonths(1)
        if (next <= YearMonth.now()) visibleMonth.value = next
    }
}
