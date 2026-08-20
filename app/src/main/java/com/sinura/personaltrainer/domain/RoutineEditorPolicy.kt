package com.sinura.personaltrainer.domain

object RoutineEditorPolicy {
    const val NEW_ID = "new"

    fun incomingId(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() && it != NEW_ID }

    fun shouldDiscardStub(createdThisSession: Boolean, exerciseCount: Int): Boolean =
        createdThisSession && exerciseCount <= 0

    /**
     * What to write when the editor closes with edits the user never pressed Save on, or null
     * when there is nothing worth a write.
     *
     * Every other edit in the routine editor — adding an exercise, removing one, reordering,
     * changing targets — reaches Room the moment it happens. The name and the notes were the two
     * exceptions: held in memory behind a Save button, and dropped on the floor by the exit path.
     * Renaming a routine and swiping back discarded the rename with no warning and no undo.
     *
     * Three rules earn their place here rather than at the call site:
     *
     * 1. **Nothing is written before the editor has hydrated.** The typed fields start empty and
     *    are seeded from Room by a suspending read. Backing out inside that window would compare
     *    an empty box against real stored notes, call it an edit, and erase them — trading a lost
     *    rename for lost notes, which is strictly worse than the bug this fixes.
     * 2. **A blank name never overwrites a real one.** Clearing the field to retype and then
     *    leaving must not replace "Push Day" with an empty string — the stored name is kept and
     *    only the notes, if they changed, are written.
     * 3. **Nothing is written when nothing changed.** Otherwise every visit to the screen would
     *    touch the row, and anything observing routines would see a pointless emission. Both
     *    fields are compared trimmed, because the repository trims before it stores — comparing
     *    raw text against trimmed text would call a stray trailing space an edit forever.
     */
    fun detailsToPersistOnExit(
        hydrated: Boolean,
        typedName: String,
        typedNotes: String,
        storedName: String,
        storedNotes: String,
    ): PendingDetails? {
        if (!hydrated) return null
        val trimmedName = typedName.trim()
        val name = if (trimmedName.isEmpty()) storedName else trimmedName
        val notes = typedNotes.trim()
        if (name == storedName && notes == storedNotes) return null
        return PendingDetails(name = name, notes = notes)
    }
}

/** The name and notes to write on the way out of the routine editor. */
data class PendingDetails(val name: String, val notes: String)

/** Where the routine editor is in its lifecycle. */
enum class EditorPhase {
    /** The routine row has not been read yet. */
    LOADING,

    /** There is something to edit — an existing routine, or a new one not yet created. */
    EDITING,

    /** The row is gone: deleted, restored over, or the id was stale on arrival. Terminal. */
    MISSING,
}

/**
 * The editor's load state as one immutable value.
 *
 * This used to be four independent fields — two `MutableStateFlow`s and two plain `var`s —
 * updated from two places, with the missing-routine rule spelled out inline as a four-clause
 * boolean. The rule itself is subtle enough to deserve testing on its own: a null emission
 * means "deleted" only once the routine has actually been seen present, because before the
 * first real emission null just means the query has not answered yet. Getting that backwards
 * shows the user "This routine is no longer available" for a routine that is fine.
 */
data class RoutineEditorLoad(
    /** False for a brand-new routine, which has no row to read and so nothing to wait for. */
    val opensExisting: Boolean,
    val hydrated: Boolean = !opensExisting,
    val loadedExisting: Boolean = false,
    val sawRoutine: Boolean = false,
    val missing: Boolean = false,
) {
    val phase: EditorPhase
        get() = when {
            missing -> EditorPhase.MISSING
            !hydrated -> EditorPhase.LOADING
            else -> EditorPhase.EDITING
        }

    /** The one-off read that opens the screen. */
    fun onInitialRead(found: Boolean): RoutineEditorLoad = when {
        !opensExisting -> copy(hydrated = true)
        found -> copy(hydrated = true, loadedExisting = true)
        else -> copy(hydrated = true, missing = true)
    }

    /** A live emission of the routine flow. */
    fun onRoutineEmission(present: Boolean): RoutineEditorLoad = when {
        present -> copy(sawRoutine = true)
        loadedExisting && sawRoutine && !missing -> copy(missing = true)
        else -> this
    }

    /** The row turned out to be gone during an action this screen took. */
    fun markMissing(): RoutineEditorLoad = copy(missing = true)
}
