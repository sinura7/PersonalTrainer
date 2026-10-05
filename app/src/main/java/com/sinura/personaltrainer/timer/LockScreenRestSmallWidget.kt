package com.sinura.personaltrainer.timer

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** Samsung monotone lock-screen widget (1×1). Listed beside the 2×1 in One UI pickers. */
class LockScreenRestSmallWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        RestLockScreenWidgetUpdater.updateAll(context)
    }
}
