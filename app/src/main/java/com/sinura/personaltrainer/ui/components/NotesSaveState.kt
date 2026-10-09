package com.sinura.personaltrainer.ui.components

enum class NotesSaveStatus { UNKNOWN, PENDING, SAVING, SAVED, FAILED }

/** Presentation of the existing notes write, never a second text draft. */
data class NotesSaveState(
    val status: NotesSaveStatus = NotesSaveStatus.UNKNOWN,
    val busy: Boolean = false,
    val canRetry: Boolean = false,
    val cleared: Boolean = false,
    val missing: Boolean = false,
)

object NotesTestTags {
    const val STATUS = "session-notes-status"
    const val RETRY = "session-notes-retry"
    const val EXIT_RETRY = "session-notes-exit-retry"
    const val KEEP_EDITING = "session-notes-keep-editing"
    const val LEAVE = "session-notes-leave"
}

fun notesSaveMessage(state: NotesSaveState): String? = when (state.status) {
    NotesSaveStatus.UNKNOWN -> null
    NotesSaveStatus.PENDING -> "Notes not saved yet"
    NotesSaveStatus.SAVING -> "Saving notes…"
    NotesSaveStatus.SAVED -> if (state.cleared) "Notes cleared" else "Notes saved"
    NotesSaveStatus.FAILED -> if (state.missing) {
        "Notes not saved. This workout is no longer on this phone."
    } else {
        "Notes not saved. Your text is kept here."
    }
}
