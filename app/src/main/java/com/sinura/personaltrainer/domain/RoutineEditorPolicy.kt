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

    /**
     * What to write for one lift's targets, or null when there is nothing worth a write.
     *
     * The routine editor used to make the owner press "Update targets" on each card, and the
     * screen's other, much more prominent Save button did not touch them — it wrote the name
     * and notes and then said "Routine saved". Typing 4×8 into three lifts, pressing the button
     * that claims to save the routine, and leaving discarded all three. This function is what
     * lets those fields write themselves through the way every other edit on the screen already
     * does, so that neither button needs to exist.
     *
     * The rules are the same shape as [detailsToPersistOnExit], for the same reasons:
     *
     * 1. **An empty box means "leave this one alone", not "zero".** A field is empty for a whole
     *    keystroke every time someone clears it to retype, and that instant must not be read as
     *    a request to store no sets. A *typed* zero is a different thing and is passed through
     *    to be rejected by the caller — the owner said something wrong and should be told.
     * 2. **A blank weight is a real answer.** Unlike sets and reps, clearing the target weight
     *    means "no target", which is why [typedWeightKg] is taken at face value: the caller has
     *    already parsed it, and null there is the parse of an empty box.
     * 3. **Nothing changed, nothing written.** Committing on every focus change would otherwise
     *    touch the row each time a finger passed through a field, and everything observing
     *    routines would redraw for no reason.
     */
    fun targetsToPersist(
        typedSets: Int?,
        typedReps: Int?,
        typedWeightKg: Double?,
        typedRestSeconds: Int?,
        storedSets: Int,
        storedReps: Int,
        storedWeightKg: Double?,
        storedRestSeconds: Int,
    ): PendingTargets? {
        val sets = typedSets ?: storedSets
        val reps = typedReps ?: storedReps
        val rest = typedRestSeconds ?: storedRestSeconds
        if (sets == storedSets &&
            reps == storedReps &&
            rest == storedRestSeconds &&
            typedWeightKg == storedWeightKg
        ) {
            return null
        }
        return PendingTargets(
            targetSets = sets,
            targetReps = reps,
            targetWeightKg = typedWeightKg,
            restSeconds = rest,
        )
    }
}

/** The name and notes to write on the way out of the routine editor. */
data class PendingDetails(val name: String, val notes: String)

/** One lift’s targets, resolved against what is stored and ready to write. */
data class PendingTargets(
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double?,
    val restSeconds: Int,
)

/** Where the routine editor is in its lifecycle. */
enum class EditorPhase {
    /** The routine row has not been read yet. */
    LOADING,

    /** There is something to edit — an existing routine, or a new one not yet created. */
    EDITING,

    /** The row is gone: deleted, restored over, or the id was stale on arrival. Terminal. */
    MISSING,

    /**
     * The opening read of an existing routine threw before it could hydrate. Distinct from
     * MISSING: the row may well be there, but Room could not answer, so the editor cannot say
     * it is gone. Retriable — a retry drops back to LOADING and reads again — rather than
     * terminal, and it exists so a failed hydration surfaces an error the user can act on
     * instead of a spinner that never resolves.
     */
    FAILED,
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
    val failed: Boolean = false,
) {
    val phase: EditorPhase
        get() = when {
            missing -> EditorPhase.MISSING
            failed -> EditorPhase.FAILED
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

    /**
     * The opening read threw. Only meaningful before hydration: once the editor is EDITING there
     * is real data on screen, and once MISSING the routine's absence is already settled, so
     * neither is torn down for a late read failure. A new routine has nothing to read and so
     * never reaches here.
     */
    fun markFailed(): RoutineEditorLoad =
        if (hydrated || missing || failed) this else copy(failed = true)

    /** Drop a FAILED editor back to LOADING so hydration can be attempted again. */
    fun onRetry(): RoutineEditorLoad = if (failed) copy(failed = false) else this
}
