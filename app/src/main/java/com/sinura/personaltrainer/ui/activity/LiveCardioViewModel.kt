package com.sinura.personaltrainer.ui.activity

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CompleteTrainingOutcome
import com.sinura.personaltrainer.domain.DistanceUnit
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.timer.BootSession
import com.sinura.personaltrainer.timer.CardioElapsed
import com.sinura.personaltrainer.timer.PersistedCardioTimer
import com.sinura.personaltrainer.util.ErrorSlot
import com.sinura.personaltrainer.util.JvmTime
import com.sinura.personaltrainer.util.recoverWith
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [ErrorSlot] families: a success may clear only its own family's refusal. */
private const val ERR_FINISH = "finish"
private const val ERR_DISCARD = "discard"

/**
 * [missing] is a successful read that found no row. [finished] is a successful read of a
 * session whose clock has already stopped. [failed] is a read that threw: the session is
 * most likely still live and the read is what broke. Missing and finished used to be one
 * flag, so opening a finished session from History said the session was gone.
 */
data class LiveCardioUiState(
    val session: ActivitySession? = null,
    val missing: Boolean = false,
    val finished: Boolean = false,
    val failed: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val type: CardioType = CardioType.RUN,
    val indoor: Boolean = false,
    val distanceKm: String = "",
    /** The unit the distance box was typed in. Null until a number is entered. */
    val distanceEntryUnit: DistanceUnit? = null,
    /** Finish read [distanceKm] and could not: the rule it broke, shown under the box. */
    val distanceError: String? = null,
    val error: String? = null,
    val finishing: Boolean = false,
    /** D12: the clock is frozen; paused time is not counted. Read back from the timer row. */
    val paused: Boolean = false,
)

