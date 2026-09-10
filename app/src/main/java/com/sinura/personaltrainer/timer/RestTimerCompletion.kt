package com.sinura.personaltrainer.timer

import android.content.Context
import android.os.SystemClock
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.RestTimerClaim
import com.sinura.personaltrainer.domain.RestTimerClaimLedger
import com.sinura.personaltrainer.domain.RestTimerPreferences
import kotlinx.coroutines.flow.first

/**
 * The one place rest completion happens.
 *
 * Three independent paths can notice that rest is over — the wakeup alarm (the reliable one,
 * screen off), the service's deadline runnable (screen on), and same-boot rehydration of an expired
 * rest — and all of them funnel here. [completeOnce] claims by timer id: whichever matching,
 * due attempt arrives first wins and the others are no-ops.
 */
object RestTimerCompletion {
    internal val ledger = RestTimerClaimLedger()

    /**
     * @return true if this call performed the completion, false if another path already did
     *   or the attempt was stale/early.
     */
    suspend fun completeOnce(
        context: Context,
        incomingTimerId: String,
        expectedTimerId: String,
        deadlineElapsedRealtime: Long,
        sessionId: String?,
        nowElapsedRealtime: Long = SystemClock.elapsedRealtime(),
        playCue: Boolean = true,
    ): Boolean {
        val decision = ledger.decide(
            incomingId = incomingTimerId,
            expectedId = expectedTimerId,
            nowElapsedRealtime = nowElapsedRealtime,
            deadlineElapsedRealtime = deadlineElapsedRealtime,
        )
        when (decision) {
            RestTimerClaim.STALE, RestTimerClaim.ALREADY_CLAIMED -> return false
            RestTimerClaim.EARLY -> {
                val app = context.applicationContext as? PersonalTrainerApp
                app?.container?.restTimerController?.rescheduleCurrent()
                return false
            }
            RestTimerClaim.CLAIMED -> Unit
        }
        val appContext = context.applicationContext
        val app = appContext as? PersonalTrainerApp

        // Stop the countdown before announcing, so the ongoing notification and its
        // ±15s / Skip actions cannot be tapped into a resurrected timer mid-alert.
        // Id-checked: a +15s tapped between the claim and this line minted a NEW
        // timer, and announcing done over it would wipe the extension the user
        // just bought — that newer timer's own alarm owns its completion.
        val stopped = app?.container?.restTimerController
            ?.completeIfCurrent(incomingTimerId, fromService = true)
            ?: true
        if (!stopped) return false

        if (playCue) {
            val prefs = try {
                app?.container?.preferencesRepository?.restTimerPreferences?.first()
                    ?: RestTimerPreferences.DEFAULT
            } catch (_: Exception) {
                RestTimerPreferences.DEFAULT
            }
            RestTimerAlerts.announce(appContext, prefs)
        }
        RestTimerNotifications.showDone(appContext, sessionId)
        return true
    }

    /** Test seam. Production claims by id, so a new rest does not need this. */
    fun reset() {
        ledger.reset()
    }
}
