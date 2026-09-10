package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.repository.ApplyPlanResult
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.domain.CustomWeekLift
import com.sinura.personaltrainer.domain.CustomWeekPolicy
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseOrdering
import com.sinura.personaltrainer.domain.LiftCart
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.ui.library.DUPLICATE_NAME_MESSAGE
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
class CustomWeekViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
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
        if (applying.value) return
        userPickedDay = true
        selectedDay.value = day
        persistDraft()
    }

    fun seedFromGuided(
        preferred: Set<Weekday>,
        answers: OnboardingAnswers?,
        unit: WeightUnit?,
    ) {
        guidedAnswers = answers
        pendingWeightUnit = unit
        preferredDays.value = preferred
        if (!userPickedDay) {
            selectedDay.value = CustomWeekPolicy.initialSelectedDay(weekStart.value, preferred)
            persistDraft()
        }
    }

    fun setPickerVisible(visible: Boolean) {
        if (visible && applying.value) return
        showPicker.value = visible
        // Only the typed query. The lifts are on the day already — losing a cart to a tap
        // outside the sheet is exactly what this screen no longer does.
        if (!visible) searchQuery.value = ""
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
        if (applying.value) return
        val day = selectedDay.value
        val existing = days.value[day].orEmpty()
        val stored = existing.firstOrNull { it.exercise.id == exercise.id }
        val next = if (stored != null) {
            // The tap takes the lift off the day, so the card and its complaint go together.
            forgetTargetRule(stored.id)
            existing.filterNot { it.id == stored.id }
        } else {
            CustomWeekPolicy.addLifts(existing, listOf(exercise)) { UUID.randomUUID().toString() }
        }
        days.value = days.value + (day to next)
        error.clearFrom(source = ERR_ADD_LIFT)
        persistDraft()
    }

    /** A lift created inside the picker joins the day; it is never a tap that removes one. */
    private fun addPicked(exercise: Exercise) {
        if (applying.value) return
        val day = selectedDay.value
        val existing = days.value[day].orEmpty()
        if (existing.any { it.exercise.id == exercise.id }) return
        togglePicked(exercise)
    }

    fun createAndSelect(name: String, muscleGroup: String) {
        if (applying.value) return
        viewModelScope.launch {
            if (name.isBlank()) {
                error.fail(source = ERR_ADD_LIFT, message = SessionOrderCopy.LIFT_NAME_REQUIRED)
                return@launch
            }
            runCatchingCancellable {
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
                        catalog.value = LiftCart.mergeSources(catalog.value, extraCatalog.value)
                        if (showPicker.value) {
                            addPicked(result.exercise)
                        }
                    }
                }
            }.onFailure {
                AppLog.w(TAG, "createAndSelect failed", it)
                error.fail(source = ERR_ADD_LIFT, message = SessionOrderCopy.CREATE_LIFT_FAILED)
            }
        }
    }

    fun moveLift(itemId: String, direction: Int) {
        if (applying.value) return
        val day = selectedDay.value
        days.value = days.value + (day to CustomWeekPolicy.move(days.value[day].orEmpty(), itemId, direction))
        persistDraft()
    }

    fun removeLift(itemId: String) {
        if (applying.value) return
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
    ) {
        if (applying.value) return
        if (invalidReason == null) {
            invalidTargets.remove(itemId)
            // The box was fixed, so its complaint must not outlive it — but fixing one card
            // does not answer for another, so the banner moves to whatever is still
            // unreadable rather than clearing outright.
            moveTargetRuleBanner()
        } else {
            invalidTargets[itemId] = invalidReason
        }
        val day = selectedDay.value
        days.value = days.value + (
            day to CustomWeekPolicy.updateTargets(days.value[day].orEmpty(), itemId, sets, reps, rest, weightKg)
            )
        persistDraft()
    }

    fun confirm() {
        val started = error.mark()
        if (applying.value || !CustomWeekPolicy.canConfirm(days.value)) return
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
