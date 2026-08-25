package com.sinura.personaltrainer.ui.onboarding

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.repository.ApplyPlanResult
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.PlanBlueprint
import com.sinura.personaltrainer.domain.RoutineGenerator
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingEmphasis
import com.sinura.personaltrainer.domain.TrainingFocus
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.TrainingPlace
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.domain.Weekday
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PT/OnboardingVM"

internal const val CATALOG_MISSING_MESSAGE =
    "Couldn't load the lift catalog. Try again, or build your own."

/**
 * The questions, in order.
 *
 * An enum rather than an index so the screen cannot be on step 7 of 6, and so the order is
 * stated in one readable place instead of emerging from a `when` on an Int.
 */
enum class OnboardingStep {
    /** Build it for me, or I'll do it myself. Everything after this is the guided path. */
    FORK,
    FOCUS,
    EXPERIENCE,
    DAYS_PER_WEEK,
    WHICH_DAYS,
    PLACE,
    GOAL,
    EMPHASIS,
    BODYWEIGHT,
    PREVIEW,
    ;

    val isQuestion: Boolean get() = this != FORK && this != PREVIEW
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.FORK,
    val answers: OnboardingAnswers = OnboardingAnswers(),
    /** Rebuilt on every answer, so the preview is never stale. Null until the catalog loads. */
    val preview: PlanBlueprint? = null,
    val applying: Boolean = false,
    val existingProgram: Boolean = false,
    val error: String? = null,
    /** Display unit for the bodyweight wheel. Pending until apply; abandon leaves DataStore. */
    val weightUnit: WeightUnit = WeightUnit.LBS,
) {
    /** 1-based position among the questions, for the progress line. Zero on fork and preview. */
    val questionNumber: Int
        get() = if (!step.isQuestion) 0 else {
            OnboardingStep.entries.filter { it.isQuestion }.indexOf(step) + 1
        }

    val questionCount: Int get() = OnboardingStep.entries.count { it.isQuestion }
}

/**
 * The guided setup.
 *
 * Holds the answers, regenerates the preview on every change, and applies it once. It never
 * writes anything until "Use this plan" — so backing out of setup leaves an app that is exactly
 * as it was, and the lifter can see the actual lifts before a single routine exists.
 */
class OnboardingViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val step = MutableStateFlow(OnboardingStep.FORK)
    private val answers = MutableStateFlow(OnboardingAnswers())
    private val applying = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val existingProgram = MutableStateFlow(false)
    private val catalog = MutableStateFlow<List<Exercise>>(emptyList())

    /**
     * The lifter's stored first day of the week.
     *
     * Setup never asks about it and must never write it, but it does have to *read* it: the
     * generator lays the week out starting from this day, so a Sunday-week lifter generating
     * against a Monday default gets their sessions on the wrong weekdays.
     */
    private val weekStart = MutableStateFlow(SchedulePreferences.DEFAULT_WEEK_START)
    private val storedWeightUnit = MutableStateFlow(WeightUnit.LBS)
    private val pendingWeightUnit = MutableStateFlow<WeightUnit?>(null)

    val uiState: StateFlow<OnboardingUiState> = combine(
        step,
        answers,
        catalog,
        combine(
            combine(applying, error, existingProgram, weekStart) { busy, err, existing, start ->
                Quad(busy, err, existing, start)
            },
            storedWeightUnit,
            pendingWeightUnit,
        ) { quad, stored, pending ->
            Flags(
                applying = quad.applying,
                error = quad.error,
                existingProgram = quad.existingProgram,
                weekStart = quad.weekStart,
                weightUnit = pending ?: stored,
            )
        },
    ) { currentStep, currentAnswers, exercises, flags ->
        OnboardingUiState(
            step = currentStep,
            answers = currentAnswers,
            // Regenerated rather than cached: it is cheap, and a preview that lags one answer
            // behind is worse than no preview, because it is confidently wrong.
            preview = when {
                currentAnswers.focus == TrainingFocus.CARDIO ->
                    RoutineGenerator.generate(currentAnswers, exercises, flags.weekStart)
                exercises.isEmpty() -> null
                else -> RoutineGenerator.generate(currentAnswers, exercises, flags.weekStart)
            },
            applying = flags.applying,
            error = flags.error,
            existingProgram = flags.existingProgram,
            weightUnit = flags.weightUnit,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OnboardingUiState(),
    )

    /** Set once the plan is written, so the host can leave. Held as state, not a callback. */
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    init {
        viewModelScope.launch {
            // Collected for as long as this view model lives, not read once.
            //
            // `observeAll().first()` took whatever the catalog tables held at the instant of
            // subscription — and on the launch this whole phase exists for, that is nothing.
            // A fresh install seeds the catalog from Application.onCreate, fire and forget on
            // an IO dispatcher, while the gate puts this screen up on the first composed
            // frame. Room emits the empty table immediately, `first()` takes it, and the field
            // is never read again: the preview stays null, the last step of setup says
            // "Building it…" forever, and "Use this plan" stays disabled with no way to
            // finish. Collecting means the seed's arrival is what fills the preview in.
            container.exerciseRepository.observeAll()
                .catch { thrown ->
                    AppLog.w(TAG, "Reading the catalog for setup failed", thrown)
                    emit(emptyList())
                }
                .collect { exercises -> catalog.value = exercises }
        }
        viewModelScope.launch {
            // Its own coroutine: the collection above never returns, so anything sequenced
            // after it would never run.
            existingProgram.value = runCatchingCancellable { container.onboardingApplier.hasExistingProgram() }
                .getOrDefault(false)
        }
        viewModelScope.launch {
            // Collected rather than snapshotted, so that a week start changed in Settings while
            // setup is open regenerates the preview instead of leaving it a day out.
            container.preferencesRepository.schedulePreferences
                .catch { thrown ->
                    AppLog.w(TAG, "Reading the week start for setup failed", thrown)
                }
                .collect { preferences -> weekStart.value = preferences.weekStart }
        }
        viewModelScope.launch {
            container.preferencesRepository.weightUnit
                .catch { thrown ->
                    AppLog.w(TAG, "Reading the weight unit for setup failed", thrown)
                }
                .collect { unit -> storedWeightUnit.value = unit }
        }
    }

    fun back(): Boolean {
        val index = OnboardingStep.entries.indexOf(step.value)
        if (index <= 0) {
            // Settings re-run flipped the gate to SETUP. Back on the fork used to
            // call onFinished as a no-op and leave them trapped. A first install
            // still has no program and stays here until they pick a path.
            leaveExistingProgram()
            return false
        }
        step.value = OnboardingStep.entries[index - 1]
        return true
    }

    private fun leaveExistingProgram() {
        viewModelScope.launch {
            val exists = runCatchingCancellable { container.onboardingApplier.hasExistingProgram() }
                .getOrDefault(existingProgram.value)
            if (!exists) return@launch
            runCatchingCancellable { container.preferencesRepository.setOnboardingComplete(true) }
                .onFailure { AppLog.w(TAG, "Restoring setup complete failed", it) }
            _finished.value = true
        }
    }

    fun next() {
        val index = OnboardingStep.entries.indexOf(step.value)
        if (index < OnboardingStep.entries.lastIndex) {
            step.value = OnboardingStep.entries[index + 1]
            if (step.value == OnboardingStep.PREVIEW && catalog.value.isEmpty()) {
                retryCatalog()
            }
        }
    }

    /**
     * Re-runs the catalog seed and waits for Room to emit what it now holds.
     *
     * Setup can open before Application finishes seeding. That is normal, and
     * collecting `observeAll()` fills the preview when the rows arrive. A seed
     * that failed is not normal: the preview stays null and "Use this plan"
     * stays disabled. This is the recovery — not a timeout that guesses.
     */
    fun retryCatalog() {
        viewModelScope.launch {
            error.value = null
            val seeded = runCatchingCancellable {
                container.dbMaintenance.seedCatalog()
                container.exerciseRepository.observeAll().first()
            }
            seeded.onSuccess { exercises ->
                catalog.value = exercises
                if (exercises.isEmpty()) error.value = CATALOG_MISSING_MESSAGE
            }.onFailure { thrown ->
                AppLog.w(TAG, "Retrying the catalog seed failed", thrown)
                error.value = CATALOG_MISSING_MESSAGE
            }
        }
    }

    fun beginGuided() {
        step.value = OnboardingStep.FOCUS
    }

    fun setFocus(value: TrainingFocus) = advance { it.copy(focus = value) }

    fun setExperience(value: TrainingAge) = advance { it.copy(trainingAge = value) }

    fun setDaysPerWeek(value: Int) = update { it.withDaysPerWeek(value) }

    fun toggleDay(day: Weekday) = update { current ->
        val picked = current.preferredDays
        when {
            day in picked -> current.copy(preferredDays = picked - day)
            // Silently dropping the oldest pick would make the taps feel broken. Refusing the
            // extra one is honest, and the count beside the question says why.
            picked.size >= current.daysPerWeek -> current
            else -> current.copy(preferredDays = picked + day)
        }
    }

    fun togglePlace(value: TrainingPlace) = update { it.withToggledPlace(value) }

    fun setWeightUnit(unit: WeightUnit) {
        pendingWeightUnit.value = unit
    }

    fun setGoal(value: TrainingGoal) = advance { it.copy(goal = value) }

    fun setEmphasis(value: TrainingEmphasis) = advance { it.copy(emphasis = value) }

    fun setBodyweight(kg: Double?) = update { it.copy(bodyweightKg = kg) }

    /**
     * Writes the plan.
     *
     * The only method here that touches the database, and it is idempotent by accident rather
     * than design — so it is guarded, because a double tap on "Use this plan" would otherwise
     * build the program twice.
     */
    fun applyPlan() {
        if (applying.value) return
        val blueprint = uiState.value.preview ?: return
        applying.value = true
        viewModelScope.launch {
            val result = container.onboardingApplier.apply(
                answers = answers.value,
                blueprint = blueprint,
                catalog = catalog.value,
                weekStart = weekStart.value,
                today = LocalDate.now(),
            )
            applying.value = false
            when (result) {
                is ApplyPlanResult.Applied -> {
                    pendingWeightUnit.value?.let { unit ->
                        runCatchingCancellable { container.preferencesRepository.setWeightUnit(unit) }
                            .onFailure { AppLog.w(TAG, "Saving the weight unit failed", it) }
                    }
                    runCatchingCancellable {
                        container.plannerRepository.publishPinnedWeek(
                            weekStart.value,
                            LocalDate.now().toEpochDay(),
                        )
                    }.onFailure { AppLog.w(TAG, "Publishing the plan to Home failed", it) }
                    error.value = null
                    _finished.value = true
                }
                is ApplyPlanResult.Failed -> error.value = result.message
            }
        }
    }

    private fun update(transform: (OnboardingAnswers) -> OnboardingAnswers) {
        answers.value = transform(answers.value)
        error.value = null
    }

    /** Answer and move on. Single-choice questions do not need a separate Next tap. */
    private fun advance(transform: (OnboardingAnswers) -> OnboardingAnswers) {
        update(transform)
        next()
    }

    private data class Quad(
        val applying: Boolean,
        val error: String?,
        val existingProgram: Boolean,
        val weekStart: Weekday,
    )

    /**
     * The flags that ride alongside the answers. A named type rather than a Triple, because
     * `flags.first` said nothing about which of three booleans it was.
     */
    private data class Flags(
        val applying: Boolean,
        val error: String?,
        val existingProgram: Boolean,
        val weekStart: Weekday,
        val weightUnit: WeightUnit,
    )
}
