package com.sinura.personaltrainer.timer

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns the visible rest countdown: the ongoing lock-screen chronometer
 * and its ±15s / Skip actions.
 *
 * It is deliberately NOT the thing that guarantees the alert. A foreground
 * service does not hold the CPU awake. [RestTimerAlarmScheduler] owns waking
 * the phone, and both paths funnel into [RestTimerCompletion] so only one
 * alert ever fires. SystemUI draws the countdown; this service does not
 * re-post every second.
 */
class RestTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val completeRunnable = Runnable { onComplete() }
    private var completing = false
    private var startedForeground = false
    private var lastShownEndsAt = Long.MIN_VALUE
    private val controller: RestTimerController
        get() = (application as PersonalTrainerApp).container.restTimerController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Sticky restart: recover from disk before claiming foreground so an
            // idle snapshot cannot post Rest 0:00, then linger.
            val restored = controller.rehydrate()
            ensureForegroundClaimed()
            if (!restored) {
                stopNow()
                return START_NOT_STICKY
            }
            syncForeground()
            return START_STICKY
        }

        // startForegroundService() still needs a prompt startForeground() on an
        // explicit action, including STOP, so the process cannot be killed for
        // skipping the claim.
        ensureForegroundClaimed()

        when (intent.action) {
            ACTION_STOP, ACTION_SKIP -> {
                controller.stop(fromService = true)
                stopNow()
                return START_NOT_STICKY
            }
            ACTION_ADD_15 -> {
                controller.adjust(15)
                syncForeground()
            }
            ACTION_MINUS_15 -> {
                controller.adjust(-15)
                syncForeground()
            }
            else -> syncForeground()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(completeRunnable)
        scope.cancel()
        super.onDestroy()
    }

    private fun syncForeground() {
        val state = controller.snapshot.value
        val remaining = state.remainingSeconds(SystemClock.elapsedRealtime())
        if (!state.running || remaining <= 0) {
            if (state.running) {
                onComplete()
            } else {
                stopNow()
            }
            return
        }
        completing = false
        RestTimerNotifications.ensureChannels(this)
        RestTimerNotifications.cancelDone(this)
        lastShownEndsAt = Long.MIN_VALUE
        publishRunning(state, force = true)
        handler.removeCallbacks(completeRunnable)
        val delayMs = (state.endsAtElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        handler.postDelayed(completeRunnable, delayMs)
    }

    private fun ensureForegroundClaimed() {
        if (startedForeground) return
        val state = controller.snapshot.value
        RestTimerNotifications.ensureChannels(this)
        startForegroundWith(RestTimerNotifications.runningNotification(this, state))
    }

    private fun publishRunning(state: RestTimerSnapshot, force: Boolean = false) {
        val remaining = state.remainingSeconds(SystemClock.elapsedRealtime()).coerceAtLeast(0)
        if (!startedForeground) {
            startForegroundWith(RestTimerNotifications.runningNotification(this, state))
            lastShownEndsAt = state.endsAtElapsedRealtime
            return
        }
        // Chronometer ticks in SystemUI. Re-post only when the deadline
        // changes (±15) so we do not fight the lock-screen countdown.
        if (!force && state.endsAtElapsedRealtime == lastShownEndsAt) return
        lastShownEndsAt = state.endsAtElapsedRealtime
        if (remaining <= 0) {
            onComplete()
            return
        }
        try {
            getSystemService(NotificationManager::class.java)
                ?.notify(
                    RestTimerNotifications.RUNNING_ID,
                    RestTimerNotifications.runningNotification(this, state),
                )
        } catch (_: Exception) {
            // POST_NOTIFICATIONS denied; the countdown still runs and the alert still fires.
        }
    }

    private fun startForegroundWith(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(
                    this,
                    RestTimerNotifications.RUNNING_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(RestTimerNotifications.RUNNING_ID, notification)
            }
            startedForeground = true
        } catch (_: Exception) {
            // Background-start restrictions can refuse this; in-app state still runs and the
            // wakeup alarm still fires, so the rest is not lost.
        }
    }

    private fun onComplete() {
        if (completing) return
        completing = true
        handler.removeCallbacks(completeRunnable)
        val state = controller.snapshot.value
        scope.launch {
            // Idempotent: if the wakeup alarm already announced this rest, this is a no-op.
            RestTimerCompletion.completeOnce(
                context = applicationContext,
                incomingTimerId = state.timerId,
                expectedTimerId = state.timerId,
                deadlineElapsedRealtime = state.endsAtElapsedRealtime,
                sessionId = state.sessionId,
            )
            stopNow()
        }
    }

    private fun stopNow() {
        handler.removeCallbacks(completeRunnable)
        completing = false
        lastShownEndsAt = Long.MIN_VALUE
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // Already gone.
        }
        startedForeground = false
        stopSelf()
    }

    companion object {
        const val ACTION_SYNC = "com.sinura.personaltrainer.timer.SYNC"
        const val ACTION_STOP = "com.sinura.personaltrainer.timer.STOP"
        const val ACTION_SKIP = "com.sinura.personaltrainer.timer.SKIP"
        const val ACTION_ADD_15 = "com.sinura.personaltrainer.timer.ADD_15"
        const val ACTION_MINUS_15 = "com.sinura.personaltrainer.timer.MINUS_15"
        const val EXTRA_SESSION_ID = "sessionId"
        const val EXTRA_TIMER_ID = "timerId"
    }
}
