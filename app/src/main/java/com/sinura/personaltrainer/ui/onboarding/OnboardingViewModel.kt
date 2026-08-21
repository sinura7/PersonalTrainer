package com.sinura.personaltrainer.ui.onboarding

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.data.repository.ApplyPlanResult
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.PlanBlueprint
import com.sinura.personaltrainer.domain.RoutineGenerator
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.TrainingPlace
import com.sinura.personaltrainer.logging.AppLog
import java.time.DayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PT/OnboardingVM"

/**
 * The questions, in order.
 *
 * An enum rather than an index so the screen cannot be on step 7 of 6, and so the order is
 * stated in one readable place instead of emerging from a `when` on an Int.
 */
enum class OnboardingStep {
    /** Build it for me, or I'll do it myself. Everything after this is the guided path. */
    FORK,
    EXPERIENCE,
    DAYS_PER_WEEK,
    WHICH_DAYS,
    PLACE,
    GOAL,
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
class OnboardingViewModel(application: Application) : AppViewModel(application) {
    private val step = MutableStateFlow(OnboardingStep.FORK)
    private val answers = MutableStateFlow(OnboardingAnswers())
    private val applying = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val existingProgram = MutableStateFlow(false)
    private val catalog = MutableStateFlow<List<Exercise>>(emptyList())

    val uiState: StateFlow<OnboardingUiState> = combine(
        step,
        answers,
        catalog,
        combine(applying, error, existingProgram) { busy, err, existing ->
            Triple(busy, err, existing)
        },
    ) { currentStep, currentAnswers, exercises, flags ->
        OnboardingUiState(
            step = currentStep,
            answers = currentAnswers,
            // Regenerated rather than cached: it is cheap, and a preview that lags one answer
            // behind is worse than no preview, because it is confidently wrong.
            preview = if (exercises.isEmpty()) {
                null
            } else {
                RoutineGenerator.generate(currentAnswers, exercises)
            },
            applying = flags.first,
            error = flags.second,
            existingProgram = flags.third,
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
            catalog.value = runCatching { container.exerciseRepository.observeAll().first() }
                .onFailure { AppLog.w(TAG, "Reading the catalog for setup failed", it) }
                .getOrDefault(emptyList())
            existingProgram.value = runCatching { container.onboardingApplier.hasExistingProgram() }
                .getOrDefault(false)
        }
    }

    fun back(): Boolean {
        val index = OnboardingStep.entries.indexOf(step.value)
        if (index <= 0) return false
        step.value = OnboardingStep.entries[index - 1]
        return true
    }

    fun next() {
        val index = OnboardingStep.entries.indexOf(step.value)
        if (index < OnboardingStep.entries.lastIndex) {
            step.value = OnboardingStep.entries[index + 1]
        }
    }

    fun beginGuided() {
        step.value = OnboardingStep.EXPERIENCE
    }

    fun setExperience(value: TrainingAge) = advance { it.copy(trainingAge = value) }

    fun setDaysPerWeek(value: Int) = update { current ->
        // Picking fewer days than are already selected would leave a week that contradicts the
        // answer, so the selection is trimmed to fit rather than silently overriding it later.
        val trimmed = if (current.preferredDays.size > value) emptySet() else current.preferredDays
        current.copy(daysPerWeek = value, preferredDays = trimmed)
    }

    fun toggleDay(day: DayOfWeek) = update { current ->
        val picked = current.preferredDays
        when {
            day in picked -> current.copy(preferredDays = picked - day)
            // Silently dropping the oldest pick would make the taps feel broken. Refusing the
            // extra one is honest, and the count beside the question says why.
            picked.size >= current.daysPerWeek -> current
            else -> current.copy(preferredDays = picked + day)
        }
    }

    fun setPlace(value: TrainingPlace) = advance { it.copy(place = value) }

    fun setGoal(value: TrainingGoal) = advance { it.copy(goal = value) }

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
            )
            applying.value = false
            when (result) {
                is ApplyPlanResult.Applied -> {
                    error.value = null
                    _finished.value = true
                }
                is ApplyPlanResult.Failed -> error.value = result.message
            }
        }
    }

    /**
     * Leaves without a plan, and does not ask again.
     *
     * Skipping is a decision, not an accident — someone who wants to build their own routines
     * has said so, and re-presenting the questionnaire on next launch would be the app refusing
     * to hear it.
     */
    fun skip() {
        viewModelScope.launch {
            runCatching { container.preferencesRepository.setOnboardingComplete(true) }
                .onFailure { AppLog.w(TAG, "Marking setup complete failed", it) }
            _finished.value = true
        }
    }

    private inline fun update(transform: (OnboardingAnswers) -> OnboardingAnswers) {
        answers.value = transform(answers.value)
        error.value = null
    }

    /** Answer and move on. Single-choice questions do not need a separate Next tap. */
    private inline fun advance(transform: (OnboardingAnswers) -> OnboardingAnswers) {
        update(transform)
        next()
    }
}
