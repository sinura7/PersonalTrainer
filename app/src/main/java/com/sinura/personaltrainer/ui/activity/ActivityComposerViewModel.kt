package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.domain.ActivityBlock
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.CompleteTrainingOutcome
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilDateTime
import com.sinura.personaltrainer.domain.ComposerCopy
import com.sinura.personaltrainer.domain.DstGapPolicy
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.StrengthSet
import com.sinura.personaltrainer.ui.library.DUPLICATE_NAME_MESSAGE
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.domain.ExercisePickerProgress
import com.sinura.personaltrainer.util.PickerWrites
import com.sinura.personaltrainer.util.ErrorSlot
import com.sinura.personaltrainer.util.IdFactory
import com.sinura.personaltrainer.util.JvmTime
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.sinura.personaltrainer.data.draft.ActivityFormCodec
import com.sinura.personaltrainer.data.draft.ActivityFormFields
import com.sinura.personaltrainer.data.draft.CustomExerciseDrafts
import com.sinura.personaltrainer.data.draft.DraftForm
import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.PickerContextDrafts
import com.sinura.personaltrainer.data.draft.StagedCardio
import com.sinura.personaltrainer.data.draft.StagedPickerForms
import com.sinura.personaltrainer.data.draft.StagedStrength
import com.sinura.personaltrainer.ui.components.OwnedPickerRecovery
import com.sinura.personaltrainer.ui.components.StagedFormRecovery
import java.util.UUID
import com.sinura.personaltrainer.domain.WeightUnit

/**
 * [ErrorSlot] families: a success may clear only its own family's refusal.
 *
 * [ERR_DRAFT] and [ERR_SAVE] are raised into the form's slot; [ERR_CREATE_LIFT] into the
 * picker sheet's own slot. Two slots, not one, because the two surfaces are on screen at
 * the same time: a refusal from the sheet must not push the form's refusal off the screen
 * above Save, and neither may clear the other.
 */
private const val ERR_DRAFT = "draft"
private const val ERR_SAVE = "save"
private const val ERR_CREATE_LIFT = "createLift"

enum class ComposerMode { STRENGTH, CARDIO, MIXED }

data class ComposerStrengthLine(
    val exercise: Exercise,
    val weightKg: Double,
    val reps: Int,
)

data class ComposerCardioLine(
    val type: CardioType,
    val minutes: Int,
    val distanceKm: Double?,
    val indoor: Boolean,
)

data class ActivityComposerUiState(
    val mode: ComposerMode = ComposerMode.STRENGTH,
    val title: String = "",
    val epochDay: Long = 0L,
    val todayEpochDay: Long = 0L,
    val strength: List<ComposerStrengthLine> = emptyList(),
    val cardio: List<ComposerCardioLine> = emptyList(),
    val catalog: List<Exercise> = emptyList(),
    /** Form-level outcomes: a refused or failed save. Rendered above Save. */
    val error: String? = null,
    val pickerProgress: ExercisePickerProgress = ExercisePickerProgress(),
    /** The picker's Create row failed. Rendered inside the sheet that asked, and only there. */
    val createError: String? = null,
    val saving: Boolean = false,
) {
    val canShiftLater: Boolean
        get() = ComposerCopy.canShiftLater(epochDay, todayEpochDay)

    val isDirty: Boolean
        get() = ComposerCopy.isDirty(
            title = title,
            strengthCount = strength.size,
            cardioCount = cardio.size,
            epochDay = epochDay,
            todayEpochDay = todayEpochDay,
        )

    /** Save needs at least one strength or cardio line; an empty form cannot write. */
    val canSave: Boolean
        get() = strength.isNotEmpty() || cardio.isNotEmpty()
}

internal data class StrengthAdderFields(val pickedId: String = "", val weight: String = "0", val reps: String = "8", val unit: WeightUnit? = null)

