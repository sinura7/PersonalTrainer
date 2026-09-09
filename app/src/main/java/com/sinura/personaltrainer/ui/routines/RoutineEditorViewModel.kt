package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.ErrorSlot
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
import com.sinura.personaltrainer.domain.PendingPick
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineEditorLoad
import com.sinura.personaltrainer.domain.RoutineEditorPolicy
import com.sinura.personaltrainer.domain.SessionOrderCopy
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "PT/RoutineEditorVM"

/** [ErrorSlot] families: a success may clear only its own family's refusal. */
private const val ERR_LOAD = "load"
private const val ERR_SWAP_LIFT = "swapLift"
private const val ERR_SAVE = "save"
private const val ERR_ADD_LIFT = "addLift"
private const val ERR_TARGETS = "targets"
private const val ERR_REMOVE_LIFT = "removeLift"
private const val ERR_REORDER = "reorder"
private const val ERR_ROUTINE = "routine"

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
    /**
     * What the picker draws as chosen, in session order: every lift the routine already
     * holds, plus any tap whose write has not landed. It is a view of the routine, not a
     * staging list in front of it — closing the sheet cannot lose a single one of them.
     */
    val pickedIds: List<String> = emptyList(),
    val error: String? = null,
    /** True while a tap from the picker is still being written. */
    val addingLifts: Boolean = false,
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
    private val savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val incomingId: String? = RoutineEditorPolicy.incomingId(
        savedStateHandle.get<String>("routineId"),
    )
    private var createdThisSession: Boolean =
        savedStateHandle.get<Boolean>(KEY_CREATED) ?: (incomingId == null)

    private val routineId = MutableStateFlow(
        savedStateHandle.get<String>(KEY_ID) ?: incomingId,
    )
    private val load = MutableStateFlow(RoutineEditorLoad(opensExisting = incomingId != null))
    private val missing: Boolean get() = load.value.phase == EditorPhase.MISSING
    private val name = MutableStateFlow(savedStateHandle.get<String>(KEY_NAME).orEmpty())
    private val notes = MutableStateFlow(savedStateHandle.get<String>(KEY_NOTES).orEmpty())
    private val searchQuery = MutableStateFlow("")
    private val showPicker = MutableStateFlow(false)
    private val error = ErrorSlot()
    private val extraCatalog = MutableStateFlow<List<Exercise>>(emptyList())
    private val swapItemId = MutableStateFlow<String?>(null)
    private val pendingPicks = MutableStateFlow<List<PendingPick>>(emptyList())

    /**
     * Picker writes run one at a time, in tap order. Two taps on the same row are an add
     * and a remove, and the remove cannot find a row the add has not finished writing.
     */
    private val pickWrites = Mutex()
    private val inFlight = ConcurrentHashMap.newKeySet<Job>()
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
        // The routine is the cart, so every emission of it settles the taps it now carries.
        // A pick is held for exactly as long as the write behind it is in flight.
        viewModelScope.launch {
            routineFlow.collect { routine -> settlePicks(committedIds(routine)) }
        }
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
                val id = incomingId ?: routineId.value
                val existing = id?.let { container.routineRepository.getById(it) }
                if (existing != null) {
                    if (!savedStateHandle.contains(KEY_NAME)) name.value = existing.name
                    if (!savedStateHandle.contains(KEY_NOTES)) notes.value = existing.notes
                    persistDraft()
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
        error.clearFrom(source = ERR_LOAD)
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
            error.fail(source = ERR_LOAD, message = "This routine is no longer available.")
            routineId.value = null
            persistDraft()
        }
    }

    val uiState: StateFlow<RoutineEditorUiState> = combine(
        combine(routineFlow, name, notes, searchQuery, resultsFlow) { routine, currentName, currentNotes, query, results ->
            EditorCore(routine, currentName, currentNotes, query, results)
        },
        combine(showPicker, error.messages, load) { picker, err, loadState ->
            EditorFlags(picker, err, loadState.phase)
        },
        combine(
            combine(container.exerciseRepository.observeAll(), extraCatalog) { catalog, extra ->
                catalog to extra
            },
            swapItemId,
            pendingPicks,
        ) { sources, swapTarget, pending ->
            val (incoming, extra) = sources
            CatalogExtras(
                catalog = LiftCart.mergeSources(incoming, extra),
                extra = extra,
                swapItemId = swapTarget,
                pending = pending,
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
            pickedIds = LiftCart.picked(
                committed = committedIds(core.routine),
                pending = catalogExtras.pending,
            ),
            error = extras.error,
            addingLifts = catalogExtras.pending.isNotEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RoutineEditorUiState(isLoading = incomingId != null),
    )

    fun requestSwap(itemId: String) {
        if (leaving) return
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
        val started = error.mark()
        if (leaving) return
        val itemId = swapItemId.value ?: return
        val routineId = routineId.value ?: return
        swapItemId.value = null
        val dropped = stagedTargets.remove(itemId)
        launchWrite {
            runCatchingCancellable {
                val message = container.routineRepository.swapExercise(routineId, itemId, replacement)
                if (message != null) {
                    error.fail(source = ERR_SWAP_LIFT, message = message)
                } else {
                    error.clearFrom(source = ERR_SWAP_LIFT, before = started)
                }
                if (message != null && dropped != null) stagedTargets[itemId] = dropped
            }.onFailure {
                AppLog.w(TAG, "swapExercise failed", it)
                error.fail(source = ERR_SWAP_LIFT, message = "Could not swap that lift. Try again.")
                if (dropped != null) stagedTargets[itemId] = dropped
            }
        }
    }

    fun onNameChange(value: String) {
        name.value = value
        persistDraft()
    }

    fun onNotesChange(value: String) {
        notes.value = value
        persistDraft()
    }

    fun dismissError() {
        error.dismiss()
    }

    /**
     * The targets typed into one lift's card but not yet written.
     *
     * Not a StateFlow: nothing renders from it. The card renders its own text fields and the
     * stored prescription above them, and this exists only so that the values survive the trip
     * from a field the finger has left to the write that follows.
     *
     * Concurrent, because the tails of the write coroutines are not all on the main thread. A
     * continuation returning from Room resumes on whatever thread the dispatcher hands it —
     * under an unconfined dispatcher that is Room's own query thread — while a Compose callback
     * is free to stage the next keystroke at the same moment. A plain LinkedHashMap mutated
     * from both is a data race, and the lost update it hides is the one [clearStaged] names.
     */
    private val stagedTargets = ConcurrentHashMap<String, StagedTargets>()

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
        if (leaving) return
        launchWrite { commitTargetsNow(itemId) }
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
            clearStaged(itemId, staged)
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
        if (settled) clearStaged(itemId, staged)
    }

    /**
     * Drop the staged value this commit was working from, and only that one.
     *
     * A Room write is slow enough for the next value to be typed into the same card before
     * it lands, and removing by key alone threw that newer value away: the write that had
     * already gone out was the older one, the card kept showing a number the routine did not
     * hold, and the exit flush had nothing left to save. Whatever is staged now is either
     * this commit's own value — finished with — or one that has not been written yet.
     *
     * Compare-and-remove in one atomic step, because the staging and this removal can be on
     * two threads: reading, comparing and then removing would let the newer value slip into
     * the gap between the read and the remove, which is the very drop this exists to stop.
     */
    private fun clearStaged(itemId: String, committed: StagedTargets) {
        stagedTargets.remove(itemId, committed)
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
        // The sheet goes with the screen. Its lifts are already written, so there is
        // nothing to keep it open for, and a picker left standing would outlive the pop.
        showPicker.value = false
        viewModelScope.launch {
            joinWrites()
            flushStagedTargets()
            discardEmptyStub()
            persistDetailsOnExit()
            _exitRequested.value = true
        }
    }

    /**
     * Keep the routine and leave. Name, notes, and staged targets land first.
     * An empty stub still cannot be saved — Add lifts is the empty Volt.
     */
    fun saveAndLeave() {
        if (leaving) return
        leaving = true
        showPicker.value = false
        viewModelScope.launch {
            joinWrites()
            flushStagedTargets()
            val id = routineId.value
            val count = if (id == null) 0 else currentExerciseCount(id)
            if (count <= 0) {
                error.fail(source = ERR_SAVE, message = SessionOrderCopy.NEED_A_LIFT)
                leaving = false
                return@launch
            }
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
        } catch (thrown: CancellationException) {
            throw thrown
        } catch (thrown: Exception) {
            // Keep leaving. The edit is lost either way if the write fails, and trapping the
            // user on the screen to say so would turn one bad outcome into two.
            AppLog.w(TAG, "persistDetailsOnExit failed", thrown)
        }
    }

    fun setPickerVisible(visible: Boolean) {
        if (missing) return
        if (visible && leaving) return
        showPicker.value = visible
        // The typed query is the only thing closing the sheet throws away. The lifts are
        // already on the routine, which is the whole point: a tap outside the sheet used to
        // empty a cart the owner had just built by hand, and the list started again.
        if (!visible) searchQuery.value = ""
    }

    /**
     * A tap in the picker, written straight through to the routine.
     *
     * The sheet used to stage taps behind a Confirm, so the scrim, the back gesture and a
     * mis-swipe each threw away work the user had already done. There is nothing left to
     * throw away: the first tap adds the lift, a second tap on the same row takes it back
     * out, and the numbers the rows carry are the session's own order.
     */
    fun togglePicked(exercise: Exercise) {
        if (leaving) return
        commitPick(
            exercise = exercise,
            adding = LiftCart.addsOnTap(
                committed = committedIds(routineFlow.value),
                pending = pendingPicks.value,
                id = exercise.id,
            ),
        )
    }

    /**
     * Hold the tap's intent, then write it. The intent is held first so the row answers the
     * finger on the same frame — Room's flow is a database round trip behind it.
     */
    private fun commitPick(exercise: Exercise, adding: Boolean) {
        if (leaving) return
        pendingPicks.value = LiftCart.record(pendingPicks.value, exercise.id, adding)
        launchWrite { pickWrites.withLock { writePick(exercise, adding) } }
    }

    private suspend fun writePick(exercise: Exercise, adding: Boolean) {
        val started = error.mark()
        val source = if (adding) ERR_ADD_LIFT else ERR_REMOVE_LIFT
        val id = ensureRoutineId() ?: run {
            pendingPicks.value = LiftCart.forget(pendingPicks.value, exercise.id)
            return
        }
        try {
            // Read the routine rather than trust the flow's last emission. This is the dedup
            // that stops a double tap writing the same lift twice, and under this lock it has
            // to see the write that finished a moment ago, not the state before it.
            val row = storedRow(id, exercise.id)
            if (adding && row == null) {
                val defaults = AddDefaults.forExercise(exercise)
                container.routineRepository.addExercise(
                    routineId = id,
                    exercise = exercise,
                    targetSets = defaults.sets,
                    targetReps = defaults.reps,
                    targetWeightKg = null,
                    restSeconds = defaults.restSeconds,
                )
            } else if (!adding && row != null) {
                container.routineRepository.removeExercise(row.id, id)
                stagedTargets.remove(row.id)
            }
            // Settle here as well as on the routine's own emissions: a tap the store already
            // agreed with writes nothing, so there may be no emission to settle it, and a
            // pick left standing would keep the editor looking busy forever.
            settlePicks(storedIds(id))
            error.clearFrom(source = source, before = started)
            error.clearFrom(source = ERR_SAVE, before = started)
        } catch (thrown: CancellationException) {
            throw thrown
        } catch (thrown: Exception) {
            AppLog.w(TAG, "writePick failed", thrown)
            pendingPicks.value = LiftCart.forget(pendingPicks.value, exercise.id)
            error.fail(
                source = source,
                message = if (adding) {
                    SessionOrderCopy.ADD_LIFT_FAILED
                } else {
                    SessionOrderCopy.REMOVE_LIFT_FAILED
                },
            )
        }
    }

    private suspend fun storedRow(routineId: String, exerciseId: String) =
        container.routineRepository.getById(routineId)
            ?.exercises
            ?.firstOrNull { it.exercise.id == exerciseId }

    private suspend fun storedIds(routineId: String): List<String> =
        committedIds(container.routineRepository.getById(routineId))

    private fun committedIds(routine: Routine?): List<String> =
        routine?.exercises.orEmpty().map { it.exercise.id }

    private fun settlePicks(committed: List<String>) {
        pendingPicks.value = LiftCart.settle(committed, pendingPicks.value)
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
        val started = error.mark()
        if (missing) {
            error.fail(source = ERR_ADD_LIFT, message = "This routine is no longer available.")
            return
        }
        if (leaving) return
        launchWrite {
            val id = ensureRoutineId() ?: return@launchWrite
            val alreadyAdded = routineFlow.value?.exercises?.any { it.exercise.id == exercise.id } == true
            if (alreadyAdded) {
                error.fail(
                    source = ERR_ADD_LIFT,
                    message = "${exercise.name} is already in this routine.",
                )
                if (!leaving) showPicker.value = false
                return@launchWrite
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
                if (!leaving) {
                    showPicker.value = false
                    searchQuery.value = ""
                }
                error.clearFrom(source = ERR_ADD_LIFT, before = started)
                error.clearFrom(source = ERR_SAVE, before = started)
            } catch (thrown: CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "addExercise failed", thrown)
                error.fail(source = ERR_ADD_LIFT, message = SessionOrderCopy.ADD_LIFT_FAILED)
            }
        }
    }

    fun createAndSelect(name: String, muscleGroup: String) {
        val started = error.mark()
        if (leaving) return
        if (name.isBlank()) {
            error.fail(source = ERR_ADD_LIFT, message = SessionOrderCopy.LIFT_NAME_REQUIRED)
            return
        }
        launchWrite {
            try {
                when (val result = container.exerciseRepository.createCustom(name, muscleGroup)) {
                    is SaveExerciseResult.DuplicateName ->
                        error.fail(source = ERR_ADD_LIFT, message = DUPLICATE_NAME_MESSAGE)
                    is SaveExerciseResult.MissingMuscle ->
                        error.fail(source = ERR_ADD_LIFT, message = MuscleGroups.MISSING_MESSAGE)
                    is SaveExerciseResult.Saved -> {
                        extraCatalog.value = LiftCart.mergeSources(
                            extraCatalog.value,
                            listOf(result.exercise),
                        )
                        if (showPicker.value) {
                            commitPick(exercise = result.exercise, adding = true)
                        }
                        error.clearFrom(source = ERR_ADD_LIFT, before = started)
                        error.clearFrom(source = ERR_SAVE, before = started)
                    }
                }
            } catch (thrown: CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "createAndSelect failed", thrown)
                error.fail(source = ERR_ADD_LIFT, message = SessionOrderCopy.CREATE_LIFT_FAILED)
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
        val started = error.mark()
        if (targetSets < 1 || targetReps < 1) {
            error.fail(source = ERR_TARGETS, message = "Sets and reps must be at least 1.")
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
            error.clearFrom(source = ERR_TARGETS, before = started)
            true
        } catch (thrown: Exception) {
            AppLog.w(TAG, "writeTargets failed", thrown)
            error.fail(source = ERR_TARGETS, message = "Could not update those targets. Try again.")
            false
        }
    }

    fun removeExercise(itemId: String) {
        val started = error.mark()
        if (leaving) return
        launchWrite {
            val id = ensureRoutineId() ?: return@launchWrite
            try {
                container.routineRepository.removeExercise(itemId, id)
                stagedTargets.remove(itemId)
                error.clearFrom(source = ERR_REMOVE_LIFT, before = started)
            } catch (thrown: CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "removeExercise failed", thrown)
                error.fail(source = ERR_REMOVE_LIFT, message = SessionOrderCopy.REMOVE_LIFT_FAILED)
            }
        }
    }

    fun moveExercise(itemId: String, direction: Int) {
        if (leaving) return
        launchWrite {
            val id = ensureRoutineId() ?: return@launchWrite
            try {
                container.routineRepository.moveExercise(id, itemId, direction)
            } catch (thrown: CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "moveExercise failed", thrown)
                error.fail(source = ERR_REORDER, message = SessionOrderCopy.REORDER_LIFT_FAILED)
            }
        }
    }

    /**
     * Register the job before it runs so [leave] cannot join an empty set while a write
     * is already scheduled on the dispatcher.
     */
    private fun launchWrite(block: suspend () -> Unit): Job {
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) { block() }
        inFlight.add(job)
        job.invokeOnCompletion { inFlight.remove(job) }
        job.start()
        return job
    }

    private suspend fun joinWrites() {
        while (true) {
            val snapshot = inFlight.toList()
            if (snapshot.isEmpty()) return
            snapshot.forEach { it.join() }
        }
    }

    private suspend fun ensureRoutineId(): String? {
        if (missing) {
            error.fail(source = ERR_ROUTINE, message = "This routine is no longer available.")
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
            persistDraft()
            created.id
        } catch (thrown: CancellationException) {
            throw thrown
        } catch (thrown: Exception) {
            AppLog.w(TAG, "ensureRoutineId failed", thrown)
            error.fail(source = ERR_ROUTINE, message = "Could not create this routine. Try again.")
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
        } catch (thrown: CancellationException) {
            throw thrown
        } catch (thrown: Exception) {
            AppLog.w(TAG, "discardEmptyStub failed", thrown)
            // Keep navigating back; an empty stub can be deleted later.
        }
        routineId.value = null
        persistDraft()
    }

    private fun persistDraft() {
        savedStateHandle[KEY_ID] = routineId.value
        savedStateHandle[KEY_NAME] = name.value
        savedStateHandle[KEY_NOTES] = notes.value
        savedStateHandle[KEY_CREATED] = createdThisSession
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
        val pending: List<PendingPick>,
    )

    private data class EditorFlags(
        val showPicker: Boolean,
        val error: String?,
        val phase: EditorPhase,
    )

    private companion object {
        const val KEY_ID = "editor.routineId"
        const val KEY_NAME = "editor.name"
        const val KEY_NOTES = "editor.notes"
        const val KEY_CREATED = "editor.createdThisSession"
    }
}