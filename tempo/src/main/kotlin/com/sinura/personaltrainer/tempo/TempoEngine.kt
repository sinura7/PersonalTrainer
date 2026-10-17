package com.sinura.personaltrainer.tempo

import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.coach.AddASetPolicy
import com.sinura.personaltrainer.domain.coach.TempoCoach
import com.sinura.personaltrainer.domain.coach.TempoCoachTip

/**
 * JVM façade for in-workout Tempo recommendations (UX blueprint R6 / module boundary).
 * Delegates to the existing coach resolver; behavior is unchanged.
 */
object TempoEngine {
    fun recommend(
        microRec: SetMicroRec?,
        prefs: CoachPreferences,
        addASet: AddASetPolicy.Context?,
        extraSetRec: SetMicroRec?,
    ): TempoCoachTip? = TempoCoach.resolve(
        microRec = microRec,
        prefs = prefs,
        addASet = addASet,
        extraSetRec = extraSetRec,
    )
}
