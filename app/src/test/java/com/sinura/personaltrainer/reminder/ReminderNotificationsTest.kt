package com.sinura.personaltrainer.reminder

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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
}
