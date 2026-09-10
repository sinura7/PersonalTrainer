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
import com.sinura.personaltrainer.domain.RestTick
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import kotlinx.coroutines.CancellationException
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
 *
 * Teardown follows the store: when the snapshot is no longer running after
 * it has been, [stopNow] runs. The alarm path completes through
 * [RestTimerCompletion] with `fromService = true`, which used to skip
 * [ACTION_STOP] and leave this process posting a negative chronometer.
 *
 * It does own the last five seconds ([RestTick]): one posted runnable per
 * boundary, re-asked on every sync, so a ±15 s moves the ticks with the
 * deadline. Same reach as the countdown — this process alive, CPU awake.
 * In doze the alarm path's completion cue is the whole alert.
 */
class RestTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val completeRunnable = Runnable { handleDeadline() }
    private val tickRunnable = Runnable { handleTick() }
    private var pendingTick = 0
    private var tickPlayer: RestTickPlayer? = null

    /** Last seen; the collector below keeps it current. Read by the tick, on the main thread. */
    internal var tickPreferences: RestTimerPreferences = RestTimerPreferences.DEFAULT
    private var completing = false
    private var startedForeground = false
    private var lastShownEndsAt = Long.MIN_VALUE
    private var lastStartId = 0
    private var sawRunning = false
    private var stopped = false
    private val controller: RestTimerController
        get() = (application as PersonalTrainerApp).container.restTimerController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            controller.snapshot.collect { snap ->
                if (snap.running) {
                    sawRunning = true
                    stopped = false
                } else if (sawRunning) {
                    stopNow()
                }
            }
        }
        scope.launch {
            try {
                (application as PersonalTrainerApp).container.preferencesRepository
                    .restTimerPreferences
                    .collect { tickPreferences = it }
            } catch (thrown: CancellationException) {
                throw thrown
            } catch (_: Exception) {
                // The ticks keep the last preferences they saw. The cue does not depend on this.
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        stopped = false

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
        handler.removeCallbacks(tickRunnable)
        tickPlayer?.release()
        tickPlayer = null
        scope.cancel()
        super.onDestroy()
    }

    private fun syncForeground() {
        val state = controller.snapshot.value
        val remaining = state.remainingSeconds(SystemClock.elapsedRealtime())
        if (!state.running || remaining <= 0) {
            if (state.running) {
                handleDeadline(state)
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
        // Loaded now, at the start of the rest, so the first tick is not the one that decodes.
        if (tickPlayer == null) tickPlayer = RestTickPlayer(this)
        scheduleTick(state)
    }

    private fun scheduleTick(state: RestTimerSnapshot) {
        handler.removeCallbacks(tickRunnable)
        val now = SystemClock.elapsedRealtime()
        val next = RestTick.nextTick(state.endsAtElapsedRealtime, now) ?: return
        pendingTick = next
        handler.postDelayed(tickRunnable, RestTick.tickAt(state.endsAtElapsedRealtime, next) - now)
    }

    /**
     * One boundary. Re-checked against the live snapshot: a ±15 s that
     * landed between the post and the fire has already re-posted through
     * [syncForeground], and a tick for the old deadline must not sound.
     */
    private fun handleTick() {
        val state = controller.snapshot.value
        if (!state.running || completing) return
        val second = pendingTick
        if (!RestTick.isDue(state.endsAtElapsedRealtime, second, SystemClock.elapsedRealtime())) {
            scheduleTick(state)
            return
        }
        val player = tickPlayer
        val ticked = RestTimerAlerts.tick(this, tickPreferences) { player?.play() }
        if (ticked) tickObserver?.invoke(second)
        scheduleTick(state)
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
            handleDeadline(state)
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

    /**
     * Screen-on deadline. The alarm path does not come through here; it
     * completes the store and the snapshot collector tears us down.
     *
     * A +15s that minted a newer id between capture and [completeOnce]
     * must leave this service alive with a running card.
     */
    internal fun handleDeadline(state: RestTimerSnapshot = controller.snapshot.value) {
        if (completing) return
        completing = true
        handler.removeCallbacks(completeRunnable)
        handler.removeCallbacks(tickRunnable)
        scope.launch {
            RestTimerCompletion.completeOnce(
                context = applicationContext,
                incomingTimerId = state.timerId,
                expectedTimerId = state.timerId,
                deadlineElapsedRealtime = state.endsAtElapsedRealtime,
                sessionId = state.sessionId,
            )
            if (controller.snapshot.value.running) {
                completing = false
                syncForeground()
            } else {
                stopNow()
            }
        }
    }

    private fun stopNow() {
        if (stopped) return
        stopped = true
        handler.removeCallbacks(completeRunnable)
        handler.removeCallbacks(tickRunnable)
        completing = false
        lastShownEndsAt = Long.MIN_VALUE
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // Already gone.
        }
        startedForeground = false
        stopSelf(lastStartId)
    }

    companion object {
        /**
         * Test seam: sees each second that ticked, after the alert. Null in
         * production. Robolectric's clock is the only way to walk a rest
         * through 5, 4, 3, 2, 1 without listening to a speaker.
         */
        internal var tickObserver: ((Int) -> Unit)? = null

        const val ACTION_SYNC = "com.sinura.personaltrainer.timer.SYNC"
        const val ACTION_STOP = "com.sinura.personaltrainer.timer.STOP"
        const val ACTION_SKIP = "com.sinura.personaltrainer.timer.SKIP"
        const val ACTION_ADD_15 = "com.sinura.personaltrainer.timer.ADD_15"
        const val ACTION_MINUS_15 = "com.sinura.personaltrainer.timer.MINUS_15"
        const val EXTRA_SESSION_ID = "sessionId"
        const val EXTRA_TIMER_ID = "timerId"
    }
}
