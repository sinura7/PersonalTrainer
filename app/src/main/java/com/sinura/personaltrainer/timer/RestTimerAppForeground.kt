package com.sinura.personaltrainer.timer

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Whether Temper's process is in the foreground (any activity visible).
 * Used to suppress rest notification peeking while the owner is still in the app.
 */
object RestTimerAppForeground {
    @Volatile
    var isInForeground: Boolean = false
        private set

    /** Robolectric tests; production uses [install]. */
    internal fun setForegroundForTest(foreground: Boolean) {
        isInForeground = foreground
    }

    private val listeners = mutableSetOf<() -> Unit>()

    fun install(application: Application) {
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
    }
}
