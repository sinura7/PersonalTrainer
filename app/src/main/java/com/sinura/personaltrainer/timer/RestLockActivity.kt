package com.sinura.personaltrainer.timer

import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.RestControl
import com.sinura.personaltrainer.ui.components.RestSweepRing
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.PrGold
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Warn
import com.sinura.personaltrainer.ui.theme.systemReduceMotion

object RestLockTags {
    const val ROOT = "rest-lock"
    const val CLOCK = "rest-lock-clock"
    const val RING = "rest-lock-ring"
    const val SKIP = "rest-lock-skip"
    const val MINUS = "rest-lock-minus"
    const val PLUS = "rest-lock-plus"
    const val BACK_TO_BAR = "rest-lock-back"
    const val CLOSE = "rest-lock-close"
}

/**
 * Samsung-style rest glance: sits on the lock screen when the user turns
 * the phone on mid-rest. Not overlay rest on the live log (that stays a
 * won’t). The log keeps the condensed bar; this activity is a separate
 * lock-screen window.
 */
class RestLockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val controller = (application as? PersonalTrainerApp)?.container?.restTimerController
        val finishedLaunch = intent.getBooleanExtra(RestTimerNotifications.EXTRA_FINISHED, false)
        val sessionId = intent.getStringExtra(RestTimerService.EXTRA_SESSION_ID)
        if (controller == null) {
            finish()
            return
        }
        if (!controller.snapshot.value.running && !finishedLaunch) {
            finish()
            return
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val reduceMotion = systemReduceMotion(this)
        setContent {
            PersonalTrainerTheme(reduceMotion = reduceMotion) {
                RestLockScreen(
                    controller = controller,
                    finishedLaunch = finishedLaunch,
                    onSkip = {
                        controller.stop()
                        finish()
                    },
                    onAdjust = controller::adjust,
                    onClose = { finish() },
                    onBackToBar = { dismissKeyguardAndOpenSession(sessionId) },
                )
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    private fun dismissKeyguardAndOpenSession(sessionId: String?) {
        val open = {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    sessionId?.takeIf { it.isNotBlank() }?.let {
                        putExtra(RestTimerService.EXTRA_SESSION_ID, it)
                    }
                },
            )
            finish()
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            open()
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = open()
                override fun onDismissCancelled() = open()
                override fun onDismissError() = open()
            },
        )
    }
}

private const val URGENT_SECONDS = 10

@Composable
private fun RestLockScreen(
    controller: RestTimerController,
    finishedLaunch: Boolean,
    onSkip: () -> Unit,
    onAdjust: (Int) -> Unit,
    onClose: () -> Unit,
    onBackToBar: () -> Unit,
) {
    val snapshot by controller.snapshot.collectAsStateWithLifecycle()
    val remaining by controller.remainingSeconds.collectAsStateWithLifecycle(0)
    var justFinished by remember { mutableStateOf(finishedLaunch) }
    var wasRunning by remember { mutableStateOf(snapshot.running) }

    LaunchedEffect(snapshot.running) {
        if (wasRunning && !snapshot.running) justFinished = true
        if (snapshot.running) justFinished = false
        wasRunning = snapshot.running
    }

    val safeRemaining = remaining.coerceAtLeast(0)
    val urgent = snapshot.running && safeRemaining <= URGENT_SECONDS
    val accent = when {
        justFinished -> PrGold
        urgent -> Warn
        else -> RestCyan
    }
    val displaySeconds = if (justFinished) {
        0
    } else if (snapshot.running) {
        safeRemaining
    } else {
        snapshot.totalSeconds
    }
    val clock = RestTimer.formatClock(displaySeconds)
    val kicker = when {
        justFinished -> "Back to the bar"
        snapshot.running -> "REST"
        else -> "Next rest"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pit)
            .navigationBarsPadding()
            .testTag(RestLockTags.ROOT),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag(RestLockTags.CLOSE),
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Close rest",
                    tint = TextSecondary,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Metrics.gutter, vertical = Metrics.space4),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                RestSweepRing(
                    remainingSeconds = if (justFinished) {
                        0
                    } else if (snapshot.running) {
                        safeRemaining
                    } else {
                        snapshot.totalSeconds
                    },
                    totalSeconds = snapshot.totalSeconds.coerceAtLeast(1),
                    accent = accent,
                    clock = clock,
                    kicker = kicker,
                    running = snapshot.running || justFinished,
                    finished = justFinished,
                    modifier = Modifier.testTag(RestLockTags.RING),
                    clockTestTag = RestLockTags.CLOCK,
                )
            }
            Text(
                "Rest",
                style = InstrumentType.title,
                color = TextSecondary,
            )
            when {
                snapshot.running -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        RestControl(
                            label = "−15s",
                            onClick = { onAdjust(-15) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(RestLockTags.MINUS),
                        )
                        RestControl(
                            label = "Skip",
                            onClick = onSkip,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(RestLockTags.SKIP),
                        )
                        RestControl(
                            label = "+15s",
                            onClick = { onAdjust(15) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(RestLockTags.PLUS),
                        )
                    }
                }
                justFinished -> {
                    PrimaryGymButton(
                        text = "Back to the bar",
                        onClick = onBackToBar,
                        modifier = Modifier.testTag(RestLockTags.BACK_TO_BAR),
                    )
                }
            }
        }
    }
}
