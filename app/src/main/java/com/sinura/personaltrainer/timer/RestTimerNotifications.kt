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
import com.sinura.personaltrainer.domain.RestTimerMarkers
import com.sinura.personaltrainer.domain.RestTimerSnapshot

/**
 * Notification channels and the rest-done / rest-running notifications, owned
 * in one place so the alarm receiver and the service post exactly the same thing.
 */
object RestTimerNotifications {
    /**
     * v3 because a channel's importance and lock-screen behaviour cannot change
     * after creation. v2 stayed shade-only on Android 14+ when the running card
     * used [NotificationCompat.Builder.setSilent] — SystemUI treats that as a
     * silent/minimized notification and skips the lock-screen chronometer. This
     * channel is HIGH, soundless (no channel sound or vibration), and public.
     */
    const val CHANNEL_RUNNING = "rest_timer_running_v3"

    /** In-app FGS posts: no peek, no lock-screen prominence while Temper is open. */
    const val CHANNEL_RUNNING_IN_APP = "rest_timer_running_in_app_v4"

    /**
     * v3 because a channel's DND bypass cannot be relied on after creation.
     * v2 was silent (so RestTimerAlerts owns the cue) but `setBypassDnd(false)`,
     * so Do Not Disturb swallowed rest. This channel is silent, vibration-free,
     * and allowed through DND; the Settings sound toggle still gates the cue.
     * [ensureChannels] deletes the legacy ids.
     */
    const val CHANNEL_DONE = "rest_timer_done_v3"

    /** Cool-down done: no DND bypass, no full-screen intent. */
    const val CHANNEL_COOLDOWN_DONE = "rest_timer_cooldown_done_v1"

