package com.sinura.personaltrainer.domain

object RoutineEditorPolicy {
    const val NEW_ID = "new"

    fun incomingId(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() && it != NEW_ID }

    fun shouldDiscardStub(createdThisSession: Boolean, exerciseCount: Int): Boolean =
        createdThisSession && exerciseCount <= 0
}

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
