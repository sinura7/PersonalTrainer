package com.sinura.personaltrainer.reminder

import android.content.Intent

/**
 * Which broadcasts rebuild future WorkManager reminders.
 *
 * Past-due PENDING rows become STALE inside [com.sinura.personaltrainer.data.repository.PlannerRepository.rebuildReminders]
 * — this set is only the trigger filter, so a new action cannot start
 * catch-up spam without being named here.
 */
internal object ReminderRebuild {
    val ACTIONS: Set<String> = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_TIME_CHANGED,
    )

    fun shouldHandle(action: String?): Boolean = action != null && action in ACTIONS
}
