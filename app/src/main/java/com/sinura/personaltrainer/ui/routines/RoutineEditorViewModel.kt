package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.EditorPhase
import com.sinura.personaltrainer.domain.RoutineEditorLoad
import com.sinura.personaltrainer.domain.RoutineEditorPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PT/RoutineEditorVM"

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

    private val routineId = MutableStateFlow(incomingId)
    private val load = MutableStateFlow(RoutineEditorLoad(opensExisting = incomingId != null))
    private val missing: Boolean get() = load.value.phase == EditorPhase.MISSING
    private val name = MutableStateFlow("")
    private val notes = MutableStateFlow("")
    private val searchQuery = MutableStateFlow("")
    private val showPicker = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val saved = MutableStateFlow(false)

    // Shared, not two independent collections: the missing-routine detector below and the
    // uiState chain each used to open their own Room query for the same row.
    // Hot and shared, for two reasons. The missing-routine detector below and the uiState chain
    // each used to open their own Room query for this same row; and actions read the routine
    // out of `uiState.value`, a WhileSubscribed(5_000) projection that stops updating five
    // seconds after the screen stops being collected — a stale snapshot of rendered state
    // rather than the data itself. The seeded null is safe: onRoutineEmission only treats null
    // as a deletion once the routine has been seen present.
    private val routineFlow: StateFlow<Routine?> = routineId.flatMapLatest { id ->
        if (id == null) flowOf(null) else container.routineRepository.observeById(id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val resultsFlow = searchQuery.flatMapLatest { query ->
        container.exerciseRepository.search(query)
    }

    init {
        viewModelScope.launch {
            // getById and the collector below both touch Room; an uncaught failure
            // here would kill the collector and leave the editor frozen with no clue why.
            runCatchingCancellable {
                val id = incomingId
                val existing = id?.let { container.routineRepository.getById(it) }
                if (existing != null) {
                    name.value = existing.name
                    notes.value = existing.notes
                }
                applyLoad { it.onInitialRead(found = existing != null) }
                routineFlow.collect { routine ->
                    applyLoad { it.onRoutineEmission(present = routine != null) }
                }
            }.onFailure { AppLog.e(TAG, "Loading the routine editor failed", it) }
        }
    }

    /**
     * The single writer of [load]. Reaching MISSING has two side effects that must happen
     * together — telling the user, and dropping the dead id so the flow stops re-querying it —
     * and they were previously duplicated at each of the three places that set the flag.
     */
    private fun applyLoad(transform: (RoutineEditorLoad) -> RoutineEditorLoad) {
        val before = load.value
        val after = transform(before)
        if (after == before) return
        load.value = after
        if (after.phase == EditorPhase.MISSING && before.phase != EditorPhase.MISSING) {
            error.value = "This routine is no longer available."
            routineId.value = null
        }
    }

    val uiState: StateFlow<RoutineEditorUiState> = combine(
        combine(routineFlow, name, notes, searchQuery, resultsFlow) { routine, currentName, currentNotes, query, results ->
            EditorCore(routine, currentName, currentNotes, query, results)
        },
        combine(showPicker, error, saved, load) { picker, err, didSave, loadState ->
            EditorFlags(picker, err, didSave, loadState.phase)
        },
    ) { core, extras ->
        RoutineEditorUiState(
            isLoading = extras.phase == EditorPhase.LOADING,
            missing = extras.phase == EditorPhase.MISSING,
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
        if (missing) {
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
            } catch (thrown: Exception) {
                AppLog.w(TAG, "saveDetails failed", thrown)
                error.value = "Could not save this routine. Try again."
                saved.value = false
            }
        }
    }

    /**
     * Set when this screen should be popped. Held as state for the same reason as forward
     * navigation: a callback captured into a coroutine is bound to a NavController that may
     * no longer exist by the time the database work finishes.
     *
     * Pops ack BEFORE navigating (forward navigations ack after) — a duplicate pop would eat
     * an extra screen, which is worse than the vanishingly narrow window it guards against.
     */
    private val _exitRequested = MutableStateFlow(false)
    val exitRequested: StateFlow<Boolean> = _exitRequested.asStateFlow()

    fun onExitHandled() {
        _exitRequested.value = false
    }

    fun leave() {
        viewModelScope.launch {
            discardEmptyStub()
            _exitRequested.value = true
        }
    }

    fun setPickerVisible(visible: Boolean) {
        if (missing) return
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
        if (missing) {
            error.value = "This routine is no longer available."
            return
        }
        viewModelScope.launch {
            val id = ensureRoutineId() ?: return@launch
            val alreadyAdded = routineFlow.value?.exercises?.any { it.exercise.id == exercise.id } == true
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
            } catch (thrown: Exception) {
                AppLog.w(TAG, "addExercise failed", thrown)
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
            } catch (thrown: Exception) {
                AppLog.w(TAG, "createAndAddExercise failed", thrown)
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
            } catch (thrown: Exception) {
                AppLog.w(TAG, "updateExercise failed", thrown)
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
            } catch (thrown: Exception) {
                AppLog.w(TAG, "removeExercise failed", thrown)
                error.value = "Could not remove that exercise. Try again."
            }
        }
    }

    fun moveExercise(itemId: String, direction: Int) {
        viewModelScope.launch {
            val id = ensureRoutineId() ?: return@launch
            try {
                container.routineRepository.moveExercise(id, itemId, direction)
            } catch (thrown: Exception) {
                AppLog.w(TAG, "moveExercise failed", thrown)
                error.value = "Could not reorder that exercise. Try again."
            }
        }
    }

    private suspend fun ensureRoutineId(): String? {
        if (missing) {
            error.value = "This routine is no longer available."
            return null
        }
        val current = routineId.value
        if (current != null) {
            val row = container.routineRepository.getById(current)
            if (row != null) return current
            if (!createdThisSession) {
                applyLoad { it.markMissing() }
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
        } catch (thrown: Exception) {
            AppLog.w(TAG, "ensureRoutineId failed", thrown)
            error.value = "Could not create this routine. Try again."
            null
        }
    }

    private suspend fun currentExerciseCount(id: String): Int {
        return container.routineRepository.getById(id)?.exercises?.size
            ?: routineFlow.value?.exercises?.size
            ?: 0
    }

    private suspend fun discardEmptyStub() {
        val id = routineId.value ?: return
        val count = currentExerciseCount(id)
        if (!RoutineEditorPolicy.shouldDiscardStub(createdThisSession, count)) return
        try {
            container.routineRepository.delete(id)
        } catch (thrown: Exception) {
            AppLog.w(TAG, "discardEmptyStub failed", thrown)
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
        val phase: EditorPhase,
    )
}