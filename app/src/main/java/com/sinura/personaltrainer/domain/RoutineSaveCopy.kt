package com.sinura.personaltrainer.domain

/**
 * What the routine editor says about saving.
 *
 * The editor has two kinds of write and the copy must not blur them. Lifts, order and
 * removals write through the moment they happen; the name, the notes and typed targets wait
 * for Save or Back. Every line here is factual about which is which: one explanation of
 * what did not save, one action. Nothing here promises a save that has not happened.
 */
object RoutineSaveCopy {
    const val SAVE = SessionOrderCopy.SAVE_ROUTINE
    const val SAVING = "Saving…"

    /** The one quiet line beside the dock that explains the write-through model. */
    const val WRITE_THROUGH =
        "Lift changes save as you make them. Save keeps the name, notes and targets."

    /** A value the routine cannot hold. The card says this; so does Save when it refuses. */
    const val TARGETS_REJECTED = "Sets and reps must be at least 1."

    const val DETAILS_FAILED =
        "Could not save the name and notes. Your lifts are saved. Try again."

    /** The exit could not even read the routine to decide what is owed. Nothing was written. */
    const val EXIT_READ_FAILED =
        "Could not check what still needs saving. Nothing was changed. Try again."

    /** What the Back prompt lists when a read fault stopped it from naming the writes. */
    const val UNKNOWN_ITEMS = "your latest changes"

    const val UNSAVED_TITLE = "Some changes are not saved"
    const val TRY_AGAIN = "Try again"
    const val LEAVE_ANYWAY = "Leave without saving these"
    const val ALREADY_SAVED = "Lifts you added, moved or removed are already saved."

    /** How the name and notes are named in the Back prompt's list. */
    const val DETAILS_ITEM = "the name and notes"

    fun targetsFailed(liftName: String): String =
        "Could not save the targets for $liftName. Try again."

    /** How one lift's targets are named in the Back prompt's list. */
    fun targetsItem(liftName: String): String = "the targets for $liftName"

    /**
     * The Back prompt's body. Lists what did not save, then says what did, so that "leave
     * without saving these" cannot be read as "lose the lifts too".
     */
    fun unsavedOnBackBody(items: List<String>): String =
        "Not saved: ${items.joinToString(", ")}. $ALREADY_SAVED"

    /** The dock's label. "Saving…" while an exit attempt is landing writes; "Save" otherwise. */
    fun saveLabel(saving: Boolean): String = if (saving) SAVING else SAVE
}
