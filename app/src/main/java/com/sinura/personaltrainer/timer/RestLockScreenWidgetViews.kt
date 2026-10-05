package com.sinura.personaltrainer.timer

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.widget.RemoteViews
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestIdleCopy
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerSnapshot

/** Shared RemoteViews for Samsung lock-screen rest widgets (1×1 and 2×1). TextView-only. */
object RestLockScreenWidgetViews {
    fun remoteViewsWide(context: Context, state: RestTimerSnapshot): RemoteViews =
        build(context, state, R.layout.lockscreen_rest_2x1, wide = true)

    fun remoteViewsSmall(context: Context, state: RestTimerSnapshot): RemoteViews =
        build(context, state, R.layout.lockscreen_rest_1x1, wide = false)

    fun serviceBoxViews(context: Context, pageId: String, state: RestTimerSnapshot): RemoteViews {
        val wide = pageId == RestLockScreenServiceBoxReceiver.PAGE_REST_2X1
        val layoutId = if (wide) R.layout.lockscreen_rest_2x1 else R.layout.lockscreen_rest_1x1
        return build(context, state, layoutId, wide = wide)
    }

    private fun build(
        context: Context,
        state: RestTimerSnapshot,
        layoutId: Int,
        wide: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutId)
        val now = SystemClock.elapsedRealtime()
        val remaining = state.remainingSeconds(now).coerceAtLeast(0)
        val timeText = when {
            state.running -> RestTimer.formatClock(remaining)
            else -> RestIdleCopy.NOT_RUNNING
        }
        views.setTextViewText(R.id.lockscreen_rest_time, timeText)
        views.setTextColor(R.id.lockscreen_rest_time, Color.WHITE)
        if (wide) {
            views.setTextViewText(
                R.id.lockscreen_rest_kicker,
                context.getString(R.string.lockscreen_rest_widget_kicker),
            )
            views.setTextColor(R.id.lockscreen_rest_kicker, Color.WHITE)
        }
        return views
    }
}
