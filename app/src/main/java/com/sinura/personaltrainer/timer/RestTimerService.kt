package com.sinura.personaltrainer.timer

import android.app.Notification
import android.app.NotificationChannel
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
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RestTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val completeRunnable = Runnable { onComplete() }
    private val controller: RestTimerController
        get() = (application as PersonalTrainerApp).container.restTimerController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
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
        if (!state.running || state.remainingSeconds(SystemClock.elapsedRealtime()) <= 0) {
            if (state.running) {
                onComplete()
            } else {
                stopNow()
            }
            return
        }
        ensureChannels()
        getSystemService(NotificationManager::class.java)?.cancel(DONE_ID)
        val notification = runningNotification(state)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                RUNNING_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(RUNNING_ID, notification)
        }
        handler.removeCallbacks(completeRunnable)
        handler.postAtTime(completeRunnable, state.endsAtElapsedRealtime)
    }

    private fun onComplete() {
        handler.removeCallbacks(completeRunnable)
        val sessionId = controller.snapshot.value.sessionId
        controller.stop(fromService = true)
        scope.launch {
            val prefs = try {
                (application as PersonalTrainerApp).container.preferencesRepository.restTimerPreferences.first()
            } catch (_: Exception) {
                RestTimerPreferences.DEFAULT
            }
            RestTimerAlerts.announce(applicationContext, prefs)
            showDoneNotification(sessionId)
            stopNow()
        }
    }

    private fun stopNow() {
        handler.removeCallbacks(completeRunnable)
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // Already gone.
        }
        stopSelf()
    }

    private fun runningNotification(state: RestTimerSnapshot): Notification {
        val remainingMs = (state.endsAtElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val endAtWall = System.currentTimeMillis() + remainingMs
        val remainingLabel = RestTimer.formatClock(state.remainingSeconds(SystemClock.elapsedRealtime()))
        return NotificationCompat.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Rest")
            .setContentText("$remainingLabel remaining")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endAtWall)
            .setShowWhen(true)
            .setContentIntent(openAppIntent(state.sessionId))
            .addAction(0, "−15s", serviceIntent(ACTION_MINUS_15, 11))
            .addAction(0, "+15s", serviceIntent(ACTION_ADD_15, 12))
            .addAction(0, "Skip", serviceIntent(ACTION_SKIP, 13))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun showDoneNotification(sessionId: String?) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Rest done")
            .setContentText("Back to the bar.")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(sessionId))
            .build()
        try {
            manager.notify(DONE_ID, notification)
        } catch (_: Exception) {
            // Notification permission may be denied.
        }
    }

    private fun openAppIntent(sessionId: String?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            sessionId?.let { putExtra(EXTRA_SESSION_ID, it) }
        }
        return PendingIntent.getActivity(
            this,
            20,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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

    private fun ensureChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val running = NotificationChannel(
            CHANNEL_RUNNING,
            "Rest timer",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows remaining rest while you train"
            setShowBadge(false)
        }
        val done = NotificationChannel(
            CHANNEL_DONE,
            "Rest complete",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts when rest is over"
        }
        manager.createNotificationChannel(running)
        manager.createNotificationChannel(done)
    }

    companion object {
        const val ACTION_SYNC = "com.sinura.personaltrainer.timer.SYNC"
        const val ACTION_STOP = "com.sinura.personaltrainer.timer.STOP"
        const val ACTION_SKIP = "com.sinura.personaltrainer.timer.SKIP"
        const val ACTION_ADD_15 = "com.sinura.personaltrainer.timer.ADD_15"
        const val ACTION_MINUS_15 = "com.sinura.personaltrainer.timer.MINUS_15"
        const val EXTRA_SESSION_ID = "sessionId"
        private const val CHANNEL_RUNNING = "rest_timer_running"
        private const val CHANNEL_DONE = "rest_timer_done"
        private const val RUNNING_ID = 4101
        private const val DONE_ID = 4102
    }
}
