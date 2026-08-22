package com.sinura.personaltrainer.timer

import android.content.Context
import com.sinura.personaltrainer.R

/**
 * Which rest-done noise to make, decided before anything is opened.
 *
 * The toggle and the ringer are the only gates. A missing asset falls back to
 * the system tone rather than going quiet — silence is a choice the lifter
 * already has a switch for.
 */
internal object RestSound {
    enum class Choice { QUIET, BUNDLED, SYSTEM }

    fun choose(
        soundEnabled: Boolean,
        ringerSilent: Boolean,
        bundledAvailable: Boolean,
    ): Choice {
        if (!soundEnabled || ringerSilent) return Choice.QUIET
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
