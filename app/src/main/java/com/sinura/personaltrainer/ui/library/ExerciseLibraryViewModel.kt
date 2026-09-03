package com.sinura.personaltrainer.ui.library

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.data.repository.DeleteExerciseResult
import com.sinura.personaltrainer.domain.AddDefaults
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.CatalogMeta
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseOrdering
import com.sinura.personaltrainer.domain.ExerciseUsage
import com.sinura.personaltrainer.domain.LibraryFamily
import com.sinura.personaltrainer.domain.LibraryFilter
import com.sinura.personaltrainer.domain.LibraryGrouping
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.SessionOrderCopy
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
    val muscleGroup: String = "",
    val notes: String = "",
)

data class ExerciseLibraryUiState(
    val isLoading: Boolean = true,
    val exercises: List<Exercise> = emptyList(),
    /** The flat list, used whenever a query or a filter is active. */
    val visibleExercises: List<Exercise> = emptyList(),
    /**
     * The grouped list, populated ONLY when the library is unfiltered and unsearched.
     *
     * Grouping a result set the user has already narrowed would hide the answer inside
     * collapsed headers: if you typed "incline" you want the three inclines, not three
     * one-member families to expand.
     */
    val families: List<LibraryFamily> = emptyList(),
    val expandedFamilies: Set<String> = emptySet(),
    val muscleFilters: List<CanonicalMuscle> = emptyList(),
    val selectedMuscle: CanonicalMuscle? = null,
    val equipmentFilters: List<EquipmentType> = emptyList(),
    val selectedEquipment: EquipmentType? = null,
    /** Customs sharing a name with a built-in, minus the ones already waved through. */
    val needsAttention: List<Exercise> = emptyList(),
    val query: String = "",
    val routines: List<Routine> = emptyList(),
    val editor: ExerciseEditorDraft? = null,
    val pendingDelete: Exercise? = null,
    val blockedDelete: Pair<Exercise, ExerciseUsage>? = null,
    val addToRoutine: Exercise? = null,
    val error: String? = null,
    val message: String? = null,
) {
    /** True when nothing is narrowing the list, which is the only time families are shown. */
    val grouped: Boolean
        get() = query.isBlank() && selectedMuscle == null && selectedEquipment == null
}

class ExerciseLibraryViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val query = MutableStateFlow("")
    private val selectedMuscle = MutableStateFlow<CanonicalMuscle?>(null)
    private val selectedEquipment = MutableStateFlow<EquipmentType?>(null)
    private val expandedFamilies = MutableStateFlow<Set<String>>(emptySet())
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
            selectedMuscle,
            selectedEquipment,
        ) { exercises, routines, currentQuery, muscle, equipment ->
            LibraryCore(exercises, routines, currentQuery, muscle, equipment)
        },
        combine(editor, pendingDelete, blockedDelete, addToRoutine) { draft, delete, blocked, add ->
            LibraryDialogs(draft, delete, blocked, add)
        },
        combine(
            container.exerciseRepository.observeNameCollisions(),
            container.preferencesRepository.dismissedCollisionIds,
            expandedFamilies,
        ) { collisions, dismissed, expanded ->
            LibraryAside(collisions.filterNot { it.id in dismissed }, expanded)
        },
        combine(error, message) { err, note -> err to note },
    ) { core, dialogs, aside, notices ->
        val needle = core.query.trim()
        // Muscle first, because it is the filter that reorders as well as narrows: a lift where
        // the muscle is a secondary credit belongs in the list, below the ones where it is the
        // point of the lift.
        var visible = LibraryFilter.apply(core.exercises, core.muscle)
        if (core.equipment != null) {
            visible = visible.filter { it.equipment == core.equipment }
        }
        if (needle.isNotEmpty()) {
            visible = visible.filter { exercise ->
                exercise.name.contains(needle, ignoreCase = true) ||
                    exercise.muscleGroup.contains(needle, ignoreCase = true) ||
                    exercise.notes.contains(needle, ignoreCase = true) ||
                    CatalogMeta.matchesSearchTerms(needle, exercise.id)
            }
        }
        // A muscle filter has already ranked its results by credit; re-sorting by catalog rank
        // would throw that away and put the leg extension back below the sumo deadlift.
        if (core.muscle == null) visible = ExerciseOrdering.catalogOrder(visible)

        val grouped = needle.isEmpty() && core.muscle == null && core.equipment == null
        ExerciseLibraryUiState(
            isLoading = false,
            exercises = core.exercises,
            visibleExercises = visible,
            families = if (grouped) LibraryGrouping.group(core.exercises) else emptyList(),
            expandedFamilies = aside.expandedFamilies,
            muscleFilters = LibraryFilter.musclesPresentIn(core.exercises),
            selectedMuscle = core.muscle,
            equipmentFilters = EquipmentType.entries.filter { type ->
                core.exercises.any { it.equipment == type }
            },
            selectedEquipment = core.equipment,
            needsAttention = aside.collisions,
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

    /** Tapping the selected chip clears it, so a filter is never a one-way door. */
    fun onMuscleSelected(muscle: CanonicalMuscle?) {
        selectedMuscle.value = if (selectedMuscle.value == muscle) null else muscle
    }

    fun onEquipmentSelected(equipment: EquipmentType?) {
        selectedEquipment.value = if (selectedEquipment.value == equipment) null else equipment
    }

    /**
     * Seeded once from the route, the first time this ViewModel exists.
     *
     * Library leaves composition when a lift is opened. Re-applying the Body muscle on
     * every recomposition would put back a filter the user had already cleared, which is
     * the opposite of "the filter survives the hop". A later arrival (Plan → Library with
     * no muscle, then Body → Library with one) is a new back-stack entry and a new
     * ViewModel, so it seeds cleanly.
     */
    private var routeMuscleSeeded = false

    fun seedMuscleFromRoute(muscle: CanonicalMuscle?) {
        if (routeMuscleSeeded) return
        routeMuscleSeeded = true
        if (muscle != null) selectedMuscle.value = muscle
    }

    fun toggleFamily(movementKey: String) {
        expandedFamilies.value = expandedFamilies.value.let { open ->
            if (movementKey in open) open - movementKey else open + movementKey
        }
    }

    /**
     * "Keep both" — the owner has looked at the collision and decided it is fine.
     *
     * Nothing is renamed, merged, or deleted: their history points at their row, and the only
     * thing that changes is that the app stops asking.
     */
    fun keepBothNames(exercise: Exercise) {
        viewModelScope.launch {
            container.preferencesRepository.dismissCollision(exercise.id)
        }
    }

    fun openCreate() {
        editor.value = ExerciseEditorDraft(muscleGroup = MuscleGroups.forNewDraft(selectedMuscle.value))
        error.value = null
    }

    fun dismissError() {
        error.value = null
    }

    fun openEdit(exercise: Exercise) {
        if (!exercise.isCustom) {
            error.value = "Built-in lifts can’t be edited."
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
            error.value = "Give this lift a name."
            return
        }
        if (MuscleGroups.resolved(draft.muscleGroup) == null) {
            error.value = MuscleGroups.MISSING_MESSAGE
            return
        }
        viewModelScope.launch {
            try {
                val result = if (draft.id == null) {
                    container.exerciseRepository.createCustom(name, draft.muscleGroup, draft.notes)
                } else {
                    container.exerciseRepository.updateCustom(
                        id = draft.id,
                        name = name,
                        muscleGroup = draft.muscleGroup,
                        notes = draft.notes,
                    )
                }
                when (result) {
                    is SaveExerciseResult.DuplicateName -> {
                        // The editor stays open on the name that was refused, so the fix is one
                        // edit away rather than a re-entry of the whole form.
                        error.value = DUPLICATE_NAME_MESSAGE
                        return@launch
                    }
                    is SaveExerciseResult.MissingMuscle -> {
                        error.value = MuscleGroups.MISSING_MESSAGE
                        return@launch
                    }
                    is SaveExerciseResult.Saved -> {
                        message.value = if (draft.id == null) "Created $name." else "Updated $name."
                        editor.value = null
                        error.value = null
                    }
                    null -> {
                        error.value = SessionOrderCopy.SAVE_LIFT_FAILED
                    }
                }
            } catch (thrown: Exception) {
                AppLog.w(TAG, "saveEditor failed", thrown)
                error.value = SessionOrderCopy.SAVE_LIFT_FAILED
            }
        }
    }

    fun requestDelete(exercise: Exercise) {
        if (!exercise.isCustom) {
            error.value = "Built-in lifts can’t be deleted."
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
                error.value = "Could not check where this lift is used."
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
                    error.value = "Built-in lifts can’t be deleted."
                }
                DeleteExerciseResult.Missing -> {
                    pendingDelete.value = null
                    error.value = "That lift is already gone."
                }
            }
        }
    }

    fun dismissDelete() {
        pendingDelete.value = null
        blockedDelete.value = null
    }

    /**
     * Clears the status note once its banner has dwelled. Leaving it set made
     * the StateFlow dedupe a repeat of the same action ("Added X to Y." twice),
     * so the second add showed no feedback at all.
     */
    fun dismissMessage() {
        message.value = null
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
                val defaults = AddDefaults.forExercise(exercise)
                container.routineRepository.addExercise(
                    routineId = routine.id,
                    exercise = exercise,
                    targetSets = defaults.sets,
                    targetReps = defaults.reps,
                    targetWeightKg = null,
                    restSeconds = defaults.restSeconds,
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
        val muscle: CanonicalMuscle?,
        val equipment: EquipmentType?,
    )

    private data class LibraryAside(
        val collisions: List<Exercise>,
        val expandedFamilies: Set<String>,
    )

    private data class LibraryDialogs(
        val editor: ExerciseEditorDraft?,
        val pendingDelete: Exercise?,
        val blockedDelete: Pair<Exercise, ExerciseUsage>?,
        val addToRoutine: Exercise?,
    )
}

/** One string, three screens: naming is refused the same way wherever it happens. */
internal const val DUPLICATE_NAME_MESSAGE = "That name is already in your library"
