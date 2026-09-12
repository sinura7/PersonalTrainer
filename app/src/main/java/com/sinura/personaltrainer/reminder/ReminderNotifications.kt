package com.sinura.personaltrainer.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.ScheduleOccurrence

/**
 * Best-effort workout reminders. Not the rest-timer channel, and never
 * an exact-alarm path (ADR-012).
 */
object ReminderNotifications {
    const val CHANNEL_ID = "workout_reminders"
    const val ACTION_START = "com.sinura.personaltrainer.REMINDER_START"
    const val ACTION_SNOOZE = "com.sinura.personaltrainer.REMINDER_SNOOZE"
    const val ACTION_MOVE = "com.sinura.personaltrainer.REMINDER_MOVE"
    const val ACTION_SKIP = "com.sinura.personaltrainer.REMINDER_SKIP"
    const val EXTRA_OCCURRENCE_ID = "occurrence_id"
    const val EXTRA_REVIEW_OCCURRENCE_ID = "review_occurrence_id"
    const val EXTRA_DELIVERY_ID = "delivery_id"
    private const val ALARM_NOTIFICATION_ID = 0x71A1

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Best-effort reminders for planned sessions"
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun show(context: Context, occurrence: ScheduleOccurrence, deliveryId: String, title: String) {
        val app = context.applicationContext
        if (!canNotify(app)) return
        ensureChannel(app)
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Time to train")
            .setContentText(title)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(app, occurrence.id))
            .addAction(0, "Start", startApp(app, occurrence.id, deliveryId))
            .addAction(0, "Snooze", actionIntent(app, ACTION_SNOOZE, occurrence.id, deliveryId))
            .addAction(0, "Move", actionIntent(app, ACTION_MOVE, occurrence.id, deliveryId))
            .addAction(0, "Skip", actionIntent(app, ACTION_SKIP, occurrence.id, deliveryId))
            .build()
        try {
            manager.notify(occurrence.id.hashCode(), notification)
        } catch (_: Exception) {
            // Permission denied after the check, or the manager is gone.
        }
    }

    /**
     * Per-day Settings alarm. Rest alerts stay on their own channel.
     * When a planned session exists, Start opens it; otherwise the tap
     * just opens Home.
     */
    fun showWorkoutAlarm(context: Context, occurrenceId: String?, title: String) {
        val app = context.applicationContext
        if (!canNotify(app)) return
        ensureChannel(app)
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        val builder = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Time to train")
            .setContentText(title)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openHome(app))
        if (occurrenceId != null) {
            builder.setContentIntent(openApp(app, occurrenceId))
            builder.addAction(0, "Start", startApp(app, occurrenceId, "alarm-$occurrenceId"))
        }
        try {
            manager.notify(ALARM_NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {
            // Permission denied after the check, or the manager is gone.
        }
    }

    fun cancel(context: Context, occurrenceId: String) {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(occurrenceId.hashCode())
    }

    /** Reads and strips the extra so one tap cannot be delivered twice. */
    fun consumeOccurrenceId(intent: Intent?): String? {
        val id = intent?.getStringExtra(EXTRA_OCCURRENCE_ID) ?: return null
        intent.removeExtra(EXTRA_OCCURRENCE_ID)
        return id.takeIf { it.isNotBlank() }
    }

    /**
     * Body tap: show the day and the ADR-018 confirm. Never starts,
     * and never marks the delivery STARTED.
     */
    fun consumeReviewOccurrenceId(intent: Intent?): String? {
        val id = intent?.getStringExtra(EXTRA_REVIEW_OCCURRENCE_ID) ?: return null
        intent.removeExtra(EXTRA_REVIEW_OCCURRENCE_ID)
        return id.takeIf { it.isNotBlank() }
    }

    /** Reads and strips the delivery id a Start-action launch carries. */
    fun consumeStartedDeliveryId(intent: Intent?): String? {
        val id = intent?.getStringExtra(EXTRA_DELIVERY_ID) ?: return null
        intent.removeExtra(EXTRA_DELIVERY_ID)
        return id.takeIf { it.isNotBlank() }
    }

    /**
     * The Start action. An activity PendingIntent, not the broadcast
     * receiver: since API 31 a receiver cannot launch an activity from a
     * notification action — the system silently drops it, so the old
     * trampoline consumed the tap, dismissed the notification, and opened
     * nothing. MainActivity marks the delivery STARTED when it consumes
     * the extras.
     */
    internal fun startLaunchIntent(
        context: Context,
        occurrenceId: String,
        deliveryId: String,
    ): Intent = Intent(context, MainActivity::class.java).apply {
        action = ACTION_START
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
        putExtra(EXTRA_DELIVERY_ID, deliveryId)
    }

    /**
     * Content tap. Distinct extra so looking at the day does not start
     * the session the Start action owns.
     */
    internal fun reviewLaunchIntent(context: Context, occurrenceId: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_REVIEW_OCCURRENCE_ID, occurrenceId)
        }

    private fun openHome(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            ALARM_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun startApp(
        context: Context,
        occurrenceId: String,
        deliveryId: String,
    ): PendingIntent {
        return PendingIntent.getActivity(
            context,
            ("start" + occurrenceId).hashCode(),
            startLaunchIntent(context, occurrenceId, deliveryId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openApp(context: Context, occurrenceId: String): PendingIntent {
        return PendingIntent.getActivity(
            context,
            occurrenceId.hashCode(),
            reviewLaunchIntent(context, occurrenceId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(
        context: Context,
        action: String,
        occurrenceId: String,
        deliveryId: String,
    ): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(EXTRA_DELIVERY_ID, deliveryId)
        }
        return PendingIntent.getBroadcast(
            context,
            (action + occurrenceId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
