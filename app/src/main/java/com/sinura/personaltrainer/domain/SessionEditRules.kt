package com.sinura.personaltrainer.domain

/**
 * When a lift can still be taken out of a live session, or exchanged for another.
 *
 * The rule is the same for both and it is about history, not about tidiness: once a set has
 * been logged against a lift, that lift is part of what happened. Removing it would delete a
 * record of work actually done, and swapping it would silently reattribute those sets to a
 * different exercise — the weights would stay and the name over them would change. A warm-up
 * counts: it is still something the person did.
 *
 * Pure so the guards can be tested without a database. The repository enforces them; nothing
 * else is allowed to decide.
 */
object SessionEditRules {
    const val FINISHED_SESSION = "That workout has already finished."
    const val HAS_LOGGED_SETS = "That lift already has logged sets. Delete them first."
    const val ITEM_MISSING = "That lift is no longer in this session."
    const val ALREADY_PRESENT = "That lift is already in this session."

    /** Null when the edit is allowed; otherwise the user-facing reason it is not. */
    fun refusalForRemove(
        sessionFinished: Boolean,
        itemExists: Boolean,
        loggedSetCount: Int,
    ): String? = when {
        sessionFinished -> FINISHED_SESSION
        !itemExists -> ITEM_MISSING
        loggedSetCount > 0 -> HAS_LOGGED_SETS
        else -> null
    }

    fun refusalForSwap(
        sessionFinished: Boolean,
        itemExists: Boolean,
        loggedSetCount: Int,
        replacementAlreadyPresent: Boolean,
    ): String? = refusalForRemove(sessionFinished, itemExists, loggedSetCount)
        ?: ALREADY_PRESENT.takeIf { replacementAlreadyPresent }
}
