package com.sinura.personaltrainer.timer

import android.app.Application
import android.content.Context
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
 * Sound-off and silent ringer stay quiet. A missing asset falls back.
 * The packaged cue is the happy path, not a hope.
 */
class RestSoundChoiceTest {
    @Test
    fun soundOffIsQuietEvenWhenTheAssetIsThere() {
        assertEquals(
            RestSound.Choice.QUIET,
            RestSound.choose(
                soundEnabled = false,
                ringerSilent = false,
                bundledAvailable = true,
            ),
        )
    }

    @Test
    fun silentRingerIsQuietEvenWhenSoundIsOn() {
        assertEquals(
            RestSound.Choice.QUIET,
            RestSound.choose(
                soundEnabled = true,
                ringerSilent = true,
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
                ringerSilent = false,
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
                ringerSilent = false,
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
