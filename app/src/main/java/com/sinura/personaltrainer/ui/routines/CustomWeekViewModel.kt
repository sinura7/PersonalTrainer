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

data class CustomWeekUiState(
    val selectedDay: Weekday = Weekday.MONDAY,
    val days: Map<Weekday, List<CustomWeekLift>> = emptyMap(),
    val weekStart: Weekday = SchedulePreferences.DEFAULT_WEEK_START,
    val preferredDays: Set<Weekday> = emptySet(),
    val searchQuery: String = "",
    val searchResults: List<Exercise> = emptyList(),
    val catalog: List<Exercise> = emptyList(),
    val showPicker: Boolean = false,
    val pendingAddIds: List<String> = emptyList(),
    val applying: Boolean = false,
    val error: String? = null,
) {
    val selectedLifts: List<CustomWeekLift> get() = days[selectedDay].orEmpty()
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
    private val pendingAddIds = MutableStateFlow<List<String>>(emptyList())
    private val applying = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
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
        combine(resultsFlow, showPicker, pendingAddIds, applying, error) { results, picker, pending, busy, err ->
            WeekExtras(results, picker, pending, busy, err)
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
            pendingAddIds = extras.pendingAddIds,
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
        if (!visible) {
            searchQuery.value = ""
            pendingAddIds.value = emptyList()
        }
    }

    fun onSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun togglePendingAdd(exercise: Exercise) {
        if (applying.value) return
        pendingAddIds.value = LiftCart.toggle(pendingAddIds.value, exercise.id)
    }

    fun confirmPendingAdd() {
        if (applying.value) return
        val selected = LiftCart.sanitize(pendingAddIds.value)
        if (selected.isEmpty()) return
        val day = selectedDay.value
        val plan = LiftCart.planConfirm(
            order = selected,
            sources = LiftCart.mergeSources(
                LiftCart.mergeSources(catalog.value, extraCatalog.value),
                uiState.value.searchResults,
            ),
            already = days.value[day].orEmpty().map { it.exercise.id }.toSet(),
        )
        if (plan.blocked) {
            error.value = SessionOrderCopy.ADD_LIFT_FAILED
            return
        }
        pendingAddIds.value = emptyList()
        if (plan.toAdd.isNotEmpty()) {
            days.value = days.value + (day to CustomWeekPolicy.addLifts(days.value[day].orEmpty(), plan.toAdd) { UUID.randomUUID().toString() })
        }
        extraCatalog.value = extraCatalog.value.filter { extra ->
            extra.id !in plan.toAdd.map { it.id }.toSet()
        }
        showPicker.value = false
        searchQuery.value = ""
        error.value = null
        persistDraft()
    }

    fun createAndSelect(name: String, muscleGroup: String) {
        if (applying.value) return
        viewModelScope.launch {
            if (name.isBlank()) {
                error.value = SessionOrderCopy.LIFT_NAME_REQUIRED
                return@launch
            }
            runCatchingCancellable {
                when (val result = container.exerciseRepository.createCustom(name, muscleGroup)) {
                    is SaveExerciseResult.DuplicateName ->
                        error.value = DUPLICATE_NAME_MESSAGE
                    is SaveExerciseResult.MissingMuscle ->
                        error.value = MuscleGroups.MISSING_MESSAGE
                    is SaveExerciseResult.Saved -> {
                        extraCatalog.value = LiftCart.mergeSources(
                            extraCatalog.value,
                            listOf(result.exercise),
                        )
                        catalog.value = LiftCart.mergeSources(catalog.value, extraCatalog.value)
                        if (showPicker.value) {
                            togglePendingAdd(result.exercise)
                        }
                    }
                }
            }.onFailure {
                AppLog.w(TAG, "createAndSelect failed", it)
                error.value = SessionOrderCopy.CREATE_LIFT_FAILED
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
        val day = selectedDay.value
        days.value = days.value + (day to days.value[day].orEmpty().filterNot { it.id == itemId })
        persistDraft()
    }

    fun stageTargets(itemId: String, sets: Int?, reps: Int?, rest: Int?, weightKg: Double?) {
        if (applying.value) return
        val day = selectedDay.value
        days.value = days.value + (
            day to CustomWeekPolicy.updateTargets(days.value[day].orEmpty(), itemId, sets, reps, rest, weightKg)
            )
        persistDraft()
    }

    fun confirm() {
        if (applying.value || !CustomWeekPolicy.canConfirm(days.value)) return
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
                    error.value = null
                    _finished.value = true
                }
                is ApplyPlanResult.Failed -> error.value = result.message
            }
        }
    }

    fun dismissError() {
        error.value = null
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
        val pendingAddIds: List<String>,
        val applying: Boolean,
        val error: String?,
    )
}
