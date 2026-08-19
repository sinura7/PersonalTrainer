package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineEditorPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutineEditorUiState(
    val isLoading: Boolean = true,
    val missing: Boolean = false,
    val routine: Routine? = null,
    val name: String = "",
    val notes: String = "",
    val searchQuery: String = "",
    val searchResults: List<Exercise> = emptyList(),
    val showExercisePicker: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineEditorViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AppViewModel(application) {
    private val incomingId: String? = RoutineEditorPolicy.incomingId(
        savedStateHandle.get<String>("routineId"),
    )
    private var createdThisSession: Boolean = incomingId == null
    private var loadedExisting: Boolean = false
    private var sawLiveRoutine: Boolean = false

    private val routineId = MutableStateFlow(incomingId)
    private val hydrated = MutableStateFlow(incomingId == null)
    private val missing = MutableStateFlow(false)
    private val name = MutableStateFlow("")
    private val notes = MutableStateFlow("")
    private val searchQuery = MutableStateFlow("")
    private val showPicker = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val saved = MutableStateFlow(false)

    private val routineFlow = routineId.flatMapLatest { id ->
        if (id == null) flowOf(null) else container.routineRepository.observeById(id)
    }

    private val resultsFlow = searchQuery.flatMapLatest { query ->
        container.exerciseRepository.search(query)
    }

    init {
        viewModelScope.launch {
            val id = incomingId
            if (id != null) {
                val existing = container.routineRepository.getById(id)
                if (existing != null) {
                    loadedExisting = true
                    name.value = existing.name
                    notes.value = existing.notes
                } else {
                    missing.value = true
                    error.value = "This routine is no longer available."
                    routineId.value = null
                }
            }
            hydrated.value = true
            routineFlow.collect { routine ->
                if (routine != null) {
                    sawLiveRoutine = true
                } else if (
                    loadedExisting &&
                    sawLiveRoutine &&
                    routineId.value != null &&
                    !missing.value
                ) {
                    missing.value = true
                    error.value = "This routine is no longer available."
                    routineId.value = null
                }
            }
        }
    }

    val uiState: StateFlow<RoutineEditorUiState> = combine(
        combine(routineFlow, name, notes, searchQuery, resultsFlow) { routine, currentName, currentNotes, query, results ->
            EditorCore(routine, currentName, currentNotes, query, results)
        },
        combine(showPicker, error, saved, hydrated, missing) { picker, err, didSave, ready, gone ->
            EditorFlags(picker, err, didSave, ready, gone)
        },
    ) { core, extras ->
        RoutineEditorUiState(
            isLoading = !extras.hydrated,
            missing = extras.missing,
            routine = core.routine,
            name = core.name,
            notes = core.notes,
            searchQuery = core.query,
            searchResults = core.results,
            showExercisePicker = extras.showPicker,
            error = extras.error,
            saved = extras.saved,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RoutineEditorUiState(isLoading = incomingId != null),
    )

    fun onNameChange(value: String) {
        name.value = value
        saved.value = false
    }

    fun onNotesChange(value: String) {
        notes.value = value
        saved.value = false
    }

    fun saveDetails() {
        if (missing.value) {
            error.value = "This routine is no longer available."
            return
        }
        viewModelScope.launch {
            val trimmedName = name.value.trim()
            if (trimmedName.isEmpty()) {
                error.value = "Give this routine a name."
                saved.value = false
                return@launch
            }
            val existingId = routineId.value
            val exercises = if (existingId != null) {
                currentExerciseCount(existingId)
            } else {
                0
            }
            if (exercises == 0) {
                error.value = "Add at least one exercise before saving."
                saved.value = false
                return@launch
            }
            val id = ensureRoutineId() ?: return@launch
            try {
                container.routineRepository.updateDetails(id, trimmedName, notes.value)
                error.value = null
                saved.value = true
            } catch (_: Exception) {
                error.value = "Could not save this routine. Try again."
                saved.value = false
            }
        }
    }

    fun leave(onLeave: () -> Unit) {
        viewModelScope.launch {
            discardEmptyStub()
            onLeave()
        }
    }

    fun setPickerVisible(visible: Boolean) {
        if (missing.value) return
        showPicker.value = visible
        if (!visible) searchQuery.value = ""
    }

    fun onSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun addExercise(
        exercise: Exercise,
        targetSets: Int,
        targetReps: Int,
        targetWeightKg: Double?,
        restSeconds: Int,
    ) {
        if (missing.value) {
            error.value = "This routine is no longer available."
            return
        }
        viewModelScope.launch {
            val id = ensureRoutineId() ?: return@launch
            val alreadyAdded = uiState.value.routine?.exercises?.any { it.exercise.id == exercise.id } == true
            if (alreadyAdded) {
                error.value = "${exercise.name} is already in this routine."
                showPicker.value = false
                return@launch
            }
            try {
                container.routineRepository.addExercise(
                    routineId = id,
                    exercise = exercise,
                    targetSets = targetSets,
                    targetReps = targetReps,
                    targetWeightKg = targetWeightKg,
                    restSeconds = restSeconds,
                )
                showPicker.value = false
                searchQuery.value = ""
                error.value = null
            } catch (_: Exception) {
                error.value = "Could not add that exercise. Try again."
            }
        }
    }

    fun createAndAddExercise(
        customName: String,
        muscleGroup: String,
        targetSets: Int,
        targetReps: Int,
        targetWeightKg: Double?,
        restSeconds: Int,
    ) {
        viewModelScope.launch {
            if (customName.isBlank()) {
                error.value = "Exercise name is required."
                return@launch
            }
            try {
                val created = container.exerciseRepository.createCustom(customName, muscleGroup)
                addExercise(created, targetSets, targetReps, targetWeightKg, restSeconds)
            } catch (_: Exception) {
                error.value = "Could not create that exercise. Try again."
            }
        }
    }

    fun updateExercise(
        itemId: String,
        targetSets: Int,
        targetReps: Int,
        targetWeightKg: Double?,
        restSeconds: Int,
    ) {
        viewModelScope.launch {
            val id = ensureRoutineId() ?: return@launch
            if (targetSets < 1 || targetReps < 1) {
                error.value = "Sets and reps must be at least 1."
                return@launch
            }
            try {
                container.routineRepository.updateExercise(
                    itemId = itemId,
                    routineId = id,
                    targetSets = targetSets,
                    targetReps = targetReps,
                    targetWeightKg = targetWeightKg,
                    restSeconds = restSeconds,
                )
                error.value = null
            } catch (_: Exception) {
                error.value = "Could not update those targets. Try again."
            }
        }
    }

    fun removeExercise(itemId: String) {
        viewModelScope.launch {
            val id = ensureRoutineId() ?: return@launch
            try {
                container.routineRepository.removeExercise(itemId, id)
                error.value = null
            } catch (_: Exception) {
                error.value = "Could not remove that exercise. Try again."
            }
        }
    }

    fun moveExercise(itemId: String, direction: Int) {
        viewModelScope.launch {
            val id = ensureRoutineId() ?: return@launch
            try {
                container.routineRepository.moveExercise(id, itemId, direction)
            } catch (_: Exception) {
                error.value = "Could not reorder that exercise. Try again."
            }
        }
    }

    private suspend fun ensureRoutineId(): String? {
        if (missing.value) {
            error.value = "This routine is no longer available."
            return null
        }
        val current = routineId.value
        if (current != null) {
            val row = container.routineRepository.getById(current)
            if (row != null) return current
            if (!createdThisSession) {
                missing.value = true
                error.value = "This routine is no longer available."
                routineId.value = null
                return null
            }
            routineId.value = null
        }
        return try {
            val created = container.routineRepository.create(
                name.value.trim().ifBlank { "Untitled routine" },
                notes.value,
            )
            createdThisSession = true
            routineId.value = created.id
            created.id
        } catch (_: Exception) {
            error.value = "Could not create this routine. Try again."
            null
        }
    }

    private suspend fun currentExerciseCount(id: String): Int {
        return container.routineRepository.getById(id)?.exercises?.size
            ?: uiState.value.routine?.exercises?.size
            ?: 0
    }

    private suspend fun discardEmptyStub() {
        val id = routineId.value ?: return
        val count = currentExerciseCount(id)
        if (!RoutineEditorPolicy.shouldDiscardStub(createdThisSession, count)) return
        try {
            container.routineRepository.delete(id)
        } catch (_: Exception) {
            // Keep navigating back; an empty stub can be deleted later.
        }
        routineId.value = null
    }

    private data class EditorCore(
        val routine: Routine?,
        val name: String,
        val notes: String,
        val query: String,
        val results: List<Exercise>,
    )

    private data class EditorFlags(
        val showPicker: Boolean,
        val error: String?,
        val saved: Boolean,
        val hydrated: Boolean,
        val missing: Boolean,
    )
}