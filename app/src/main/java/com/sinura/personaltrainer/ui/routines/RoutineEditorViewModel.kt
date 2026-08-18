package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.Routine
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
    private val incomingId: String? = savedStateHandle.get<String>("routineId")
        ?.takeIf { it.isNotBlank() && it != "new" }

    private val routineId = MutableStateFlow(incomingId)
    private val name = MutableStateFlow("")
    private val notes = MutableStateFlow("")
    private val searchQuery = MutableStateFlow("")
    private val showPicker = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val saved = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            if (routineId.value == null) {
                val created = container.routineRepository.create("Untitled routine")
                routineId.value = created.id
                name.value = created.name
            } else {
                val existing = container.routineRepository.getById(routineId.value.orEmpty())
                if (existing != null) {
                    name.value = existing.name
                    notes.value = existing.notes
                }
            }
        }
    }

    private val routineFlow = routineId.flatMapLatest { id ->
        if (id == null) flowOf(null) else container.routineRepository.observeById(id)
    }

    private val resultsFlow = searchQuery.flatMapLatest { query ->
        container.exerciseRepository.search(query)
    }

    val uiState: StateFlow<RoutineEditorUiState> = combine(
        combine(routineFlow, name, notes, searchQuery, resultsFlow) { routine, currentName, currentNotes, query, results ->
            EditorCore(routine, currentName, currentNotes, query, results)
        },
        combine(showPicker, error, saved) { picker, err, didSave ->
            Triple(picker, err, didSave)
        },
    ) { core, extras ->
        RoutineEditorUiState(
            isLoading = routineId.value == null,
            routine = core.routine,
            name = core.name,
            notes = core.notes,
            searchQuery = core.query,
            searchResults = core.results,
            showExercisePicker = extras.first,
            error = extras.second,
            saved = extras.third,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RoutineEditorUiState(),
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
        val id = routineId.value ?: return
        viewModelScope.launch {
            if (name.value.isBlank()) {
                error.value = "Give this routine a name."
                return@launch
            }
            container.routineRepository.updateDetails(id, name.value, notes.value)
            error.value = null
            saved.value = true
        }
    }

    fun setPickerVisible(visible: Boolean) {
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
        val id = routineId.value ?: return
        viewModelScope.launch {
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
            val created = container.exerciseRepository.createCustom(customName, muscleGroup)
            addExercise(created, targetSets, targetReps, targetWeightKg, restSeconds)
        }
    }

    fun updateExercise(
        itemId: String,
        targetSets: Int,
        targetReps: Int,
        targetWeightKg: Double?,
        restSeconds: Int,
    ) {
        val id = routineId.value ?: return
        viewModelScope.launch {
            container.routineRepository.updateExercise(
                itemId = itemId,
                routineId = id,
                targetSets = targetSets,
                targetReps = targetReps,
                targetWeightKg = targetWeightKg,
                restSeconds = restSeconds,
            )
        }
    }

    fun removeExercise(itemId: String) {
        val id = routineId.value ?: return
        viewModelScope.launch {
            container.routineRepository.removeExercise(itemId, id)
        }
    }

    fun moveExercise(itemId: String, direction: Int) {
        val id = routineId.value ?: return
        viewModelScope.launch {
            container.routineRepository.moveExercise(id, itemId, direction)
        }
    }

    private data class EditorCore(
        val routine: Routine?,
        val name: String,
        val notes: String,
        val query: String,
        val results: List<Exercise>,
    )
}
