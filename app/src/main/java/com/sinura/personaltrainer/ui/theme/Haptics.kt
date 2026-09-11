package com.sinura.personaltrainer.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import kotlinx.coroutines.delay

/**
 * The haptic palette.
 *
 * There was not one `performHapticFeedback` call anywhere in the Compose layer before this.
 * The only vibration the product could produce was the rest-completion waveform inside the
 * foreground service — so the single most repeated physical act in the app, pressing "log
 * set" with chalked hands and the phone on the floor, acknowledged nothing at all.
 *
 * This deliberately goes through [View] rather than Compose's `LocalHapticFeedback`. On the
 * version of Compose this project pins, `HapticFeedbackType` offers only `LongPress` and
 * `TextHandleMove`; the expressive constants — confirm, reject, segment ticks — live on the
 * platform, where they can also be degraded sensibly on older releases. Every call routes
 * through the platform's own honouring of the user's haptics setting, so a phone with
 * touch feedback switched off stays silent without any checks here.
 *
 * Get the [View] with `LocalView.current`.
 */
object Haptics {
    /** A detent: one stepper increment, one chip, one scrub step. */
    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** A softer tick for rapid repeats, so press-and-hold does not buzz. */
    fun tickLight(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Last three seconds of rest: heavier than [tick], still not [commit]. */
    fun warn(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** A set landed, a workout finished: the weightiest confirmation available. */
    fun commit(view: View) {
        view.performHapticFeedback(confirmConstant())
    }

    /**
     * A rejected entry, or a disabled control that was pressed anyway.
     * Two beats — D-06 error — so a miss is not the same as a stepper tick.
     */
    fun reject(view: View) {
        val constant = rejectConstant()
        view.performHapticFeedback(constant)
        view.postDelayed({ view.performHapticFeedback(constant) }, ERROR_BEAT_GAP_MS)
    }

    /**
     * A record broke. Three beats. A refused entry is two ([reject]); this
     * is the only three-beat event.
     */
    suspend fun celebrate(view: View) {
        repeat(3) { index ->
            view.performHapticFeedback(confirmConstant())
            if (index < 2) delay(PR_BEAT_GAP_MS)
        }
    }

    /** CONFIRM is API 30; below that the heaviest thing available is a long press. */
    private fun confirmConstant(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }

    /** REJECT is API 30; below that a long press is the refusal that exists. */
    private fun rejectConstant(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
}

private const val PR_BEAT_GAP_MS = 90L
private const val ERROR_BEAT_GAP_MS = 90L
