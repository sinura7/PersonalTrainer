package com.sinura.personaltrainer.reminder

import com.sinura.personaltrainer.data.repository.PlannerRepository
import com.sinura.personaltrainer.domain.ReminderDeliveryStatus

/**
 * Notification-action writes, without a [android.content.Context].
 *
 * The Start action used to live only inside [ReminderActionReceiver], which
 * also tried to open the app. Since API 31 a receiver cannot launch an
 * activity from a notification action, so Start now records the tap and
 * clears the notification. Keeping that write on a function the receiver
 * and the tests share means a future trampoline cannot sneak back in
 * without this file noticing.
 */
internal object ReminderActionApply {
    suspend fun apply(
        action: String?,
        occurrenceId: String,
        deliveryId: String,
        planner: PlannerRepository,
        cancelNotification: (occurrenceId: String) -> Unit,
    ) {
        when (action) {
            ReminderNotifications.ACTION_START -> {
                planner.markDeliveryStatus(deliveryId, ReminderDeliveryStatus.STARTED)
                cancelNotification(occurrenceId)
            }
            ReminderNotifications.ACTION_SNOOZE -> {
                planner.snoozeDelivery(deliveryId)
                cancelNotification(occurrenceId)
            }
            ReminderNotifications.ACTION_MOVE -> {
                planner.markDeliveryStatus(deliveryId, ReminderDeliveryStatus.MOVED)
                planner.moveOccurrenceForward(occurrenceId)
                cancelNotification(occurrenceId)
            }
            ReminderNotifications.ACTION_SKIP -> {
                planner.markDeliveryStatus(deliveryId, ReminderDeliveryStatus.SKIPPED)
                planner.skipOccurrence(occurrenceId)
                cancelNotification(occurrenceId)
            }
            else -> Unit
        }
    }
}
