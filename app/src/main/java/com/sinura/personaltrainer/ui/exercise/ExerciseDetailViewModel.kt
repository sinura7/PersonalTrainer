package com.sinura.personaltrainer.ui.exercise

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseHistory
import com.sinura.personaltrainer.domain.ExerciseHistoryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

data class ExerciseDetailUiState(
    val isLoading: Boolean = true,
    /** The row is gone — deleted from the library, or restored over. Terminal. */
    val missing: Boolean = false,
    val exercise: Exercise? = null,
    val history: ExerciseHistory = ExerciseHistory(exerciseId = ""),
)

class ExerciseDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AppViewModel(application) {
    private val exerciseId: String = savedStateHandle.get<String>("exerciseId").orEmpty()

    /**
     * Same reason Active Workout tracks this: "exercise is null" alone cannot tell "the query
     * has not answered" from "the row is gone", and collapsing them leaves the screen spinning
     * on a deleted lift with no way out.
     */
    private val resolved = MutableStateFlow(exerciseId.isBlank())

    val uiState: StateFlow<ExerciseDetailUiState> = combine(
        container.exerciseRepository.observeById(exerciseId).onEach { resolved.value = true },
        container.workoutRepository.observeExerciseSets(exerciseId),
        container.preferencesRepository.schedulePreferences,
        resolved,
    ) { exercise, entries, preferences, isResolved ->
        ExerciseDetailUiState(
            isLoading = !isResolved,
            missing = isResolved && exercise == null,
            exercise = exercise,
            history = ExerciseHistoryBuilder.fromEntries(
                exerciseId = exerciseId,
                entries = entries,
                zone = ZoneId.systemDefault(),
                // The tonnage weeks have to start where the planner's weeks start, or "this
                // week's volume" means two different spans in two places in the same app.
                weekStart = preferences.weekStart,
            ),
        )
    }
        // Bucketing a lift's whole history is real work and does not belong on the main thread.
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExerciseDetailUiState(),
        )
}