class LiveCardioViewModel @JvmOverloads constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
    private val clock: com.sinura.personaltrainer.domain.TimePort = JvmTime,
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClock: () -> Long = { System.currentTimeMillis() },
    private val bootCount: () -> Long = { BootSession.count(application) },
) : AppViewModel(application, container) {
    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()
    private val session = MutableStateFlow<ActivitySession?>(null)
    private val missing = MutableStateFlow(false)
    private val finished = MutableStateFlow(false)
    private val failed = MutableStateFlow(false)
    private val elapsedSeconds = MutableStateFlow(0L)
    private var lastToggleAtMs: Long? = null

    // The three inputs the owner can change while the clock runs. Mirrored into
    // SavedStateHandle on every change: the Room row holds what the session started as, and
    // writing a half-typed distance into it mid-session would mix committed and temporary
    // data (and bump the revision), so the draft lives here until finish() reads it.
    private val inputs = SavedStateCardioDraft(savedStateHandle)
    private val type = MutableStateFlow(inputs.read()?.type ?: CardioType.RUN)
    private val indoor = MutableStateFlow(inputs.read()?.indoor ?: false)
    private val distanceKm = MutableStateFlow(inputs.read()?.distanceKm.orEmpty())
    private val distanceEntryUnit = MutableStateFlow(inputs.read()?.distanceUnit)
    /**
     * The distance box's own complaint, kept separate from [error] on purpose (UX06).
     * [ErrorSlot] holds ONE action failure at a time; a field rule is not an action failure —
     * it belongs under its box, it is answered by retyping rather than by retrying, and it
     * must not evict a Finish refusal the owner has not read yet.
     */
    private val distanceError = MutableStateFlow<String?>(null)
    private val error = ErrorSlot()
    private val finishing = MutableStateFlow(false)
    private val paused = MutableStateFlow(false)

    val uiState: StateFlow<LiveCardioUiState> = combine(
        combine(session, missing, finished, failed, elapsedSeconds) { current, gone, done, broken, elapsed ->
            Loaded(current, gone, done, broken, elapsed)
        },
        combine(type, indoor, distanceKm, distanceEntryUnit) { cardioType, isIndoor, distance, unit ->
            CardioInputs(cardioType, isIndoor, distance, unit)
        },
        combine(error.messages, finishing, distanceError, paused) { err, busy, distanceProblem, frozen ->
            Flags(err, busy, distanceProblem, frozen)
        },
    ) { loaded, cardioTriple, flags ->
        LiveCardioUiState(
            session = loaded.session,
            missing = loaded.missing,
            finished = loaded.finished,
            failed = loaded.failed,
            elapsedSeconds = loaded.elapsedSeconds,
            type = cardioTriple.type,
            indoor = cardioTriple.indoor,
            distanceKm = cardioTriple.distance,
            distanceEntryUnit = cardioTriple.unit,
            distanceError = flags.distanceError,
            error = flags.error,
            finishing = flags.finishing,
            paused = flags.paused,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveCardioUiState())

    private val _finishedId = MutableStateFlow<String?>(null)
    val finishedId: StateFlow<String?> = _finishedId.asStateFlow()

    private var ticking: Job? = null

    init {
        ticking = viewModelScope.launch { loadAndTick() }
    }

    fun setType(value: CardioType) {
        type.value = value
        persistInputs()
    }

    fun setIndoor(value: Boolean) {
        indoor.value = value
        persistInputs()
    }

    fun setDistanceKm(value: String, unit: DistanceUnit? = null) {
        distanceKm.value = value
        distanceEntryUnit.value = if (value.isBlank()) null else unit ?: distanceEntryUnit.value
        distanceError.value = null
        persistInputs()
    }

    /** Re-read the live row after a failed load. A no-op unless the last read actually threw. */
    fun retry() {
        if (!failed.value) return
        failed.value = false
        ticking?.cancel()
        ticking = viewModelScope.launch { loadAndTick() }
    }

    fun finish() {
        val current = session.value ?: return
        if (finishing.value) return
        // Same one-minute gate as the dock (A24): a stray tap or a scripted call before the
        // first minute does not end the run.
        // Read the clock now rather than trust the last tick, which can be up to a second old.
        elapsedSeconds.value = readElapsed(current)
        if (!CardioFinishGate.isOpen(elapsedSeconds = elapsedSeconds.value)) return
        finishing.value = true
        viewModelScope.launch {
            val now = clock.captureNow()
            // The distance box is read here, at the commit, as typed. A value that is not a
            // distance stops the finish with the rule under the box; it is never dropped so
            // that the run saves with no distance the owner can see went missing.
            val typedUnit = distanceEntryUnit.value
            val distanceUnit = if (typedUnit != null) {
                typedUnit
            } else {
                val resolved = runCatchingCancellable {
                    val weight = container.preferencesRepository.weightUnit.first()
                    val stored = container.preferencesRepository.storedDistanceUnit.first()
                    DistanceUnit.resolve(stored, weight)
                }.getOrElse { thrown ->
                    // Guessing a unit here would read "5" as miles or kilometres on a coin
                    // toss; refusing keeps the clock running and the typed text in place.
                    AppLog.w(TAG, "Reading the distance unit failed", thrown)
                    error.fail(
                        source = ERR_FINISH,
                        message = "Could not finish that session. Try again.",
                    )
                    finishing.value = false
                    return@launch
                }
                resolved
            }
            val distance = NumericEntry.typedDistanceKm(distanceKm.value, distanceUnit)
            if (distance is NumericEntry.Typed.Invalid) {
                distanceError.value = distance.message
                finishing.value = false
                return@launch
            }
            val block = CardioBlock(
                id = current.cardioBlocks.firstOrNull()?.id
                    ?: current.blocks.firstOrNull()?.id
                    ?: current.id,
                sortOrder = 0,
                type = type.value,
                indoor = indoor.value,
                elapsedSeconds = elapsedSeconds.value,
                movingSeconds = elapsedSeconds.value,
                distanceMeters = distance.valueOrNull?.times(1_000.0),
                elevationMeters = null,
                heartRateBpm = null,
                energyKj = null,
                rpe = null,
                routeRef = null,
            )
            val result = runCatchingCancellable {
                container.completeTraining.finishLiveActivity(
                    sessionId = current.id,
                    now = now,
                    blocks = listOf(block),
                )
            }
            finishing.value = false
            result.onSuccess { outcome ->
                when (outcome) {
                    is CompleteTrainingOutcome.Written -> {
                        forgetInputs()
                        _finishedId.value = outcome.id
                    }
                    is CompleteTrainingOutcome.RuledOut ->
                        error.fail(source = ERR_FINISH, message = outcome.reason)
                    is CompleteTrainingOutcome.Failed ->
                        error.fail(source = ERR_FINISH, message = outcome.message)
                }
            }.onFailure { thrown ->
                AppLog.w(TAG, "Finishing live cardio failed", thrown)
                error.fail(
                    source = ERR_FINISH,
                    message = "Could not finish that session. Try again.",
                )
            }
        }
    }

    fun discard() {
        val started = error.mark()
        val id = session.value?.id ?: return
        viewModelScope.launch {
            runCatchingCancellable { container.discardActivity(id) }
                .onSuccess {
                    clearTimerRow()
                    forgetInputs()
                    error.clearFrom(source = ERR_DISCARD, before = started)
                    finished.value = false
                    missing.value = true
                    session.value = null
                }
                .onFailure { thrown ->
                    // The session is still live. Saying "that session is gone" while
                    // the bar resurrects it — now with a wiped timer baseline — is
                    // the worse failure.
                    AppLog.w(TAG, "Discarding live cardio failed", thrown)
                    error.fail(
                        source = ERR_DISCARD,
                        message = "Could not discard that session. Try again.",
                    )
                }
        }
    }

    /**
     * D12 Pause / Resume. The timer row (SharedPreferences, not Room) carries the frozen
     * total, so a pause survives leaving the screen and process death, and the live bar
     * reads the same frozen clock.
     */
    fun togglePause() {
        val live = session.value ?: return
        if (finishing.value) return
        // v4 V1b: a double tap inside PAUSE_DEBOUNCE_MS is one press, not pause then resume.
        val now = elapsedRealtime()
        val last = lastToggleAtMs
        if (last != null && now - last < PAUSE_DEBOUNCE_MS) return
        lastToggleAtMs = now
        ensurePersisted(live)
        val row = recoverWith(TAG, "Reading the cardio timer row", null) {
            container.cardioTimerPersistence.load()
        }?.takeIf { it.sessionId == live.id } ?: return
        val next = if (row.paused) {
            CardioElapsed.resumed(row, nowElapsedMs = elapsedRealtime(), nowWallMs = wallClock())
        } else {
            CardioElapsed.paused(row, elapsedSeconds = readElapsed(live))
        }
        val saved = recoverWith(TAG, "Saving the cardio timer row", false) {
            container.cardioTimerPersistence.save(next)
        }
        if (!saved) {
            AppLog.w(TAG, "Cardio pause state did not commit")
            return
        }
        paused.value = next.paused
        elapsedSeconds.value = readElapsed(live)
    }

    fun onFinishedHandled() {
        _finishedId.value = null
    }

    fun dismissError() {
        error.dismiss()
    }

    private suspend fun loadAndTick() {
        val live = runCatchingCancellable { container.activityRepository.get(sessionId) }
            .getOrElse { thrown ->
                AppLog.e(TAG, "Loading live cardio failed", thrown)
                failed.value = true
                return
            }
        if (live == null) {
            finished.value = false
            missing.value = true
            return
        }
        if (!live.isLive) {
            missing.value = false
            finished.value = true
            session.value = live
            return
        }
        finished.value = false
        missing.value = false
        session.value = live
        // The row's block is the starting point only. Inputs already mirrored into saved
        // state are the owner's later choices, and win over it after a recreation — each
        // by its own key, since the owner may have changed one and not the other.
        live.cardioBlocks.firstOrNull()?.let { block ->
            if (!inputs.containsType()) type.value = block.type
            if (!inputs.containsIndoor()) indoor.value = block.indoor
        }
        ensurePersisted(live)
        while (true) {
            elapsedSeconds.value = readElapsed(live)
            delay(1_000)
        }
    }

    private fun readElapsed(live: ActivitySession): Long {
        val persisted = recoverWith(TAG, "Reading the cardio timer row", null) {
            container.cardioTimerPersistence.load()
        }?.takeIf { it.sessionId == live.id }
        paused.value = persisted?.paused == true
        return CardioElapsed.seconds(
            persisted = persisted,
            sessionStartedAtMs = live.performedStart.instantMillis,
            nowElapsedMs = elapsedRealtime(),
            nowWallMs = wallClock(),
            nowBootCount = bootCount(),
        )
    }

    private fun ensurePersisted(live: ActivitySession) {
        val existing = recoverWith(TAG, "Reading the cardio timer row", null) {
            container.cardioTimerPersistence.load()
        }
        if (existing?.sessionId == live.id) return
        val nowElapsed = elapsedRealtime()
        val nowWall = wallClock()
        val saved = recoverWith(TAG, "Saving the cardio timer row", false) {
            container.cardioTimerPersistence.save(
                PersistedCardioTimer(
                    sessionId = live.id,
                    startedAtElapsedRealtime = nowElapsed,
                    startedAtWallClockMillis = nowWall,
                    bootMarker = CardioElapsed.bootMarker(nowWall, nowElapsed),
                ),
            )
        }
        if (!saved) {
            // Not a user-facing failure. loadAndTick reads the row back each
            // second and, finding none, CardioElapsed.seconds counts from the
            // session's own performedStart — so the clock stays honest; it
            // only loses the elapsedRealtime path across a wall-clock step.
            AppLog.w(TAG, "Cardio timer row did not commit; elapsed falls back to session start")
        }
    }

    /**
     * A row that outlives its session is harmless — [loadAndTick] matches
     * rows by session id — but it is still a write that did not happen.
     */
    private fun clearTimerRow() {
        val cleared = recoverWith(TAG, "Clearing the cardio timer row", false) {
            container.cardioTimerPersistence.clear()
        }
        if (!cleared) {
            AppLog.w(TAG, "Cardio timer row did not clear; the next session ignores it by id")
        }
    }

    private fun persistInputs() {
        inputs.write(
            CardioInputDraft(
                type = type.value,
                indoor = indoor.value,
                distanceKm = distanceKm.value,
                distanceUnit = distanceEntryUnit.value,
            ),
        )
    }

    /** The session is over one way or the other; its inputs must not greet the next one. */
    private fun forgetInputs() {
        inputs.clear()
    }

    private data class CardioInputs(
        val type: CardioType,
        val indoor: Boolean,
        val distance: String,
        val unit: DistanceUnit?,
    )

    private data class Flags(
        val error: String?,
        val finishing: Boolean,
        val distanceError: String?,
        val paused: Boolean,
    )

    private data class Loaded(
        val session: ActivitySession?,
        val missing: Boolean,
        val finished: Boolean,
        val failed: Boolean,
        val elapsedSeconds: Long,
    )

    companion object {
        private const val TAG = "PT/LiveCardio"

        /** v4 V1b: pause toggles closer together than this are ignored. */
        const val PAUSE_DEBOUNCE_MS = 300L
    }
}
