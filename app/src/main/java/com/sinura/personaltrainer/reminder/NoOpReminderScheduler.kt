package com.sinura.personaltrainer.reminder

import com.sinura.personaltrainer.domain.ReminderDelivery
import com.sinura.personaltrainer.domain.ReminderScheduler

/** Tests and JVM hosts that must not touch WorkManager. */
class NoOpReminderScheduler : ReminderScheduler {
    override fun schedule(delivery: ReminderDelivery) = Unit
    override fun cancel(deliveryId: String) = Unit
    override fun cancelForOccurrence(occurrenceId: String) = Unit
}
