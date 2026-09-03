package com.sinura.personaltrainer.domain

/**
 * One filled Volt per screen. A recovery command already on the leftover
 * card is the Volt; Start a workout must not become a second fill.
 */
enum class FreeStartRank {
    PRIMARY,
    SECONDARY,
    TEXT,
    HIDDEN,
}

object OneFilledVolt {
    /** Plan's empty-routines action is quiet so Add session stays the Volt. */
    const val PLAN_EMPTY_COMPACT = true

    /** Settings Export is the page Volt (ADR-014 §4, decision 4). */
    const val SETTINGS_EXPORT_IS_PRIMARY = true

    fun leftoverHasRecoveryVolt(
        setupComplete: Boolean,
        offerSetupActions: Boolean,
    ): Boolean = if (!setupComplete) offerSetupActions else true

    fun leftoverFreeStart(
        sessionLive: Boolean,
        setupComplete: Boolean,
        hasRecoveryVolt: Boolean,
        quietStart: Boolean,
    ): FreeStartRank = when {
        sessionLive -> FreeStartRank.HIDDEN
        !setupComplete -> FreeStartRank.TEXT
        quietStart -> FreeStartRank.TEXT
        hasRecoveryVolt -> FreeStartRank.SECONDARY
        else -> FreeStartRank.PRIMARY
    }

    /**
     * Filled [PrimaryGymButton] count on Home's leftover card with no plan.
     * Must stay at most one in every combination the card actually renders.
     */
    fun leftoverNoPlanFilledCount(
        setupComplete: Boolean,
        sessionLive: Boolean,
        quietStart: Boolean,
        offerSetupActions: Boolean,
    ): Int {
        var filled = 0
        if (!setupComplete) {
            if (offerSetupActions) filled += 1
        } else {
            filled += 1
        }
        val rank = leftoverFreeStart(
            sessionLive = sessionLive,
            setupComplete = setupComplete,
            hasRecoveryVolt = leftoverHasRecoveryVolt(
                setupComplete = setupComplete,
                offerSetupActions = offerSetupActions,
            ),
            quietStart = quietStart,
        )
        if (rank == FreeStartRank.PRIMARY) filled += 1
        return filled
    }
}
