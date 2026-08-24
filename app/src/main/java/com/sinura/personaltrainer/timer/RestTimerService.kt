package com.sinura.personaltrainer.timer

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns the visible rest countdown: the ongoing notification and its ±15s / Skip actions.
 *
 * It is deliberately NOT the thing that guarantees the alert. A foreground service does not
 * hold the CPU awake, so the tick loop below stops the moment the device suspends;
 * [RestTimerAlarmScheduler] owns waking the phone, and both paths funnel into
 * [RestTimerCompletion] so only one alert ever fires.
 */
class RestTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val completeRunnable = Runnable { onComplete() }
    private val tickRunnable = object : Runnable {
        override fun run() {
            val state = controller.snapshot.value
            val remaining = state.remainingSeconds(SystemClock.elapsedRealtime())
            if (!state.running || remaining <= 0) {
                if (state.running) onComplete() else stopNow()
                return
            }
            publishRunning(state)
            handler.postDelayed(this, 250L)
        }
    }
    private var completing = false
    private var startedForeground = false
    private var lastShownRemaining = Int.MIN_VALUE
    private val controller: RestTimerController
        get() = (application as PersonalTrainerApp).container.restTimerController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Whatever brought us here, the platform expects startForeground() promptly after a
        // startForegroundService(). Claiming it up front closes the crash lane where an
        // action routes straight to stopSelf() without ever going foreground.
        ensureForegroundClaimed()

        if (intent == null) {
            // Sticky restart after a process kill: nothing is in memory, so recover from disk.
            val restored = controller.rehydrate()
            if (!restored) {
                stopNow()
                return START_NOT_STICKY
            }
            syncForeground()
            return START_STICKY
        }

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
        handler.removeCallbacks(tickRunnable)
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
        lastShownRemaining = Int.MIN_VALUE
        publishRunning(state)
        handler.removeCallbacks(completeRunnable)
        handler.removeCallbacks(tickRunnable)
        // postAtTime's clock base is uptimeMillis (which excludes deep sleep) while
        // endsAtElapsedRealtime is on the elapsedRealtime base (which includes it). Passing
        // the latter to the former scheduled this callback hours late on any phone that had
        // ever slept, making it dead code. A relative delay is base-agnostic and correct.
        val delayMs = (state.endsAtElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        handler.postDelayed(completeRunnable, delayMs)
        handler.post(tickRunnable)
    }

    private fun ensureForegroundClaimed() {
        if (startedForeground) return
        val state = controller.snapshot.value
        RestTimerNotifications.ensureChannels(this)
        startForegroundWith(runningNotification(state))
    }

    private fun publishRunning(state: RestTimerSnapshot) {
        val remaining = state.remainingSeconds(SystemClock.elapsedRealtime()).coerceAtLeast(0)
        if (!startedForeground) {
            startForegroundWith(runningNotification(state, remaining))
            lastShownRemaining = remaining
            return
        }
        // The tick runs 4x/second so the countdown stays honest, but the text only changes
        // once a second — re-posting on every tick was 4 binder round trips per second for
        // the whole rest, and flirts with the platform's notification rate limiter.
        if (remaining == lastShownRemaining) return
        lastShownRemaining = remaining
        try {
            getSystemService(NotificationManager::class.java)
                ?.notify(RestTimerNotifications.RUNNING_ID, runningNotification(state, remaining))
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
        handler.removeCallbacks(tickRunnable)
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
        handler.removeCallbacks(tickRunnable)
        completing = false
        lastShownRemaining = Int.MIN_VALUE
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // Already gone.
        }
        startedForeground = false
        stopSelf()
    }

    private fun runningNotification(
        state: RestTimerSnapshot,
        remainingSeconds: Int = state.remainingSeconds(SystemClock.elapsedRealtime()).coerceAtLeast(0),
    ): Notification {
        val remainingLabel = RestTimer.formatClock(remainingSeconds.coerceAtLeast(0))
        return NotificationCompat.Builder(this, RestTimerNotifications.CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Rest")
            .setContentText("$remainingLabel remaining")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setUsesChronometer(false)
            .setShowWhen(false)
            .setContentIntent(RestTimerNotifications.openAppIntent(this, state.sessionId))
            .addAction(0, "−15s", serviceIntent(ACTION_MINUS_15, 11))
            .addAction(0, "+15s", serviceIntent(ACTION_ADD_15, 12))
            .addAction(0, "Skip", serviceIntent(ACTION_SKIP, 13))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, RestTimerService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
