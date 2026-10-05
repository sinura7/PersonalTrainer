package com.sinura.personaltrainer.timer

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestIdleCopy
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerSnapshot

/** Shared RemoteViews for Samsung lock-screen rest widgets (1×1 and 2×1). */
object RestLockScreenWidgetViews {
    fun remoteViewsWide(context: Context, state: RestTimerSnapshot): RemoteViews =
        remoteViews(context, state, R.layout.lockscreen_rest_2x1, wide = true)

    fun remoteViewsSmall(context: Context, state: RestTimerSnapshot): RemoteViews =
        remoteViews(context, state, R.layout.lockscreen_rest_1x1, wide = false)

    private fun remoteViews(
        context: Context,
        state: RestTimerSnapshot,
        layoutId: Int,
        wide: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutId)
        val now = SystemClock.elapsedRealtime()
        val remaining = state.remainingSeconds(now).coerceAtLeast(0)
        if (wide) {
            views.setTextViewText(R.id.lockscreen_rest_kicker, context.getString(R.string.rest_notification_kicker))
        }
        if (state.running && RestTimer.usesLiveChronometer(remaining)) {
            views.setChronometerCountDown(R.id.lockscreen_rest_chrono, true)
            views.setChronometer(R.id.lockscreen_rest_chrono, state.endsAtElapsedRealtime, null, true)
        } else if (state.running) {
            views.setChronometerCountDown(R.id.lockscreen_rest_chrono, true)
            views.setChronometer(R.id.lockscreen_rest_chrono, now, null, false)
            views.setTextViewText(R.id.lockscreen_rest_chrono, RestTimer.formatClock(remaining))
        } else {
            views.setChronometerCountDown(R.id.lockscreen_rest_chrono, true)
            views.setChronometer(R.id.lockscreen_rest_chrono, now, null, false)
            views.setTextViewText(R.id.lockscreen_rest_chrono, RestIdleCopy.NOT_RUNNING)
        }
        return views
    }
}
