package com.sinura.personaltrainer.timer

import android.content.Context
import android.provider.Settings

/**
 * The system's boot counter, used to tell "same boot" from "rebooted"
 * definitively.
 *
 * The heuristics that guess from clocks are each wrong in one direction:
 * the boot marker (wall minus elapsed) moves on any wall-clock step, and
 * "elapsedRealtime has not gone backwards past the start" reads a genuine
 * reboot as same-boot whenever the previous boot's uptime at start was
 * smaller than the current uptime at read (a rest or run started shortly
 * after a boot, then another reboot). BOOT_COUNT increments exactly once
 * per boot and ignores the wall clock entirely, so when both sides carry
 * it, the comparison is the answer. The clock heuristics stay as the
 * fallback for rows written by builds that did not stamp it.
 */
object BootSession {
    /** No counter available (settings read failed, or a pre-stamp row). */
    const val UNKNOWN = -1L

    fun count(context: Context): Long = try {
        Settings.Global.getInt(
            context.applicationContext.contentResolver,
            Settings.Global.BOOT_COUNT,
        ).toLong()
    } catch (_: Exception) {
        UNKNOWN
    }
}
