package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextPrimary

internal object CompletedNotesTags {
    const val RECOVERY = "completed-notes-recovery"
    const val RETRY = "completed-notes-read-retry"
    const val DISCARD = "completed-notes-discard"
}

/** Conflicting notes remain selectable; accepting a private draft never implies a saved note. */
@Composable
internal fun CompletedReceiptNotes(
    notes: String, state: CompletedNotesFormState, save: NotesSaveState, expanded: Boolean,
    onToggle: () -> Unit, onChange: (String) -> Unit, onRetrySave: () -> Unit,
    onRetryPrivate: () -> Unit, onDiscard: () -> Unit, allowActions: Boolean = true,
) {
    if (state.error != null && !state.ready) {
        CompletedNotesConflict(raw = notes, state = state, onRetry = onRetryPrivate, onDiscard = onDiscard,
            allowDiscard = allowActions && !save.busy)
    } else {
        NotesBlock(notes = notes, expanded = expanded, onToggle = onToggle, onChange = onChange,
            saveState = save, onRetryNotes = onRetrySave, enabled = state.ready && allowActions)
        CompletedNotesFeedback(state = state, onRetry = onRetryPrivate, onDiscard = onDiscard,
            allowDiscard = allowActions && !save.busy)
    }
}

@Composable
internal fun CompletedNotesFeedback(state: CompletedNotesFormState, onRetry: () -> Unit, onDiscard: () -> Unit, allowDiscard: Boolean = true) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val message = when {
        state.discarding -> "Discarding unfinished notes…"
        state.error != null -> state.error
        state.keeping -> "Keeping unfinished notes…"
        state.resumed -> "Unfinished notes restored. Review them before saving."
        state.kept -> "Unfinished notes kept on this phone."
        else -> null
    }
    if (message != null) {
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(CompletedNotesTags.RECOVERY)) {
            Text(message, style = InstrumentType.caption, color = TextSecondary)
            if (state.error != null) {
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(CompletedNotesTags.RETRY)) {
                    Text("Read unfinished notes again")
                }
            }
            if (state.resumed || state.error != null || state.kept) {
                TextButton(onClick = { confirmDiscard = true }, enabled = allowDiscard && !state.discarding, modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(CompletedNotesTags.DISCARD)) {
                    Text("Discard unfinished notes", color = TextSecondary)
                }
            }
        }
    }
    if (confirmDiscard) {
        ConfirmActionDialog(
            title = "Discard unfinished notes?",
            body = "Only this unfinished note draft is removed. Notes already saved to the session stay saved.",
            confirmLabel = "Discard draft", destructive = true,
            onConfirm = { confirmDiscard = false; onDiscard() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

/** A missing/unavailable receipt still exposes its retained authored text and recovery actions. */
@Composable
internal fun CompletedNotesConflict(raw: String, state: CompletedNotesFormState, onRetry: () -> Unit, onDiscard: () -> Unit, allowDiscard: Boolean = true) {
    if (raw.isNotEmpty()) {
        Text("Unfinished notes", style = InstrumentType.bodyStrong, color = TextSecondary)
        SelectionContainer { Text(raw, style = InstrumentType.body, color = TextPrimary) }
    }
    CompletedNotesFeedback(state = state, onRetry = onRetry, onDiscard = onDiscard, allowDiscard = allowDiscard)
}
