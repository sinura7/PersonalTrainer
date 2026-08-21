package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.AddDefaults
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.OwnedLiftResolver
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.workout.DiscardOutcome
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PT/StartOptionsVM"

data class StartOptionsUiState(
    val isLoading: Boolean = true,
    val inProgress: WorkoutSession? = null,
    val routines: List<Routine> = emptyList(),
    /** The coach's named lift, offered as a one-tap start. Null when it has nothing specific. */
    val suggestion: Exercise? = null,
    val suggestionReason: String? = null,
    val error: String? = null,
)

/**
 * The start spine, now behind a sheet instead of a screen.
 *
 * Starting a workout used to cost a full-screen interstitial: you tapped Start on Home, a
 * screen appeared, you tapped again. It also became a THIRD place that offered to resume a
 * live session, alongside Home's hero and the notification. The logic here is unchanged — what
 * changed is that it opens over the screen you were already on, so the common case (start
 * today's plan) skips it entirely and everything else is one tap deeper rather than one screen.
 */
class StartOptionsViewModel(application: Application) : AppViewModel(application) {
    private val error = MutableStateFlow<String?>(null)

    private val suggestedLift: Flow<Pair<Exercise, String>?> =
        container.trainingInsights.observe(includeWeekPlan = false)
            .map { insights ->
                val card = insights.recommendations.firstOrNull { it.actionExerciseId != null }
                    ?: return@map null
                val exercise = container.exerciseRepository.getById(card.actionExerciseId!!)
                    ?: return@map null
                exercise to card.title
            }
            .catch { thrown ->
                AppLog.w(TAG, "Reading the suggested lift failed", thrown)
                emit(null)
            }

    val uiState: StateFlow<StartOptionsUiState> = combine(
        container.workoutRepository.observeInProgress(),
        container.routineRepository.observeAll(),
        suggestedLift,
        error,
    ) { inProgress, routines, suggested, err ->
        StartOptionsUiState(
            isLoading = false,
            inProgress = inProgress,
            routines = routines,
            suggestion = suggested?.first,
            suggestionReason = suggested?.second,
            error = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StartOptionsUiState(),
    )

    /**
     * The session to open, held as state rather than passed as a callback.
     *
     * A navigation lambda captured into a viewModelScope coroutine closes over the
     * composition's NavController; if the Activity is recreated between the tap and the
     * database write completing, that controller is dead and the navigation is simply lost —
     * the workout starts but the screen never moves. A StateFlow survives recreation and is
     * re-read by the new composition.
     */
    private val _navigateToSession = MutableStateFlow<String?>(null)
    val navigateToSession: StateFlow<String?> = _navigateToSession.asStateFlow()

    fun onSessionNavigationHandled() {
        _navigateToSession.value = null
    }

    fun startRoutine(routineId: String) {
        viewModelScope.launch {
            val routine = container.routineRepository.getById(routineId)
            if (routine == null) {
                error.value = "That routine is no longer available."
                return@launch
            }
            if (routine.exercises.isEmpty()) {
                error.value = "Add at least one exercise before starting this routine."
                return@launch
            }
            try {
                val session = container.workoutRepository.startRoutine(routine)
                error.value = null
                _navigateToSession.value = session.id
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startRoutine failed", thrown)
                error.value = "Could not start that routine. Try again."
            }
        }
    }

    /**
     * Starts a free session with the suggested lift already in it.
     *
     * The suggestion is only worth pinning here if acting on it is one tap: a row that opened
     * a picker so you could find the lift it had just named would be a worse version of
     * scrolling the routines list.
     */
    fun startSuggested() {
        val exercise = uiState.value.suggestion ?: return
        viewModelScope.launch {
            try {
                val focus = OwnedLiftResolver.primaryMuscleOf(exercise)?.displayName
                val session = container.workoutRepository.startFreeWorkout(focusTitle = focus)
                val defaults = AddDefaults.forExercise(exercise)
                container.workoutRepository.addExerciseToSession(
                    sessionId = session.id,
                    exercise = exercise,
                    targetSets = defaults.sets,
                    targetReps = defaults.reps,
                    targetWeightKg = null,
                    restSeconds = defaults.restSeconds,
                )
                error.value = null
                _navigateToSession.value = session.id
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startSuggested failed", thrown)
                error.value = "Could not start that session. Try again."
            }
        }
    }

    /**
     * Discards the live session from inside the sheet.
     *
     * Routed through the shared use case so the rest timer stops and the draft is cleared —
     * the sheet is not allowed its own idea of what discarding means.
     */
    fun discardInProgress() {
        val live = uiState.value.inProgress ?: return
        viewModelScope.launch {
            when (val result = container.discardWorkout(live.id)) {
                DiscardOutcome.Discarded -> error.value = null
                is DiscardOutcome.Failed -> error.value = result.message
            }
        }
    }

    fun startFree() {
        viewModelScope.launch {
            try {
                val session = container.workoutRepository.startFreeWorkout()
                error.value = null
                _navigateToSession.value = session.id
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startFree failed", thrown)
                error.value = "Could not start a free workout. Try again."
            }
        }
    }
}
