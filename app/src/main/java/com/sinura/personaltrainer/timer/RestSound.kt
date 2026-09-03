package com.sinura.personaltrainer.timer

import android.content.Context
import com.sinura.personaltrainer.R

/**
 * Which rest-done noise to make, decided before anything is opened.
 *
 * The Settings sound toggle is the off switch. The cue rides the alarm
 * stream, so a silent ringer or paused earbuds must not mute it. A missing
 * asset falls back to the system tone rather than going quiet.
 */
internal object RestSound {
    enum class Choice { QUIET, BUNDLED, SYSTEM }

    fun choose(
        soundEnabled: Boolean,
        bundledAvailable: Boolean,
    ): Choice {
        if (!soundEnabled) return Choice.QUIET
        return if (bundledAvailable) Choice.BUNDLED else Choice.SYSTEM
    }

    fun bundledAvailable(context: Context): Boolean {
        return try {
            context.resources.openRawResourceFd(R.raw.rest_done).use { fd ->
                fd.length > 0L
            }
        } catch (_: Exception) {
            false
        }
    }
}
