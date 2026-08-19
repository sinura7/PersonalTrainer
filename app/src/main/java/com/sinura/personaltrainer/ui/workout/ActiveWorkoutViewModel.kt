package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.workout.SavedStateWorkoutDraft
import com.sinura.personaltrainer.workout.WorkoutDraft
import com.sinura.personaltrainer.workout.WorkoutDraftRecovery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActiveExerciseDraft(
    val weightKg: Double = 0.0,
    val reps: Int = 5,
    val rpe: Int? = null,
    val isWarmup: Boolean = false,
)

/** Whether the session row behind this screen has been resolved yet. */
enum class SessionLoadState {
    /** The session Flow has not emitted anything yet. */
    LOADING,

    /** A session row exists and is in [ActiveWorkoutUiState.session]. */
    FOUND,

    /**
     * The Flow emitted null for a real id: the workout was discarded, restored over, or the
     * id came from a stale notification. Terminal — never resolves into FOUND on its own, so
     * the UI must offer a way out instead of spinning forever.
     */
    MISSING,
}

data class ActiveWorkoutUiState(
    val loadState: SessionLoadState = SessionLoadState.LOADING,
    val session: WorkoutSession? = null,
    val selectedExerciseId: String? = null,
    val draft: ActiveExerciseDraft = ActiveExerciseDraft(),
    val hint: ProgressionHint? = null,
    val searchQuery: String = "",
    val searchResults: List<Exercise> = emptyList(),
    val showExercisePicker: Boolean = false,
    val notes: String = "",
    val error: String? = null,
    val finished: Boolean = false,
    val editingSetId: String? = null,
) {
    val isLoading: Boolean get() = loadState == SessionLoadState.LOADING
}

