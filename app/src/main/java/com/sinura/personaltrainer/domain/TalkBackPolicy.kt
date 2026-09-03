package com.sinura.personaltrainer.domain

/**
 * G1: what TalkBack must be able to find, without composing the tree.
 *
 * Physical TalkBack stays an owner phone pass. [AccessibilityMatrix.publicCandidateReady]
 * stays false until that session exists; this object is the JVM contract the
 * surfaces have to keep in the meantime.
 */
object TalkBackPolicy {
    const val REST_FINISHED_KICKER = "Back to the bar"
    const val REST_RUNNING_KICKER = "REST"
    const val BODYWEIGHT_TYPED_TITLE = "Bodyweight"
    const val BODYWEIGHT_TYPED_SPOKEN = "Type bodyweight"
    const val BODYWEIGHT_TYPED_HELPER = "A whole number in the unit on the wheel."

    fun restKicker(justFinished: Boolean): String =
        if (justFinished) REST_FINISHED_KICKER else REST_RUNNING_KICKER

    /** Announce the rest kicker only at the finished flash, not every second. */
    fun announceRestKicker(justFinished: Boolean): Boolean = justFinished

    fun announceRecordBanner(): Boolean = true

    /**
     * Typed bodyweight for the onboarding wheel. Snaps to the same whole
     * numerals the pager shows, and refuses anything outside the stored range.
     */
    fun parseTypedBodyweightKg(input: String, unit: WeightUnit): Double? {
        val kg = NumericEntry.parseWeightKg(input, unit) ?: return null
        if (kg !in OnboardingAnswers.MIN_BODYWEIGHT_KG..OnboardingAnswers.MAX_BODYWEIGHT_KG) {
            return null
        }
        val display = BodyweightSteps.displayOf(kg, unit)
        val values = BodyweightSteps.displayValues(unit)
        if (display !in values.first()..values.last()) return null
        return BodyweightSteps.toKg(display, unit)
    }
}
