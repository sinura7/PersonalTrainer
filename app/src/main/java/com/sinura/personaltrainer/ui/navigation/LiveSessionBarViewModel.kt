package com.sinura.personaltrainer.ui.navigation

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.CompleteTrainingOutcome
import com.sinura.personaltrainer.domain.LiveBarKind
import com.sinura.personaltrainer.domain.LiveSessionRules
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.AppClock
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.workout.CompleteTraining
import com.sinura.personaltrainer.workout.DiscardOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    val kind: LiveBarKind = LiveBarKind.WORKOUT,
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
class LiveSessionBarViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
    private val clock: AppClock = AppClock.System,
) : AppViewModel(application, container) {

    private val routeHidesBar = MutableStateFlow(false)

    /**
     * The host composition hides the bar on routes that already own the
     * session. Stop the 1 Hz elapsed poll and the rest-seconds collector
     * there — Active Workout / Rest already tick for themselves.
     */
    fun setRouteHidesBar(hidden: Boolean) {
        routeHidesBar.value = hidden
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val ticker = routeHidesBar.flatMapLatest { hidden ->
        if (hidden) {
            flowOf(clock.nowMs())
        } else {
            flow {
                while (true) {
                    emit(clock.nowMs())
                    delay(1_000)
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LiveSessionBarUiState?> =
        combine(
            container.workoutRepository.observeInProgress(),
            container.activityRepository.observeLive(),
        ) { workout, activity -> workout to activity }
            .flatMapLatest { (session, liveActivity) ->
                when {
                    session != null -> combine(
                        container.workoutRepository.observeSessionActivity(session.id),
                        routeHidesBar.flatMapLatest { hidden ->
                            if (hidden) flowOf(0) else container.restTimerController.remainingSeconds
                        },
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
                            kind = LiveBarKind.WORKOUT,
                        )
                    }
                    liveActivity != null -> ticker.map { now ->
                        val persisted = container.cardioTimerPersistence.load()
                            ?.takeIf { it.sessionId == liveActivity.id }
                        val elapsed = com.sinura.personaltrainer.timer.CardioElapsed.seconds(
                            persisted = persisted,
                            sessionStartedAtMs = liveActivity.performedStart.instantMillis,
                            nowWallMs = now,
                            nowBootCount = com.sinura.personaltrainer.timer.BootSession.count(getApplication()),
                        )
                        LiveSessionBarUiState(
                            sessionId = liveActivity.id,
                            title = liveActivity.title,
                            elapsedLabel = LiveSessionRules.formatElapsed(elapsed),
                            workingSets = liveActivity.strengthSetCount(),
                            totalSets = liveActivity.strengthSetCount().coerceAtLeast(1),
                            restRemainingSeconds = 0,
                            restRunning = false,
                            stale = false,
                            staleHours = 0,
                            kind = LiveBarKind.ACTIVITY,
                        )
                    }
                    else -> flowOf(null)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Presence only. The elapsed label ticks inside [LiveSessionBarHost];
     * this boolean must not change on those ticks or the nav root recomposes.
     */
    val hasLiveSession: StateFlow<Boolean> = combine(
        container.workoutRepository.observeInProgress(),
        container.activityRepository.observeLive(),
    ) { workout, activity -> workout != null || activity != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Consumed by the host composition, so a finish survives Activity recreation. */
    private val _finishedNavigation = MutableStateFlow<String?>(null)
    val finishedNavigation: StateFlow<String?> = _finishedNavigation.asStateFlow()
    private val _finishedActivityNavigation = MutableStateFlow<String?>(null)
    val finishedActivityNavigation: StateFlow<String?> = _finishedActivityNavigation.asStateFlow()

    fun onFinishNavigationHandled() {
        _finishedNavigation.value = null
        _finishedActivityNavigation.value = null
    }

    /**
     * A finish or discard the user confirmed on the bar that then failed.
     * The bar renders it: a confirmed destructive act that silently
     * no-ops (log-only) leaves the user believing it worked.
     */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun onActionErrorShown() {
        _actionError.value = null
    }

    private suspend fun finishActivityFromBar(sessionId: String) {
        val now = com.sinura.personaltrainer.util.JvmTime.captureNow()
        val current = container.activityRepository.get(sessionId)
        val persisted = container.cardioTimerPersistence.load()
            ?.takeIf { it.sessionId == sessionId }
        val elapsed = com.sinura.personaltrainer.timer.CardioElapsed.seconds(
            persisted = persisted,
            sessionStartedAtMs = current?.performedStart?.instantMillis ?: now.instantMillis,
            nowWallMs = now.instantMillis,
            nowBootCount = com.sinura.personaltrainer.timer.BootSession.count(getApplication()),
        )
        val blocks = current?.cardioBlocks?.map { block ->
            block.copy(elapsedSeconds = elapsed, movingSeconds = elapsed)
        } ?: current?.blocks
        applyFinishOutcome(
            outcome = container.completeTraining.finishLiveActivity(
                sessionId = sessionId,
                now = now,
                blocks = blocks,
            ),
            activity = true,
        )
    }

    fun finishFromBar() {
        val live = uiState.value ?: return
        viewModelScope.launch {
            if (live.kind == LiveBarKind.ACTIVITY) {
                runCatchingCancellable { finishActivityFromBar(live.sessionId) }
                    .onFailure { thrown ->
                        AppLog.w(TAG, "Finishing live cardio from the bar threw", thrown)
                        _actionError.value = CompleteTraining.LIVE_FINISH_FAILED
                    }
            } else {
                applyFinishOutcome(
                    outcome = container.completeTraining.finishWorkout(
                        sessionId = live.sessionId,
                        notes = null,
                    ),
                    activity = false,
                )
            }
        }
    }

    private fun applyFinishOutcome(outcome: CompleteTrainingOutcome, activity: Boolean) {
        when (outcome) {
            is CompleteTrainingOutcome.Accepted -> {
                if (!activity) {
                    PendingOccurrence.complete(container, outcome.id)
                    _finishedNavigation.value = outcome.id
                } else {
                    _finishedActivityNavigation.value = outcome.id
                }
                _actionError.value = null
            }
            is CompleteTrainingOutcome.Rejected -> {
                AppLog.w(TAG, "Finishing from the bar did not complete: ${outcome.reason}")
                _actionError.value = outcome.reason
            }
            is CompleteTrainingOutcome.Failed -> {
                AppLog.w(TAG, "Finishing from the bar failed: ${outcome.message}")
                _actionError.value = outcome.message
            }
        }
    }

    fun discardFromBar() {
        val live = uiState.value ?: return
        viewModelScope.launch {
            if (live.kind == LiveBarKind.ACTIVITY) {
                // Guarded like the workout branch: an unhandled throw here took
                // the whole process down, and clearing the timer on failure
                // wiped the baseline of a session that still exists.
                try {
                    container.discardActivity(live.sessionId)
                    container.cardioTimerPersistence.clear()
                    _actionError.value = null
                } catch (thrown: kotlinx.coroutines.CancellationException) {
                    throw thrown
                } catch (thrown: Exception) {
                    AppLog.w(TAG, "Discarding live cardio from the bar failed", thrown)
                    _actionError.value = "Could not discard this session. Try again."
                }
            } else {
                when (val outcome = container.discardWorkout(live.sessionId)) {
                    DiscardOutcome.Discarded -> {
                        PendingOccurrence.forgetIfSession(container, live.sessionId)
                        _actionError.value = null
                    }
                    is DiscardOutcome.Failed -> {
                        AppLog.w(TAG, "Discarding from the bar failed")
                        _actionError.value = outcome.message
                    }
                }
            }
        }
    }
}
