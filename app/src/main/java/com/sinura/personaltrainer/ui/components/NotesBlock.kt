package com.sinura.personaltrainer.ui.components


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * How the session went, in words, folded away until it is wanted.
 *
 * Collapsed by default because a text field mounted permanently is a text field the thumb
 * finds by accident mid-set; the toggle's own label carries whether anything is written, so
 * nothing has to be opened to check.
 *
 * Shared by the live workout and by session detail rather than duplicated, so a note written
 * during a session and a note added to it a week later are the same affordance with the same
 * words. The write behind [onChange] is debounced by each caller's ViewModel.
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
) {
    Column(modifier = modifier) {
        TextButton(onClick = onToggle, contentPadding = PaddingValues(0.dp)) {
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
            )
        }
    }
}

fun notesToggleLabel(kind: NotesKind, notes: String, expanded: Boolean): String = when {
    expanded -> "Hide notes"
    kind == NotesKind.PROGRAM && notes.isBlank() -> "Add notes"
    kind == NotesKind.PROGRAM -> "Notes"
    notes.isBlank() -> "Session notes"
    else -> "Session notes · saved"
}
