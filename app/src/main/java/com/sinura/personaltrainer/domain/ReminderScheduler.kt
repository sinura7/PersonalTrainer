package com.sinura.personaltrainer.domain

/**
 * Best-effort reminder enqueue. Production uses WorkManager.
 * Tests use a recording fake. Never an exact alarm.
 */
interface ReminderScheduler {
    fun schedule(delivery: ReminderDelivery)
    fun cancel(deliveryId: String)
    fun cancelForOccurrence(occurrenceId: String)
}
