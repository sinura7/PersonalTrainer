package com.sinura.personaltrainer.reminder

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReminderNotificationsTest {
    @Test
    fun consumeOccurrenceIdStripsTheExtra() {
        val intent = Intent().putExtra(ReminderNotifications.EXTRA_OCCURRENCE_ID, "occ-1")
        assertEquals("occ-1", ReminderNotifications.consumeOccurrenceId(intent))
        assertNull(intent.getStringExtra(ReminderNotifications.EXTRA_OCCURRENCE_ID))
        assertNull(ReminderNotifications.consumeOccurrenceId(intent))
    }

    @Test
    fun consumeOccurrenceIdIgnoresBlank() {
        assertNull(ReminderNotifications.consumeOccurrenceId(null))
        assertNull(
            ReminderNotifications.consumeOccurrenceId(
                Intent().putExtra(ReminderNotifications.EXTRA_OCCURRENCE_ID, "  "),
            ),
        )
    }

    @Test
    fun startLaunchCarriesOccurrenceAndDelivery() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = ReminderNotifications.startLaunchIntent(context, "occ-1", "del-1")
        assertEquals(ReminderNotifications.ACTION_START, intent.action)
        assertEquals("occ-1", ReminderNotifications.consumeOccurrenceId(intent))
        assertEquals("del-1", ReminderNotifications.consumeStartedDeliveryId(intent))
        assertNull(ReminderNotifications.consumeReviewOccurrenceId(intent))
    }

    @Test
    fun reviewLaunchCarriesOnlyTheReviewExtra() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = ReminderNotifications.reviewLaunchIntent(context, "occ-1")
        assertEquals("occ-1", ReminderNotifications.consumeReviewOccurrenceId(intent))
        assertNull(ReminderNotifications.consumeOccurrenceId(intent))
        assertNull(ReminderNotifications.consumeStartedDeliveryId(intent))
        assertNull(ReminderNotifications.consumeReviewOccurrenceId(intent))
    }
}
