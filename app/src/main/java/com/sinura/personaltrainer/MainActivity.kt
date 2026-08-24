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
import com.sinura.personaltrainer.timer.RestTimerService
import com.sinura.personaltrainer.ui.navigation.PersonalTrainerNav
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme

class MainActivity : ComponentActivity() {
    private var openSessionId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a genuinely NEW launch may carry a session to open. Android redelivers the
        // creating intent on every recreation (rotation, theme change, process-death
        // restore), so reading it unconditionally meant one rest-notification tap could
        // force-navigate back into that workout for the rest of the Activity's life —
        // including after it had been finished or discarded.
        openSessionId = if (savedInstanceState == null) consumeSessionId(intent) else null
        // Both bars transparent, both pinned to light icons. The default picks icon colour
        // from the system's light/dark setting, which is the wrong signal for an app that
        // draws one dark theme regardless: a phone in light mode got dark status icons on a
        // near-black window.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            PersonalTrainerTheme {
                PersonalTrainerNav(
                    openSessionId = openSessionId,
                    onOpenSessionConsumed = { openSessionId = null },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as? PersonalTrainerApp)
            ?.container
            ?.restTimerController
            ?.refreshAlarmCapability()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeSessionId(intent)?.let { openSessionId = it }
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
