package com.sinura.personaltrainer.ui.library

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.data.repository.DeleteExerciseResult
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseUsage
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.domain.MuscleNormalizer
import com.sinura.personaltrainer.domain.Routine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PT/LibraryVM"

data class ExerciseEditorDraft(
    val id: String? = null,
    val name: String = "",
    val muscleGroup: String = "Other",
    val notes: String = "",
)

data class ExerciseLibraryUiState(
    val isLoading: Boolean = true,
    val exercises: List<Exercise> = emptyList(),
    val visibleExercises: List<Exercise> = emptyList(),
    val muscleFilters: List<String> = emptyList(),
    val selectedGroup: String? = null,
    val query: String = "",
    val routines: List<Routine> = emptyList(),
    val editor: ExerciseEditorDraft? = null,
    val pendingDelete: Exercise? = null,
    val blockedDelete: Pair<Exercise, ExerciseUsage>? = null,
    val addToRoutine: Exercise? = null,
    val error: String? = null,
    val message: String? = null,
)

class ExerciseLibraryViewModel(application: Application) : AppViewModel(application) {
    private val query = MutableStateFlow("")
    private val selectedGroup = MutableStateFlow<String?>(null)
    private val editor = MutableStateFlow<ExerciseEditorDraft?>(null)
    private val pendingDelete = MutableStateFlow<Exercise?>(null)
    private val blockedDelete = MutableStateFlow<Pair<Exercise, ExerciseUsage>?>(null)
    private val addToRoutine = MutableStateFlow<Exercise?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ExerciseLibraryUiState> = combine(
        combine(
            container.exerciseRepository.observeAll(),
            container.routineRepository.observeAll(),
            query,
            selectedGroup,
        ) { exercises, routines, currentQuery, group ->
            LibraryCore(exercises, routines, currentQuery, group)
        },
        combine(editor, pendingDelete, blockedDelete, addToRoutine) { draft, delete, blocked, add ->
            LibraryDialogs(draft, delete, blocked, add)
        },
        combine(error, message) { err, note -> err to note },
    ) { core, dialogs, notices ->
        val needle = core.query.trim()
        val visible = core.exercises.filter { exercise ->
            val matchesGroup = MuscleNormalizer.matchesFilter(exercise.muscleGroup, core.group)
            val matchesQuery = needle.isEmpty() ||
                exercise.name.contains(needle, ignoreCase = true) ||
                exercise.muscleGroup.contains(needle, ignoreCase = true) ||
                exercise.notes.contains(needle, ignoreCase = true)
            matchesGroup && matchesQuery
        }
        ExerciseLibraryUiState(
            isLoading = false,
            exercises = core.exercises,
            visibleExercises = visible,
            muscleFilters = MuscleGroups.presentIn(core.exercises),
            selectedGroup = core.group,
            query = core.query,
            routines = core.routines,
            editor = dialogs.editor,
            pendingDelete = dialogs.pendingDelete,
            blockedDelete = dialogs.blockedDelete,
            addToRoutine = dialogs.addToRoutine,
            error = notices.first,
            message = notices.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExerciseLibraryUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onGroupSelected(group: String?) {
        selectedGroup.value = if (selectedGroup.value == group) null else group
    }

    fun applyMuscleFilter(group: String?) {
        selectedGroup.value = group?.trim()?.ifBlank { null }
    }

    fun openCreate() {
        editor.value = ExerciseEditorDraft()
        error.value = null
    }

    fun openEdit(exercise: Exercise) {
        if (!exercise.isCustom) {
            error.value = "Built-in exercises can’t be edited."
            return
        }
        editor.value = ExerciseEditorDraft(
            id = exercise.id,
            name = exercise.name,
            muscleGroup = exercise.muscleGroup,
            notes = exercise.notes,
        )
        error.value = null
    }

    fun updateEditor(draft: ExerciseEditorDraft) {
        editor.value = draft
    }

    fun dismissEditor() {
        editor.value = null
    }

    fun saveEditor() {
        val draft = editor.value ?: return
        val name = draft.name.trim()
        if (name.isEmpty()) {
            error.value = "Give this exercise a name."
            return
        }
        viewModelScope.launch {
            try {
                if (draft.id == null) {
                    container.exerciseRepository.createCustom(name, draft.muscleGroup, draft.notes)
                    message.value = "Created $name."
                } else {
                    container.exerciseRepository.updateCustom(draft.id, name, draft.muscleGroup, draft.notes)
                    message.value = "Updated $name."
                }
                editor.value = null
                error.value = null
            } catch (thrown: Exception) {
                AppLog.w(TAG, "saveEditor failed", thrown)
                error.value = "Could not save that exercise. Try again."
            }
        }
    }

    fun requestDelete(exercise: Exercise) {
        if (!exercise.isCustom) {
            error.value = "Built-in exercises can’t be deleted."
            return
        }
        viewModelScope.launch {
            try {
                val usage = container.exerciseRepository.usageFor(exercise.id)
                if (usage.isReferenced) {
                    blockedDelete.value = exercise to usage
                } else {
                    pendingDelete.value = exercise
                }
                error.value = null
            } catch (thrown: Exception) {
                AppLog.w(TAG, "requestDelete failed", thrown)
                error.value = "Could not check where this exercise is used."
            }
        }
    }

    fun confirmDelete() {
        val exercise = pendingDelete.value ?: return
        viewModelScope.launch {
            when (val result = container.exerciseRepository.deleteCustom(exercise.id)) {
                DeleteExerciseResult.Deleted -> {
                    message.value = "Deleted ${exercise.name}."
                    pendingDelete.value = null
                    error.value = null
                }
                is DeleteExerciseResult.InUse -> {
                    pendingDelete.value = null
                    blockedDelete.value = exercise to result.usage
                }
                DeleteExerciseResult.NotCustom -> {
                    pendingDelete.value = null
                    error.value = "Built-in exercises can’t be deleted."
                }
                DeleteExerciseResult.Missing -> {
                    pendingDelete.value = null
                    error.value = "That exercise is already gone."
                }
            }
        }
    }

    fun dismissDelete() {
        pendingDelete.value = null
        blockedDelete.value = null
    }

    fun openAddToRoutine(exercise: Exercise) {
        addToRoutine.value = exercise
        message.value = null
        error.value = null
    }

    fun dismissAddToRoutine() {
        addToRoutine.value = null
    }

    fun addToRoutine(routineId: String) {
        val exercise = addToRoutine.value ?: return
        viewModelScope.launch {
            val routine = container.routineRepository.getById(routineId)
            if (routine == null) {
                error.value = "That routine is no longer available."
                return@launch
            }
            if (routine.exercises.any { it.exercise.id == exercise.id }) {
                message.value = "${exercise.name} is already in ${routine.name}."
                addToRoutine.value = null
                return@launch
            }
            try {
                container.routineRepository.addExercise(
                    routineId = routine.id,
                    exercise = exercise,
                    targetSets = 3,
                    targetReps = 5,
                    targetWeightKg = null,
                    restSeconds = 90,
                )
                message.value = "Added ${exercise.name} to ${routine.name}."
                addToRoutine.value = null
                error.value = null
            } catch (thrown: Exception) {
                AppLog.w(TAG, "addToRoutine failed", thrown)
                error.value = "Could not add that lift to the routine."
            }
        }
    }

    private data class LibraryCore(
        val exercises: List<Exercise>,
        val routines: List<Routine>,
        val query: String,
        val group: String?,
    )

    private data class LibraryDialogs(
        val editor: ExerciseEditorDraft?,
        val pendingDelete: Exercise?,
        val blockedDelete: Pair<Exercise, ExerciseUsage>?,
        val addToRoutine: Exercise?,
    )
}
