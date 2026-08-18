package com.sinura.personaltrainer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
        openSessionId = intent.sessionId()
        enableEdgeToEdge()
        setContent {
            PersonalTrainerTheme {
                PersonalTrainerNav(
                    openSessionId = openSessionId,
                    onOpenSessionConsumed = { openSessionId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openSessionId = intent.sessionId()
    }

    private fun Intent.sessionId(): String? = getStringExtra(RestTimerService.EXTRA_SESSION_ID)
}
