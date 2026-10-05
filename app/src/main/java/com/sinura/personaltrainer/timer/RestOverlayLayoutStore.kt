package com.sinura.personaltrainer.timer

import android.content.Context
import kotlin.math.roundToInt

/** Persisted overlay position and size (session-long; cleared on [RestTimerOverlayController.release]). */
internal object RestOverlayLayoutStore {
    private const val PREFS = "rest_overlay_layout"
    private const val KEY_X = "x"
    private const val KEY_Y = "y"
    private const val KEY_SIZE_DP = "size_dp"

    const val DEFAULT_SIZE_DP = 168
    const val MIN_SIZE_DP = 120
    const val MAX_SIZE_DP = 280

    fun load(context: Context): SavedLayout {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return SavedLayout(
            x = prefs.getInt(KEY_X, Int.MIN_VALUE),
            y = prefs.getInt(KEY_Y, Int.MIN_VALUE),
            sizeDp = prefs.getInt(KEY_SIZE_DP, DEFAULT_SIZE_DP).coerceIn(MIN_SIZE_DP, MAX_SIZE_DP),
        )
    }

    fun savePosition(context: Context, x: Int, y: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_X, x)
            .putInt(KEY_Y, y)
            .apply()
    }

    fun saveSize(context: Context, sizeDp: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SIZE_DP, sizeDp.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP))
            .apply()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    data class SavedLayout(val x: Int, val y: Int, val sizeDp: Int)
}

internal fun Int.dpToPx(context: Context): Int =
    (this * context.resources.displayMetrics.density).roundToInt()
