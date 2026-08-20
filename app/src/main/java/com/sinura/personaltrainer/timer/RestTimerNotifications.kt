package com.sinura.personaltrainer.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.R

/**
 * Notification channels and the rest-done notification, owned in one place so the alarm
 * receiver and the service post exactly the same thing.
 */
object RestTimerNotifications {
    const val CHANNEL_RUNNING = "rest_timer_running"

    /**
     * v2 because a channel's sound cannot be changed after creation. The original
     * "rest_timer_done" channel was created at IMPORTANCE_HIGH with the system default
     * sound, so it played its own tone on top of (or instead of) the app's — meaning the
     * in-app sound toggle did not actually control the alert. This channel is silent and
     * vibration-free by design: RestTimerAlerts owns the cue, gated on the user's
     * preferences, and [ensureChannels] deletes the legacy channel.
     */
    const val CHANNEL_DONE = "rest_timer_done_v2"

    private const val LEGACY_CHANNEL_DONE = "rest_timer_done"
    const val RUNNING_ID = 4101
    const val DONE_ID = 4102

    fun ensureChannels(context: Context) {
        val manager = context.applicationContext
            .getSystemService(NotificationManager::class.java) ?: return
        try {
            manager.deleteNotificationChannel(LEGACY_CHANNEL_DONE)
        } catch (_: Exception) {
            // Never existed on a fresh install.
        }
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
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(false)
        }
        manager.createNotificationChannel(running)
        manager.createNotificationChannel(done)
    }

    fun showDone(context: Context, sessionId: String?) {
        val appContext = context.applicationContext
        ensureChannels(appContext)
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(appContext, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Rest done")
            .setContentText("Back to the bar.")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(appContext, sessionId))
            .build()
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
}
