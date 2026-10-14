package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.repository.ApplyPlanResult
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.domain.CustomWeekLift
import com.sinura.personaltrainer.domain.CustomWeekPolicy
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseOrdering
import com.sinura.personaltrainer.domain.LiftCart
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.ui.library.DUPLICATE_NAME_MESSAGE
import com.sinura.personaltrainer.domain.ExercisePickerProgress
import com.sinura.personaltrainer.util.PickerWrites
import com.sinura.personaltrainer.util.ErrorSlot
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.util.toLocalDate
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.sinura.personaltrainer.data.draft.CustomExerciseDrafts
import com.sinura.personaltrainer.data.draft.DraftForm
import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.PickerContextDrafts
import com.sinura.personaltrainer.data.draft.StagedPickerForms
import com.sinura.personaltrainer.data.draft.WeekFormCodec
import com.sinura.personaltrainer.data.draft.WeekFormFields
import com.sinura.personaltrainer.ui.components.OwnedPickerRecovery
import com.sinura.personaltrainer.ui.components.StagedFormRecovery

private const val TAG = "PT/CustomWeekVM"

/** [ErrorSlot] families: a success may clear only its own family's refusal. */
private const val ERR_ADD_LIFT = "addLift"

/** A target box holding text the week cannot hold. Raised by [CustomWeekViewModel.confirm]. */
private const val ERR_TARGETS = "targets"
private const val ERR_CONFIRM = "confirm"

