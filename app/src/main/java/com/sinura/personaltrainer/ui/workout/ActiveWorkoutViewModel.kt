package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.WorkoutSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActiveExerciseDraft(
    val weightKg: Double = 0.0,
    val reps: Int = 5,
    val rpe: Int? = null,
    val isWarmup: Boolean = false,
)

data class ActiveWorkoutUiState(
    val isLoading: Boolean = true,
    val session: WorkoutSession? = null,
    val selectedExerciseId: String? = null,
    val draft: ActiveExerciseDraft = ActiveExerciseDraft(),
    val hint: ProgressionHint? = null,
    val restRemainingSeconds: Int = 0,
    val restTotalSeconds: Int = 90,
    val searchQuery: String = "",
    val searchResults: List<Exercise> = emptyList(),
    val showExercisePicker: Boolean = false,
    val notes: String = "",
    val error: String? = null,
    val finished: Boolean = false,
    val editingSetId: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AppViewModel(application) {
    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val selectedExerciseId = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow(ActiveExerciseDraft())
    private val hint = MutableStateFlow<ProgressionHint?>(null)
    private val restRemaining = MutableStateFlow(0)
    private val restTotal = MutableStateFlow(90)
    private val searchQuery = MutableStateFlow("")
    private val showPicker = MutableStateFlow(false)
    private val notes = MutableStateFlow("")
    private val error = MutableStateFlow<String?>(null)
    private val finished = MutableStateFlow(false)
    private val editingSetId = MutableStateFlow<String?>(null)
    private var restJob: Job? = null
    private var lastPrefillExerciseId: String? = null

    private val sessionFlow = container.workoutRepository.observeSession(sessionId)

    init {
        viewModelScope.launch {
            sessionFlow.collect { session ->
                if (session != null && selectedExerciseId.value == null) {
                    selectedExerciseId.value = session.exercises.firstOrNull()?.exercise?.id
                }
                if (session != null && notes.value.isEmpty() && session.notes.isNotEmpty()) {
                    notes.value = session.notes
                }
            }
        }
    }

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        sessionFlow,
        selectedExerciseId,
        draft,
        hint,
        restRemaining,
    ) { session, selected, currentDraft, currentHint, rest ->
        WorkoutCore(session, selected, currentDraft, currentHint, rest)
    }.combine(
        combine(
            combine(restTotal, searchQuery, showPicker) { total, query, picker ->
                Triple(total, query, picker)
            },
            combine(notes, error, finished, editingSetId) { sessionNotes, err, done, editing ->
                EditorMeta(sessionNotes, err, done, editing)
            },
        ) { first, second ->
            WorkoutExtras(
                restTotal = first.first,
                query = first.second,
                showPicker = first.third,
                notes = second.notes,
                error = second.error,
                finished = second.finished,
                editingSetId = second.editingSetId,
            )
        },
    ) { core, extras ->
        ActiveWorkoutUiState(
            isLoading = core.session == null && !extras.finished,
            session = core.session,
            selectedExerciseId = core.selected,
            draft = core.draft,
            hint = core.hint,
            restRemainingSeconds = core.rest,
            restTotalSeconds = extras.restTotal,
            searchQuery = extras.query,
            searchResults = emptyList(),
            showExercisePicker = extras.showPicker,
            notes = extras.notes,
            error = extras.error,
            finished = extras.finished,
            editingSetId = extras.editingSetId,
        )
    }.combine(searchQuery.flatMapLatest { container.exerciseRepository.search(it) }) { state, results ->
        state.copy(searchResults = results)
    }.mapLatest { state ->
        prefillIfNeeded(state)
        state
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActiveWorkoutUiState(),
    )

    private suspend fun prefillIfNeeded(state: ActiveWorkoutUiState) {
        val session = state.session ?: return
        val exerciseId = state.selectedExerciseId ?: return
        if (exerciseId == lastPrefillExerciseId) return
        lastPrefillExerciseId = exerciseId
        val planned = session.exercises.firstOrNull { it.exercise.id == exerciseId }
        val targetReps = planned?.targetReps ?: 5
        val rest = planned?.restSeconds?.takeIf { it > 0 } ?: 90
        restTotal.value = rest
        val progression = container.workoutRepository.progressionFor(
            exerciseId = exerciseId,
            exerciseName = planned?.exercise?.name ?: "",
            targetReps = targetReps,
            excludeSessionId = sessionId,
        )
        hint.value = progression
        val lastWeight = progression?.suggestedWeightKg
            ?: planned?.targetWeightKg
            ?: 0.0
        draft.value = ActiveExerciseDraft(
            weightKg = lastWeight,
            reps = targetReps,
            rpe = null,
            isWarmup = false,
        )
    }

    fun selectExercise(exerciseId: String) {
        selectedExerciseId.value = exerciseId
        lastPrefillExerciseId = null
    }

    fun adjustWeight(deltaKg: Double) {
        draft.value = draft.value.copy(weightKg = (draft.value.weightKg + deltaKg).coerceAtLeast(0.0))
    }

    fun setWeight(weightKg: Double) {
        draft.value = draft.value.copy(weightKg = weightKg.coerceAtLeast(0.0))
    }

    fun adjustReps(delta: Int) {
        draft.value = draft.value.copy(reps = (draft.value.reps + delta).coerceAtLeast(0))
    }

    fun setRpe(rpe: Int?) {
        draft.value = draft.value.copy(rpe = rpe)
    }

    fun setWarmup(isWarmup: Boolean) {
        draft.value = draft.value.copy(isWarmup = isWarmup)
    }

    fun setNotes(value: String) {
        notes.value = value
    }

    fun setPickerVisible(visible: Boolean) {
        showPicker.value = visible
        if (!visible) searchQuery.value = ""
    }

    fun onSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun addExercise(exercise: Exercise) {
        viewModelScope.launch {
            container.workoutRepository.addExerciseToSession(sessionId, exercise)
            selectedExerciseId.value = exercise.id
            lastPrefillExerciseId = null
            showPicker.value = false
        }
    }

    fun createAndAddExercise(name: String, muscleGroup: String) {
        viewModelScope.launch {
            if (name.isBlank()) {
                error.value = "Exercise name is required."
                return@launch
            }
            val created = container.exerciseRepository.createCustom(name, muscleGroup)
            addExercise(created)
        }
    }

    fun logSet() {
        val exerciseId = selectedExerciseId.value ?: return
        val current = draft.value
        if (current.reps <= 0 && current.weightKg <= 0.0) {
            error.value = "Enter weight and reps before logging a set."
            return
        }
        viewModelScope.launch {
            val editingId = editingSetId.value
            if (editingId != null) {
                container.workoutRepository.updateSet(
                    setId = editingId,
                    weightKg = current.weightKg,
                    reps = current.reps,
                    rpe = current.rpe,
                    isWarmup = current.isWarmup,
                )
                editingSetId.value = null
            } else {
                container.workoutRepository.logSet(
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    weightKg = current.weightKg,
                    reps = current.reps,
                    rpe = current.rpe,
                    isWarmup = current.isWarmup,
                )
                if (!current.isWarmup) {
                    startRest(restTotal.value)
                }
            }
            error.value = null
            draft.value = current.copy(isWarmup = false, rpe = null)
        }
    }

    fun editSet(setId: String) {
        val set = uiState.value.session?.sets?.firstOrNull { it.id == setId } ?: return
        lastPrefillExerciseId = set.exerciseId
        selectedExerciseId.value = set.exerciseId
        draft.value = ActiveExerciseDraft(
            weightKg = set.weightKg,
            reps = set.reps,
            rpe = set.rpe,
            isWarmup = set.isWarmup,
        )
        editingSetId.value = set.id
    }

    fun cancelEdit() {
        editingSetId.value = null
    }

    fun deleteSet(setId: String) {
        viewModelScope.launch {
            if (editingSetId.value == setId) {
                editingSetId.value = null
            }
            container.workoutRepository.deleteSet(setId)
        }
    }

    fun skipRest() {
        restJob?.cancel()
        restRemaining.value = 0
    }

    fun adjustRest(deltaSeconds: Int) {
        if (restRemaining.value <= 0 && deltaSeconds < 0) return
        val next = (restRemaining.value + deltaSeconds).coerceAtLeast(0)
        restJob?.cancel()
        if (next == 0) {
            restRemaining.value = 0
            return
        }
        restJob = viewModelScope.launch {
            for (remaining in next downTo 0) {
                restRemaining.value = remaining
                if (remaining == 0) break
                delay(1_000)
            }
        }
    }

    fun startRest(seconds: Int = restTotal.value) {
        restJob?.cancel()
        restJob = viewModelScope.launch {
            for (remaining in seconds downTo 0) {
                restRemaining.value = remaining
                if (remaining == 0) break
                delay(1_000)
            }
        }
    }

    fun applySuggestedWeight() {
        val suggested = hint.value?.suggestedWeightKg ?: return
        draft.value = draft.value.copy(weightKg = suggested)
    }

    fun finishWorkout(onFinished: () -> Unit) {
        viewModelScope.launch {
            val current = uiState.value.session
            if (current == null || current.sets.isEmpty()) {
                error.value = "Log at least one set before finishing."
                return@launch
            }
            container.workoutRepository.finishSession(sessionId, notes.value)
            finished.value = true
            onFinished()
        }
    }

    fun discardWorkout(onDiscarded: () -> Unit) {
        viewModelScope.launch {
            container.workoutRepository.discardSession(sessionId)
            onDiscarded()
        }
    }

    private data class WorkoutCore(
        val session: WorkoutSession?,
        val selected: String?,
        val draft: ActiveExerciseDraft,
        val hint: ProgressionHint?,
        val rest: Int,
    )

    private data class WorkoutExtras(
        val restTotal: Int,
        val query: String,
        val showPicker: Boolean,
        val notes: String,
        val error: String?,
        val finished: Boolean,
        val editingSetId: String?,
    )

    private data class EditorMeta(
        val notes: String,
        val error: String?,
        val finished: Boolean,
        val editingSetId: String?,
    )
}
