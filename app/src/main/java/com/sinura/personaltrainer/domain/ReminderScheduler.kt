package com.sinura.personaltrainer.domain

/**
 * Best-effort reminder enqueue. Production uses WorkManager.
 * Tests use a recording fake. Never an exact alarm.
 */
interface ReminderScheduler {
    fun schedule(delivery: ReminderDelivery)
    fun cancel(deliveryId: String)
    fun cancelForOccurrence(occurrenceId: String)

    /** Takes [occurrenceId]'s reminder off the screen, leaving its deliveries as they are. */
    fun dismissShown(occurrenceId: String)
}
