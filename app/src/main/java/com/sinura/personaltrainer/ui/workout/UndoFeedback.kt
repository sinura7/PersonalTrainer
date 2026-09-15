package com.sinura.personaltrainer.ui.workout

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager

/**
 * Packet G haptics: delete/remove land with a light warning, a landed undo confirms.
 * The screen plays these; the ViewModel only names them.
 */
enum class DeleteFeedback {
    DELETED,
    REMOVED,
    UNDO,
}

/**
 * Packet G: the undo dwell honours the platform's recommended timeout.
 *
 * A seam so tests can fake TalkBack timing without an [AccessibilityManager].
 */
fun interface UndoTimeoutProvider {
    fun recommendedTimeoutMs(originalMs: Int): Long
}

/** Production seam: TalkBack / switch access may ask for longer than the 6 s base. */
fun systemUndoTimeout(context: Context): UndoTimeoutProvider = UndoTimeoutProvider { original ->
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@UndoTimeoutProvider original.toLong()
    try {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return@UndoTimeoutProvider original.toLong()
        manager.getRecommendedTimeoutMillis(
            original,
            AccessibilityManager.FLAG_CONTENT_CONTROLS or
                AccessibilityManager.FLAG_CONTENT_ICONS or
                AccessibilityManager.FLAG_CONTENT_TEXT,
        ).toLong()
    } catch (_: Exception) {
        original.toLong()
    }
}
