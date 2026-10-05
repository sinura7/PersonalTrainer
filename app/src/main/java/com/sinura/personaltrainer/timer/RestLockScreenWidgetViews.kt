package com.sinura.personaltrainer.timer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import com.sinura.personaltrainer.ui.overlay.OVERLAY_VOLT_COLOR

/** Shared RemoteViews for Samsung lock-screen rest widgets (1×1 and 2×1). TextView-only. */
object RestLockScreenWidgetViews {
    private const val TAP_REQUEST_COMPACT = 71_001
    private const val TAP_REQUEST_WIDE = 71_002

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
        val kickerRunning = context.getString(R.string.lockscreen_rest_widget_kicker)
        val kickerDone = context.getString(R.string.rest_exterior_done_kicker)
        val model = RestExteriorDisplay.model(
            state = state,
            nowElapsedRealtime = SystemClock.elapsedRealtime(),
            kickerRunning = kickerRunning,
            kickerDone = kickerDone,
        )
        val timeColor = context.getColor(R.color.lock_widget_time_on_monotone)
        views.setTextViewText(R.id.lockscreen_rest_kicker, model.kicker)
        views.setTextColor(R.id.lockscreen_rest_kicker, OVERLAY_VOLT_COLOR)
        views.setTextViewText(R.id.lockscreen_rest_time, model.timeText)
        views.setTextColor(R.id.lockscreen_rest_time, timeColor)
        val tap = openWorkoutPendingIntent(
            context = context,
            state = state,
            requestCode = if (wide) TAP_REQUEST_WIDE else TAP_REQUEST_COMPACT,
        )
        views.setOnClickPendingIntent(R.id.lockscreen_rest_root, tap)
        return views
    }

    private fun openWorkoutPendingIntent(
        context: Context,
        state: RestTimerSnapshot,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            state.sessionId?.let { putExtra(RestTimerService.EXTRA_SESSION_ID, it) }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
