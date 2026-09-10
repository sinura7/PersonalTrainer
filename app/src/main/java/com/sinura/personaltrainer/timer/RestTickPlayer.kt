package com.sinura.personaltrainer.timer

import android.content.Context
import android.media.SoundPool
import com.sinura.personaltrainer.R

/**
 * The click for the last five seconds, kept loaded.
 *
 * A [SoundPool], not the cue's MediaPlayer: five clicks in five seconds
 * through `MediaPlayer.create` would be five decoder set-ups on the main
 * thread, each late by its own prepare. A pool decodes `rest_tick` once when
 * the rest starts and plays it with no latency the ear can place. Same alarm
 * attributes as the cue, so a silent ringer does not mute it either.
 *
 * Everything here is optional. A pool that fails to build or a clip that
 * fails to load plays nothing and throws nothing; the pulse still goes.
 */
internal class RestTickPlayer(context: Context) {
    private var pool: SoundPool? = null
    private var soundId = 0

    @Volatile
    private var loaded = false

    init {
        try {
            val built = SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(RestTimerAlerts.CUE_ATTRIBUTES)
                .build()
            // One clip in this pool, so any successful load is ours — the
            // callback can land before load() has even returned the id.
            built.setOnLoadCompleteListener { _, _, status -> if (status == 0) loaded = true }
            soundId = built.load(context.applicationContext, R.raw.rest_tick, 1)
            pool = built
        } catch (_: Exception) {
            pool = null
        }
    }

    fun play() {
        val ready = pool ?: return
        if (!loaded) return
        try {
            ready.play(soundId, 1f, 1f, 1, 0, 1f)
        } catch (_: Exception) {
            // A click is optional. Never fail the timer.
        }
    }

    fun release() {
        try {
            pool?.release()
        } catch (_: Exception) {
            // Already gone.
        }
        pool = null
        loaded = false
    }
}