data class RestTimerUiState(
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 90,
    val running: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AppViewModel(application) {
    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()
    private val draftCache = container.workoutDraftCache
    private val savedDraft = SavedStateWorkoutDraft(savedStateHandle)

    private val selectedExerciseId = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow(ActiveExerciseDraft())
    private val hint = MutableStateFlow<ProgressionHint?>(null)
    private val restTotal = MutableStateFlow(90)
    private val searchQuery = MutableStateFlow("")
    private val showPicker = MutableStateFlow(false)
    private val notes = MutableStateFlow("")
    private val error = MutableStateFlow<String?>(null)
    private val finished = MutableStateFlow(false)
    private val editingSetId = MutableStateFlow<String?>(null)
    private var lastPrefillExerciseId: String? = null
    private var pendingResumeDraft: WorkoutDraft? = null
    private val restTimer = container.restTimerController

    /**
     * Flips true the first time the session query emits — including when it emits null.
     * "Session is null" alone cannot distinguish "still loading" from "gone", which is how a
     * discarded workout used to leave the screen on a spinner with no exit.
     */
    private val sessionResolved = MutableStateFlow(false)

    private val sessionFlow = container.workoutRepository.observeSession(sessionId)
        .onEach { sessionResolved.value = true }

    init {
        if (sessionId.isBlank()) {
            error.value = "This workout is no longer available."
            // Nothing will ever emit for a blank id, so resolve immediately rather than spin.
            sessionResolved.value = true
        }
        // Survives process death; the in-memory cache does not. See WorkoutDraftRecovery.
        val recovered = WorkoutDraftRecovery.resolve(
            sessionId = sessionId,
            inMemory = draftCache.get(sessionId),
            persisted = savedDraft.read(sessionId),
        )
        recovered?.let { cached ->
            pendingResumeDraft = cached
            selectedExerciseId.value = cached.exerciseId
            draft.value = ActiveExerciseDraft(
                weightKg = cached.weightKg,
                reps = cached.reps.coerceAtLeast(1),
                rpe = cached.rpe,
                isWarmup = cached.isWarmup,
            )
            notes.value = cached.notes
        }
        viewModelScope.launch {
            sessionFlow.collect { session ->
                if (session == null) return@collect
                val resolved = session.resolveSelectedExerciseId(selectedExerciseId.value)
                if (resolved != selectedExerciseId.value) {
                    selectedExerciseId.value = resolved
                }
                val resume = pendingResumeDraft
                if (resume != null) {
                    val cacheMatches = resume.exerciseId == null || resume.exerciseId == resolved
                    if (cacheMatches) {
                        lastPrefillExerciseId = resolved
                    }
                    pendingResumeDraft = null
                }
                if (notes.value.isEmpty() && session.notes.isNotEmpty()) {
                    notes.value = session.notes
                }
                persistDraft()
            }
        }
    }

    val restTimerState: StateFlow<RestTimerUiState> = combine(
        restTimer.remainingSeconds,
        restTimer.snapshot,
        restTotal,
    ) { remaining, snapshot, planned ->
        RestTimerUiState(
            remainingSeconds = remaining,
            totalSeconds = if (snapshot.running) snapshot.totalSeconds else planned,
            running = snapshot.running,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RestTimerUiState(),
    )

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        sessionFlow,
        selectedExerciseId,
        draft,
        hint,
    ) { session, selected, currentDraft, currentHint ->
        WorkoutCore(session, selected, currentDraft, currentHint)
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
            // Overwritten below once sessionResolved is known; see the combine on that flow.
            loadState = SessionLoadState.LOADING,
            session = core.session,
            selectedExerciseId = core.session?.resolveSelectedExerciseId(core.selected) ?: core.selected,
            draft = core.draft,
            hint = core.hint,
            searchQuery = extras.query,
            searchResults = emptyList(),
            showExercisePicker = extras.showPicker,
            notes = extras.notes,
            error = extras.error,
            finished = extras.finished,
            editingSetId = extras.editingSetId,
        )
    }.combine(sessionResolved) { state, resolved ->
        state.copy(
            loadState = when {
                !resolved && sessionId.isNotBlank() -> SessionLoadState.LOADING
                state.session != null -> SessionLoadState.FOUND
                else -> SessionLoadState.MISSING
            },
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
            reps = targetReps.coerceAtLeast(1),
            rpe = null,
            isWarmup = false,
        )
        persistDraft()
    }

    fun selectExercise(exerciseId: String) {
        selectedExerciseId.value = exerciseId
        lastPrefillExerciseId = null
        persistDraft()
    }

    fun adjustWeight(deltaKg: Double) {
        val next = if (deltaKg.isFinite()) draft.value.weightKg + deltaKg else draft.value.weightKg
        draft.value = draft.value.copy(weightKg = next.coerceAtLeast(0.0))
        persistDraft()
    }

    fun setWeight(weightKg: Double) {
        if (!weightKg.isFinite()) return
        draft.value = draft.value.copy(weightKg = weightKg.coerceAtLeast(0.0))
        persistDraft()
    }

    fun adjustReps(delta: Int) {
        draft.value = draft.value.copy(reps = (draft.value.reps + delta).coerceAtLeast(1))
        persistDraft()
    }

    fun setRpe(rpe: Int?) {
        draft.value = draft.value.copy(rpe = rpe)
        persistDraft()
    }

    fun setWarmup(isWarmup: Boolean) {
        draft.value = draft.value.copy(isWarmup = isWarmup)
        persistDraft()
    }

    fun setNotes(value: String) {
        notes.value = value
        persistDraft()
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            try {
                container.workoutRepository.updateSessionNotes(sessionId, value)
            } catch (_: Exception) {
                // Notes stay in the draft cache if the write fails.
            }
        }
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
            addExerciseInternal(exercise)
        }
    }

    fun createAndAddExercise(name: String, muscleGroup: String) {
        viewModelScope.launch {
            if (name.isBlank()) {
                error.value = "Give that lift a name."
                return@launch
            }
            try {
                val created = container.exerciseRepository.createCustom(name, muscleGroup)
                addExerciseInternal(created)
            } catch (_: Exception) {
                error.value = "Could not create that exercise. Try again."
            }
        }
    }

    private suspend fun addExerciseInternal(exercise: Exercise) {
        if (sessionId.isBlank()) {
            error.value = "This workout is no longer available."
            return
        }
        val alreadyAdded = uiState.value.session?.exercises?.any { it.exercise.id == exercise.id } == true
        if (alreadyAdded) {
            selectedExerciseId.value = exercise.id
            lastPrefillExerciseId = null
            showPicker.value = false
            persistDraft()
            error.value = null
            return
        }
        try {
            container.workoutRepository.addExerciseToSession(sessionId, exercise)
            selectedExerciseId.value = exercise.id
            lastPrefillExerciseId = null
            showPicker.value = false
            persistDraft()
            error.value = null
        } catch (_: Exception) {
            error.value = "Could not add that lift. Try again."
        }
    }

    fun logSet() {
        val exerciseId = selectedExerciseId.value
        if (exerciseId == null) {
            error.value = "Add a lift before logging a set."
            return
        }
        val current = draft.value
        val invalid = SetLogRules.validate(
            weightKg = current.weightKg,
            reps = current.reps,
            isWarmup = current.isWarmup,
        )
        if (invalid != null) {
            error.value = invalid
            return
        }
        viewModelScope.launch {
            try {
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
                        startRestAfterSet()
                    }
                }
                error.value = null
                draft.value = current.copy(isWarmup = false, rpe = null)
                persistDraft()
            } catch (thrown: Exception) {
                error.value = thrown.message?.takeIf { message ->
                    SetLogRules.isUserMessage(message)
                } ?: "Could not save that set. Try again."
            }
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
        persistDraft()
    }

    fun cancelEdit() {
        editingSetId.value = null
    }

    fun deleteSet(setId: String) {
        viewModelScope.launch {
            val session = uiState.value.session
            val deleted = session?.sets?.firstOrNull { it.id == setId }
            val wasLatest = deleted != null &&
                session.sets.maxByOrNull { it.completedAt }?.id == setId
            if (editingSetId.value == setId) {
                editingSetId.value = null
            }
            try {
                container.workoutRepository.deleteSet(setId)
                if (wasLatest) {
                    restTimer.stop()
                }
                error.value = null
            } catch (_: Exception) {
                error.value = "Could not delete that set. Try again."
            }
        }
    }

    fun skipRest() {
        restTimer.stop()
    }

    fun adjustRest(deltaSeconds: Int) {
        restTimer.adjust(deltaSeconds)
    }

    fun startPreset(seconds: Int) {
        viewModelScope.launch {
            container.preferencesRepository.setLastRestPresetSeconds(seconds)
            restTotal.value = seconds
            restTimer.start(seconds, sessionId)
        }
    }

    fun startCustom(input: String): Boolean {
        val seconds = RestTimer.parseCustom(input) ?: return false
        startPreset(seconds)
        return true
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
            try {
                restTimer.stop()
                container.workoutRepository.finishSession(sessionId, notes.value)
                clearDraft()
                finished.value = true
                onFinished()
            } catch (_: Exception) {
                error.value = "Could not finish this workout. Try again."
            }
        }
    }

    fun discardWorkout(onDiscarded: () -> Unit) {
        viewModelScope.launch {
            restTimer.stop()
            try {
                container.workoutRepository.discardSession(sessionId)
                clearDraft()
                onDiscarded()
            } catch (_: Exception) {
                error.value = "Could not discard this workout. Try again."
            }
        }
    }

    private fun startRestAfterSet() {
        viewModelScope.launch {
            val prefs = container.preferencesRepository.restTimerPreferences.first()
            val planned = uiState.value.session
                ?.exercises
                ?.firstOrNull { it.exercise.id == selectedExerciseId.value }
                ?.restSeconds
            val seconds = RestTimer.secondsToStart(planned, prefs)
            restTotal.value = seconds
            restTimer.start(seconds, sessionId)
        }
    }

    fun persistDraftForExit() {
        persistDraft()
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            try {
                container.workoutRepository.updateSessionNotes(sessionId, notes.value)
            } catch (_: Exception) {
                // Draft cache still holds the notes.
            }
        }
    }

    private fun persistDraft() {
        if (sessionId.isBlank()) return
        val current = WorkoutDraft(
            sessionId = sessionId,
            exerciseId = selectedExerciseId.value,
            weightKg = draft.value.weightKg,
            reps = draft.value.reps,
            rpe = draft.value.rpe,
            isWarmup = draft.value.isWarmup,
            notes = notes.value,
        )
        draftCache.put(current)
        // Written through to saved state so the numbers dialed in before a rest survive the
        // process being killed while the phone sits in a pocket.
        savedDraft.write(current)
    }

    private fun clearDraft() {
        draftCache.clear(sessionId)
        savedDraft.clear()
    }

    private data class WorkoutCore(
        val session: WorkoutSession?,
        val selected: String?,
        val draft: ActiveExerciseDraft,
        val hint: ProgressionHint?,
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
