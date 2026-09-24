package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.RestFloorContext
import com.sinura.personaltrainer.domain.RestFloorCopy
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "PT/RestTimerVM"

data class RestTimerScreenState(
    val loadState: SessionLoadState = SessionLoadState.LOADING,
    val rest: RestTimerUiState = RestTimerUiState(),
    val floor: RestFloorContext = RestFloorContext(
        exerciseName = null,
        lastSetLine = null,
        sessionTargetLine = null,
    ),
)

/**
 * Floor-page rest. Commands hit the same [com.sinura.personaltrainer.timer.RestTimerGateway]
 * as [ActiveWorkoutViewModel]. There is no second clock.
 */
class RestTimerViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()
    private val restTimer = container.restTimerController
    private val restTotal = MutableStateFlow(PlannedRest(seconds = RestTimerPreferences.DEFAULT_SECONDS, chosen = false))
    private val hint = MutableStateFlow<ProgressionHint?>(null)
    private val lighterWeek = MutableStateFlow(false)
    private val sessionReader = WorkoutSessionReader(container.workoutRepository, sessionId, viewModelScope)
    private val restCommands = RestCommands(container, viewModelScope, sessionId)
    private val hintLoader = ProgressionHintLoader(container, sessionId)

    init {
        viewModelScope.launch {
            runCatchingCancellable {
                val current = sessionReader.observations.first {
                    it.loadState == SessionLoadState.FOUND || it.loadState == SessionLoadState.MISSING
                }.session
                val prefs = container.preferencesRepository.restTimerPreferences.first()
                val exerciseId = resolveExerciseId(current)
                val planned = current?.exercises?.firstOrNull { it.exercise.id == exerciseId }
                if (current != null && exerciseId != null && !current.isFinished) {
                    hint.value = loadHint(current, exerciseId)
                }
                val unit = container.preferencesRepository.weightUnit.first()
                val coachPrefs = container.preferencesRepository.coachPreferences.first()
                val cached = container.workoutDraftCache.get(sessionId)
                val rec = workoutMicroRec(
                    session = current,
                    selectedExerciseId = exerciseId,
                    draft = ActiveExerciseDraft(
                        weightKg = cached?.weightKg ?: 0.0,
                        reps = (cached?.reps ?: 5).coerceAtLeast(1),
                        rpe = cached?.rpe,
                        isWarmup = cached?.isWarmup ?: false,
                    ),
                    hint = hint.value,
                    editingSetId = null,
                    lighterWeek = lighterWeek.value,
                    unit = unit,
                    nowMs = time.nowMillis(),
                    todayEpochDay = todayEpochDay(),
                    coachPrefs = coachPrefs,
                )
                val seeded = RestTimer.secondsToStart(
                    planned?.restSeconds,
                    prefs,
                    prescribedSeconds = rec?.restSeconds,
                )
                // The seed fills in the plan; it never replaces a length someone already picked.
                // This load can finish after a tap here or on the Log, and a plain write put the
                // coach's 2:30 back over the 1:45 just chosen (23 Sept).
                restTotal.update { plan -> if (plan.chosen) plan else PlannedRest(seconds = seeded, chosen = false) }
            }.onFailure { AppLog.w(TAG, "Loading rest floor context failed", it) }
        }
        viewModelScope.launch {
            restCommands.presetEchoes.collect { last ->
                restTotal.value = PlannedRest(seconds = last, chosen = true)
            }
        }
    }

    val uiState: StateFlow<RestTimerScreenState> = combine(
        sessionReader.observations,
        restCommands.restState(restTotal) { it.seconds },
        combine(
            hint,
            lighterWeek,
            container.preferencesRepository.weightUnit,
            container.preferencesRepository.coachPreferences,
        ) { currentHint, lighter, unit, coach ->
            RestFloorInputs(hint = currentHint, lighterWeek = lighter, unit = unit, coachPrefs = coach)
        },
    ) { read, rest, extras ->
        val current = read.session
        val currentHint = extras.hint
        val lighter = extras.lighterWeek
        val unit = extras.unit
        val missing = current == null || current.isFinished
        RestTimerScreenState(
            loadState = when {
                read.loadState != SessionLoadState.FOUND -> read.loadState
                missing -> SessionLoadState.MISSING
                else -> SessionLoadState.FOUND
            },
            rest = rest,
            floor = if (missing) {
                RestFloorContext(exerciseName = null, lastSetLine = null, sessionTargetLine = null)
            } else {
                val exerciseId = resolveExerciseId(current)
                val cached = container.workoutDraftCache.get(sessionId)
                val rec = workoutMicroRec(
                    session = current,
                    selectedExerciseId = exerciseId,
                    draft = ActiveExerciseDraft(
                        weightKg = cached?.weightKg ?: 0.0,
                        reps = (cached?.reps ?: 5).coerceAtLeast(1),
                        rpe = cached?.rpe,
                        isWarmup = cached?.isWarmup ?: false,
                    ),
                    hint = currentHint,
                    editingSetId = null,
                    lighterWeek = lighter,
                    unit = unit,
                    nowMs = time.nowMillis(),
                    todayEpochDay = todayEpochDay(),
                    // The rest page makes the same call as the floor, goal included.
                    coachPrefs = extras.coachPrefs,
                )
                val loadClass = exerciseId?.let { current.loadClassOf(it) } ?: LoadClass.LOADED
                RestFloorCopy.context(
                    session = current,
                    selectedExerciseId = exerciseId,
                    unit = unit,
                    nextLine = rec?.let { SetMicroRecCopy.line(it, loadClass, unit) },
                    prescribedSeconds = rec?.restSeconds,
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RestTimerScreenState(),
    )

    fun skipRest() {
        restTimer.stop()
    }

    fun retrySession() {
        sessionReader.retry()
    }

    fun adjustRest(deltaSeconds: Int) {
        restTimer.adjust(deltaSeconds)
    }

    fun selectRestDuration(seconds: Int) {
        restTotal.value = PlannedRest(seconds = seconds, chosen = true)
        restCommands.rememberPick(seconds)
    }

    fun selectCustomRest(input: String): Boolean {
        val seconds = RestTimer.parseCustom(input) ?: return false
        selectRestDuration(seconds)
        return true
    }

    fun startSelectedRest() {
        restCommands.startPlanned(restTotal.value.seconds)
    }

    fun acknowledgeRestBatteryHint() {
        restCommands.acknowledgeBatteryHint()
    }

    private fun resolveExerciseId(current: WorkoutSession?): String? {
        val preferred = container.workoutDraftCache.get(sessionId)?.exerciseId
        return current?.resolveSelectedExerciseId(preferred)
    }

    private suspend fun loadHint(current: WorkoutSession, exerciseId: String): ProgressionHint? {
        val planned = current.exercises.firstOrNull { it.exercise.id == exerciseId }
        val lighter = hintLoader.isLighterWeek(hintLoader.thisWeekStart())
        lighterWeek.value = lighter
        return hintLoader.progression(exerciseId, planned, lighter)
    }
}

/**
 * The length this page will start. [chosen] once someone picked it — here, or on the Log
 * through the shared last preset — and from then on the loader's seed leaves it alone.
 */
private data class PlannedRest(val seconds: Int, val chosen: Boolean)

/** The rest page's inputs to its coach call, beside the session and the clock. */
private data class RestFloorInputs(
    val hint: ProgressionHint?,
    val lighterWeek: Boolean,
    val unit: WeightUnit,
    val coachPrefs: CoachPreferences,
)
