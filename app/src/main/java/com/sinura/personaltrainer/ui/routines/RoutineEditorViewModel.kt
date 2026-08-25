package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.ui.library.DUPLICATE_NAME_MESSAGE
import com.sinura.personaltrainer.domain.AddDefaults
import com.sinura.personaltrainer.domain.EditorPhase
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseOrdering
import com.sinura.personaltrainer.domain.LibraryGrouping
import com.sinura.personaltrainer.domain.LiftCart
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineEditorLoad
import com.sinura.personaltrainer.domain.RoutineEditorPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
    /** The opening read of an existing routine threw; the screen offers a retry, not a spinner. */
    val failed: Boolean = false,
    val routine: Routine? = null,
    val name: String = "",
    val notes: String = "",
    val searchQuery: String = "",
    val searchResults: List<Exercise> = emptyList(),
    val showExercisePicker: Boolean = false,
    /** The whole catalog, so the editor can work out a lift's variants without another query. */
    val catalog: List<Exercise> = emptyList(),
    /** Non-null while a swap sheet is open, naming the routine row being replaced. */
    val swapItemId: String? = null,
    val pendingAddIds: List<String> = emptyList(),
    val error: String? = null,
) {
    /**
     * The other lifts in this one's family, minus what the routine already holds.
     *
     * Computed here rather than stored per row: the exclusion set changes every time the
     * routine changes, and a cached list would go stale exactly when it mattered — offering a
     * swap to a lift that had just been added two rows down.
     */
    fun swapCandidates(exerciseId: String): List<Exercise> {
        val exercise = catalog.firstOrNull { it.id == exerciseId } ?: return emptyList()
        return LibraryGrouping.siblings(
            exercise = exercise,
            catalog = catalog,
            exclude = routine?.exercises.orEmpty().map { it.exercise.id }.toSet(),
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineEditorViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
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
    private val extraCatalog = MutableStateFlow<List<Exercise>>(emptyList())
    private val swapItemId = MutableStateFlow<String?>(null)
    private val pendingAddIds = MutableStateFlow<List<String>>(emptyList())
    private var confirmInFlight = false
    private var confirmJob: Job? = null
    private var leaving = false

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

    private val resultsFlow = combine(
        searchQuery.flatMapLatest { query ->
            container.exerciseRepository.search(query)
        },
        container.exerciseRepository.observeLastLogged(),
        searchQuery,
    ) { results, lastLogged, query ->
        if (query.isBlank()) ExerciseOrdering.pickerOrder(results, lastLogged) else results
    }

    private var hydrateJob: Job? = null

    init {
        hydrate()
    }

    /**
     * Read the routine once, then keep watching it. Split out of `init` so [retryHydration] can
     * run it again: without that, a hydration failure had no exit but the system back button.
     */
    private fun hydrate() {
        hydrateJob?.cancel()
        hydrateJob = viewModelScope.launch {
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
            }.onFailure {
                // Before this, the read simply stopped and `hydrated` stayed false, so the editor
                // sat on ScreenLoading forever. FAILED turns that dead spinner into a retriable
                // error state — the new-routine path never reaches here, so it is untouched.
                AppLog.e(TAG, "Loading the routine editor failed", it)
                applyLoad { it.markFailed() }
            }
        }
    }

    /** Re-read after a failed hydration. No-op unless the editor is actually in FAILED. */
    fun retryHydration() {
        if (load.value.phase != EditorPhase.FAILED) return
        error.value = null
        applyLoad { it.onRetry() }
        hydrate()
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
        combine(showPicker, error, load) { picker, err, loadState ->
            EditorFlags(picker, err, loadState.phase)
        },
        combine(
            combine(container.exerciseRepository.observeAll(), extraCatalog) { catalog, extra ->
                catalog to extra
            },
            swapItemId,
            pendingAddIds,
        ) { sources, swapTarget, pending ->
            val (incoming, extra) = sources
            CatalogExtras(
                catalog = LiftCart.mergeSources(incoming, extra),
                extra = extra,
                swapItemId = swapTarget,
                pendingAddIds = pending,
            )
        },
    ) { core, extras, catalogExtras ->
        RoutineEditorUiState(
            isLoading = extras.phase == EditorPhase.LOADING,
            missing = extras.phase == EditorPhase.MISSING,
            failed = extras.phase == EditorPhase.FAILED,
            routine = core.routine,
            name = core.name,
            notes = core.notes,
            searchQuery = core.query,
            searchResults = LiftCart.visibleResults(
                results = core.results,
                extra = catalogExtras.extra,
                query = core.query,
            ),
            showExercisePicker = extras.showPicker,
            catalog = catalogExtras.catalog,
            swapItemId = catalogExtras.swapItemId,
            pendingAddIds = catalogExtras.pendingAddIds,
            error = extras.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RoutineEditorUiState(isLoading = incomingId != null),
    )

    fun requestSwap(itemId: String) {
        swapItemId.value = itemId
    }

    fun dismissSwap() {
        swapItemId.value = null
    }

    /**
     * Replaces the lift, keeping its position and its targets.
     *
     * The repository decides whether it is allowed and says why if not, so this cannot drift
     * from the rule the routine actually enforces.
     */
    fun swapExercise(replacement: Exercise) {
        val itemId = swapItemId.value ?: return
        val routineId = routineId.value ?: return
        swapItemId.value = null
        val dropped = stagedTargets.remove(itemId)
        viewModelScope.launch {
            runCatchingCancellable {
                val message = container.routineRepository.swapExercise(routineId, itemId, replacement)
                error.value = message
                if (message != null && dropped != null) stagedTargets[itemId] = dropped
            }.onFailure {
                AppLog.w(TAG, "swapExercise failed", it)
                error.value = "Could not swap that lift. Try again."
                if (dropped != null) stagedTargets[itemId] = dropped
            }
        }
    }

    fun onNameChange(value: String) {
        name.value = value
    }

    fun onNotesChange(value: String) {
        notes.value = value
    }

    /**
     * The targets typed into one lift's card but not yet written.
     *
     * A plain map, not a StateFlow: nothing renders from it. The card renders its own text
     * fields and the stored prescription above them, and this exists only so that the values
     * survive the trip from a field the finger has left to the write that follows. Every
     * mutation runs on the main thread from a Compose callback, so it needs no synchronisation.
     */
    private val stagedTargets = mutableMapOf<String, StagedTargets>()

    /**
     * Record what is currently in a card's four fields, without touching the database.
     *
     * Called on every keystroke. It has to be, because the commit points — a field losing
     * focus, and leaving the screen — both happen after the last keystroke has already been
     * forgotten by anything that is not holding it.
     */
    fun stageTargets(
        itemId: String,
        targetSets: Int?,
        targetReps: Int?,
        targetWeightKg: Double?,
        restSeconds: Int?,
    ) {
        stagedTargets[itemId] = StagedTargets(
            targetSets = targetSets,
            targetReps = targetReps,
            targetWeightKg = targetWeightKg,
            restSeconds = restSeconds,
        )
    }

    /** Write one card's staged targets if they differ from what is stored. */
    fun commitTargets(itemId: String) {
        viewModelScope.launch { commitTargetsNow(itemId) }
    }

    private suspend fun commitTargetsNow(itemId: String) {
        val staged = stagedTargets[itemId] ?: return
        val stored = routineFlow.value?.exercises?.firstOrNull { it.id == itemId } ?: return
        val pending = RoutineEditorPolicy.targetsToPersist(
            typedSets = staged.targetSets,
            typedReps = staged.targetReps,
            typedWeightKg = staged.targetWeightKg,
            typedRestSeconds = staged.restSeconds,
            storedSets = stored.targetSets,
            storedReps = stored.targetReps,
            storedWeightKg = stored.targetWeightKg,
            storedRestSeconds = stored.restSeconds,
        )
        if (pending == null) {
            // Identical to what is stored, so there is nothing to write and nothing to keep.
            stagedTargets.remove(itemId)
            return
        }
        val settled = writeTargets(
            itemId = itemId,
            targetSets = pending.targetSets,
            targetReps = pending.targetReps,
            targetWeightKg = pending.targetWeightKg,
            restSeconds = pending.restSeconds,
        )
        // Kept on a failed write, so that leaving the screen is one more chance to save it —
        // dropping it here is how a typed target disappears quietly, which is the whole reason
        // this path exists. Dropped when the write lands, and dropped when the value was
        // rejected: a rejected value that stayed staged would raise the same complaint on every
        // focus change and again on the way out, and the owner has to retype it either way.
        if (settled) stagedTargets.remove(itemId)
    }

    /**
     * Write every card that still has something staged.
     *
     * The exit path, not the cards, because a card that is being disposed cannot be trusted to
     * finish a database write: its coroutine would be racing the view model's own teardown. By
     * the time this runs the screen is still alive and [leave] is still suspended on it.
     */
    private suspend fun flushStagedTargets() {
        stagedTargets.keys.toList().forEach { commitTargetsNow(it) }
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
        if (leaving) return
        leaving = true
        viewModelScope.launch {
            confirmJob?.join()
            flushStagedTargets()
            discardEmptyStub()
            persistDetailsOnExit()
            _exitRequested.value = true
        }
    }

    /**
     * Save the name and notes the user typed but never pressed Save on.
     *
     * This screen writes every edit straight through to Room — adding an exercise, removing
     * one, reordering, changing targets. The name and the notes are the last two that cannot
     * be, because there is no moment during typing at which a half-typed name should be stored,
     * so they are written here instead.
     *
     * Autosave rather than a "discard changes?" dialog: a dialog would be the one thing on this
     * screen asking permission to keep work the user had already done, and the flagship flow is
     * already carrying more modals than it should.
     *
     * Deliberately looser than creation: a routine with no exercises is discarded by
     * [discardEmptyStub], which is what stops empty routines existing. That rule has no
     * business blocking a rename of one that already does.
     */
    private suspend fun persistDetailsOnExit() {
        val id = routineId.value ?: return
        val stored = container.routineRepository.getById(id) ?: return
        val pending = RoutineEditorPolicy.detailsToPersistOnExit(
            // LOADING means the seed from Room has not landed, so the typed fields are still
            // empty defaults rather than the user's text; MISSING means there is no row left.
            hydrated = load.value.phase == EditorPhase.EDITING,
            typedName = name.value,
            typedNotes = notes.value,
            storedName = stored.name,
            storedNotes = stored.notes,
        ) ?: return
        try {
            container.routineRepository.updateDetails(id, pending.name, pending.notes)
        } catch (thrown: Exception) {
            // Keep leaving. The edit is lost either way if the write fails, and trapping the
            // user on the screen to say so would turn one bad outcome into two.
            AppLog.w(TAG, "persistDetailsOnExit failed", thrown)
        }
    }

    fun setPickerVisible(visible: Boolean) {
        if (missing) return
        if (visible && (confirmInFlight || leaving)) return
        showPicker.value = visible
        if (!visible && !confirmInFlight) {
            searchQuery.value = ""
            pendingAddIds.value = emptyList()
        }
    }

    fun togglePendingAdd(exercise: Exercise) {
        if (confirmInFlight) return
        pendingAddIds.value = LiftCart.toggle(pendingAddIds.value, exercise.id)
    }

    fun confirmPendingAdd() {
        if (confirmInFlight || leaving) return
        val selected = LiftCart.sanitize(pendingAddIds.value)
        if (selected.isEmpty()) return
        val snapshot = uiState.value
        val plan = LiftCart.planConfirm(
            order = selected,
            sources = LiftCart.mergeSources(
                LiftCart.mergeSources(snapshot.catalog, extraCatalog.value),
                snapshot.searchResults,
            ),
            already = routineFlow.value?.exercises.orEmpty().map { it.exercise.id }.toSet(),
        )
        if (plan.blocked) {
            error.value = "Could not add that exercise. Try again."
            return
        }
        pendingAddIds.value = emptyList()
        if (plan.nothingNew) {
            showPicker.value = false
            searchQuery.value = ""
            error.value = null
            return
        }
        confirmInFlight = true
        showPicker.value = false
        searchQuery.value = ""
        confirmJob = viewModelScope.launch {
            try {
                val id = ensureRoutineId() ?: run {
                    pendingAddIds.value = selected
                    showPicker.value = true
                    return@launch
                }
                val remaining = plan.toAdd.toMutableList()
                for (exercise in plan.toAdd) {
                    val defaults = AddDefaults.forExercise(exercise)
                    try {
                        container.routineRepository.addExercise(
                            routineId = id,
                            exercise = exercise,
                            targetSets = defaults.sets,
                            targetReps = defaults.reps,
                            targetWeightKg = null,
                            restSeconds = defaults.restSeconds,
                        )
                        remaining.remove(exercise)
                    } catch (thrown: Exception) {
                        AppLog.w(TAG, "addExercise failed", thrown)
                        pendingAddIds.value = remaining.map { it.id }
                        showPicker.value = true
                        error.value = "Could not add that exercise. Try again."
                        return@launch
                    }
                }
                extraCatalog.value = extraCatalog.value.filter { extra ->
                    extra.id !in plan.toAdd.map { it.id }.toSet()
                }
                error.value = null
            } finally {
                confirmInFlight = false
            }
        }
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

    fun createAndSelect(name: String, muscleGroup: String) {
        viewModelScope.launch {
            if (name.isBlank()) {
                error.value = "Exercise name is required."
                return@launch
            }
            try {
                when (val result = container.exerciseRepository.createCustom(name, muscleGroup)) {
                    is SaveExerciseResult.DuplicateName -> error.value = DUPLICATE_NAME_MESSAGE
                    is SaveExerciseResult.MissingMuscle -> error.value = MuscleGroups.MISSING_MESSAGE
                    is SaveExerciseResult.Saved -> {
                        extraCatalog.value = LiftCart.mergeSources(
                            extraCatalog.value,
                            listOf(result.exercise),
                        )
                        if (showPicker.value && !confirmInFlight) {
                            togglePendingAdd(result.exercise)
                        }
                        error.value = null
                    }
                }
            } catch (thrown: Exception) {
                AppLog.w(TAG, "createAndSelect failed", thrown)
                error.value = "Could not create that exercise. Try again."
            }
        }
    }

    /**
     * @return true when this value is finished with — stored, or rejected and needing retyping.
     * False means the write itself failed and the value is worth one more attempt.
     */
    private suspend fun writeTargets(
        itemId: String,
        targetSets: Int,
        targetReps: Int,
        targetWeightKg: Double?,
        restSeconds: Int,
    ): Boolean {
        if (targetSets < 1 || targetReps < 1) {
            error.value = "Sets and reps must be at least 1."
            return true
        }
        val id = ensureRoutineId() ?: return false
        return try {
            container.routineRepository.updateExercise(
                itemId = itemId,
                routineId = id,
                targetSets = targetSets,
                targetReps = targetReps,
                targetWeightKg = targetWeightKg,
                restSeconds = restSeconds,
            )
            error.value = null
            true
        } catch (thrown: Exception) {
            AppLog.w(TAG, "writeTargets failed", thrown)
            error.value = "Could not update those targets. Try again."
            false
        }
    }

    fun removeExercise(itemId: String) {
        viewModelScope.launch {
            val id = ensureRoutineId() ?: return@launch
            try {
                container.routineRepository.removeExercise(itemId, id)
                stagedTargets.remove(itemId)
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

    /** Nulls are empty boxes; [RoutineEditorPolicy.targetsToPersist] decides what they mean. */
    private data class StagedTargets(
        val targetSets: Int?,
        val targetReps: Int?,
        val targetWeightKg: Double?,
        val restSeconds: Int?,
    )

    private data class EditorCore(
        val routine: Routine?,
        val name: String,
        val notes: String,
        val query: String,
        val results: List<Exercise>,
    )

    private data class CatalogExtras(
        val catalog: List<Exercise>,
        val extra: List<Exercise>,
        val swapItemId: String?,
        val pendingAddIds: List<String>,
    )

    private data class EditorFlags(
        val showPicker: Boolean,
        val error: String?,
        val phase: EditorPhase,
    )
}