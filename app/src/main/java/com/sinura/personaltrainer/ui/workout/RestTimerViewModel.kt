package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.LighterWeek
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
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
    private val restTotal = MutableStateFlow(RestTimerPreferences.DEFAULT_SECONDS)
    private val hint = MutableStateFlow<ProgressionHint?>(null)
    private val lighterWeek = MutableStateFlow(false)
    private val sessionResolved = MutableStateFlow(false)

    private val session: StateFlow<WorkoutSession?> =
        container.workoutRepository.observeSession(sessionId)
            .onEach { sessionResolved.value = true }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        if (sessionId.isBlank()) {
            sessionResolved.value = true
        }
        viewModelScope.launch {
            runCatchingCancellable {
                val current = session.value ?: run {
                    sessionResolved.first { it }
                    session.value
                }
                val prefs = container.preferencesRepository.restTimerPreferences.first()
                val exerciseId = resolveExerciseId(current)
                val planned = current?.exercises?.firstOrNull { it.exercise.id == exerciseId }
                restTotal.value = RestTimer.secondsToStart(planned?.restSeconds, prefs)
                if (current != null && exerciseId != null && !current.isFinished) {
                    hint.value = loadHint(current, exerciseId)
                }
            }.onFailure { AppLog.w(TAG, "Loading rest floor context failed", it) }
        }
        viewModelScope.launch {
            container.preferencesRepository.restTimerPreferences
                .map { it.lastPresetSeconds }
                .distinctUntilChanged()
                .drop(1)
                .collect { last ->
                    if (last != null && !restTimer.snapshot.value.running) {
                        restTotal.value = last
                    }
                }
        }
    }

    val uiState: StateFlow<RestTimerScreenState> = combine(
        session,
        sessionResolved,
        combine(
            restTimer.remainingSeconds,
            restTimer.snapshot,
            restTotal,
            restTimer.lastCompletedTimerId,
            restTimer.persistenceHealthy,
        ) { remaining, snapshot, planned, completedId, healthy ->
            RestTimerUiState(
                remainingSeconds = remaining,
                totalSeconds = if (snapshot.running) snapshot.totalSeconds else planned,
                running = snapshot.running,
                completedTimerId = completedId,
                persistenceHealthy = healthy,
            )
        },
        combine(hint, lighterWeek, container.preferencesRepository.weightUnit) { currentHint, lighter, unit ->
            Triple(currentHint, lighter, unit)
        },
    ) { current, resolved, rest, extras ->
        val (currentHint, lighter, unit) = extras
        val missing = current == null || current.isFinished
        RestTimerScreenState(
            loadState = when {
                !resolved && sessionId.isNotBlank() -> SessionLoadState.LOADING
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
                )
                val loadClass = exerciseId?.let { current.loadClassOf(it) } ?: LoadClass.LOADED
                RestFloorCopy.context(
                    session = current,
                    selectedExerciseId = exerciseId,
                    unit = unit,
                    nextLine = rec?.let { SetMicroRecCopy.line(it, loadClass, unit) },
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

    fun adjustRest(deltaSeconds: Int) {
        restTimer.adjust(deltaSeconds)
    }

    fun selectRestDuration(seconds: Int) {
        restTotal.value = seconds
        viewModelScope.launch {
            container.preferencesRepository.setLastRestPresetSeconds(seconds)
        }
    }

    fun selectCustomRest(input: String): Boolean {
        val seconds = RestTimer.parseCustom(input) ?: return false
        selectRestDuration(seconds)
        return true
    }

    fun startSelectedRest() {
        val seconds = restTotal.value.coerceIn(
            RestTimerPreferences.MIN_SECONDS,
            RestTimerPreferences.MAX_SECONDS,
        )
        viewModelScope.launch {
            container.preferencesRepository.setLastRestPresetSeconds(seconds)
            container.preferencesRepository.markRestAlarmEligible()
            restTimer.start(seconds, sessionId)
        }
    }

    private fun resolveExerciseId(current: WorkoutSession?): String? {
        val preferred = container.workoutDraftCache.get(sessionId)?.exerciseId
        return current?.resolveSelectedExerciseId(preferred)
    }

    private suspend fun loadHint(current: WorkoutSession, exerciseId: String): ProgressionHint? {
        val planned = current.exercises.firstOrNull { it.exercise.id == exerciseId }
        val schedule = container.preferencesRepository.schedulePreferences.first()
        val thisWeek = LighterWeek.weekStartEpochDay(
            civilToday(),
            schedule.weekStart,
        )
        val lighter = LighterWeek.isCurrent(
            container.preferencesRepository.lighterWeekStartEpochDay.first(),
            thisWeek,
        )
        lighterWeek.value = lighter
        val unit: WeightUnit = container.preferencesRepository.weightUnit.first()
        return container.workoutRepository.progressionFor(
            exerciseId = exerciseId,
            exerciseName = planned?.exercise?.name ?: "",
            targetReps = planned?.targetReps ?: 5,
            excludeSessionId = sessionId,
            loadType = planned?.exercise?.loadType,
            unit = unit,
            lighterWeek = lighter,
        )
    }
}
