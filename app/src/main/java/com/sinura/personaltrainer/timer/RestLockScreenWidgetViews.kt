package com.sinura.personaltrainer.timer

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestIdleCopy
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerSnapshot

/** Shared RemoteViews for the Samsung lock-screen rest widget (2×1). */
object RestLockScreenWidgetViews {
    fun remoteViews(context: Context, state: RestTimerSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.lockscreen_rest_2x1)
        val now = SystemClock.elapsedRealtime()
        val remaining = state.remainingSeconds(now).coerceAtLeast(0)
        if (state.running && RestTimer.usesLiveChronometer(remaining)) {
            views.setTextViewText(R.id.lockscreen_rest_kicker, context.getString(R.string.rest_notification_kicker))
            views.setChronometerCountDown(R.id.lockscreen_rest_chrono, true)
            views.setChronometer(R.id.lockscreen_rest_chrono, state.endsAtElapsedRealtime, null, true)
        } else if (state.running) {
            views.setTextViewText(R.id.lockscreen_rest_kicker, context.getString(R.string.rest_notification_kicker))
            views.setChronometerCountDown(R.id.lockscreen_rest_chrono, true)
            views.setChronometer(R.id.lockscreen_rest_chrono, now, null, false)
            views.setTextViewText(R.id.lockscreen_rest_chrono, RestTimer.formatClock(remaining))
        } else {
            views.setTextViewText(R.id.lockscreen_rest_kicker, context.getString(R.string.rest_notification_kicker))
            views.setChronometerCountDown(R.id.lockscreen_rest_chrono, true)
            views.setChronometer(R.id.lockscreen_rest_chrono, now, null, false)
            views.setTextViewText(R.id.lockscreen_rest_chrono, RestIdleCopy.NOT_RUNNING)
        }
        return views
    }
}