    private const val LEGACY_CHANNEL_RUNNING = "rest_timer_running"
    private const val LEGACY_CHANNEL_RUNNING_V2 = "rest_timer_running_v2"
    private const val LEGACY_CHANNEL_RUNNING_IN_APP = "rest_timer_running_in_app"
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
        try {
            manager.deleteNotificationChannel(LEGACY_CHANNEL_RUNNING_V2)
        } catch (_: Exception) {
            // Temper Debug builds that still hold the v2 running channel.
        }
        try {
            manager.deleteNotificationChannel(LEGACY_CHANNEL_RUNNING_IN_APP)
        } catch (_: Exception) {
            // Prior in-app channel id experiments.
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
        val quietName = context.getString(R.string.rest_channel_quiet_name)
        val quietDescription = context.getString(R.string.rest_channel_quiet_description)
        val inApp = NotificationChannel(
            CHANNEL_RUNNING_IN_APP,
            quietName,
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = quietDescription
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        val cooldownDone = NotificationChannel(
            CHANNEL_COOLDOWN_DONE,
            context.getString(R.string.rest_notification_cooldown_done_title),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.rest_notification_cooldown_done_body)
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(false)
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
        manager.createNotificationChannel(inApp)
        manager.createNotificationChannel(cooldownDone)
        manager.createNotificationChannel(done)
    }

    fun showDone(context: Context, sessionId: String?, isCooldown: Boolean = false) {
        val appContext = context.applicationContext
        ensureChannels(appContext)
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val builder = if (isCooldown) {
            NotificationCompat.Builder(appContext, CHANNEL_COOLDOWN_DONE)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(appContext.getString(R.string.rest_notification_cooldown_done_title))
                .setContentText(appContext.getString(R.string.rest_notification_cooldown_done_body))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openAppIntent(appContext, sessionId))
        } else {
            NotificationCompat.Builder(appContext, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle("Rest done")
                .setContentText("Back to the bar.")
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openAppIntent(appContext, sessionId))
                .also { restBuilder ->
                    // On API 34+ the USE_FULL_SCREEN_INTENT special access is denied by
                    // default; attaching the intent anyway made the system silently
                    // degrade the lock-screen glance. Attach only when the gate is open —
                    // the heads-up path above is the honest fallback.
                    if (canUseFullScreenIntent(appContext)) {
                        restBuilder.setFullScreenIntent(
                            lockScreenIntent(appContext, sessionId, finished = true),
                            true,
                        )
                    }
                }
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
        presentation: RestTimerRunningPresentation = RestTimerRunningPresentation.resolve(context),
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
        val channelId = runningChannelId(appContext, state, presentation)
        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(runningTitle(appContext, state))
            .setContentText(RestTimer.remainingCopy(remaining))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(lockScreenIntent(appContext, state.sessionId, finished = false))
        if (RestTimerMarkers.isCooldown(state)) {
            builder.addAction(
                0,
                "Skip",
                serviceIntent(appContext, RestTimerService.ACTION_SKIP, 13, state.timerId),
            )
        } else {
            builder
                .addAction(0, "−15s", serviceIntent(appContext, RestTimerService.ACTION_MINUS_15, 11))
                .addAction(0, "+15s", serviceIntent(appContext, RestTimerService.ACTION_ADD_15, 12))
                .addAction(0, "Skip", serviceIntent(appContext, RestTimerService.ACTION_SKIP, 13, state.timerId))
        }
        when (presentation) {
            RestTimerRunningPresentation.FOREGROUND_IN_APP -> {
                builder
                    .setSilent(true)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            }
            RestTimerRunningPresentation.BACKGROUND_UNLOCKED -> {
                if (RestTimerOverlayController.prefersQuietShade(appContext, state)) {
                    builder
                        .setSilent(true)
                        .setPriority(NotificationCompat.PRIORITY_MIN)
                        .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
                } else {
                    builder
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setCustomContentView(compact)
                        .setCustomBigContentView(expanded)
                        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            RestTimerRunningPresentation.LOCKED -> {
                // Debug 114 lock path — unchanged until owner picks a new lock UX.
                builder
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setCustomContentView(compact)
                    .setCustomBigContentView(expanded)
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCustomHeadsUpContentView(expanded)
                    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                if (live && canUseFullScreenIntent(appContext)) {
                    builder.setFullScreenIntent(
                        lockScreenIntent(appContext, state.sessionId, finished = false),
                        true,
                    )
                }
            }
        }
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

    fun runningTitle(context: Context, state: RestTimerSnapshot): String {
        val res = if (RestTimerMarkers.isCooldown(state)) {
            R.string.rest_notification_title_cooldown
        } else {
            R.string.rest_notification_title_rest
        }
        return context.getString(res)
    }

    /**
     * MIN channel while Temper is foreground or while the floating dot would show (overlay on,
     * not dismissed). Lock glance keeps the public HIGH running channel.
     */
    fun runningChannelId(
        context: Context,
        state: RestTimerSnapshot,
        presentation: RestTimerRunningPresentation,
    ): String = when (presentation) {
        RestTimerRunningPresentation.FOREGROUND_IN_APP -> CHANNEL_RUNNING_IN_APP
        RestTimerRunningPresentation.BACKGROUND_UNLOCKED ->
            if (RestTimerOverlayController.prefersQuietShade(context, state)) {
                CHANNEL_RUNNING_IN_APP
            } else {
                CHANNEL_RUNNING
            }
        RestTimerRunningPresentation.LOCKED -> CHANNEL_RUNNING
    }

    fun isRestDoneChannelEnabled(context: Context): Boolean {
        val manager = context.applicationContext
            .getSystemService(NotificationManager::class.java) ?: return true
        ensureChannels(context)
        val channel = manager.getNotificationChannel(CHANNEL_DONE) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
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

    /**
     * [timerId] names the rest the card shows. FLAG_UPDATE_CURRENT replaces it each time the
     * card is rebuilt, so a tap names the rest on screen, not a newer one the card has not
     * caught up with.
     */
    private fun serviceIntent(
        context: Context,
        action: String,
        requestCode: Int,
        timerId: String? = null,
    ): PendingIntent {
        val intent = Intent(context, RestTimerService::class.java).setAction(action)
        timerId?.let { intent.putExtra(RestTimerService.EXTRA_TIMER_ID, it) }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
