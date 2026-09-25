package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.EndWorkoutCopy
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * Finish on the live log: save the logged sets, or leave without saving.
 *
 * X / back is go-Home with the session still live. This dialog is the
 * explicit end. Discard still routes through a named confirm so destroy
 * is never one tap (G-07).
 *
 * With [editOpen], a logged set is open for correction and the change is not saved yet: the
 * dialog says so first and offers the way back to it (audit UI-2).
 */
@Composable
fun EndWorkoutDialog(
    loggedSets: Int,
    onSave: () -> Unit,
    onDiscardInstead: () -> Unit,
    onDismiss: () -> Unit,
    notes: String = "",
    notesExpanded: Boolean = false,
    onToggleNotes: () -> Unit = {},
    onNotesChange: (String) -> Unit = {},
    editOpen: Boolean = false,
) {
    val canSave = EndWorkoutCopy.canSave(loggedSets)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(EndWorkoutCopy.TITLE, style = InstrumentType.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                if (editOpen) {
                    Text(
                        EndWorkoutCopy.EDIT_OPEN,
                        style = InstrumentType.body,
                        modifier = Modifier.testTag(EndWorkoutTags.EDIT_OPEN),
                    )
                }
                Text(
                    EndWorkoutCopy.body(loggedSets),
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
                NotesBlock(
                    notes = notes,
                    expanded = notesExpanded,
                    onToggle = onToggleNotes,
                    onChange = onNotesChange,
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                if (editOpen) {
                    SecondaryGymButton(
                        text = EndWorkoutCopy.BACK_TO_EDIT,
                        onClick = onDismiss,
                        modifier = Modifier.testTag(EndWorkoutTags.BACK_TO_EDIT),
                    )
                }
                PrimaryGymButton(
                    text = EndWorkoutCopy.SAVE,
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.testTag(EndWorkoutTags.SAVE),
                )
                DangerGymButton(
                    text = EndWorkoutCopy.DISCARD,
                    onClick = onDiscardInstead,
                    modifier = Modifier.testTag(EndWorkoutTags.DISCARD),
                )
            }
        },
    )
}

object EndWorkoutTags {
    const val SAVE = "workout-end-save"
    const val DISCARD = "workout-end-discard"
    const val EDIT_OPEN = "workout-end-edit-open"
    const val BACK_TO_EDIT = "workout-end-back-to-edit"
}