data class CustomWeekUiState(
    val selectedDay: Weekday = Weekday.MONDAY,
    val days: Map<Weekday, List<CustomWeekLift>> = emptyMap(),
    val weekStart: Weekday = SchedulePreferences.DEFAULT_WEEK_START,
    val preferredDays: Set<Weekday> = emptySet(),
    val searchQuery: String = "",
    val searchResults: List<Exercise> = emptyList(),
    val catalog: List<Exercise> = emptyList(),
    val showPicker: Boolean = false,
    val applying: Boolean = false,
    val error: String? = null,
    val pickerProgress: ExercisePickerProgress = ExercisePickerProgress(),
) {
    val selectedLifts: List<CustomWeekLift> get() = days[selectedDay].orEmpty()

    /**
     * What the picker draws as chosen, in session order. It is the selected day itself:
     * a tap puts the lift on the day as it happens, so there is no separate list to lose
     * when the sheet closes.
     */
    val pickedIds: List<String> get() = selectedLifts.map { it.exercise.id }
    val canConfirm: Boolean get() = CustomWeekPolicy.canConfirm(days)
    val trainingDays: Int get() = days.count { it.value.isNotEmpty() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CustomWeekViewModel internal constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies,
    forms: StagedPickerForms?,
    contexts: PickerContextDrafts?,
    customDrafts: CustomExerciseDrafts?,
) : AppViewModel(application, container) {
    @JvmOverloads constructor(application: Application, savedStateHandle: SavedStateHandle,
        container: AppDependencies = application.appContainer()) : this(application, savedStateHandle, container,
        (container as? AppContainer)?.stagedPickerForms, (container as? AppContainer)?.pickerContextDrafts,
        (container as? AppContainer)?.customExerciseDrafts)
    private val savedDraft = SavedStateCustomWeekDraft(savedStateHandle)
    private val selectedDay = MutableStateFlow(
        savedDraft.selectedDay() ?: SchedulePreferences.DEFAULT_WEEK_START,
    )
    private val days = MutableStateFlow(
        savedDraft.days() ?: emptyMap(),
    )
    private val weekStart = MutableStateFlow(SchedulePreferences.DEFAULT_WEEK_START)
    private val preferredDays = MutableStateFlow<Set<Weekday>>(emptySet())
    private val searchQuery = MutableStateFlow("")
    private val showPicker = MutableStateFlow(false)
    private val applying = MutableStateFlow(false)
    private val pickerWrites = PickerWrites()
    private val error = ErrorSlot()

    /**
     * Cards whose boxes cannot be read as written, by lift id, holding the rule each broke.
     * Ids are minted per staged lift, so a lift on two days has two ids and two entries.
     * In memory only: the box text itself is saved state, so after process recreation the card
     * shows its complaint again and re-stages it on the first keystroke.
     */
    private val invalidTargets = mutableMapOf<String, String>()
    private val catalog = MutableStateFlow<List<Exercise>>(emptyList())
    private val extraCatalog = MutableStateFlow<List<Exercise>>(emptyList())
    private var guidedAnswers: OnboardingAnswers? = null
    private var pendingWeightUnit: WeightUnit? = null
    private var userPickedDay = savedDraft.userPickedDay()
    private val trainingGoal = MutableStateFlow(TrainingGoal.GENERAL)
    private var formIdentity = savedStateHandle.get<String>("week.private.identity") ?: UUID.randomUUID().toString()
    internal val formRecovery = forms?.let { service ->
        StagedFormRecovery(scope = viewModelScope, drafts = service,
            owner = { DraftOwner(DraftForm.ONBOARDING, "custom-week") }, codec = WeekFormCodec,
            snapshot = ::privateFields, restore = { fields ->
                formIdentity = fields.identity; selectedDay.value = fields.selectedDay; days.value = fields.days
                userPickedDay = fields.userPickedDay; guidedAnswers = fields.answers
                pendingWeightUnit = fields.weightUnit; preferredDays.value = fields.preferredDays
                savedStateHandle["week.private.identity"] = formIdentity
                persistSavedDraft()
            }, available = { !container.backupService.restoreInProgress() }, stillOwned = { fields ->
                fields.days.values.flatten().all { container.exerciseRepository.getById(it.exercise.id) == it.exercise }
            })
    }
    private fun pickerOwner() = DraftOwner(DraftForm.ONBOARDING, "custom-week:$formIdentity:${selectedDay.value.name}")
    internal val pickerRecovery = if (contexts != null && customDrafts != null && formRecovery != null) {
        OwnedPickerRecovery(scope = viewModelScope, contextDrafts = contexts, customDrafts = customDrafts,
            repository = container.exerciseRepository, savedState = savedStateHandle, owner = {
                formRecovery.opening.join(); check(formRecovery.status.value.ready); pickerOwner()
            }, stillOwned = ::pickerOwned, ownedAtCommit = ::pickerOwned,
            selectionBlocked = { pickerWrites.canRetry },
            selectionBusy = { pickerWrites.busy }, retryLabel = { "Retry closing" },
            onQuery = { searchQuery.value = it }, onReset = { formRecovery.retry() }, selectCreated = { exercise ->
                if (!pickerOwned(pickerOwner())) false else {
                    addPicked(exercise, selectedDay.value); formRecovery.flush()
                }
            })
    } else null

    private suspend fun pickerOwned(owner: DraftOwner): Boolean = owner == pickerOwner() &&
        !applying.value && !_finished.value && formRecovery?.isCurrent() == true
    private fun privateFields() = WeekFormFields(identity = formIdentity, selectedDay = selectedDay.value,
        days = days.value, userPickedDay = userPickedDay, answers = guidedAnswers,
        weightUnit = pendingWeightUnit, preferredDays = preferredDays.value)

    private val resultsFlow = combine(
        searchQuery.flatMapLatest { query ->
            container.exerciseRepository.search(query)
        },
        container.exerciseRepository.observeLastLogged(),
        searchQuery,
    ) { results, lastLogged, query ->
        if (query.isBlank()) ExerciseOrdering.pickerOrder(results, lastLogged) else results
    }

    val uiState: StateFlow<CustomWeekUiState> = combine(
        combine(selectedDay, days, weekStart, preferredDays, searchQuery) { day, draft, start, preferred, query ->
            WeekCore(day, draft, start, preferred, query)
        },
        combine(resultsFlow, showPicker, applying, error.messages) { results, picker, busy, err ->
            WeekExtras(results, picker, busy, err)
        },
        catalog,
        extraCatalog,
    ) { core, extras, lifts, extra ->
        CustomWeekUiState(
            selectedDay = core.selectedDay,
            days = core.days,
            weekStart = core.weekStart,
            preferredDays = core.preferredDays,
            searchQuery = core.query,
            searchResults = LiftCart.visibleResults(
                results = extras.results,
                extra = extra,
                query = core.query,
            ),
            catalog = LiftCart.mergeSources(lifts, extra),
            showPicker = extras.showPicker,
            applying = extras.applying,
            error = extras.error,
        )
    }.combine(pickerWrites.state) { state, progress ->
        state.copy(pickerProgress = progress)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CustomWeekUiState(
            selectedDay = selectedDay.value,
            days = days.value,
        ),
    )

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    init {
        viewModelScope.launch {
            container.exerciseRepository.observeAll()
                .catch { thrown ->
                    AppLog.w(TAG, "Reading the catalog failed", thrown)
                    emit(emptyList())
                }
                .collect { catalog.value = it }
        }
        viewModelScope.launch {
            container.preferencesRepository.coachPreferences
                .catch { thrown ->
                    AppLog.w(TAG, "Reading training goal failed", thrown)
                }
                .collect { trainingGoal.value = it.goal }
        }
        viewModelScope.launch {
            container.preferencesRepository.schedulePreferences
                .catch { thrown ->
                    AppLog.w(TAG, "Reading week start failed", thrown)
                }
                .collect { preferences ->
                    weekStart.value = preferences.weekStart
                    if (!userPickedDay) {
                        selectedDay.value = CustomWeekPolicy.initialSelectedDay(
                            preferences.weekStart,
                            preferredDays.value,
                        )
                    }
                }
        }
    }

    fun selectDay(day: Weekday) {
        if (applying.value || pickerWrites.busy || showPicker.value || formRecovery?.status?.value?.ready == false) return
        userPickedDay = true
        selectedDay.value = day
        persistDraft()
    }

    fun seedFromGuided(
        preferred: Set<Weekday>,
        answers: OnboardingAnswers?,
        unit: WeightUnit?,
    ) {
        if (formRecovery?.status?.value?.loading == true) {
            viewModelScope.launch { formRecovery.opening.join(); if (!formRecovery.status.value.resumed) seedFromGuided(preferred, answers, unit) }
            return
        }
        if (formRecovery?.status?.value?.resumed == true) return
        guidedAnswers = answers
        pendingWeightUnit = unit
        preferredDays.value = preferred
        if (!userPickedDay) {
            selectedDay.value = CustomWeekPolicy.initialSelectedDay(weekStart.value, preferred)
            persistDraft()
        }
    }

    fun canLeave(): Boolean = !applying.value && !pickerWrites.busy && formRecovery?.status?.value?.loading != true && formRecovery?.status?.value?.keeping != true && pickerRecovery?.create?.status?.value?.busy != true
    fun keepAndLeave(onKept: () -> Unit) { if (!canLeave()) return; formRecovery?.leave(onKept) ?: onKept() }
    fun discardAndLeave(onCleared: () -> Unit) {
        if (!canLeave()) return
        viewModelScope.launch { if (formRecovery?.discard() != false) onCleared() }
    }

    fun canDismissPicker(): Boolean = !pickerWrites.busy

    fun setPickerVisible(visible: Boolean) {
        if (visible && (applying.value || formRecovery?.status?.value?.ready == false)) return
        if (!visible && pickerWrites.busy) return
        val recovery = formRecovery
        if (!visible && recovery != null) {
            val ticket = pickerWrites.begin("keeping-week-picker")
            viewModelScope.launch {
                if (recovery.flush()) { showPicker.value = false; pickerWrites.succeed(ticket) }
                else pickerWrites.fail(ticket, "Week draft was not kept. Retry before closing the picker.") { setPickerVisible(false) }
            }
            return
        }
        showPicker.value = visible
        // Only the typed query. The lifts are on the day already — losing a cart to a tap
        // outside the sheet is exactly what this screen no longer does.
        if (!visible && pickerRecovery == null) searchQuery.value = ""
    }

    fun onSearchQuery(value: String) {
        searchQuery.value = value
    }

    /**
     * A tap in the picker, written straight on to the selected day.
     *
     * There is no Confirm to lose any more: the first tap puts the lift on the day, a
     * second tap on the same row takes it off, and the numbers on the rows are the order
     * the day will be lifted in.
     */
    fun togglePicked(exercise: Exercise) {
        if (applying.value || pickerWrites.busy || formRecovery?.status?.value?.ready == false) return
        val day = selectedDay.value
        val existing = days.value[day].orEmpty()
        val stored = existing.firstOrNull { it.exercise.id == exercise.id }
        val next = if (stored != null) {
            // The tap takes the lift off the day, so the card and its complaint go together.
            forgetTargetRule(stored.id)
            existing.filterNot { it.id == stored.id }
        } else {
            CustomWeekPolicy.addLifts(
                existing = existing,
                incoming = listOf(exercise),
                goal = guidedAnswers?.goal ?: trainingGoal.value,
            ) { UUID.randomUUID().toString() }
        }
        days.value = days.value + (day to next)
        error.clearFrom(source = ERR_ADD_LIFT)
        persistDraft()
    }

    fun retryPicker() = pickerWrites.retry()
    fun dismissPickerError() { pickerWrites.dismissError(); error.clearFrom(ERR_ADD_LIFT) }

    fun selectPickerExercise(exercise: Exercise) {
        if (applying.value || pickerWrites.busy || formRecovery?.status?.value?.ready == false) return
        addPicked(exercise, selectedDay.value)
        pickerWrites.dismissError()
    }

    private fun addPicked(exercise: Exercise, day: Weekday) {
        val existing = days.value[day].orEmpty()
        days.value = days.value + (day to CustomWeekPolicy.addLifts(
            existing, listOf(exercise), guidedAnswers?.goal ?: trainingGoal.value,
        ) { UUID.randomUUID().toString() })
        persistDraft()
        error.clearFrom(ERR_ADD_LIFT)
    }

    fun createAndSelect(name: String, muscleGroup: String, loadType: LoadType = LoadType.EXTERNAL) {
        if (applying.value || pickerWrites.busy) return
        createAndSelect(name, muscleGroup, loadType, selectedDay.value, showPicker.value)
    }

    private fun createAndSelect(name: String, muscleGroup: String, loadType: LoadType, day: Weekday, selectHere: Boolean) {
        if (applying.value || pickerWrites.busy) return
        val ticket = pickerWrites.begin("create", creating = true)
        if (name.isBlank()) {
            error.fail(ERR_ADD_LIFT, SessionOrderCopy.LIFT_NAME_REQUIRED)
            pickerWrites.fail(ticket, SessionOrderCopy.LIFT_NAME_REQUIRED)
            return
        }
        viewModelScope.launch {
            try {
                when (val result = container.exerciseRepository.createCustom(name, muscleGroup, loadType = loadType)) {
                    is SaveExerciseResult.DuplicateName -> {
                        error.fail(ERR_ADD_LIFT, DUPLICATE_NAME_MESSAGE)
                        pickerWrites.fail(ticket, DUPLICATE_NAME_MESSAGE, existing = result.existing)
                    }
                    is SaveExerciseResult.MissingMuscle -> {
                        error.fail(ERR_ADD_LIFT, MuscleGroups.MISSING_MESSAGE)
                        pickerWrites.fail(ticket, MuscleGroups.MISSING_MESSAGE)
                    }
                    is SaveExerciseResult.Saved -> {
                        extraCatalog.value = LiftCart.mergeSources(extraCatalog.value, listOf(result.exercise))
                        catalog.value = LiftCart.mergeSources(catalog.value, extraCatalog.value)
                        if (selectHere) addPicked(result.exercise, day)
                        pickerWrites.succeed(ticket, created = true)
                    }
                }
            } catch (thrown: kotlinx.coroutines.CancellationException) {
                pickerWrites.cancel(ticket)
                throw thrown
            } catch (thrown: Exception) {
                error.fail(ERR_ADD_LIFT, SessionOrderCopy.CREATE_LIFT_FAILED)
                pickerWrites.fail(ticket, SessionOrderCopy.CREATE_LIFT_FAILED) { createAndSelect(name, muscleGroup, loadType, day, selectHere) }
            }
        }
    }

    fun moveLift(itemId: String, direction: Int) {
        if (applying.value || pickerWrites.busy || formRecovery?.status?.value?.ready == false) return
        val day = selectedDay.value
        days.value = days.value + (day to CustomWeekPolicy.move(days.value[day].orEmpty(), itemId, direction))
        persistDraft()
    }

    fun removeLift(itemId: String) {
        if (applying.value || pickerWrites.busy || formRecovery?.status?.value?.ready == false) return
        forgetTargetRule(itemId)
        val day = selectedDay.value
        days.value = days.value + (day to days.value[day].orEmpty().filterNot { it.id == itemId })
        persistDraft()
    }

    /**
     * The four target boxes of one card, as the owner left them.
     *
     * @param invalidReason non-null when a box holds text the week cannot hold — "8.5" reps, a
     * negative load — carrying the rule it broke. Nothing is staged from such a box, so the
     * lift keeps what it had; but the reason is remembered, because a card reading 8.5 while
     * the week holds 5 is the quiet mismatch UX06 exists to stop. [confirm] refuses until it
     * reads as something the week can hold. The same card, and so the same rules, as the
     * routine editor.
     */
    fun stageTargets(
        itemId: String,
        sets: Int?,
        reps: Int?,
        rest: Int?,
        weightKg: Double?,
        invalidReason: String? = null,
        seconds: Int? = null,
        secondsMax: Int? = null,
    ) {
        if (applying.value || pickerWrites.busy || formRecovery?.status?.value?.ready == false) return
        if (invalidReason == null) {
            invalidTargets.remove(itemId)
            moveTargetRuleBanner()
        } else {
            invalidTargets[itemId] = invalidReason
            persistDraft()
            return
        }
        val day = selectedDay.value
        days.value = days.value + (
            day to CustomWeekPolicy.updateTargets(
                days.value[day].orEmpty(),
                itemId,
                sets,
                reps,
                rest,
                weightKg,
                seconds,
                secondsMax,
            )
            )
        persistDraft()
    }

    fun confirm() {
        val started = error.mark()
        if (applying.value || pickerWrites.busy || !CustomWeekPolicy.canConfirm(days.value)) return
        if (formRecovery?.status?.value?.ready == false || pickerRecovery?.create?.status?.value?.busy == true) return
        // A card still showing a value the week cannot hold is unfinished work, not a value to
        // walk past: applying would write the number underneath it instead. Say the rule and stay.
        invalidTargets.values.firstOrNull()?.let { rule ->
            error.fail(source = ERR_TARGETS, message = rule)
            return
        }
        applying.value = true
        showPicker.value = false
        val snapshot = days.value
        viewModelScope.launch {
            if (formRecovery?.beginSubmission() == false) { applying.value = false; return@launch }
            val result = container.onboardingApplier.applyCustom(
                days = snapshot,
                weekStart = weekStart.value,
                today = civilToday().toLocalDate(),
                answers = guidedAnswers,
            )
            applying.value = false
            when (result) {
                is ApplyPlanResult.Applied -> {
                    pendingWeightUnit?.let { unit ->
                        runCatchingCancellable { container.preferencesRepository.setWeightUnit(unit) }
                            .onFailure { AppLog.w(TAG, "Saving the weight unit failed", it) }
                    }
                    runCatchingCancellable {
                        container.plannerRepository.publishPinnedWeek(
                            weekStart.value,
                            todayEpochDay(),
                        )
                    }.onFailure { AppLog.w(TAG, "Publishing the custom week to Home failed", it) }
                    error.clearFrom(source = ERR_CONFIRM, before = started)
                    // Getting past the gate above means no box was unreadable at the tap, so
                    // this success answers the rule an earlier Confirm raised as well. Anything
                    // staged since the tap is newer information and survives the mark.
                    error.clearFrom(source = ERR_TARGETS, before = started)
                    formRecovery?.accepted()
                    _finished.value = true
                }
                is ApplyPlanResult.Failed ->
                    error.fail(source = ERR_CONFIRM, message = result.message)
            }
        }
    }

    fun dismissError() {
        error.dismiss()
    }

    /**
     * Forget one card's complaint. A card that has gone — removed from the day, or untapped
     * in the picker — takes its rule with it: leaving the entry behind would block Confirm on
     * a rule with no box left to fix, a dead end with no way out of it.
     */
    /**
     * The card's boxes went away — folded shut, or the lift tapped off the day — so the rule
     * one of them broke goes with them. Holding it would refuse Confirm for a box that is no
     * longer on screen, with nothing to correct.
     */
    fun forgetTargetRule(itemId: String) {
        if (invalidTargets.remove(itemId) == null) return
        moveTargetRuleBanner()
    }

    /**
     * Point the banner at whatever is still unreadable, after [invalidTargets] changed.
     *
     * The refusal belongs to [ERR_TARGETS], so [ErrorSlot.clearFrom] answers that family and
     * nothing else — where this used to ask whether the message text was one of the target
     * rules. If the clear took, the banner was this family's and the next unreadable card
     * takes it over. If it did not, an add-lift or create-lift failure is showing: a different
     * message, and not this box's to dismiss, so it stays.
     */
    private fun moveTargetRuleBanner() {
        val showing = error.message
        error.clearFrom(source = ERR_TARGETS)
        if (showing == null || error.message != null) return
        invalidTargets.values.firstOrNull()?.let { rule ->
            error.fail(source = ERR_TARGETS, message = rule)
        }
    }

    private fun persistDraft() {
        persistSavedDraft()
        formRecovery?.changed()
    }
    private fun persistSavedDraft() {
        savedDraft.write(
            selected = selectedDay.value,
            days = days.value,
            userPicked = userPickedDay,
        )
    }

    private data class WeekCore(
        val selectedDay: Weekday,
        val days: Map<Weekday, List<CustomWeekLift>>,
        val weekStart: Weekday,
        val preferredDays: Set<Weekday>,
        val query: String,
    )

    private data class WeekExtras(
        val results: List<Exercise>,
        val showPicker: Boolean,
        val applying: Boolean,
        val error: String?,
    )
}
