package com.sinura.personaltrainer.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.sinura.personaltrainer.domain.RestTimerPreferences

object RestTimerAlerts {
    private val COMPLETE_PATTERN = longArrayOf(0, 140, 90, 140, 90, 320)

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
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audio != null && audio.ringerMode == AudioManager.RINGER_MODE_SILENT) {
                return
            }
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: return
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
        } catch (_: Exception) {
            // Sound is optional. Never fail the timer.
        }
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
