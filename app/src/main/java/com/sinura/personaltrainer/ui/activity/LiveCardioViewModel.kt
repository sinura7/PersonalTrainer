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
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.timer.CardioElapsed
import com.sinura.personaltrainer.timer.PersistedCardioTimer
import com.sinura.personaltrainer.util.JvmTime
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LiveCardioUiState(
    val session: ActivitySession? = null,
    val missing: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val type: CardioType = CardioType.RUN,
    val indoor: Boolean = false,
    val distanceKm: String = "",
    val error: String? = null,
    val finishing: Boolean = false,
)

class LiveCardioViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
    private val clock: com.sinura.personaltrainer.domain.TimePort = JvmTime,
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClock: () -> Long = { System.currentTimeMillis() },
) : AppViewModel(application, container) {
    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()
    private val session = MutableStateFlow<ActivitySession?>(null)
    private val missing = MutableStateFlow(false)
    private val elapsedSeconds = MutableStateFlow(0L)
    private val type = MutableStateFlow(CardioType.RUN)
    private val indoor = MutableStateFlow(false)
    private val distanceKm = MutableStateFlow("")
    private val error = MutableStateFlow<String?>(null)
    private val finishing = MutableStateFlow(false)

    val uiState: StateFlow<LiveCardioUiState> = combine(
        combine(session, missing, elapsedSeconds) { current, gone, elapsed ->
            Triple(current, gone, elapsed)
        },
        combine(type, indoor, distanceKm) { cardioType, isIndoor, distance ->
            Triple(cardioType, isIndoor, distance)
        },
        combine(error, finishing) { err, busy -> err to busy },
    ) { sessionTriple, cardioTriple, flags ->
        LiveCardioUiState(
            session = sessionTriple.first,
            missing = sessionTriple.second,
            elapsedSeconds = sessionTriple.third,
            type = cardioTriple.first,
            indoor = cardioTriple.second,
            distanceKm = cardioTriple.third,
            error = flags.first,
            finishing = flags.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveCardioUiState())

    private val _finishedId = MutableStateFlow<String?>(null)
    val finishedId: StateFlow<String?> = _finishedId.asStateFlow()

    init {
        viewModelScope.launch { loadAndTick() }
    }

    fun setType(value: CardioType) {
        type.value = value
    }

    fun setIndoor(value: Boolean) {
        indoor.value = value
    }

    fun setDistanceKm(value: String) {
        distanceKm.value = value
    }

    fun finish() {
        val current = session.value ?: return
        if (finishing.value) return
        finishing.value = true
        viewModelScope.launch {
            val now = clock.captureNow()
            val block = CardioBlock(
                id = current.cardioBlocks.firstOrNull()?.id
                    ?: current.blocks.firstOrNull()?.id
                    ?: current.id,
                sortOrder = 0,
                type = type.value,
                indoor = indoor.value,
                elapsedSeconds = elapsedSeconds.value,
                movingSeconds = elapsedSeconds.value,
                distanceMeters = distanceKm.value.toDoubleOrNull()?.takeIf { it > 0.0 }?.times(1_000.0),
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
                        container.cardioTimerPersistence.clear()
                        _finishedId.value = write.session.id
                    }
                    is ActivityWrite.Rejected -> error.value = write.reason
                }
            }.onFailure { thrown ->
                AppLog.w(TAG, "Finishing live cardio failed", thrown)
                error.value = "Could not finish that session. Try again."
            }
        }
    }

    fun discard() {
        val id = session.value?.id ?: return
        viewModelScope.launch {
            runCatchingCancellable { container.discardActivity(id) }
            container.cardioTimerPersistence.clear()
            missing.value = true
            session.value = null
        }
    }

    fun onFinishedHandled() {
        _finishedId.value = null
    }

    private suspend fun loadAndTick() {
        val live = container.activityRepository.get(sessionId)
            ?: container.activityRepository.getLive()
        if (live == null || !live.isLive) {
            missing.value = true
            return
        }
        session.value = live
        live.cardioBlocks.firstOrNull()?.let { block ->
            type.value = block.type
            indoor.value = block.indoor
        }
        ensurePersisted(live)
        while (true) {
            val persisted = container.cardioTimerPersistence.load()
                ?.takeIf { it.sessionId == live.id }
            elapsedSeconds.value = CardioElapsed.seconds(
                persisted = persisted,
                sessionStartedAtMs = live.performedStart.instantMillis,
                nowElapsedMs = elapsedRealtime(),
                nowWallMs = wallClock(),
            )
            delay(1_000)
        }
    }

    private fun ensurePersisted(live: ActivitySession) {
        val existing = container.cardioTimerPersistence.load()
        if (existing?.sessionId == live.id) return
        val nowElapsed = elapsedRealtime()
        val nowWall = wallClock()
        container.cardioTimerPersistence.save(
            PersistedCardioTimer(
                sessionId = live.id,
                startedAtElapsedRealtime = nowElapsed,
                startedAtWallClockMillis = nowWall,
                bootMarker = CardioElapsed.bootMarker(nowWall, nowElapsed),
            ),
        )
    }

    private companion object {
        const val TAG = "PT/LiveCardio"
    }
}
