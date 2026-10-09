package com.sinura.personaltrainer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.reminder.ReminderNotifications
import com.sinura.personaltrainer.ui.intro.ColdStartIntro
import com.sinura.personaltrainer.ui.saveposture.SavePostureHost
import com.sinura.personaltrainer.ui.saveposture.hiddenUnderFirstLaunchOverlay
import com.sinura.personaltrainer.ui.saveposture.savePostureChooserShowing
import com.sinura.personaltrainer.timer.RestTimerService
import com.sinura.personaltrainer.ui.navigation.PersonalTrainerNav
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.systemReduceMotion
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var openSessionId by mutableStateOf<String?>(null)
    private var openOccurrenceId by mutableStateOf<String?>(null)
    private var openDeliveryId by mutableStateOf<String?>(null)
    private var openOccurrenceRequestId by mutableStateOf<String?>(null)
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
            savedInstanceState.getString(PENDING_OCCURRENCE)?.takeIf { it.isNotBlank() }
        }
        reviewOccurrenceId = if (savedInstanceState == null) {
            ReminderNotifications.consumeReviewOccurrenceId(intent)
        } else {
            null
        }
        openDeliveryId = if (savedInstanceState == null) {
            ReminderNotifications.consumeStartedDeliveryId(intent)
        } else {
            savedInstanceState.getString(PENDING_DELIVERY).takeIf { openOccurrenceId != null }
        }
        // Keep only a handoff Home has not yet accepted or refused. The creating intent has
        // already been stripped; rereading it would replay a consumed tap after rotation.
        openOccurrenceRequestId = openOccurrenceId?.let {
            savedInstanceState?.getString(PENDING_REQUEST)?.takeIf { id -> id.isNotBlank() }
                ?: UUID.randomUUID().toString()
        }
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
            val app = application as PersonalTrainerApp
            var showColdStartIntro by remember {
                mutableStateOf(app.shouldShowColdStartIntro())
            }
            PersonalTrainerTheme(reduceMotion = reduceMotion) {
                val chooserShowing = savePostureChooserShowing(coldStartIntroVisible = showColdStartIntro)
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .hiddenUnderFirstLaunchOverlay(covered = showColdStartIntro || chooserShowing),
                    ) {
                        PersonalTrainerNav(
                            openSessionId = openSessionId,
                            onOpenSessionConsumed = { openSessionId = null },
                            openOccurrenceId = openOccurrenceId,
                            openDeliveryId = openDeliveryId,
                            openOccurrenceRequestId = openOccurrenceRequestId,
                            onOpenOccurrenceConsumed = {
                                openOccurrenceId = null
                                openDeliveryId = null
                                openOccurrenceRequestId = null
                            },
                            reviewOccurrenceId = reviewOccurrenceId,
                            onReviewOccurrenceConsumed = { reviewOccurrenceId = null },
                        )
                    }
                    if (showColdStartIntro) {
                        ColdStartIntro(
                            reduceMotion = reduceMotion,
                            onIntroDisplayed = { app.markColdStartIntroShown() },
                            onFinished = {
                                app.markColdStartIntroShown()
                                showColdStartIntro = false
                            },
                        )
                    }
                    SavePostureHost(coldStartIntroVisible = showColdStartIntro)
                }
            }
        }
        openInstallSheetWhenInFront()
    }

    /**
     * Temper Debug: Android answers an install session with its install sheet, for the app to
     * open, and an app may open a screen only while it is in front (audit RM-1). The sheet waits
     * with the update's monitor; this opens it once the app is in front, at once or when the owner
     * comes back. Gym-floor never has one.
     */
    private fun openInstallSheetWhenInFront() {
        val port = (application as? PersonalTrainerApp)?.container?.debugUpdate ?: return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                port.installSheet.filterNotNull().collect { sheet ->
                    val opened = try {
                        startActivity(sheet)
                        true
                    } catch (error: RuntimeException) {
                        AppLog.w(TAG, "Android's install sheet could not be opened", error)
                        false
                    }
                    port.onInstallSheetShown(opened)
                }
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
        app?.container?.debugUpdate?.onForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeSessionId(intent)?.let {
            openSessionId = it
            clearPendingOccurrenceStart()
            reviewOccurrenceId = null
        }
        ReminderNotifications.consumeOccurrenceId(intent)?.let { occurrenceId ->
            openSessionId = null
            reviewOccurrenceId = null
            openOccurrenceId = occurrenceId
            // Always replaced with the start it came with: a Start carries its delivery, and
            // anything else that names a start carries none.
            openDeliveryId = ReminderNotifications.consumeStartedDeliveryId(intent)
            // The same still-pending delivery can be tapped intentionally again after a
            // read refusal. It is a new request, rather than an automatic retry of the old one.
            openOccurrenceRequestId = UUID.randomUUID().toString()
        }
        ReminderNotifications.consumeReviewOccurrenceId(intent)?.let {
            // A body tap asks to inspect. It replaces a still-waiting Start rather than
            // allowing that older request to start once Home's required reads recover.
            clearPendingOccurrenceStart()
            openSessionId = null
            reviewOccurrenceId = it
        }
    }

    private fun clearPendingOccurrenceStart() {
        openOccurrenceId = null
        openDeliveryId = null
        openOccurrenceRequestId = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (openOccurrenceId != null) {
            outState.putString(PENDING_OCCURRENCE, openOccurrenceId)
            outState.putString(PENDING_DELIVERY, openDeliveryId)
            outState.putString(PENDING_REQUEST, openOccurrenceRequestId)
        }
        super.onSaveInstanceState(outState)
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

private const val TAG = "PT/MainActivity"
private const val PENDING_OCCURRENCE = "temper.pending-occurrence-start"
private const val PENDING_DELIVERY = "temper.pending-occurrence-delivery"
private const val PENDING_REQUEST = "temper.pending-occurrence-request"
