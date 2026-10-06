package com.sinura.personaltrainer.domain.coach

import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.SetMicroRecCopy

/**
 * Chooses the single Tempo popup after a working set: next-set load/reps, or add-a-set.
 */
object TempoCoach {
    fun resolve(
        microRec: SetMicroRec?,
        prefs: CoachPreferences,
        addASet: AddASetPolicy.Context?,
        extraSetRec: SetMicroRec?,
    ): TempoCoachTip? {
        if (microRec?.reasonCode == SetMicroRecCalculator.EDITING) return null
        val addOffer = addASet?.let { AddASetPolicy.evaluate(it) }
        if (addOffer != null && extraSetRec != null) {
            return TempoCoachTip.AddASet(
                offer = addOffer,
                seedRec = extraSetRec,
                trace = extraSetRec.trace,
            )
        }
        val nextRec = when {
            microRec != null && SetMicroRecCopy.visibleOnEntry(microRec) -> microRec
            extraSetRec != null && SetMicroRecCopy.visibleOnEntry(extraSetRec) -> extraSetRec
            else -> null
        } ?: return null
        val suggestion = CoachEngine.fromMicroRec(nextRec, prefs)
        return TempoCoachTip.NextSet(rec = nextRec, suggestion = suggestion)
    }
}
