package com.sinura.personaltrainer.domain

/**
 * The fate of one write the routine editor's exit is responsible for.
 *
 * The editor writes lifts, order and removals through as they happen. Two things wait for
 * the way out: the name and notes, and any targets typed into a card but not yet committed.
 * Those two used to be fire-and-forget — a failed write was logged and the screen popped
 * anyway, so the owner saw a Save button do its animation and walked off with a rename that
 * was never stored. Making the exit truthful starts with each write reporting what became
 * of it, as a value the exit can reason about rather than a log line nobody reads.
 */
sealed interface RoutineWriteOutcome {
    /** The write reached Room. Finished with. */
    data object Stored : RoutineWriteOutcome

    /** Nothing differed from what is stored, so nothing was attempted. Finished with. */
    data object NothingToWrite : RoutineWriteOutcome

    /**
     * Refused before any write: the value cannot be stored as typed (zero sets, say).
     * Retrying changes nothing — the owner has to change the value — so it is held rather
     * than dropped, and [reason] is what the card has already told them.
     */
    data class Rejected(val reason: String) : RoutineWriteOutcome

    /** Attempted and thrown. The value is held so that one more attempt can land it. */
    data object Failed : RoutineWriteOutcome

    /** True when there is nothing left to do for this write. */
    val landed: Boolean
        get() = this is Stored || this is NothingToWrite
}

/** One lift's targets during an exit attempt, named so the message can say which lift. */
data class RoutineTargetsOutcome(
    val liftName: String,
    val outcome: RoutineWriteOutcome,
)

/** What one exit attempt — Back or Save — found when it tried to land every required write. */
sealed interface RoutineExitOutcome {
    /** Everything Save is responsible for is in Room. The screen may pop. */
    data object Landed : RoutineExitOutcome

    /**
     * At least one required write did not land, so the screen must stay.
     *
     * [message] is the one line the Save dock shows — see [RoutineEditorPolicy.exitOutcome]
     * for which unsaved write wins when several did not land. [items] names every unsaved
     * write in the order the flush ran, for the Back prompt that lists them.
     */
    data class Unsaved(
        val message: String,
        val items: List<String>,
    ) : RoutineExitOutcome {
        /** The body of the Back prompt: what did not save, and that everything else did. */
        val backPromptBody: String
            get() = RoutineSaveCopy.unsavedOnBackBody(items)
    }
}
