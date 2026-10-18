package com.sinura.personaltrainer.domain

/** Persisted on [RestTimerSnapshot.sourceTag] for post-finish cool-down rests. */
object RestTimerMarkers {
    const val COOLDOWN = "cool-down"
    const val COOLDOWN_SECONDS = RestTimerPreferences.DEFAULT_SECONDS

    fun isCooldown(snapshot: RestTimerSnapshot): Boolean = snapshot.sourceTag == COOLDOWN
}

object CooldownCopy {
    const val KICKER = "Cool-down"
    const val SKIP = "Skip"
    const val LOCK_KICKER = "COOL-DOWN"
    const val LOCK_TITLE = "Cool-down"
    const val DONE_KICKER = "Cool-down complete"
    const val DONE_BUTTON = "Done"
}
