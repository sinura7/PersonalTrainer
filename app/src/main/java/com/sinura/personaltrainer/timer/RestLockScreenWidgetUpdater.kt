package com.sinura.personaltrainer.timer

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.RestTimerSnapshot

/**
 * Pushes the live rest snapshot to installed lock-screen widgets. Same store
 * as [RestTimerOverlayController] and the foreground service.
 */
object RestLockScreenWidgetUpdater {
    fun updateAll(context: Context, state: RestTimerSnapshot? = null) {
        val appContext = context.applicationContext
        val snapshot = state ?: (appContext as? PersonalTrainerApp)
            ?.container
            ?.restTimerController
            ?.snapshot
            ?.value
            ?: return
        val manager = appContext.getSystemService(AppWidgetManager::class.java) ?: return
        push(
            manager = manager,
            appContext = appContext,
            widgetClass = LockScreenRestWideWidget::class.java,
            views = RestLockScreenWidgetViews.remoteViewsWide(appContext, snapshot),
        )
        push(
            manager = manager,
            appContext = appContext,
            widgetClass = LockScreenRestSmallWidget::class.java,
            views = RestLockScreenWidgetViews.remoteViewsSmall(appContext, snapshot),
        )
    }

    private fun push(
        manager: AppWidgetManager,
        appContext: Context,
        widgetClass: Class<*>,
        views: android.widget.RemoteViews,
    ) {
        val ids = manager.getAppWidgetIds(ComponentName(appContext, widgetClass))
        if (ids.isEmpty()) return
        ids.forEach { id -> manager.updateAppWidget(id, views) }
    }
}
