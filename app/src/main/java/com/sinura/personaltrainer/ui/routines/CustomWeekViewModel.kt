package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.repository.ApplyPlanResult
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.domain.CustomWeekLift
import com.sinura.personaltrainer.domain.CustomWeekPolicy
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.ui.library.DUPLICATE_NAME_MESSAGE
import com.sinura.personaltrainer.util.runCatchingCancellable
import java.time.DayOfWeek
import java.time.LocalDate
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
    val selectedDay: DayOfWeek = DayOfWeek.MONDAY,
    val days: Map<DayOfWeek, List<CustomWeekLift>> = emptyMap(),
    val weekStart: DayOfWeek = SchedulePreferences.DEFAULT_WEEK_START,
    val preferredDays: Set<DayOfWeek> = emptySet(),
    val searchQuery: String = "",
    val searchResults: List<Exercise> = emptyList(),
    val catalog: List<Exercise> = emptyList(),
    val showPicker: Boolean = false,
    val pendingAddIds: Set<String> = emptySet(),
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
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val selectedDay = MutableStateFlow(SchedulePreferences.DEFAULT_WEEK_START)
    private val days = MutableStateFlow<Map<DayOfWeek, List<CustomWeekLift>>>(emptyMap())
    private val weekStart = MutableStateFlow(SchedulePreferences.DEFAULT_WEEK_START)
    private val preferredDays = MutableStateFlow<Set<DayOfWeek>>(emptySet())
    private val searchQuery = MutableStateFlow("")
    private val showPicker = MutableStateFlow(false)
    private val pendingAddIds = MutableStateFlow<Set<String>>(emptySet())
    private val applying = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val catalog = MutableStateFlow<List<Exercise>>(emptyList())
    private var guidedAnswers: OnboardingAnswers? = null
    private var pendingWeightUnit: WeightUnit? = null
    private var userPickedDay = false

    private val resultsFlow = searchQuery.flatMapLatest { query ->
        container.exerciseRepository.search(query)
    }

    val uiState: StateFlow<CustomWeekUiState> = combine(
        combine(selectedDay, days, weekStart, preferredDays, searchQuery) { day, draft, start, preferred, query ->
            WeekCore(day, draft, start, preferred, query)
        },
        combine(resultsFlow, showPicker, pendingAddIds, applying, error) { results, picker, pending, busy, err ->
            WeekExtras(results, picker, pending, busy, err)
        },
        catalog,
    ) { core, extras, lifts ->
        CustomWeekUiState(
            selectedDay = core.selectedDay,
            days = core.days,
            weekStart = core.weekStart,
            preferredDays = core.preferredDays,
            searchQuery = core.query,
            searchResults = extras.results,
            catalog = lifts,
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

    fun selectDay(day: DayOfWeek) {
        userPickedDay = true
        selectedDay.value = day
    }

    fun seedFromGuided(
        preferred: Set<DayOfWeek>,
        answers: OnboardingAnswers?,
        unit: WeightUnit?,
    ) {
        guidedAnswers = answers
        pendingWeightUnit = unit
        preferredDays.value = preferred
        if (!userPickedDay) {
            selectedDay.value = CustomWeekPolicy.initialSelectedDay(weekStart.value, preferred)
        }
    }

    fun setPickerVisible(visible: Boolean) {
        showPicker.value = visible
        if (!visible) {
            searchQuery.value = ""
            pendingAddIds.value = emptySet()
        }
    }

    fun onSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun togglePendingAdd(exercise: Exercise) {
        val current = pendingAddIds.value
        pendingAddIds.value = if (exercise.id in current) current - exercise.id else current + exercise.id
    }

    fun confirmPendingAdd() {
        val selected = pendingAddIds.value
        if (selected.isEmpty()) return
        val incoming = selected.mapNotNull { id -> catalog.value.firstOrNull { it.id == id } }
        val day = selectedDay.value
        val current = days.value[day].orEmpty()
        days.value = days.value + (day to CustomWeekPolicy.addLifts(current, incoming) { UUID.randomUUID().toString() })
        pendingAddIds.value = emptySet()
        showPicker.value = false
        searchQuery.value = ""
        error.value = null
    }

    fun createAndSelect(name: String, muscleGroup: String) {
        viewModelScope.launch {
            runCatchingCancellable {
                when (val result = container.exerciseRepository.createCustom(name, muscleGroup)) {
                    is SaveExerciseResult.DuplicateName ->
                        error.value = DUPLICATE_NAME_MESSAGE
                    is SaveExerciseResult.MissingMuscle ->
                        error.value = MuscleGroups.MISSING_MESSAGE
                    is SaveExerciseResult.Saved -> {
                        catalog.value = catalog.value + result.exercise
                        togglePendingAdd(result.exercise)
                    }
                }
            }.onFailure {
                AppLog.w(TAG, "createAndSelect failed", it)
                error.value = "Could not create that exercise. Try again."
            }
        }
    }

    fun moveLift(itemId: String, direction: Int) {
        val day = selectedDay.value
        days.value = days.value + (day to CustomWeekPolicy.move(days.value[day].orEmpty(), itemId, direction))
    }

    fun removeLift(itemId: String) {
        val day = selectedDay.value
        days.value = days.value + (day to days.value[day].orEmpty().filterNot { it.id == itemId })
    }

    fun stageTargets(itemId: String, sets: Int?, reps: Int?, rest: Int?) {
        val day = selectedDay.value
        days.value = days.value + (
            day to CustomWeekPolicy.updateTargets(days.value[day].orEmpty(), itemId, sets, reps, rest)
            )
    }

    fun confirm() {
        if (applying.value || !CustomWeekPolicy.canConfirm(days.value)) return
        applying.value = true
        viewModelScope.launch {
            val result = container.onboardingApplier.applyCustom(
                days = days.value,
                weekStart = weekStart.value,
                today = LocalDate.now(),
                answers = guidedAnswers,
            )
            applying.value = false
            when (result) {
                is ApplyPlanResult.Applied -> {
                    pendingWeightUnit?.let { unit ->
                        runCatchingCancellable { container.preferencesRepository.setWeightUnit(unit) }
                            .onFailure { AppLog.w(TAG, "Saving the weight unit failed", it) }
                    }
                    error.value = null
                    _finished.value = true
                }
                is ApplyPlanResult.Failed -> error.value = result.message
            }
        }
    }

    private data class WeekCore(
        val selectedDay: DayOfWeek,
        val days: Map<DayOfWeek, List<CustomWeekLift>>,
        val weekStart: DayOfWeek,
        val preferredDays: Set<DayOfWeek>,
        val query: String,
    )

    private data class WeekExtras(
        val results: List<Exercise>,
        val showPicker: Boolean,
        val pendingAddIds: Set<String>,
        val applying: Boolean,
        val error: String?,
    )
}
