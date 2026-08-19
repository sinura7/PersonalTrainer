package com.sinura.personaltrainer.domain

object SetLogRules {
    const val ZERO_WORKING_WEIGHT =
        "Enter a weight for working sets. Use warm-up for 0 kg."
    const val INVALID_WEIGHT = "Weight must be zero or greater."
    const val INVALID_REPS = "Reps must be at least 1."

    fun validate(weightKg: Double, reps: Int, isWarmup: Boolean): String? {
        if (!weightKg.isFinite() || weightKg < 0.0) return INVALID_WEIGHT
        if (!isWarmup && weightKg == 0.0) return ZERO_WORKING_WEIGHT
        if (reps < 1) return INVALID_REPS
        return null
    }

    fun isUserMessage(message: String): Boolean =
        message == ZERO_WORKING_WEIGHT ||
            message == INVALID_WEIGHT ||
            message == INVALID_REPS ||
            message.startsWith("This workout")
}