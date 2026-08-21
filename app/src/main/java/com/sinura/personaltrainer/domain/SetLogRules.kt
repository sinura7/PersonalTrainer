package com.sinura.personaltrainer.domain

object SetLogRules {
    const val ZERO_WORKING_WEIGHT =
        "Enter a weight for working sets. Use warm-up for 0 kg."
    const val INVALID_WEIGHT = "Weight must be zero or greater."
    const val INVALID_REPS = "Reps must be at least 1."

    /**
     * @param loadType how the lift is loaded. Null — a custom, or a row from a backup this
     * build predates — is treated as externally loaded, which is the stricter reading.
     *
     * The zero-weight rule is about EXTERNALLY loaded lifts. A barbell set at 0 kg is a typo
     * or a warm-up, and catching it saves a corrupted log. A push-up at 0 kg is a push-up:
     * there is nothing to load and no other number the user could enter. Refusing it made 18
     * of the catalog's 98 lifts impossible to record, and told the user to use warm-up mode
     * instead — which would have silently excluded the set from every volume total.
     */
    fun validate(
        weightKg: Double,
        reps: Int,
        isWarmup: Boolean,
        loadType: LoadType? = null,
    ): String? {
        if (!weightKg.isFinite() || weightKg < 0.0) return INVALID_WEIGHT
        if (!isWarmup && weightKg == 0.0 && requiresWeight(loadType)) return ZERO_WORKING_WEIGHT
        if (reps < 1) return INVALID_REPS
        return null
    }

    /**
     * Whether a working set of this lift must carry a weight.
     *
     * BODYWEIGHT has nothing to add. BODYWEIGHT_PLUS *can* take added load but does not have
     * to — an unweighted pull-up is a complete set. ASSISTED counts assistance subtracted, so
     * zero assistance is the hardest version, not a missing entry.
     */
    fun requiresWeight(loadType: LoadType?): Boolean = when (loadType) {
        LoadType.EXTERNAL, LoadType.STACK, null -> true
        LoadType.BODYWEIGHT, LoadType.BODYWEIGHT_PLUS, LoadType.ASSISTED -> false
    }

    fun isUserMessage(message: String): Boolean =
        message == ZERO_WORKING_WEIGHT ||
            message == INVALID_WEIGHT ||
            message == INVALID_REPS ||
            message.startsWith("This workout")
}