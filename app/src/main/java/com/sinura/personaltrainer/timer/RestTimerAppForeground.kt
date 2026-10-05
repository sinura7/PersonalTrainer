package com.sinura.personaltrainer.timer

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Whether Temper's UI is visible. Activity callbacks fire as soon as the last
 * activity stops (Home / recents), before ProcessLifecycleOwner's delayed stop.
 */
object RestTimerAppForeground {
    @Volatile
    var isInForeground: Boolean = false
        private set

    private var application: Application? = null
    private var startedActivities = 0
    private val listeners = mutableSetOf<() -> Unit>()

    /** Robolectric tests; production uses [install]. */
    internal fun setForegroundForTest(foreground: Boolean) {
        isInForeground = foreground
    }

    fun install(app: Application) {
        application = app
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    startedActivities++
                    if (startedActivities == 1) setForeground(true)
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities = (startedActivities - 1).coerceAtLeast(0)
                    if (startedActivities == 0) setForeground(false)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    setForeground(true)
                }

                override fun onStop(owner: LifecycleOwner) {
                    setForeground(false)
                }
            },
        )
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun setForeground(foreground: Boolean) {
        if (isInForeground == foreground) return
        isInForeground = foreground
        listeners.forEach { it.invoke() }
        pingExteriorSync()
    }

    private fun pingExteriorSync() {
        val app = application ?: return
        val intent = Intent(app, RestTimerService::class.java)
            .setAction(RestTimerService.ACTION_EXTERIOR_SYNC)
        runCatching {
            ContextCompat.startForegroundService(app, intent)
        }.onFailure {
            runCatching { app.startService(intent) }
        }
    }
}
