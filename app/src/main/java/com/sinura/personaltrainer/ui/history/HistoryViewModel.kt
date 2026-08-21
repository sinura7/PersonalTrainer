package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.data.repository.RepeatOutcome
import com.sinura.personaltrainer.domain.PrSummaryRow
import com.sinura.personaltrainer.domain.SessionMonthGroup
import com.sinura.personaltrainer.domain.TrainingCalendarBuilder
import com.sinura.personaltrainer.domain.groupSessionsByMonth
import com.sinura.personaltrainer.domain.prSummary
import com.sinura.personaltrainer.domain.TrainingMonth
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZoneId

data class HistoryUiState(
    val isLoading: Boolean = true,
    val sessions: List<WorkoutSession> = emptyList(),
    /** The same sessions, grouped for the list. Derived, never a second query. */
    val monthGroups: List<SessionMonthGroup> = emptyList(),
    /** The standing records, newest first — what History could never tell you before. */
    val records: List<PrSummaryRow> = emptyList(),
    val calendar: TrainingMonth = TrainingMonth(month = YearMonth.now()),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
)

class HistoryViewModel(application: Application) : AppViewModel(application) {
    /**
     * Which month the calendar is showing. Held in the ViewModel rather than the composition so
     * paging back through a year survives rotation and process death.
     */
    private val visibleMonth = MutableStateFlow(YearMonth.now())

    /**
     * One-shot navigation held as state rather than a captured callback: the repeat writes a
     * session row before the destination is known, and a lambda captured into that coroutine
     * belongs to a composition that may already be gone.
     */
    private val _navigateToSession = MutableStateFlow<String?>(null)
    val navigateToSession: StateFlow<String?> = _navigateToSession.asStateFlow()

    private val _blockedRepeat = MutableStateFlow<RepeatOutcome.Blocked?>(null)
    val blockedRepeat: StateFlow<RepeatOutcome.Blocked?> = _blockedRepeat.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val uiState: StateFlow<HistoryUiState> = combine(
        container.workoutRepository.observeHistory(),
        container.preferencesRepository.schedulePreferences,
        visibleMonth,
    ) { sessions, preferences, month ->
        HistoryUiState(
            isLoading = false,
            sessions = sessions,
            monthGroups = groupSessionsByMonth(sessions, ZoneId.systemDefault()),
            records = prSummary(sessions),
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

    /**
     * Starts a fresh session shaped like an old one.
     *
     * The outcome is three-way on purpose. A repeat that quietly resumed whatever session
     * happened to be open would be the worst of the three: the lifter taps "Repeat Push Day"
     * and lands in Tuesday's half-finished Legs, with no signal that anything went wrong.
     */
    fun repeatSession(sessionId: String) {
        viewModelScope.launch {
            runCatchingCancellable { container.workoutRepository.repeatSession(sessionId) }
                .onSuccess { outcome ->
                    when (outcome) {
                        is RepeatOutcome.Started -> _navigateToSession.value = outcome.sessionId
                        is RepeatOutcome.Blocked -> _blockedRepeat.value = outcome
                        is RepeatOutcome.Failed -> _error.value = outcome.message
                    }
                }
                .onFailure { thrown ->
                    AppLog.w(TAG, "repeatSession failed", thrown)
                    _error.value = "Could not repeat that workout. Try again."
                }
        }
    }

    fun resumeBlockedSession() {
        val blocked = _blockedRepeat.value ?: return
        _blockedRepeat.value = null
        _navigateToSession.value = blocked.inProgressSessionId
    }

    fun dismissBlockedRepeat() {
        _blockedRepeat.value = null
    }

    fun onNavigationHandled() {
        _navigateToSession.value = null
    }

    fun onErrorShown() {
        _error.value = null
    }
}

private const val TAG = "PT/HistoryViewModel"
