package com.sinura.personaltrainer.util

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** A user-facing failure, remembered with the action that raised it and when. */
data class ActionError(
    val source: String,
    val message: String,
    /** Position on the slot's own clock, so a success can tell older refusals from newer. */
    val raisedAt: Long,
)

/**
 * One user-facing error at a time, owned by the action that raised it.
 *
 * Every ViewModel used to hold a `MutableStateFlow<String?>` and have each action write
 * `null` into it when it succeeded. Two actions in flight at once then raced: the first one's
 * success wiped the second one's fresh failure before any collector had seen it. The tests
 * met that as a wedge — `logSet`'s tail clearing `removeSelectedLift`'s refusal, one target
 * commit's success clearing the next commit's "at least 1" — and on the phone it is a message
 * that never appears.
 *
 * Two rules replace "success clears everything":
 *
 * 1. **A success clears only its own family.** `logSet` succeeding clears a `logSet`
 *    refusal, because the user fixed the input; it can no longer clear anything else. A
 *    success that genuinely answers a related refusal names it at the call site.
 * 2. **A success clears only what was already showing when it started.** The action takes
 *    a [mark] before its first suspension and hands it back to [clearFrom]. A refusal raised
 *    after that mark is newer information — very often from a second tap of the same
 *    action — and survives. This is what the family rule alone cannot cover: two commits of
 *    the same field, the first slow and successful, the second instant and refused.
 *
 * Screens keep reading a plain `String?`: [messages] and [message] drop the bookkeeping.
 */
class ErrorSlot {
    private val held = MutableStateFlow<ActionError?>(null)
    private val clock = AtomicLong()

    /** The text alone, for a `uiState` projection. Screens never see the source. */
    val messages: Flow<String?> = held.map { it?.message }

    /** The text alone, read synchronously. */
    val message: String?
        get() = held.value?.message

    /**
     * Where an action starts. Take it before the first suspension — at the tap, in effect —
     * and pass it to [clearFrom] so that only refusals older than the tap are cleared.
     */
    fun mark(): Long = clock.incrementAndGet()

    /** Raise [message] on behalf of [source], replacing whatever was showing. */
    fun fail(source: String, message: String) {
        held.value = ActionError(
            source = source,
            message = message,
            raisedAt = clock.incrementAndGet(),
        )
    }

    /**
     * Clear the error only if [source] raised it, and only if it was raised before [before].
     * Anything else stays. Omitting [before] clears the family regardless of age, which is
     * right for an explicit retry the user just asked for. A success that also answers a
     * related refusal calls this once per family, so each pairing is written out.
     */
    fun clearFrom(source: String, before: Long = Long.MAX_VALUE) {
        held.update { current ->
            current?.takeUnless { it.source == source && it.raisedAt <= before }
        }
    }

    /** The user dismissed it, whoever raised it. */
    fun dismiss() {
        held.value = null
    }
}
