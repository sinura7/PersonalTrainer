package com.sinura.personaltrainer.domain

/**
 * Settings Play: sample the rest-complete cue without waiting for 0:00.
 *
 * DESIGN_AUDIT N-01. The Sound and Vibration switches decide what happens
 * when rest actually ends. Play is the sample of that cue, so the owner
 * can hear the stand-up tone in the locker room instead of mid-set.
 *
 * Preview always turns the tone on — a Play row that does nothing when
 * Sound is off is a dead control. Vibration still follows the live
 * switch, the same gate 0:00 uses.
 */
object RestCompleteCue {
    const val TITLE = "Play complete cue"
    const val CAPTION = "Hear the stand-up tone. Vibration follows the switch above."

    fun previewPreferences(current: RestTimerPreferences): RestTimerPreferences =
        current.copy(soundEnabled = true)
}
