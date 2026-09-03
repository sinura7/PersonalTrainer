package com.sinura.personaltrainer.timer

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
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
        RestTimerAlerts.announce(
            context,
            RestTimerPreferences(soundEnabled = false, vibrationEnabled = false),
        )
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
