package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.ScheduleRule
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PT/RoutinesVM"

data class RoutinesUiState(
    val isLoading: Boolean = true,
    val routines: List<Routine> = emptyList(),
    val rules: List<ScheduleRule> = emptyList(),
    /** Planned occurrences in this calendar week, for the delete confirmation copy. */
    val occurrences: List<ScheduleOccurrence> = emptyList(),
    val error: String? = null,
)

/**
 * v4 V3: the Routines screen's own state. Reads routines and the planner rules only, so it never
 * touches Plan's one-shot flags (pendingWeekSuggestion, pendingAnswerReplay) or its pipelines.
 */
class RoutinesViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application = application, container = container) {
    private val actionError = MutableStateFlow<String?>(null)
    private val readError = MutableStateFlow<String?>(null)

    private val routines = container.routineRepository.observeAll()
        .catch { thrown ->
            AppLog.w(TAG, READ_FAILED, thrown)
            readError.value = READ_FAILED
            emit(emptyList())
        }

    val uiState: StateFlow<RoutinesUiState> = combine(
        routines,
        container.plannerRepository.observeRules(),
        container.plannerRepository.observeOccurrences(),
        container.preferencesRepository.schedulePreferences,
        combine(actionError, readError) { action, read -> action ?: read },
    ) { list, rules, occurrences, preferences, error ->
        val today = todayEpochDay()
        val weekStart = CivilDate.fromEpochDay(today).previousOrSame(preferences.weekStart).epochDay
        RoutinesUiState(
            isLoading = false,
            routines = list,
            rules = rules,
            occurrences = occurrences.filter { it.localEpochDay in weekStart..(weekStart + 6) },
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesUiState())

    fun deleteRoutine(routineId: String) {
        viewModelScope.launch {
            runCatchingCancellable { container.routineRepository.delete(routineId) }
                .onSuccess { actionError.value = null }
                .onFailure { thrown ->
                    AppLog.w(TAG, DELETE_FAILED, thrown)
                    actionError.value = DELETE_FAILED
                }
        }
    }

    fun dismissError() {
        actionError.value = null
        readError.value = null
    }

    companion object {
        const val READ_FAILED = "Could not read your routines. Try again."
        const val DELETE_FAILED = "Could not delete that routine. Try again."
    }
}
