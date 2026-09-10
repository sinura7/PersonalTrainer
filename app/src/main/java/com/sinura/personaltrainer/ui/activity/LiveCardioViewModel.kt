package com.sinura.personaltrainer.ui.activity

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
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
 * [missing] is a successful read that found no live row: the session is gone. [failed] is a
 * read that threw: the session is most likely still live and the read is what broke. They
 * used to be one flag, and a Room fault on the first read was worse than either — it was an
 * uncaught coroutine failure that took the process down.
 */
data class LiveCardioUiState(
    val session: ActivitySession? = null,
    val missing: Boolean = false,
    val failed: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val type: CardioType = CardioType.RUN,
    val indoor: Boolean = false,
    val distanceKm: String = "",
    /** Finish read [distanceKm] and could not: the rule it broke, shown under the box. */
    val distanceError: String? = null,
    val error: String? = null,
    val finishing: Boolean = false,
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
    private val failed = MutableStateFlow(false)
    private val elapsedSeconds = MutableStateFlow(0L)

    // The three inputs the owner can change while the clock runs. Mirrored into
    // SavedStateHandle on every change: the Room row holds what the session started as, and
    // writing a half-typed distance into it mid-session would mix committed and temporary
    // data (and bump the revision), so the draft lives here until finish() reads it.
    private val type = MutableStateFlow(
        savedStateHandle.get<String>(KEY_TYPE)
            ?.let { raw -> CardioType.entries.firstOrNull { it.name == raw } }
            ?: CardioType.RUN,
    )
    private val indoor = MutableStateFlow(savedStateHandle.get<Boolean>(KEY_INDOOR) ?: false)
    private val distanceKm = MutableStateFlow(savedStateHandle.get<String>(KEY_DISTANCE).orEmpty())
    /**
     * The distance box's own complaint, kept separate from [error] on purpose (UX06).
     * [ErrorSlot] holds ONE action failure at a time; a field rule is not an action failure —
     * it belongs under its box, it is answered by retyping rather than by retrying, and it
     * must not evict a Finish refusal the owner has not read yet.
     */
    private val distanceError = MutableStateFlow<String?>(null)
    private val error = ErrorSlot()
    private val finishing = MutableStateFlow(false)

    val uiState: StateFlow<LiveCardioUiState> = combine(
        combine(session, missing, failed, elapsedSeconds) { current, gone, broken, elapsed ->
            Loaded(current, gone, broken, elapsed)
        },
        combine(type, indoor, distanceKm) { cardioType, isIndoor, distance ->
            Triple(cardioType, isIndoor, distance)
        },
        combine(error.messages, finishing, distanceError) { err, busy, distanceProblem ->
            Flags(err, busy, distanceProblem)
        },
    ) { loaded, cardioTriple, flags ->
        LiveCardioUiState(
            session = loaded.session,
            missing = loaded.missing,
            failed = loaded.failed,
            elapsedSeconds = loaded.elapsedSeconds,
            type = cardioTriple.first,
            indoor = cardioTriple.second,
            distanceKm = cardioTriple.third,
            distanceError = flags.distanceError,
            error = flags.error,
            finishing = flags.finishing,
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
        savedStateHandle[KEY_TYPE] = value.name
    }

    fun setIndoor(value: Boolean) {
        indoor.value = value
        savedStateHandle[KEY_INDOOR] = value
    }

    fun setDistanceKm(value: String) {
        distanceKm.value = value
        distanceError.value = null
        savedStateHandle[KEY_DISTANCE] = value
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
        finishing.value = true
        viewModelScope.launch {
            val now = clock.captureNow()
            // The distance box is read here, at the commit, as typed. A value that is not a
            // distance stops the finish with the rule under the box; it is never dropped so
            // that the run saves with no distance the owner can see went missing.
            val weightUnit = runCatchingCancellable { container.preferencesRepository.weightUnit.first() }
                .getOrElse { thrown ->
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
            val distance = NumericEntry.typedDistanceKm(distanceKm.value, DistanceUnit.fromWeight(weightUnit))
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
                container.finishActivity(current.id, now, listOf(block))
            }
            finishing.value = false
            result.onSuccess { write ->
                when (write) {
                    is ActivityWrite.Accepted -> {
                        clearTimerRow()
                        forgetInputs()
                        _finishedId.value = write.session.id
                    }
                    is ActivityWrite.Rejected ->
                        error.fail(source = ERR_FINISH, message = write.reason)
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
        if (live == null || !live.isLive) {
            missing.value = true
            return
        }
        session.value = live
        // The row's block is the starting point only. Inputs already mirrored into saved
        // state are the owner's later choices, and win over it after a recreation — each
        // by its own key, since the owner may have changed one and not the other.
        live.cardioBlocks.firstOrNull()?.let { block ->
            if (!savedStateHandle.contains(KEY_TYPE)) type.value = block.type
            if (!savedStateHandle.contains(KEY_INDOOR)) indoor.value = block.indoor
        }
        ensurePersisted(live)
        while (true) {
            val persisted = recoverWith(TAG, "Reading the cardio timer row", null) {
                container.cardioTimerPersistence.load()
            }?.takeIf { it.sessionId == live.id }
            elapsedSeconds.value = CardioElapsed.seconds(
                persisted = persisted,
                sessionStartedAtMs = live.performedStart.instantMillis,
                nowElapsedMs = elapsedRealtime(),
                nowWallMs = wallClock(),
                nowBootCount = bootCount(),
            )
            delay(1_000)
        }
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

    /** The session is over one way or the other; its inputs must not greet the next one. */
    private fun forgetInputs() {
        savedStateHandle.remove<String>(KEY_TYPE)
        savedStateHandle.remove<Boolean>(KEY_INDOOR)
        savedStateHandle.remove<String>(KEY_DISTANCE)
    }

    private data class Flags(
        val error: String?,
        val finishing: Boolean,
        val distanceError: String?,
    )

    private data class Loaded(
        val session: ActivitySession?,
        val missing: Boolean,
        val failed: Boolean,
        val elapsedSeconds: Long,
    )

    private companion object {
        const val TAG = "PT/LiveCardio"
        const val KEY_TYPE = "liveCardio.type"
        const val KEY_INDOOR = "liveCardio.indoor"
        const val KEY_DISTANCE = "liveCardio.distance"
    }
}
