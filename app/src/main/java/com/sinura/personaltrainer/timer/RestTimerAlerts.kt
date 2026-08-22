package com.sinura.personaltrainer.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestTimerPreferences

object RestTimerAlerts {
    private val COMPLETE_PATTERN = longArrayOf(0, 140, 90, 140, 90, 320)
    private val CUE_ATTRIBUTES = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun announce(context: Context, preferences: RestTimerPreferences) {
        if (preferences.soundEnabled) {
            playSound(context)
        }
        if (preferences.vibrationEnabled) {
            vibrate(context)
        }
    }

    private fun playSound(context: Context) {
        try {
            val app = context.applicationContext
            when (
                RestSound.choose(
                    soundEnabled = true,
                    ringerSilent = ringerIsSilent(app),
                    bundledAvailable = RestSound.bundledAvailable(app),
                )
            ) {
                RestSound.Choice.QUIET -> return
                RestSound.Choice.BUNDLED -> if (!playBundled(app)) playSystemFallback(app)
                RestSound.Choice.SYSTEM -> playSystemFallback(app)
            }
        } catch (_: Exception) {
            // Sound is optional. Never fail the timer.
        }
    }

    private fun ringerIsSilent(context: Context): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        return audio != null && audio.ringerMode == AudioManager.RINGER_MODE_SILENT
    }

    /**
     * Starts the bundled cue and releases the player when it finishes.
     *
     * [MediaPlayer.create] can return null on a device that cannot decode the
     * asset; the caller then falls back. Completion and error both release so
     * a 400 ms clip cannot hold a decoder until the next rest.
     */
    private fun playBundled(context: Context): Boolean {
        val player = MediaPlayer.create(context, R.raw.rest_done) ?: return false
        player.setAudioAttributes(CUE_ATTRIBUTES)
        player.setOnCompletionListener { done -> done.release() }
        player.setOnErrorListener { broken, _, _ ->
            broken.release()
            true
        }
        return try {
            player.start()
            true
        } catch (_: Exception) {
            player.release()
            false
        }
    }

    private fun playSystemFallback(context: Context) {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: return
        val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
        ringtone.audioAttributes = CUE_ATTRIBUTES
        ringtone.play()
    }

    private fun vibrate(context: Context) {
        try {
            val vibrator = vibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(COMPLETE_PATTERN, -1),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build(),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(COMPLETE_PATTERN, -1)
            }
        } catch (_: Exception) {
            // Vibration is optional.
        }
    }

    private fun vibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
