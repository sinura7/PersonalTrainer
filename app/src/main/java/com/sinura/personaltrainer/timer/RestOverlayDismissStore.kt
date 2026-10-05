package com.sinura.personaltrainer.timer

import android.content.Context

/** User hid the floating pill for the current rest; cleared when the rest ends. */
internal object RestOverlayDismissStore {
    private const val PREFS = "rest_overlay_dismiss"
    private const val KEY_TIMER_ID = "timer_id"

    fun dismissForRest(context: Context, timerId: String) {
        if (timerId.isBlank()) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TIMER_ID, timerId)
            .apply()
    }

    fun isDismissedForRest(context: Context, timerId: String): Boolean {
        if (timerId.isBlank()) return false
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TIMER_ID, null) == timerId
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
