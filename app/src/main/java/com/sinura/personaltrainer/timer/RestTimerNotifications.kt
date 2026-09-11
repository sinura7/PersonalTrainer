package com.sinura.personaltrainer.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerSnapshot

/**
 * Notification channels and the rest-done / rest-running notifications, owned
 * in one place so the alarm receiver and the service post exactly the same thing.
 */
object RestTimerNotifications {
    /**
     * v2 because a channel's importance cannot be changed after creation. The
     * original `rest_timer_running` channel was IMPORTANCE_LOW, so the
     * countdown never showed on the lock screen. This channel is HIGH, silent,
     * and public: SystemUI can draw a chronometer while the screen is off.
     */
    const val CHANNEL_RUNNING = "rest_timer_running_v2"

    /**
     * v3 because a channel's DND bypass cannot be relied on after creation.
     * v2 was silent (so RestTimerAlerts owns the cue) but `setBypassDnd(false)`,
     * so Do Not Disturb swallowed rest. This channel is silent, vibration-free,
     * and allowed through DND; the Settings sound toggle still gates the cue.
     * [ensureChannels] deletes the legacy ids.
     */
    const val CHANNEL_DONE = "rest_timer_done_v3"

    private const val LEGACY_CHANNEL_RUNNING = "rest_timer_running"
    private const val LEGACY_CHANNEL_DONE = "rest_timer_done"
    private const val LEGACY_CHANNEL_DONE_V2 = "rest_timer_done_v2"
    const val RUNNING_ID = 4101
    const val DONE_ID = 4102

    const val EXTRA_FINISHED = "restLockFinished"

    fun ensureChannels(context: Context) {
        val manager = context.applicationContext
            .getSystemService(NotificationManager::class.java) ?: return
        try {
            manager.deleteNotificationChannel(LEGACY_CHANNEL_DONE)
        } catch (_: Exception) {
            // Never existed on a fresh install.
        }
        try {
            manager.deleteNotificationChannel(LEGACY_CHANNEL_DONE_V2)
        } catch (_: Exception) {
            // Temper Debug that still holds the v2 done channel.
        }
        try {
            manager.deleteNotificationChannel(LEGACY_CHANNEL_RUNNING)
        } catch (_: Exception) {
            // Never existed on a fresh install.
        }
        val running = NotificationChannel(
            CHANNEL_RUNNING,
            "Rest timer",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Lock-screen countdown while you rest"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val done = NotificationChannel(
            CHANNEL_DONE,
            "Rest complete",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts when rest is over"
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(running)
        manager.createNotificationChannel(done)
    }

    fun showDone(context: Context, sessionId: String?) {
        val appContext = context.applicationContext
        ensureChannels(appContext)
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val builder = NotificationCompat.Builder(appContext, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Rest done")
            .setContentText("Back to the bar.")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(appContext, sessionId))
        // On API 34+ the USE_FULL_SCREEN_INTENT special access is denied by
        // default; attaching the intent anyway made the system silently
        // degrade the lock-screen glance. Attach only when the gate is open —
        // the heads-up path above is the honest fallback.
        if (canUseFullScreenIntent(appContext)) {
            builder.setFullScreenIntent(lockScreenIntent(appContext, sessionId, finished = true), true)
        }
        val notification = builder.build()
        try {
            manager.notify(DONE_ID, notification)
        } catch (_: Exception) {
            // POST_NOTIFICATIONS may be denied; the sound and vibration still fired.
        }
    }

    fun cancelDone(context: Context) {
        try {
            context.applicationContext
                .getSystemService(NotificationManager::class.java)
                ?.cancel(DONE_ID)
        } catch (_: Exception) {
            // Notification manager may be unavailable.
        }
    }

    fun runningNotification(
        context: Context,
        state: RestTimerSnapshot,
        nowElapsedRealtime: Long = SystemClock.elapsedRealtime(),
        nowWallClockMillis: Long = System.currentTimeMillis(),
    ): Notification {
        val appContext = context.applicationContext
        val remaining = state.remainingSeconds(nowElapsedRealtime).coerceAtLeast(0)
        val live = RestTimer.usesLiveChronometer(remaining)
        val compact = restRemoteViews(
            context = appContext,
            layoutId = R.layout.notification_rest_running,
            state = state,
            remainingSeconds = remaining,
            nowElapsedRealtime = nowElapsedRealtime,
        )
        val expanded = restRemoteViews(
            context = appContext,
            layoutId = R.layout.notification_rest_running_big,
            state = state,
            remainingSeconds = remaining,
            nowElapsedRealtime = nowElapsedRealtime,
        )
        val builder = NotificationCompat.Builder(appContext, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Rest")
            .setContentText(RestTimer.remainingCopy(remaining))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCustomContentView(compact)
            .setCustomBigContentView(expanded)
            .setCustomHeadsUpContentView(expanded)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(lockScreenIntent(appContext, state.sessionId, finished = false))
            .addAction(0, "−15s", serviceIntent(appContext, RestTimerService.ACTION_MINUS_15, 11))
            .addAction(0, "+15s", serviceIntent(appContext, RestTimerService.ACTION_ADD_15, 12))
            .addAction(0, "Skip", serviceIntent(appContext, RestTimerService.ACTION_SKIP, 13))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (live) {
            val whenMillis = RestTimer.endsAtWallClockMillis(
                endsAtElapsedRealtime = state.endsAtElapsedRealtime,
                nowElapsedRealtime = nowElapsedRealtime,
                nowWallClockMillis = nowWallClockMillis,
            )
            builder
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setWhen(whenMillis)
        } else {
            // Frozen 0:00. A live countdown whose base is already past paints
            // minus seconds in the shade until the card is cancelled (A-03).
            builder.setUsesChronometer(false).setShowWhen(false)
        }
        return builder.build()
    }

    fun openAppIntent(context: Context, sessionId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            sessionId?.let { putExtra(RestTimerService.EXTRA_SESSION_ID, it) }
        }
        return PendingIntent.getActivity(
            context,
            20,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun lockScreenIntent(
        context: Context,
        sessionId: String?,
        finished: Boolean,
    ): PendingIntent {
        val intent = Intent(context, RestLockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
            sessionId?.let { putExtra(RestTimerService.EXTRA_SESSION_ID, it) }
            putExtra(EXTRA_FINISHED, finished)
        }
        return PendingIntent.getActivity(
            context,
            if (finished) 22 else 21,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return try {
            val manager = context.applicationContext
                .getSystemService(NotificationManager::class.java)
            manager?.canUseFullScreenIntent() == true
        } catch (_: Exception) {
            false
        }
    }

    private fun restRemoteViews(
        context: Context,
        layoutId: Int,
        state: RestTimerSnapshot,
        remainingSeconds: Int,
        nowElapsedRealtime: Long,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutId)
        val clock = RestTimer.formatClock(remainingSeconds)
        views.setTextViewText(R.id.rest_chrono, clock)
        if (RestTimer.usesLiveChronometer(remainingSeconds)) {
            views.setChronometerCountDown(R.id.rest_chrono, true)
            views.setChronometer(R.id.rest_chrono, state.endsAtElapsedRealtime, null, true)
        } else {
            views.setChronometerCountDown(R.id.rest_chrono, true)
            views.setChronometer(R.id.rest_chrono, nowElapsedRealtime, null, false)
            views.setTextViewText(R.id.rest_chrono, clock)
        }
        return views
    }

    private fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, RestTimerService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