class ActivityComposerViewModel internal constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    container: AppDependencies,
    private val ids: IdFactory,
    private val clock: com.sinura.personaltrainer.domain.TimePort,
    forms: StagedPickerForms?,
    contexts: PickerContextDrafts?,
    customDrafts: CustomExerciseDrafts?,
) : AppViewModel(application, container) {
    @JvmOverloads constructor(application: Application, savedStateHandle: SavedStateHandle,
        container: AppDependencies = application.appContainer(), ids: IdFactory = IdFactory.Uuid,
        clock: com.sinura.personaltrainer.domain.TimePort = JvmTime) : this(application, savedStateHandle,
        container, ids, clock, (container as? AppContainer)?.stagedPickerForms,
        (container as? AppContainer)?.pickerContextDrafts, (container as? AppContainer)?.customExerciseDrafts)
    private val mode = MutableStateFlow(parseMode(savedStateHandle.get<String>("mode")))

    /**
     * The typed draft, mirrored into saved state on every change. Rotation kept this
     * ViewModel; background process death recreated it with an empty title, today's date and
     * no lines, while correctly keeping the plan link — so the retyped session was the only
     * thing about the day that was lost.
     */
    private val draft = SavedStateComposerDraft(savedStateHandle)
    private val title = MutableStateFlow(draft.title())
    private val epochDay = MutableStateFlow(
        // A restored day is clamped like a picked one: the clock may have moved on.
        draft.epochDay()?.coerceAtMost(clock.captureNow().localEpochDay)
            ?: clock.captureNow().localEpochDay,
    )
    private val strength = MutableStateFlow(draft.strength())
    private val cardio = MutableStateFlow(draft.cardio())
    private val catalog = MutableStateFlow<List<Exercise>>(emptyList())

    /** Form-level refusals — [ERR_DRAFT], [ERR_SAVE]. Rendered above Save. */
    private val pickerWrites = PickerWrites()
    private val error = ErrorSlot()

    /** The picker's Create row — [ERR_CREATE_LIFT]. Rendered inside the sheet that asked. */
    private val createError = ErrorSlot()

    private val saving = MutableStateFlow(false)

    val uiState: StateFlow<ActivityComposerUiState> = combine(
        combine(mode, title, epochDay, catalog) { currentMode, name, day, lifts ->
            Quad(currentMode, name, day, lifts)
        },
        combine(
            strength,
            cardio,
            error.messages,
            saving,
            createError.messages,
        ) { sets, cardioLines, err, busy, createProblem ->
            Flags(sets, cardioLines, err, busy, createProblem)
        },
    ) { quad, flags ->
        ActivityComposerUiState(
            mode = quad.mode,
            title = quad.title,
            epochDay = quad.epochDay,
            todayEpochDay = clock.captureNow().localEpochDay,
            strength = flags.strength,
            cardio = flags.cardio,
            catalog = quad.catalog,
            error = flags.error,
            createError = flags.createError,
            saving = flags.saving,
        )
    }.combine(pickerWrites.state) { state, progress ->
        state.copy(pickerProgress = progress)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityComposerUiState())

    private val _savedId = MutableStateFlow<String?>(null)
    val savedId: StateFlow<String?> = _savedId.asStateFlow()

    /**
     * The planned occurrence this composer was opened for, transferred out of
     * [PendingOccurrence] at init and mirrored into [SavedStateHandle]. Taking it
     * immediately means an abandoned composer leaves nothing armed for an
     * unrelated later save or finish to consume; holding it here (not re-reading
     * the store at save time) means a rejected first save keeps the link for the
     * retry instead of losing the Plan row forever.
     */
    private var heldOccurrenceId: String?
        get() = savedStateHandle.get<String>(KEY_HELD_OCCURRENCE)
        set(value) = savedStateHandle.set(KEY_HELD_OCCURRENCE, value)

    /**
     * The init-time transfer out of [PendingOccurrence]. [confirmDraft] joins it
     * before reading [heldOccurrenceId]: a fast save could otherwise land while
     * the transfer was still suspended on the DataStore write, saving the
     * activity with no plan link after the store had already been cleared.
     */
    private val armTransfer: Job
    private var formIdentity = savedStateHandle.get<String>(KEY_IDENTITY) ?: UUID.randomUUID().toString()
    private val adder = MutableStateFlow(StrengthAdderFields())
    internal val adderFields: StateFlow<StrengthAdderFields> = adder
    private val pickerOpen = MutableStateFlow(false)
    internal val showPicker: StateFlow<Boolean> = pickerOpen
    private val pickerQuery = MutableStateFlow("")
    internal val query: StateFlow<String> = pickerQuery
    internal val formRecovery: StagedFormRecovery<ActivityFormFields>?
    internal val pickerRecovery: OwnedPickerRecovery?

    init {
        viewModelScope.launch {
            container.exerciseRepository.observeAll().collect { catalog.value = it }
        }
        if (savedStateHandle.get<String>(KEY_IDENTITY) == null) {
            savedStateHandle[KEY_IDENTITY] = formIdentity
        }
        armTransfer = viewModelScope.launch {
            if (!savedStateHandle.contains(KEY_HELD_OCCURRENCE)) {
                heldOccurrenceId = PendingOccurrence.takeForComposer(container)
            }
        }
        formRecovery = forms?.let { service ->
            StagedFormRecovery(scope = viewModelScope, drafts = service,
                owner = {
                    armTransfer.join()
                    val occurrence = heldOccurrenceId
                    val base = mode.value.name + ":" + if (occurrence == null) "N" else "S${occurrence.length}:$occurrence"
                    val visit = savedStateHandle.get<String>(KEY_FRESH_VISIT)
                    val key = if (visit == null) base else "$base:V:$visit"
                    DraftOwner(DraftForm.ACTIVITY, "composer:" + java.security.MessageDigest.getInstance("SHA-256")
                        .digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
                }, codec = ActivityFormCodec, snapshot = ::privateFields, restore = { fields ->
                    check(fields.mode == mode.value.name && fields.occurrenceId == heldOccurrenceId)
                    // Saved state is written on each change. The private file follows up to
                    // 150ms later, so a death in that gap must keep the newer saved state.
                    // A handle with no typed draft yet is a fresh visit: the file's id wins.
                    if (!draft.exists()) {
                        formIdentity = fields.identity
                        title.value = fields.title
                        epochDay.value = fields.epochDay
                        strength.value = fields.strength.map { ComposerStrengthLine(it.exercise, it.weightKg, it.reps) }
                        cardio.value = fields.cardio.map { ComposerCardioLine(it.type, it.minutes, it.distanceKm, it.indoor) }
                        adder.value = StrengthAdderFields(fields.pickedId, fields.weightText, fields.repsText, fields.weightUnit)
                        rememberSavedDraft()
                    }
                    savedStateHandle[KEY_IDENTITY] = formIdentity
                }, available = { !container.backupService.restoreInProgress() }, stillOwned = { fields ->
                    val occurrence = fields.occurrenceId?.let { container.plannerRepository.getOccurrence(it) }
                    fields.mode == mode.value.name && fields.occurrenceId == heldOccurrenceId &&
                        (fields.occurrenceId == null || occurrence != null && occurrence.completedActivityId == null &&
                            occurrence.status != com.sinura.personaltrainer.domain.OccurrenceStatus.DONE) &&
                        fields.strength.all { container.exerciseRepository.getById(it.exercise.id) == it.exercise } &&
                        (fields.pickedId.isEmpty() || container.exerciseRepository.getById(fields.pickedId) != null)
                }, inspectLanded = { fields ->
                    container.activityRepository.get(fields.identity) != null
                }, onLanded = { fields ->
                    container.activityRepository.reconcileSaved(fields.identity)
                    _savedId.value = fields.identity
                    draft.clear()
                }, retireLandedVisit = { fields ->
                    // This screen's own id still belongs to the save that just landed.
                    // A later Past workout has a new id and must start empty.
                    if (savedStateHandle.get<String>(KEY_IDENTITY) == fields.identity) {
                        false
                    } else if (container.activityRepository.get(fields.identity) == null) {
                        false
                    } else {
                        savedStateHandle[KEY_FRESH_VISIT] = UUID.randomUUID().toString()
                        true
                    }
                })
        }
        if (formRecovery == null) {
            viewModelScope.launch {
                val existing = container.activityRepository.reconcileSaved(sessionId()) ?: return@launch
                _savedId.value = existing.id
                draft.clear()
            }
        }
        pickerRecovery = if (contexts != null && customDrafts != null && formRecovery != null) {
            OwnedPickerRecovery(scope = viewModelScope, contextDrafts = contexts, customDrafts = customDrafts,
                repository = container.exerciseRepository, savedState = savedStateHandle,
                owner = { formRecovery.opening.join(); check(formRecovery.status.value.ready); pickerOwner() },
                stillOwned = ::pickerOwned, ownedAtCommit = ::pickerOwned, onQuery = { pickerQuery.value = it },
                selectCreated = { exercise -> selectOwnedExercise(exercise) },
                onSelected = { pickerOpen.value = false }, onReset = { formRecovery.retry() })
        } else null
    }

    private fun pickerOwner() = DraftOwner(DraftForm.ACTIVITY, "composer:$formIdentity")
    private suspend fun pickerOwned(owner: DraftOwner): Boolean = owner == pickerOwner() &&
        !saving.value && _savedId.value == null && formRecovery?.isCurrent() == true
    private fun privateFields() = ActivityFormFields(identity = formIdentity, mode = mode.value.name,
        occurrenceId = heldOccurrenceId, title = title.value, epochDay = epochDay.value,
        strength = strength.value.map { StagedStrength(it.exercise, it.weightKg, it.reps) },
        cardio = cardio.value.map { StagedCardio(it.type, it.minutes, it.distanceKm, it.indoor) },
        pickedId = adder.value.pickedId, weightText = adder.value.weight, repsText = adder.value.reps,
        weightUnit = adder.value.unit)
    fun keepAndLeave(onKept: () -> Unit) { if (!canLeave()) return; formRecovery?.leave(onKept) ?: onKept() }
    fun discardAndLeave(onCleared: () -> Unit) {
        if (!canLeave()) return
        viewModelScope.launch { if (formRecovery?.discard() != false) { draft.clear(); onCleared() } }
    }
    internal fun changeAdder(fields: StrengthAdderFields) {
        if (!canEditDraft()) return
        adder.value = fields; formRecovery?.changed()
    }
    fun setPickerVisible(visible: Boolean) {
        if (visible && (saving.value || formRecovery?.status?.value?.ready == false)) return
        if (!visible && pickerWrites.busy) return
        pickerOpen.value = visible
    }
    private suspend fun selectOwnedExercise(exercise: Exercise): Boolean {
        if (!pickerOwned(pickerOwner())) return false
        catalog.value = com.sinura.personaltrainer.domain.LiftCart.mergeSources(catalog.value, listOf(exercise))
        adder.value = adder.value.copy(pickedId = exercise.id)
        return formRecovery?.flush() == true
    }
    fun selectPickerExercise(exercise: Exercise) {
        if (formRecovery == null) return
        viewModelScope.launch {
            if (selectOwnedExercise(exercise)) pickerRecovery?.view?.leave { pickerOpen.value = false }
        }
    }

    fun setTitle(value: String) {
        if (!canEditDraft()) return
        title.value = value
        error.dismiss()
        rememberDraft()
    }

    fun dismissError() {
        error.dismiss()
    }

    fun dismissCreateError() {
        createError.dismiss()
        pickerWrites.dismissError()
    }

    fun setEpochDay(value: Long) {
        if (!canEditDraft()) return
        val today = clock.captureNow().localEpochDay
        epochDay.value = value.coerceAtMost(today)
        error.dismiss()
        rememberDraft()
    }

    fun shiftDay(delta: Long) {
        setEpochDay(epochDay.value + delta)
    }

    fun addStrength(exercise: Exercise, weightKg: Double, reps: Int) {
        if (!canEditDraft()) return
        if (reps <= 0) {
            error.fail(source = ERR_DRAFT, message = "Reps must be at least 1.")
            return
        }
        strength.value = strength.value + ComposerStrengthLine(exercise, weightKg, reps)
        error.dismiss()
        rememberDraft()
    }

    fun removeStrength(index: Int) {
        if (!canEditDraft()) return
        strength.value = strength.value.filterIndexed { i, _ -> i != index }
        rememberDraft()
    }

    fun addCardio(type: CardioType, minutes: Int, distanceKm: Double?, indoor: Boolean) {
        if (!canEditDraft()) return
        if (minutes <= 0) {
            error.fail(source = ERR_DRAFT, message = "Duration must be at least one minute.")
            return
        }
        cardio.value = cardio.value + ComposerCardioLine(type, minutes, distanceKm, indoor)
        error.dismiss()
        rememberDraft()
    }

    fun removeCardio(index: Int) {
        if (!canEditDraft()) return
        cardio.value = cardio.value.filterIndexed { i, _ -> i != index }
        rememberDraft()
    }

    /**
     * The owner chose to leave without saving. The nav entry goes with them, but the
     * draft is cleared explicitly so nothing about "leave" depends on how the back stack
     * happens to be torn down.
     *
     * Refused while a save is in flight. Until [confirmActivity] answers, the draft is the
     * only record of what is being written; dropping it on a Cancel that raced the write
     * would leave a saved session the screen just called discarded, or a lost one the
     * screen just called saved. The dock disables Cancel for the same reason; this is the
     * guard behind it.
     */
    fun discardDraft() {
        if (saving.value || pickerWrites.busy || _createdExercise.value != null) return
        draft.clear()
    }

    /**
     * Whether leaving is safe right now. Read from this ViewModel's own flag, not from the
     * rendered state: [save] sets the flag synchronously, while the screen's collected state
     * catches up a frame later, and a Back in that frame must not pop the entry — and with it
     * the coroutine the write is riding on.
     */
    fun canLeave(): Boolean = !saving.value && !pickerWrites.busy && _createdExercise.value == null &&
        formRecovery?.status?.value?.loading != true && formRecovery?.status?.value?.keeping != true &&
        pickerRecovery?.create?.status?.value?.busy != true

    private fun rememberDraft() {
        rememberSavedDraft()
        formRecovery?.changed()
    }
    private fun canEditDraft(): Boolean = !saving.value && formRecovery?.status?.value?.loading != true && formRecovery?.status?.value?.pending != true
    private fun rememberSavedDraft() {
        draft.write(
            title = title.value,
            epochDay = epochDay.value,
            strength = strength.value,
            cardio = cardio.value,
        )
    }

    fun save() {
        val started = error.mark()
        if (saving.value || pickerWrites.busy || _createdExercise.value != null) return
        if (strength.value.isEmpty() && cardio.value.isEmpty()) return
        if (formRecovery?.status?.value?.ready == false || pickerRecovery?.create?.status?.value?.busy == true) return
        saving.value = true
        viewModelScope.launch {
            if (formRecovery?.beginSubmission() == false) { saving.value = false; return@launch }
            val write = runCatchingCancellable { confirmDraft() }
            saving.value = false
            write.onSuccess { result ->
                when (result) {
                    is CompleteTrainingOutcome.Written -> {
                        // Only an accepted write spends the draft, like the plan link: a
                        // rejected or thrown save keeps everything typed for the retry.
                        draft.clear()
                        formRecovery?.accepted()
                        error.clearFrom(source = ERR_SAVE, before = started)
                        _savedId.value = result.id
                    }
                    is CompleteTrainingOutcome.RuledOut -> {
                        formRecovery?.rejected()
                        error.fail(source = ERR_SAVE, message = result.reason)
                    }
                    is CompleteTrainingOutcome.Failed ->
                        error.fail(source = ERR_SAVE, message = result.message)
                }
            }.onFailure { thrown ->
                AppLog.w(TAG, "Saving an activity failed", thrown)
                error.fail(source = ERR_SAVE, message = "Could not save that session. Try again.")
            }
        }
    }

    fun onSavedHandled() {
        _savedId.value = null
    }

    /**
     * One-shot result of the picker's Create row. The screen selects it and
     * closes the picker, same as tapping a catalog lift.
     */
    private val _createdExercise = MutableStateFlow<Exercise?>(null)
    val createdExercise: StateFlow<Exercise?> = _createdExercise.asStateFlow()

    fun onCreatedExerciseHandled() {
        _createdExercise.value = null
    }

    fun retryPicker() = pickerWrites.retry()

    fun createExercise(name: String, muscleGroup: String, loadType: LoadType = LoadType.EXTERNAL) {
        if (saving.value || pickerWrites.busy || _createdExercise.value != null) return
        val started = createError.mark()
        val ticket = pickerWrites.begin("create", creating = true)
        if (name.isBlank()) {
            createError.fail(ERR_CREATE_LIFT, SessionOrderCopy.LIFT_NAME_REQUIRED)
            pickerWrites.fail(ticket, SessionOrderCopy.LIFT_NAME_REQUIRED)
            return
        }
        viewModelScope.launch {
            try {
                when (val result = container.exerciseRepository.createCustom(name, muscleGroup, loadType = loadType)) {
                    is SaveExerciseResult.DuplicateName -> {
                        createError.fail(ERR_CREATE_LIFT, DUPLICATE_NAME_MESSAGE)
                        pickerWrites.fail(ticket, DUPLICATE_NAME_MESSAGE, existing = result.existing)
                    }
                    is SaveExerciseResult.MissingMuscle -> {
                        createError.fail(ERR_CREATE_LIFT, MuscleGroups.MISSING_MESSAGE)
                        pickerWrites.fail(ticket, MuscleGroups.MISSING_MESSAGE)
                    }
                    is SaveExerciseResult.Saved -> {
                        createError.clearFrom(ERR_CREATE_LIFT, before = started)
                        catalog.value = com.sinura.personaltrainer.domain.LiftCart.mergeSources(catalog.value, listOf(result.exercise))
                        _createdExercise.value = result.exercise
                        pickerWrites.succeed(ticket, created = true)
                    }
                }
            } catch (thrown: kotlinx.coroutines.CancellationException) {
                pickerWrites.cancel(ticket)
                throw thrown
            } catch (thrown: Exception) {
                createError.fail(ERR_CREATE_LIFT, SessionOrderCopy.CREATE_LIFT_FAILED)
                pickerWrites.fail(ticket, SessionOrderCopy.CREATE_LIFT_FAILED) { createExercise(name, muscleGroup, loadType) }
            }
        }
    }

    internal suspend fun confirmDraft(): CompleteTrainingOutcome {
        armTransfer.join()
        val now = clock.captureNow()
        val performed = clock.resolveLocal(
            CivilDateTime(CivilDate.fromEpochDay(epochDay.value), hour = 12, minute = 0),
            now.zoneId,
            gap = DstGapPolicy.SHIFT_FORWARD,
        )
        val blocks = buildBlocks()
        val draft = ActivityDraft(
            id = sessionId(),
            origin = ActivityOrigin.BACKDATED,
            title = resolvedTitle(),
            performedStart = performed,
            performedEnd = performed,
            occurrenceId = heldOccurrenceId,
            blocks = blocks,
        )
        // The link is spent only by an accepted write: "Nothing to save." must
        // leave it in place so the retry still marks the Plan row DONE.
        val outcome = container.completeTraining.confirm(draft, now)
        if (outcome is CompleteTrainingOutcome.Written) heldOccurrenceId = null
        return outcome
    }

    private fun resolvedTitle(): String {
        val typed = title.value.trim()
        if (typed.isNotEmpty()) return typed
        return when (mode.value) {
            ComposerMode.CARDIO -> cardio.value.firstOrNull()?.let {
                ComposerCopy.untitledCardioTitle(it.type)
            } ?: "Cardio"
            ComposerMode.MIXED -> "Mixed session"
            ComposerMode.STRENGTH -> "Workout"
        }
    }

    private fun buildBlocks(): List<ActivityBlock> {
        val includeStrength = mode.value != ComposerMode.CARDIO
        val includeCardio = mode.value != ComposerMode.STRENGTH
        val blocks = mutableListOf<ActivityBlock>()
        var order = 0
        if (includeStrength) {
            strength.value.forEach { line ->
                blocks += StrengthBlock(
                    id = ids.newId(),
                    sortOrder = order++,
                    exerciseId = line.exercise.id,
                    exerciseName = line.exercise.name,
                    loadType = line.exercise.loadType,
                    equipment = line.exercise.equipment,
                    muscles = line.exercise.muscles,
                    sets = listOf(
                        StrengthSet(
                            id = ids.newId(),
                            setNumber = 1,
                            weightKg = line.weightKg,
                            reps = line.reps,
                            rpe = null,
                            isWarmup = false,
                            completedAtMs = clock.nowMillis(),
                        ),
                    ),
                )
            }
        }
        if (includeCardio) {
            cardio.value.forEach { line ->
                blocks += CardioBlock(
                    id = ids.newId(),
                    sortOrder = order++,
                    type = line.type,
                    indoor = line.indoor,
                    elapsedSeconds = line.minutes.toLong() * 60L,
                    movingSeconds = line.minutes.toLong() * 60L,
                    distanceMeters = line.distanceKm?.takeIf { it > 0.0 }?.times(1_000.0),
                    elevationMeters = null,
                    heartRateBpm = null,
                    energyKj = null,
                    rpe = null,
                    routeRef = null,
                )
            }
        }
        return blocks
    }

    private fun parseMode(raw: String?): ComposerMode = when (raw?.lowercase()) {
        "cardio" -> ComposerMode.CARDIO
        "mixed" -> ComposerMode.MIXED
        else -> ComposerMode.STRENGTH
    }

    private data class Quad(
        val mode: ComposerMode,
        val title: String,
        val epochDay: Long,
        val catalog: List<Exercise>,
    )

    private data class Flags(
        val strength: List<ComposerStrengthLine>,
        val cardio: List<ComposerCardioLine>,
        val error: String?,
        val saving: Boolean,
        val createError: String?,
    )

    /** One id for this composer, including after the process is recreated. */
    private fun sessionId(): String {
        val id = formIdentity
        savedStateHandle[KEY_IDENTITY] = id
        return id
    }

    private companion object {
        const val TAG = "PT/ActivityComposer"
        const val KEY_HELD_OCCURRENCE = "composer-held-occurrence"
        const val KEY_IDENTITY = "composer.private.identity"
        const val KEY_FRESH_VISIT = "composer.freshVisit"
    }
}
