// androidx.core.app / androidx.core.content — declaration-only stubs. See compile-stubs/README.md.
// Nothing here executes.
//
// DELIBERATELY NARROWER: NotificationCompat.Builder has ~90 setters; only the 21 this repo
// calls are declared, each with the real parameter type. A newly used setter is a visible RED.
// Every setter returns Builder so the fluent chains in timer/RestTimerNotifications.kt and
// reminder/ReminderNotifications.kt type-check, and build() returns a real android.app
// .Notification (from the Robolectric android-all jar), so a wrong notification type at a
// NotificationManager.notify call site is still caught.

package androidx.core.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.widget.RemoteViews

class NotificationCompat private constructor() {
    open class Style internal constructor()

    class DecoratedCustomViewStyle : Style()

    class Builder(context: android.content.Context, channelId: String) {
        fun setSmallIcon(icon: Int): Builder = TODO("compile-only stub")
        fun setContentTitle(title: CharSequence?): Builder = TODO("compile-only stub")
        fun setContentText(text: CharSequence?): Builder = TODO("compile-only stub")
        fun setAutoCancel(autoCancel: Boolean): Builder = TODO("compile-only stub")
        fun setOngoing(ongoing: Boolean): Builder = TODO("compile-only stub")
        fun setOnlyAlertOnce(onlyAlertOnce: Boolean): Builder = TODO("compile-only stub")
        fun setSilent(silent: Boolean): Builder = TODO("compile-only stub")
        fun setCategory(category: String?): Builder = TODO("compile-only stub")
        fun setVisibility(visibility: Int): Builder = TODO("compile-only stub")
        fun setPriority(pri: Int): Builder = TODO("compile-only stub")
        fun setUsesChronometer(b: Boolean): Builder = TODO("compile-only stub")
        fun setChronometerCountDown(countDown: Boolean): Builder = TODO("compile-only stub")
        fun setShowWhen(show: Boolean): Builder = TODO("compile-only stub")
        fun setWhen(whenMs: Long): Builder = TODO("compile-only stub")
        fun setContentIntent(intent: PendingIntent?): Builder = TODO("compile-only stub")
        fun setFullScreenIntent(intent: PendingIntent?, highPriority: Boolean): Builder =
            TODO("compile-only stub")
        fun setCustomContentView(contentView: RemoteViews?): Builder = TODO("compile-only stub")
        fun setCustomBigContentView(contentView: RemoteViews?): Builder = TODO("compile-only stub")
        fun setCustomHeadsUpContentView(contentView: RemoteViews?): Builder = TODO("compile-only stub")
        fun setStyle(style: Style?): Builder = TODO("compile-only stub")
        fun addAction(icon: Int, title: CharSequence?, intent: PendingIntent?): Builder =
            TODO("compile-only stub")
        fun setForegroundServiceBehavior(behavior: Int): Builder = TODO("compile-only stub")
        fun build(): Notification = TODO("compile-only stub")
    }

    companion object {
        const val CATEGORY_ALARM: String = "alarm"
        const val CATEGORY_REMINDER: String = "reminder"
        const val CATEGORY_STOPWATCH: String = "stopwatch"
        const val VISIBILITY_PUBLIC: Int = 1
        const val PRIORITY_HIGH: Int = 1
        const val FOREGROUND_SERVICE_IMMEDIATE: Int = 1
    }
}

object ServiceCompat {
    const val STOP_FOREGROUND_REMOVE: Int = 1

    @JvmStatic
    fun startForeground(
        service: Service,
        id: Int,
        notification: Notification,
        foregroundServiceType: Int,
    ) { TODO("compile-only stub") }

    @JvmStatic
    fun stopForeground(service: Service, flags: Int) { TODO("compile-only stub") }
}
