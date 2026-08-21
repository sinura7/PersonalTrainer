package com.sinura.personaltrainer.ui.navigation

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.LiveSessionRules
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.workout.DiscardOutcome
import com.sinura.personaltrainer.workout.FinishOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PT/LiveSessionBar"

data class LiveSessionBarUiState(
    val sessionId: String,
    val title: String,
    val elapsedLabel: String,
    val workingSets: Int,
    val totalSets: Int,
    val restRemainingSeconds: Int,
    val restRunning: Boolean,
    val stale: Boolean,
    val staleHours: Long,
) {
    /** A session with nothing logged can never be finished — discard is its only exit. */
    val canFinish: Boolean get() = totalSets >= 1
}

/**
 * State for the one live-session surface in the app.
 *
 * Nothing here is accumulated: elapsed is re-derived from wall clock on every tick, so
 * backgrounding, process death and a clock change cannot desynchronise it.
 */
class LiveSessionBarViewModel(application: Application) : AppViewModel(application) {

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LiveSessionBarUiState?> =
        container.workoutRepository.observeInProgress()
            .flatMapLatest { session ->
                if (session == null) {
                    flowOf(null)
                } else {
                    combine(
                        container.workoutRepository.observeSessionActivity(session.id),
                        container.restTimerController.remainingSeconds,
                        ticker,
                    ) { activity, restSeconds, now ->
                        val lastActivity = LiveSessionRules.lastActivityMs(
                            startedAt = session.startedAt,
                            lastSetCompletedAt = activity.lastCompletedAt,
                        )
                        LiveSessionBarUiState(
                            sessionId = session.id,
                            title = session.routineName ?: "Workout",
                            elapsedLabel = LiveSessionRules.formatElapsed(
                                (now - session.startedAt) / 1_000,
                            ),
                            workingSets = activity.workingSets,
                            totalSets = activity.totalSets,
                            restRemainingSeconds = restSeconds,
                            restRunning = restSeconds > 0,
                            stale = LiveSessionRules.isStale(lastActivity, now),
                            staleHours = LiveSessionRules.staleHours(lastActivity, now),
                        )
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Consumed by the host composition, so a finish survives Activity recreation. */
    private val _finishedNavigation = MutableStateFlow<String?>(null)
    val finishedNavigation: StateFlow<String?> = _finishedNavigation.asStateFlow()

    fun onFinishNavigationHandled() {
        _finishedNavigation.value = null
    }

    fun finishFromBar() {
        val id = uiState.value?.sessionId ?: return
        viewModelScope.launch {
            when (val outcome = container.finishWorkout(id, notes = null)) {
                is FinishOutcome.Finished -> _finishedNavigation.value = outcome.sessionId
                // No error surface here on purpose: the bar reflects the database, so a
                // failed finish simply leaves the session — and the bar — visible, which is
                // honest. The cause is in the log.
                else -> AppLog.w(TAG, "Finishing from the bar did not complete: $outcome")
            }
        }
    }

    fun discardFromBar() {
        val id = uiState.value?.sessionId ?: return
        viewModelScope.launch {
            when (val outcome = container.discardWorkout(id)) {
                DiscardOutcome.Discarded -> Unit
                is DiscardOutcome.Failed -> AppLog.w(TAG, "Discarding from the bar failed")
            }
        }
    }
}
