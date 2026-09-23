package com.sinura.personaltrainer.timer

import android.app.Application
import android.content.Context
import android.media.ToneGenerator
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.RestTick
import com.sinura.personaltrainer.domain.RestTimerPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToneGenerator

/**
 * What the timer service makes the lifter feel and hear, rather than the dock: the last five
 * seconds of rest pulse light on 5 and 4 and twice as long on 3, 2 and 1, and a hold that
 * reaches its target beeps once when sound is on and stays silent when it is off. Compose
 * never pulses on its own (the bans in FloorPacketEClockTest); this is the half that does.
 *
 * These were `RestTimerAlerts.kt contains "RestTick.pulseMs(second)"` and `contains
 * "holdTargetTone"`, read as text in FloorPacketEClockTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestAndHoldAlertsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val vibrationOnly = RestTimerPreferences(soundEnabled = false, vibrationEnabled = true)

    @Before
    fun setUp() {
        ShadowToneGenerator.reset()
    }

    @Test
    fun theLastFiveSecondsPulseLightThenTwiceAsLong() {
        val vibrator = checkNotNull(context.getSystemService(VibratorManager::class.java)).defaultVibrator
        listOf(5, 4, 3, 2, 1).forEach { second ->
            assertTrue(RestTimerAlerts.tick(context, vibrationOnly, second) {})
            assertEquals("the pulse at $second", RestTick.pulseMs(second), Shadows.shadowOf(vibrator).milliseconds)
        }
        assertEquals(RestTick.LIGHT_PULSE_MS * 2, RestTick.WARN_PULSE_MS)
    }

    @Test
    fun aHoldThatReachesItsTargetBeepsOnceWhenSoundIsOn() {
        RestTimerAlerts.holdTargetTone(context, soundEnabled = true)
        assertEquals(listOf(ToneGenerator.TONE_PROP_BEEP), ShadowToneGenerator.getPlayedTones().map { it.type() })
    }

    @Test
    fun aHoldThatReachesItsTargetStaysSilentWhenSoundIsOff() {
        RestTimerAlerts.holdTargetTone(context, soundEnabled = false)
        assertTrue(ShadowToneGenerator.getPlayedTones().isEmpty())
    }
}
