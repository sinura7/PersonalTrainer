package com.sinura.personaltrainer.timer

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestTimerPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Settings sound toggle is the off switch. Silent ringer is not.
 * A missing asset falls back. The packaged cue is the happy path.
 */
class RestSoundChoiceTest {
    @Test
    fun soundOffIsQuietEvenWhenTheAssetIsThere() {
        assertEquals(
            RestSound.Choice.QUIET,
            RestSound.choose(
                soundEnabled = false,
                bundledAvailable = true,
            ),
        )
    }

    @Test
    fun silentRingerStillPlaysWhenSoundIsOn() {
        assertEquals(
            RestSound.Choice.BUNDLED,
            RestSound.choose(
                soundEnabled = true,
                bundledAvailable = true,
            ),
        )
    }

    @Test
    fun soundOnPlaysTheBundledCue() {
        assertEquals(
            RestSound.Choice.BUNDLED,
            RestSound.choose(
                soundEnabled = true,
                bundledAvailable = true,
            ),
        )
    }

    @Test
    fun missingAssetFallsBackToTheSystemTone() {
        assertEquals(
            RestSound.Choice.SYSTEM,
            RestSound.choose(
                soundEnabled = true,
                bundledAvailable = false,
            ),
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestSoundAssetTest {
    @Test
    fun bundledCueIsPackaged() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(RestSound.bundledAvailable(context))
    }

    @Test
    fun announceWithSoundOffDoesNotThrow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val seen = mutableListOf<Int>()
        RestTimerAlerts.announce(
            context,
            RestTimerPreferences(soundEnabled = false, vibrationEnabled = false),
        ) { _, resId, _ ->
            seen += resId
            null
        }
        assertTrue(seen.isEmpty())
    }

    @Test
    fun announceOnSilentRingerDoesNotThrow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        RestTimerAlerts.announce(
            context,
            RestTimerPreferences(soundEnabled = true, vibrationEnabled = false),
        )
    }

    @Test
    fun announceWithVibrationDoesNotThrow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        RestTimerAlerts.announce(
            context,
            RestTimerPreferences(soundEnabled = false, vibrationEnabled = true),
        )
    }

    @Test
    fun previewWithSoundOffDoesNotThrow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        RestTimerAlerts.preview(
            context,
            RestTimerPreferences(soundEnabled = false, vibrationEnabled = true),
        )
    }
}

/**
 * Last five seconds is the off switch; Sound and Vibration then gate the
 * click and the pulse exactly as they gate the cue.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestTickAlertTest {
    @Test
    fun tickOffClicksNothingEvenWithSoundOn() {
        var clicks = 0
        val ticked = RestTimerAlerts.tick(
            ApplicationProvider.getApplicationContext<Context>(),
            RestTimerPreferences(soundEnabled = true, vibrationEnabled = true, tickEnabled = false),
        ) { clicks += 1 }
        assertEquals(false, ticked)
        assertEquals(0, clicks)
    }

    @Test
    fun soundOffKeepsTheClickQuietButTheTickStillHappens() {
        var clicks = 0
        val ticked = RestTimerAlerts.tick(
            ApplicationProvider.getApplicationContext<Context>(),
            RestTimerPreferences(soundEnabled = false, vibrationEnabled = true),
        ) { clicks += 1 }
        assertEquals(true, ticked)
        assertEquals(0, clicks)
    }

    @Test
    fun soundOnClicksOncePerTickAndAThrowingClickIsSwallowed() {
        var clicks = 0
        val context = ApplicationProvider.getApplicationContext<Context>()
        RestTimerAlerts.tick(context, RestTimerPreferences(vibrationEnabled = false)) { clicks += 1 }
        assertEquals(1, clicks)
        RestTimerAlerts.tick(context, RestTimerPreferences()) { throw IllegalStateException("no pool") }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestTimerAlertsCreateTest {
    @Test
    fun bundledCuePassesAlarmAttributesIntoCreate() {
        val seen = mutableListOf<AudioAttributes>()
        val context = ApplicationProvider.getApplicationContext<Context>()
        RestTimerAlerts.announce(
            context = context,
            preferences = RestTimerPreferences(soundEnabled = true, vibrationEnabled = false),
            createPlayer = { _, _, attributes ->
                seen += attributes
                null
            },
        )
        assertEquals(1, seen.size)
        assertEquals(AudioAttributes.USAGE_ALARM, seen.single().usage)
    }

    @Test
    fun previewStartsTheBundledCueWhenSoundToggleIsOff() {
        var resId = 0
        var usage = 0
        var started = false
        val context = ApplicationProvider.getApplicationContext<Context>()
        RestTimerAlerts.preview(
            context,
            RestTimerPreferences(soundEnabled = false, vibrationEnabled = false),
        ) { _, id, attributes ->
            resId = id
            usage = attributes.usage
            object : MediaPlayer() {
                override fun start() {
                    started = true
                }
            }
        }
        assertEquals(R.raw.rest_done, resId)
        assertEquals(AudioAttributes.USAGE_ALARM, usage)
        assertTrue(started)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestTimerDoneChannelTest {
    @Test
    fun doneChannelBypassesDnd() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        RestTimerNotifications.ensureChannels(context)
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(RestTimerNotifications.CHANNEL_DONE)
        assertTrue(channel.canBypassDnd())
    }
}
