package com.sinura.personaltrainer.ui.components


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Danger

/**
 * How the session went, in words, folded away until it is wanted.
 *
 * Collapsed by default because a text field mounted permanently is a text field the thumb
 * finds by accident mid-set. A caller that tracks writes supplies their confirmed state;
 * text in an editor alone never establishes that notes were saved.
 *
 * Shared by the live workout and by session detail rather than duplicated, so a note written
 * during a session and a note added to it a week later are the same affordance with the same
 * words. Session callers share the ordered notes writer; untracked callers stay neutral.
 */
enum class NotesKind { SESSION, PROGRAM }

@Composable
fun NotesBlock(
    notes: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    kind: NotesKind = NotesKind.SESSION,
    saveState: NotesSaveState = NotesSaveState(),
    onRetryNotes: () -> Unit = {},
    enabled: Boolean = true,
) {
    Column(modifier = modifier) {
        TextButton(onClick = onToggle, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = Metrics.touchMin)) {
            Icon(
                if (expanded) OutlinedMarks.ExpandLess else OutlinedMarks.ExpandMore,
                contentDescription = null,
                tint = TextSecondary,
            )
            Text(
                notesToggleLabel(kind = kind, notes = notes, expanded = expanded),
                style = InstrumentType.bodyStrong,
                color = TextSecondary,
                modifier = Modifier.padding(start = Metrics.space2),
            )
        }
        if (expanded) {
            OutlinedTextField(
                value = notes,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes") },
                minLines = 2,
                enabled = enabled,
            )
        }
        notesSaveMessage(saveState)?.let { message ->
            Text(
                message,
                style = InstrumentType.caption,
                color = if (saveState.status == NotesSaveStatus.FAILED) Danger else TextSecondary,
                modifier = Modifier.testTag(NotesTestTags.STATUS),
            )
        }
        if (saveState.status == NotesSaveStatus.FAILED && !saveState.missing) {
            TextButton(
                onClick = onRetryNotes,
                enabled = enabled && saveState.canRetry,
                modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(NotesTestTags.RETRY),
            ) {
                Text("Retry notes", style = InstrumentType.bodyStrong)
            }
        }
    }
}

fun notesToggleLabel(kind: NotesKind, notes: String, expanded: Boolean): String = when {
    expanded -> "Hide notes"
    kind == NotesKind.PROGRAM && notes.isBlank() -> "Add notes"
    kind == NotesKind.PROGRAM -> "Notes"
    else -> "Session notes"
}

/** A failed exit keeps the editor reachable; leaving requires an explicit choice. */
@Composable
fun NotesLeaveDialog(
    saveState: NotesSaveState,
    liveDraft: Boolean,
    onRetry: () -> Unit,
    onKeepEditing: () -> Unit,
    onLeave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Notes not saved", style = InstrumentType.title) },
        text = {
            Text(
                if (liveDraft) "Notes are not saved. Closing the app may lose this draft."
                else "Notes are not saved. Leaving discards these changes.",
                modifier = Modifier.verticalScroll(rememberScrollState()),
                style = InstrumentType.body,
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                TextButton(
                    onClick = onRetry,
                    enabled = !saveState.busy && !saveState.missing,
                    modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(NotesTestTags.EXIT_RETRY),
                ) { Text("Retry notes", style = InstrumentType.bodyStrong) }
                TextButton(
                    onClick = onKeepEditing,
                    modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(NotesTestTags.KEEP_EDITING),
                ) { Text("Keep editing", style = InstrumentType.bodyStrong) }
                TextButton(
                    onClick = onLeave,
                    enabled = !saveState.busy,
                    modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(NotesTestTags.LEAVE),
                ) {
                    Text(
                        if (liveDraft) "Leave workout open" else "Leave without these changes",
                        style = InstrumentType.bodyStrong,
                        color = if (liveDraft) TextSecondary else Danger,
                    )
                }
            }
        },
    )
}
