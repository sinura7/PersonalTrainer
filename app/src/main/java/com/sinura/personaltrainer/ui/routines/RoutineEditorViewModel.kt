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
import com.sinura.personaltrainer.domain.RoutineExitOutcome
import com.sinura.personaltrainer.domain.RoutineSaveCopy
import com.sinura.personaltrainer.domain.RoutineTargetsOutcome
import com.sinura.personaltrainer.domain.RoutineWriteOutcome
import com.sinura.personaltrainer.domain.SessionOrderCopy
import java.util.concurrent.ConcurrentHashMap
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
    /** True while Confirm is writing lifts. Add lifts is disabled so the picker cannot reopen. */
    val addingLifts: Boolean = false,
    /**
     * True while Back or Save is landing the name, notes and staged targets. The dock reads
     * "Saving…" and is disabled, and a second Back or Save is ignored until this clears.
     */
    val saving: Boolean = false,
    /**
     * Why the last Save stayed on the screen, rendered beside the dock where Save was pressed.
     * Cleared when the next attempt starts. Null while nothing is owed.
     */
    val saveError: String? = null,
    /**
     * Back found required writes that did not land. The screen asks: try again, or leave
     * without saving these. Null until Back fails; cleared when the next attempt starts.
     */
    val unsavedOnBack: RoutineExitOutcome.Unsaved? = null,
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
    private val error = MutableStateFlow<String?>(null)
    private val extraCatalog = MutableStateFlow<List<Exercise>>(emptyList())
    private val swapItemId = MutableStateFlow<String?>(null)
    private val pendingAddIds = MutableStateFlow<List<String>>(emptyList())
    private val confirmInFlight = MutableStateFlow(false)
    private val exitState = MutableStateFlow(ExitState())
    private val inFlight = ConcurrentHashMap.newKeySet<Job>()

    /**
     * True from the moment Back or Save is pressed until the attempt is decided. Every edit
     * method checks it, so it must be reset whenever the attempt ends without popping the
     * screen — otherwise a failed Save would leave the editor alive but deaf.
     */
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
            persistDraft()
        }
    }

    val uiState: StateFlow<RoutineEditorUiState> = combine(
        combine(routineFlow, name, notes, searchQuery, resultsFlow) { routine, currentName, currentNotes, query, results ->
            EditorCore(routine, currentName, currentNotes, query, results)
        },
        combine(showPicker, error, load, confirmInFlight, exitState) { picker, err, loadState, adding, exit ->
            EditorFlags(picker, err, loadState.phase, adding, exit)
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
            addingLifts = extras.addingLifts,
            saving = extras.exit.saving,
            saveError = extras.exit.saveError,
            unsavedOnBack = extras.exit.unsavedOnBack,
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
        if (leaving) return
        val itemId = swapItemId.value ?: return
        val routineId = routineId.value ?: return
        swapItemId.value = null
        val dropped = stagedTargets.remove(itemId)
        launchWrite {
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
        persistDraft()
    }

    fun onNotesChange(value: String) {
        notes.value = value
        persistDraft()
    }

    fun dismissError() {
        error.value = null
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
        /**
         * Non-null when a box holds text that cannot be stored as written — "8.5" reps, "-50"
         * — carrying the rule it broke. The card has already shown it under the box; staging
         * it here is what lets the commit refuse and Save and Back count it as unsaved, rather
         * than reading the unparseable box as "leave this one alone" (UX06).
         */
        invalidReason: String? = null,
    ) {
        stagedTargets[itemId] = StagedTargets(
            targetSets = targetSets,
            targetReps = targetReps,
            targetWeightKg = targetWeightKg,
            restSeconds = restSeconds,
            invalidReason = invalidReason,
        )
    }

    /**
     * Write one card's staged targets if they differ from what is stored.
     *
     * The focus-change commit. What it says goes in the screen's error slot, the same place
     * every other write-through edit complains; the exit flush reports through the dock
     * instead, which is why the surfacing lives here and not in [commitTargetsNow].
     */
    fun commitTargets(itemId: String) {
        if (leaving) return
        launchWrite {
            val result = commitTargetsNow(itemId) ?: return@launchWrite
            when (val outcome = result.outcome) {
                RoutineWriteOutcome.Stored -> error.value = null
                RoutineWriteOutcome.NothingToWrite -> Unit
                is RoutineWriteOutcome.Rejected -> error.value = outcome.reason
                RoutineWriteOutcome.Failed -> error.value = RoutineSaveCopy.targetsFailed(result.liftName)
            }
        }
    }

    /**
     * Land one card's staged value, or report why it did not.
     *
     * Null when there is nothing to report at all: nothing staged, or the row the value was
     * typed for is no longer in the routine, in which case the value is dropped because there
     * is nothing left to write it to.
     */
    private suspend fun commitTargetsNow(itemId: String): RoutineTargetsOutcome? {
        val staged = stagedTargets[itemId] ?: return null
        val stored = routineFlow.value?.exercises?.firstOrNull { it.id == itemId }
        if (stored == null) {
            stagedTargets.remove(itemId)
            return null
        }
        // A box that could not be read as written is refused before any value is compared:
        // nothing is written, the value stays staged so Save and Back see it, and the card's
        // own rule is the reason.
        staged.invalidReason?.let { reason ->
            return RoutineTargetsOutcome(stored.exercise.name, RoutineWriteOutcome.Rejected(reason))
        }
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
            return RoutineTargetsOutcome(stored.exercise.name, RoutineWriteOutcome.NothingToWrite)
        }
        val outcome = writeTargets(
            itemId = itemId,
            targetSets = pending.targetSets,
            targetReps = pending.targetReps,
            targetWeightKg = pending.targetWeightKg,
            restSeconds = pending.restSeconds,
        )
        // Dropped only once it is stored. A failed write is kept so that Save and Back are one
        // more chance to land it — dropping it here is how a typed target disappears quietly,
        // which is the whole reason this path exists. A rejected value is kept too: it used to
        // be dropped so the same complaint would not repeat on every focus change, but a card
        // still reading "0 sets" while Room keeps 3 has to stop Save and Back from walking past
        // it, and the only way the exit can know is if the value is still here to be refused.
        // Repeating the complaint is the honest price; the error flow conflates equal values.
        if (outcome is RoutineWriteOutcome.Stored) stagedTargets.remove(itemId)
        return RoutineTargetsOutcome(stored.exercise.name, outcome)
    }

    /**
     * Write every card that still has something staged, and say what became of each.
     *
     * The exit path, not the cards, because a card that is being disposed cannot be trusted to
     * finish a database write: its coroutine would be racing the view model's own teardown. By
     * the time this runs the screen is still alive and [leave] is still suspended on it.
     *
     * The outcomes are what the exit decides on — a failure here used to be logged and walked
     * past. The screen's error slot is left alone: the aggregate says it once, at the dock.
     */
    private suspend fun flushStagedTargets(): List<RoutineTargetsOutcome> =
        stagedTargets.keys.toList().mapNotNull { commitTargetsNow(it) }

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

    /**
     * Back. Lands what is owed, then pops — or stays and asks.
     *
     * The staged targets and the name and notes are flushed first, and the screen pops only if
     * every one of them landed ([RoutineEditorPolicy.exitOutcome]). When something did not,
     * the editor stays with the draft intact — the name and notes are still in state and in
     * the saved-state handle, the failed targets are still staged — and [RoutineEditorUiState.unsavedOnBack]
     * carries what to ask: try again, or [leaveAnyway].
     *
     * It used to pop regardless and log the failure. Autosave was the right call over a
     * "discard changes?" dialog for the case where the write works; a dialog for the case where
     * it did not is a different thing, because the alternative is losing the owner's work
     * without a word.
     */
    fun leave() {
        if (leaving) return
        beginExit()
        viewModelScope.launch {
            // A read that throws on the way out (the row behind the stub check or the details
            // compare) used to kill this coroutine with `leaving` stuck true: the editor stayed
            // on screen deaf to Back, Save and every edit. It is now one more unsaved outcome.
            val outcome = runCatchingCancellable {
                joinWrites()
                val targets = flushStagedTargets()
                discardEmptyStub()
                val details = persistDetailsOnExit()
                RoutineEditorPolicy.exitOutcome(details, targets)
            }.getOrElse { thrown ->
                AppLog.w(TAG, "Leaving the routine editor failed before it could decide", thrown)
                RoutineExitOutcome.Unsaved(
                    message = RoutineSaveCopy.EXIT_READ_FAILED,
                    items = listOf(RoutineSaveCopy.UNKNOWN_ITEMS),
                )
            }
            when (outcome) {
                RoutineExitOutcome.Landed -> _exitRequested.value = true
                is RoutineExitOutcome.Unsaved -> stayOnScreen(unsavedOnBack = outcome)
            }
        }
    }

    /**
     * Keep the routine and leave. Name, notes, and staged targets land first, and the screen
     * pops only if all of them did; otherwise it stays, says why beside the dock, and Save
     * is the retry. An empty stub still cannot be saved — Add lifts is the empty Volt.
     */
    fun saveAndLeave() {
        if (leaving) return
        beginExit()
        viewModelScope.launch {
            val outcome = runCatchingCancellable {
                joinWrites()
                val targets = flushStagedTargets()
                val id = routineId.value
                val count = if (id == null) 0 else currentExerciseCount(id)
                if (count <= 0) {
                    error.value = SessionOrderCopy.NEED_A_LIFT
                    stayOnScreen()
                    return@launch
                }
                val details = persistDetailsOnExit()
                RoutineEditorPolicy.exitOutcome(details, targets)
            }.getOrElse { thrown ->
                // Same shape as leave(): a read fault is reported at the dock, not left to
                // strand the screen with Save disabled forever.
                AppLog.w(TAG, "Saving the routine failed before it could decide", thrown)
                RoutineExitOutcome.Unsaved(
                    message = RoutineSaveCopy.EXIT_READ_FAILED,
                    items = listOf(RoutineSaveCopy.UNKNOWN_ITEMS),
                )
            }
            when (outcome) {
                RoutineExitOutcome.Landed -> _exitRequested.value = true
                is RoutineExitOutcome.Unsaved -> {
                    // Said once. A focus-change commit may already have put the same rejection in
                    // the screen's error slot; the dock is where Save was pressed, so it wins.
                    if (error.value == outcome.message) error.value = null
                    stayOnScreen(saveError = outcome.message)
                }
            }
        }
    }

    /**
     * The Back prompt's "leave without saving these".
     *
     * Pops without re-attempting the writes the last Back could not land: the staged targets
     * are dropped and the typed name and notes go with the screen. Everything that wrote
     * through — lifts, order, removals — is already in Room and is untouched. An empty stub
     * created this session is still discarded, exactly as Back would; a routine with lifts is
     * never deleted here.
     *
     * Safe to call without a prompt showing: it is Back minus the flush, nothing more.
     */
    fun leaveAnyway() {
        if (leaving) return
        beginExit()
        viewModelScope.launch {
            joinWrites()
            stagedTargets.clear()
            // The one exit that must always exit. A stub that could not be checked stays; an
            // empty routine left behind is a nuisance, an editor that cannot be left is not.
            runCatchingCancellable { discardEmptyStub() }
                .onFailure { AppLog.w(TAG, "Leaving anyway could not check for an empty stub", it) }
            _exitRequested.value = true
        }
    }

    private fun beginExit() {
        leaving = true
        exitState.value = ExitState(saving = true)
    }

    /** The attempt is over and the screen is not popping: re-arm every edit method. */
    private fun stayOnScreen(
        saveError: String? = null,
        unsavedOnBack: RoutineExitOutcome.Unsaved? = null,
    ) {
        leaving = false
        exitState.value = ExitState(saving = false, saveError = saveError, unsavedOnBack = unsavedOnBack)
    }

    /**
     * Save the name and notes the user typed but never pressed Save on, and say whether it
     * worked.
     *
     * This screen writes every edit straight through to Room — adding an exercise, removing
     * one, reordering, changing targets. The name and the notes are the last two that cannot
     * be, because there is no moment during typing at which a half-typed name should be stored,
     * so they are written here instead.
     *
     * A failure is reported, not swallowed: the caller decides whether the screen may pop, and
     * the draft stays in state and in the saved-state handle so the next attempt has something
     * to write. Cancellation is never a failure — [runCatchingCancellable] rethrows it.
     *
     * Deliberately looser than creation: a routine with no exercises is discarded by
     * [discardEmptyStub], which is what stops empty routines existing. That rule has no
     * business blocking a rename of one that already does.
     */
    private suspend fun persistDetailsOnExit(): RoutineWriteOutcome {
        val id = routineId.value ?: return RoutineWriteOutcome.NothingToWrite
        return runCatchingCancellable<RoutineWriteOutcome> {
            val stored = container.routineRepository.getById(id)
                ?: return RoutineWriteOutcome.NothingToWrite
            val pending = RoutineEditorPolicy.detailsToPersistOnExit(
                // LOADING means the seed from Room has not landed, so the typed fields are still
                // empty defaults rather than the user's text; MISSING means there is no row left.
                hydrated = load.value.phase == EditorPhase.EDITING,
                typedName = name.value,
                typedNotes = notes.value,
                storedName = stored.name,
                storedNotes = stored.notes,
            ) ?: return RoutineWriteOutcome.NothingToWrite
            container.routineRepository.updateDetails(id, pending.name, pending.notes)
            RoutineWriteOutcome.Stored
        }.getOrElse { thrown ->
            AppLog.w(TAG, "persistDetailsOnExit failed", thrown)
            RoutineWriteOutcome.Failed
        }
    }

    fun setPickerVisible(visible: Boolean) {
        if (missing) return
        if (visible && (confirmInFlight.value || leaving)) return
        showPicker.value = visible
        if (!visible && !confirmInFlight.value) {
            searchQuery.value = ""
            pendingAddIds.value = emptyList()
        }
    }

    fun togglePendingAdd(exercise: Exercise) {
        if (confirmInFlight.value || leaving) return
        pendingAddIds.value = LiftCart.toggle(pendingAddIds.value, exercise.id)
    }

    fun confirmPendingAdd() {
        if (confirmInFlight.value || leaving) return
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
            error.value = SessionOrderCopy.ADD_LIFT_FAILED
            return
        }
        pendingAddIds.value = emptyList()
        if (plan.nothingNew) {
            showPicker.value = false
            searchQuery.value = ""
            error.value = null
            return
        }
        confirmInFlight.value = true
        showPicker.value = false
        searchQuery.value = ""
        launchWrite {
            try {
                val id = ensureRoutineId() ?: run {
                    restorePicker(selected)
                    return@launchWrite
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
                        restorePicker(remaining.map { it.id })
                        error.value = SessionOrderCopy.ADD_LIFT_FAILED
                        return@launchWrite
                    }
                }
                extraCatalog.value = extraCatalog.value.filter { extra ->
                    extra.id !in plan.toAdd.map { it.id }.toSet()
                }
                error.value = null
            } finally {
                confirmInFlight.value = false
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
        if (leaving) return
        launchWrite {
            val id = ensureRoutineId() ?: return@launchWrite
            val alreadyAdded = routineFlow.value?.exercises?.any { it.exercise.id == exercise.id } == true
            if (alreadyAdded) {
                error.value = "${exercise.name} is already in this routine."
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
                error.value = null
            } catch (thrown: Exception) {
                AppLog.w(TAG, "addExercise failed", thrown)
                error.value = SessionOrderCopy.ADD_LIFT_FAILED
            }
        }
    }

    fun createAndSelect(name: String, muscleGroup: String) {
        if (leaving) return
        if (name.isBlank()) {
            error.value = SessionOrderCopy.LIFT_NAME_REQUIRED
            return
        }
        launchWrite {
            try {
                when (val result = container.exerciseRepository.createCustom(name, muscleGroup)) {
                    is SaveExerciseResult.DuplicateName -> error.value = DUPLICATE_NAME_MESSAGE
                    is SaveExerciseResult.MissingMuscle -> error.value = MuscleGroups.MISSING_MESSAGE
                    is SaveExerciseResult.Saved -> {
                        extraCatalog.value = LiftCart.mergeSources(
                            extraCatalog.value,
                            listOf(result.exercise),
                        )
                        if (showPicker.value && !confirmInFlight.value) {
                            togglePendingAdd(result.exercise)
                        }
                        error.value = null
                    }
                }
            } catch (thrown: Exception) {
                AppLog.w(TAG, "createAndSelect failed", thrown)
                error.value = SessionOrderCopy.CREATE_LIFT_FAILED
            }
        }
    }

    /**
     * One lift's targets to Room, reported rather than surfaced: the focus-change commit and
     * the exit flush tell the owner in different places, so neither is decided here. A
     * rejection is the value's fault and retrying cannot fix it; a failure is the write's
     * fault and one more attempt might. Cancellation passes straight through.
     */
    private suspend fun writeTargets(
        itemId: String,
        targetSets: Int,
        targetReps: Int,
        targetWeightKg: Double?,
        restSeconds: Int,
    ): RoutineWriteOutcome {
        if (targetSets < 1 || targetReps < 1) {
            return RoutineWriteOutcome.Rejected(RoutineSaveCopy.TARGETS_REJECTED)
        }
        val id = ensureRoutineId() ?: return RoutineWriteOutcome.Failed
        return runCatchingCancellable<RoutineWriteOutcome> {
            container.routineRepository.updateExercise(
                itemId = itemId,
                routineId = id,
                targetSets = targetSets,
                targetReps = targetReps,
                targetWeightKg = targetWeightKg,
                restSeconds = restSeconds,
            )
            RoutineWriteOutcome.Stored
        }.getOrElse { thrown ->
            AppLog.w(TAG, "writeTargets failed", thrown)
            RoutineWriteOutcome.Failed
        }
    }

    fun removeExercise(itemId: String) {
        if (leaving) return
        launchWrite {
            val id = ensureRoutineId() ?: return@launchWrite
            try {
                container.routineRepository.removeExercise(itemId, id)
                stagedTargets.remove(itemId)
                error.value = null
            } catch (thrown: Exception) {
                AppLog.w(TAG, "removeExercise failed", thrown)
                error.value = SessionOrderCopy.REMOVE_LIFT_FAILED
            }
        }
    }

    fun moveExercise(itemId: String, direction: Int) {
        if (leaving) return
        launchWrite {
            val id = ensureRoutineId() ?: return@launchWrite
            try {
                container.routineRepository.moveExercise(id, itemId, direction)
            } catch (thrown: Exception) {
                AppLog.w(TAG, "moveExercise failed", thrown)
                error.value = SessionOrderCopy.REORDER_LIFT_FAILED
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

    private fun restorePicker(remaining: List<String>) {
        pendingAddIds.value = remaining
        if (!leaving) showPicker.value = true
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
            persistDraft()
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
        val invalidReason: String? = null,
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
        val addingLifts: Boolean,
        val exit: ExitState,
    )

    /**
     * What the screen needs to know about the exit attempt in flight, or the one that just
     * stayed. One value rather than three flows so that "saving" and "why it stayed" can
     * never be observed half-updated.
     */
    private data class ExitState(
        val saving: Boolean = false,
        val saveError: String? = null,
        val unsavedOnBack: RoutineExitOutcome.Unsaved? = null,
    )

    private companion object {
        const val KEY_ID = "editor.routineId"
        const val KEY_NAME = "editor.name"
        const val KEY_NOTES = "editor.notes"
        const val KEY_CREATED = "editor.createdThisSession"
    }
}