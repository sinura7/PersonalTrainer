package com.sinura.personaltrainer.timer

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * Samsung monotone lock-screen widget (2×1). The owner adds it once from the
 * lock-screen editor; updates are pushed while a rest runs.
 */
class LockScreenRestWideWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        RestLockScreenWidgetUpdater.updateAll(context)
    }
}
