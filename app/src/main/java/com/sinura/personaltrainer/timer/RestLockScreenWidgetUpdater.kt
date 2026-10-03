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
        val component = ComponentName(appContext, LockScreenRestWideWidget::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        val views = RestLockScreenWidgetViews.remoteViews(appContext, snapshot)
        ids.forEach { id -> manager.updateAppWidget(id, views) }
    }
}
