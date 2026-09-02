package com.sinura.personaltrainer.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import com.sinura.personaltrainer.PersonalTrainerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Wakes the device at the end of rest and announces it.
 *
 * The CPU is only guaranteed awake for the duration of onReceive, and reading preferences is
 * suspending, so this uses goAsync() plus a short PARTIAL_WAKE_LOCK: the lock keeps the CPU
 * up long enough for the sound and vibration to actually play if the device was asleep, and
 * both are released on every path.
 */
class RestTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != ACTION_REST_COMPLETE) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        val wakeLock = try {
            appContext.getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
                ?.apply { setReferenceCounted(false); acquire(WAKELOCK_TIMEOUT_MS) }
        } catch (_: Exception) {
            null
        }

        val app = appContext as? PersonalTrainerApp
        val container = try {
            app?.container
        } catch (_: Exception) {
            null // Application.onCreate has not run; there is nothing to announce yet.
        }
        val snapshot = try {
            container?.restTimerStore?.current()
        } catch (_: Exception) {
            null
        }
        val stored = try {
            container?.restTimerStatePersistence?.load()
        } catch (_: Exception) {
            null
        }

        val incomingTimerId = intent.getStringExtra(RestTimerService.EXTRA_TIMER_ID).orEmpty()

        // Prefer live state; fall back to disk when the alarm resurrected a dead process.
        val expectedTimerId: String
        val deadline: Long
        val sessionId: String?
        if (snapshot != null && snapshot.running) {
            expectedTimerId = snapshot.timerId
            deadline = snapshot.endsAtElapsedRealtime
            sessionId = snapshot.sessionId
        } else if (stored != null) {
            expectedTimerId = stored.timerId
            deadline = stored.endsAtElapsedRealtime
            sessionId = stored.sessionId
        } else {
            // Neither live nor on disk: this rest was already completed and cleared by the
            // in-app path, and this is a cancelled alarm that was already in flight.
            releaseAndFinish(wakeLock, pendingResult)
            return
        }

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val lateByMs = SystemClock.elapsedRealtime() - deadline
                val playCue = lateByMs <= RestTimerRehydrator.LATE_ALERT_GRACE_MS
                withTimeoutOrNull(WAKELOCK_TIMEOUT_MS) {
                    RestTimerCompletion.completeOnce(
                        context = appContext,
                        incomingTimerId = incomingTimerId,
                        expectedTimerId = expectedTimerId,
                        deadlineElapsedRealtime = deadline,
                        sessionId = sessionId,
                        playCue = playCue,
                    )
                }
            } catch (_: Exception) {
                // Never let an alert failure crash the receiver.
            } finally {
                releaseAndFinish(wakeLock, pendingResult)
            }
        }
    }

    private fun releaseAndFinish(
        wakeLock: PowerManager.WakeLock?,
        pendingResult: BroadcastReceiver.PendingResult,
    ) {
        try {
            if (wakeLock?.isHeld == true) wakeLock.release()
        } catch (_: Exception) {
            // Already released by its own timeout.
        }
        try {
            pendingResult.finish()
        } catch (_: Exception) {
            // Already finished.
        }
    }

    companion object {
        const val ACTION_REST_COMPLETE = "com.sinura.personaltrainer.timer.REST_COMPLETE"
        private const val WAKELOCK_TAG = "PersonalTrainer:restComplete"
        private const val WAKELOCK_TIMEOUT_MS = 10_000L
    }
}
