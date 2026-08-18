package com.sinura.personaltrainer.timer

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class RestTimerController(
    context: Context,
    private val store: RestTimerStore,
) {
    private val appContext = context.applicationContext
    val snapshot: StateFlow<RestTimerSnapshot> = store.snapshot

    val remainingSeconds: Flow<Int> = snapshot.flatMapLatest { state ->
        if (!state.running) {
            flowOf(0)
        } else {
            flow {
                while (true) {
                    val left = state.remainingSeconds(SystemClock.elapsedRealtime())
                    emit(left)
                    if (left <= 0) break
                    delay(200)
                }
            }
        }
    }.distinctUntilChanged()

    val runningSessionId: Flow<String?> = snapshot.map { snap ->
        snap.sessionId.takeIf { snap.running }
    }.distinctUntilChanged()

    fun start(totalSeconds: Int, sessionId: String?) {
        store.start(totalSeconds, sessionId, SystemClock.elapsedRealtime())
        dispatch(RestTimerService.ACTION_SYNC)
    }

    fun adjust(deltaSeconds: Int) {
        store.adjust(deltaSeconds, SystemClock.elapsedRealtime())
        if (store.current().running) {
            dispatch(RestTimerService.ACTION_SYNC)
        } else {
            dispatch(RestTimerService.ACTION_STOP)
        }
    }

    fun stop(fromService: Boolean = false) {
        val wasRunning = store.current().running
        store.clear()
        if (!fromService) {
            if (wasRunning) {
                dispatch(RestTimerService.ACTION_STOP)
            }
            RestTimerService.cancelDone(appContext)
        }
    }

    private fun dispatch(action: String) {
        val intent = Intent(appContext, RestTimerService::class.java).setAction(action)
        try {
            appContext.startForegroundService(intent)
        } catch (_: Exception) {
            try {
                appContext.startService(intent)
            } catch (_: Exception) {
                // Service cannot start (restricted background). In-app state still updates.
            }
        }
    }
}
