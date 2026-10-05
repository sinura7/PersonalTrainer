package com.sinura.personaltrainer.timer

import com.sinura.personaltrainer.domain.RestIdleCopy
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerSnapshot

/** Shared copy + progress for lock widgets and the floating overlay. */
object RestExteriorDisplay {
    const val PROGRESS_MAX = 1000

    data class Model(
        val kicker: String,
        val timeText: String,
        val progressLevel: Int,
        val showProgress: Boolean,
        val atZero: Boolean,
    )

    fun model(
        state: RestTimerSnapshot,
        nowElapsedRealtime: Long,
        kickerRunning: String,
        kickerDone: String,
    ): Model {
        if (!state.running) {
            return Model(
                kicker = kickerRunning,
                timeText = RestIdleCopy.NOT_RUNNING,
                progressLevel = 0,
                showProgress = false,
                atZero = false,
            )
        }
        val remaining = state.remainingSeconds(nowElapsedRealtime).coerceAtLeast(0)
        if (remaining <= 0) {
            return Model(
                kicker = kickerDone,
                timeText = RestTimer.formatClock(0),
                progressLevel = 0,
                showProgress = true,
                atZero = true,
            )
        }
        val total = state.totalSeconds.coerceAtLeast(1)
        val fraction = remaining.toFloat() / total.toFloat()
        val level = (fraction * PROGRESS_MAX).toInt().coerceIn(0, PROGRESS_MAX)
        return Model(
            kicker = kickerRunning,
            timeText = RestTimer.formatClock(remaining),
            progressLevel = level,
            showProgress = true,
            atZero = false,
        )
    }
}
