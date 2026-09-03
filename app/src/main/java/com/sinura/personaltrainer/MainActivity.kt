package com.sinura.personaltrainer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sinura.personaltrainer.reminder.ReminderNotifications
import com.sinura.personaltrainer.timer.RestTimerService
import com.sinura.personaltrainer.ui.navigation.PersonalTrainerNav
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.systemReduceMotion

class MainActivity : ComponentActivity() {
    private var openSessionId by mutableStateOf<String?>(null)
    private var openOccurrenceId by mutableStateOf<String?>(null)
    private var reviewOccurrenceId by mutableStateOf<String?>(null)
    private var reduceMotion by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a genuinely NEW launch may carry a session to open. Android redelivers the
        // creating intent on every recreation (rotation, theme change, process-death
        // restore), so reading it unconditionally meant one rest-notification tap could
        // force-navigate back into that workout for the rest of the Activity's life —
        // including after it had been finished or discarded.
        openSessionId = if (savedInstanceState == null) consumeSessionId(intent) else null
        openOccurrenceId = if (savedInstanceState == null) {
            ReminderNotifications.consumeOccurrenceId(intent)
        } else {
            null
        }
        reviewOccurrenceId = if (savedInstanceState == null) {
            ReminderNotifications.consumeReviewOccurrenceId(intent)
        } else {
            null
        }
        if (savedInstanceState == null) consumeStartedDelivery(intent, openOccurrenceId)
        // Both bars transparent, both pinned to light icons. The default picks icon colour
        // from the system's light/dark setting, which is the wrong signal for an app that
        // draws one dark theme regardless: a phone in light mode got dark status icons on a
        // near-black window.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        reduceMotion = systemReduceMotion(this)
        setContent {
            PersonalTrainerTheme(reduceMotion = reduceMotion) {
                PersonalTrainerNav(
                    openSessionId = openSessionId,
                    onOpenSessionConsumed = { openSessionId = null },
                    openOccurrenceId = openOccurrenceId,
                    onOpenOccurrenceConsumed = { openOccurrenceId = null },
                    reviewOccurrenceId = reviewOccurrenceId,
                    onReviewOccurrenceConsumed = { reviewOccurrenceId = null },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reduceMotion = systemReduceMotion(this)
        val app = application as? PersonalTrainerApp
        app?.container?.restTimerController?.refreshAlarmCapability()
        // Week rollover must not wait for a process restart.
        app?.ensureCurrentWeek()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeSessionId(intent)?.let { openSessionId = it }
        val occurrenceId = ReminderNotifications.consumeOccurrenceId(intent)
        occurrenceId?.let { openOccurrenceId = it }
        ReminderNotifications.consumeReviewOccurrenceId(intent)?.let { reviewOccurrenceId = it }
        consumeStartedDelivery(intent, occurrenceId)
    }

    /**
     * A reminder Start launch also marks its delivery row STARTED — and
     * dismisses the notification. A notification *action* never auto-cancels
     * (setAutoCancel covers only the content tap), so without the explicit
     * cancel the started session's reminder stayed in the shade with live
     * Snooze/Move/Skip buttons that could mark the running plan row SKIPPED.
     */
    private fun consumeStartedDelivery(intent: Intent?, occurrenceId: String?) {
        val deliveryId = ReminderNotifications.consumeStartedDeliveryId(intent) ?: return
        occurrenceId?.let { ReminderNotifications.cancel(this, it) }
        (application as? PersonalTrainerApp)?.markReminderStarted(deliveryId)
    }

    /**
     * Reads the session id and strips it from the intent, so the same tap can never be
     * delivered twice. Belt and braces with the savedInstanceState gate above: that stops the
     * cold-restore replay, this stops a warm one.
     */
    private fun consumeSessionId(intent: Intent?): String? {
        val id = intent?.getStringExtra(RestTimerService.EXTRA_SESSION_ID) ?: return null
        intent.removeExtra(RestTimerService.EXTRA_SESSION_ID)
        return id.takeIf { it.isNotBlank() }
    }
}
