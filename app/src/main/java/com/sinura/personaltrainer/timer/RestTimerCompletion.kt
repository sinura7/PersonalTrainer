package com.sinura.personaltrainer.timer

import android.app.NotificationManager
import android.content.Context
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.RestTimerPreferences
import kotlinx.coroutines.flow.first

/**
 * The one place rest completion happens.
 *
 * Three independent paths can notice that rest is over — the wakeup alarm (the reliable one,
 * screen off), the service's 250ms tick (screen on), and the service's in-process backstop —
 * and all of them funnel here. [completeOnce] makes that safe: whichever arrives first wins
 * and the others are no-ops, so the user never gets a doubled alert.
 *
 * The guard is keyed on the timer's end instant rather than a plain boolean so that a genuine
 * NEXT rest is never swallowed by the previous one's completion.
 */
object RestTimerCompletion {
    private val lock = Any()
    private var lastCompletedEndsAt = Long.MIN_VALUE

    /**
     * @return true if this call performed the completion, false if another path already did.
     */
    suspend fun completeOnce(
        context: Context,
        endsAtElapsedRealtime: Long,
        sessionId: String?,
    ): Boolean {
        synchronized(lock) {
            if (lastCompletedEndsAt == endsAtElapsedRealtime) return false
            lastCompletedEndsAt = endsAtElapsedRealtime
        }
        val appContext = context.applicationContext
        val app = appContext as? PersonalTrainerApp

        // Stop the countdown before announcing, so the ongoing notification and its
        // ±15s / Skip actions cannot be tapped into a resurrected timer mid-alert.
        app?.container?.restTimerController?.stop(fromService = true)
        try {
            appContext.getSystemService(NotificationManager::class.java)
                ?.cancel(RestTimerNotifications.RUNNING_ID)
        } catch (_: Exception) {
            // Notification manager unavailable; the service teardown still removes it.
        }

        val prefs = try {
            app?.container?.preferencesRepository?.restTimerPreferences?.first()
                ?: RestTimerPreferences.DEFAULT
        } catch (_: Exception) {
            RestTimerPreferences.DEFAULT
        }

        RestTimerAlerts.announce(appContext, prefs)
        RestTimerNotifications.showDone(appContext, sessionId)
        return true
    }

    /** Lets a fresh rest complete normally after a previous one was announced. */
    fun reset() {
        synchronized(lock) { lastCompletedEndsAt = Long.MIN_VALUE }
    }
}
