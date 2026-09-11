package com.sinura.personaltrainer.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestCompleteCue
import com.sinura.personaltrainer.domain.RestTick
import com.sinura.personaltrainer.domain.RestTimerPreferences

object RestTimerAlerts {
    private val COMPLETE_PATTERN = longArrayOf(0, 140, 90, 140, 90, 320)

    internal val CUE_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun announce(
        context: Context,
        preferences: RestTimerPreferences,
        createPlayer: (Context, Int, AudioAttributes) -> MediaPlayer? = ::createWithAttributes,
    ) {
        if (preferences.soundEnabled) {
            playSound(context, createPlayer)
        }
        if (preferences.vibrationEnabled) {
            vibrate(context, VibrationEffect.createWaveform(COMPLETE_PATTERN, -1))
        }
    }

    /**
     * Settings Play. Same bundled `rest_done` on the alarm stream as
     * [announce] at 0:00 — not a second asset, not a system ringtone first.
     *
     * Sound is forced on so the row is never silent. Vibration still
     * follows the live switch.
     */
    fun preview(
        context: Context,
        preferences: RestTimerPreferences,
        createPlayer: (Context, Int, AudioAttributes) -> MediaPlayer? = ::createWithAttributes,
    ) {
        announce(context, RestCompleteCue.previewPreferences(preferences), createPlayer)
    }

    /**
     * One of the last five seconds (R-04). The click is the caller's — the
     * [RestTickPlayer] the service keeps loaded — so this stays the policy:
     * Last five seconds is the off switch, and Sound and Vibration gate the
     * two halves exactly as they gate the cue.
     *
     * [second] picks the pulse: light on 5 and 4, heavier on 3, 2, 1
     * ([RestTick.pulseMs]). Complete stays [announce]'s waveform.
     *
     * @return whether the tick was on, whatever the two halves then did.
     */
    fun tick(
        context: Context,
        preferences: RestTimerPreferences,
        second: Int = RestTick.FIRST,
        click: () -> Unit,
    ): Boolean {
        if (!preferences.tickEnabled) return false
        if (preferences.soundEnabled) {
            try {
                click()
            } catch (_: Exception) {
                // A click is optional. Never fail the timer.
            }
        }
        if (preferences.vibrationEnabled) {
            vibrate(
                context,
                VibrationEffect.createOneShot(
                    RestTick.pulseMs(second),
                    VibrationEffect.DEFAULT_AMPLITUDE,
                ),
            )
        }
        return true
    }

    private fun playSound(
        context: Context,
        createPlayer: (Context, Int, AudioAttributes) -> MediaPlayer?,
    ) {
        try {
            val app = context.applicationContext
            when (
                RestSound.choose(
                    soundEnabled = true,
                    bundledAvailable = RestSound.bundledAvailable(app),
                )
            ) {
                RestSound.Choice.QUIET -> return
                RestSound.Choice.BUNDLED -> if (!playBundled(app, createPlayer)) playSystemFallback(app)
                RestSound.Choice.SYSTEM -> playSystemFallback(app)
            }
        } catch (_: Exception) {
            // Sound is optional. Never fail the timer.
        }
    }

    /**
     * Starts the bundled cue and releases the player when it finishes.
     *
     * Attributes must reach [MediaPlayer.create] before prepare. Setting them
     * afterwards has no effect, which is how the cue used to ride the media
     * stream.
     */
    private fun playBundled(
        context: Context,
        createPlayer: (Context, Int, AudioAttributes) -> MediaPlayer?,
    ): Boolean {
        val player = createPlayer(context, R.raw.rest_done, CUE_ATTRIBUTES) ?: return false
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
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
        ringtone.audioAttributes = CUE_ATTRIBUTES
        ringtone.play()
    }

    private fun vibrate(context: Context, effect: VibrationEffect) {
        try {
            val vibrator = vibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build(),
                )
            } else {
                vibrator.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build(),
                )
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

    private fun createWithAttributes(
        context: Context,
        resId: Int,
        attributes: AudioAttributes,
    ): MediaPlayer? = MediaPlayer.create(
        context,
        resId,
        attributes,
        AudioManager.AUDIO_SESSION_ID_GENERATE,
    )
}
