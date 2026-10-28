package com.sinura.personaltrainer.testonly

import com.sinura.personaltrainer.BuildConfig

/**
 * Debug-only: extends the planned-rest window after a logged set so UX walkthrough
 * captures can show REST_IDLE before auto-start. Gym-floor release builds never set this.
 */
object WalkthroughRestCaptureHook {
    @Volatile
    private var holdPlannedRestAfterLogMs: Long = 0L

    fun setHoldPlannedRestAfterLogMs(ms: Long) {
        if (!BuildConfig.DEBUG) return
        holdPlannedRestAfterLogMs = ms.coerceAtLeast(0L)
    }

    internal fun extraDelayAfterSettleMs(): Long =
        if (BuildConfig.DEBUG) holdPlannedRestAfterLogMs else 0L
}
