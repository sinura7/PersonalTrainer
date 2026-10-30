package com.sinura.personaltrainer.ui.routines

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.ScreenSkeleton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * Plan day (routine editor) states from blueprint 4.3 (A22, A29) that are not already the
 * editor's own panels: skeleton loading, the resumed banner and the unsaved-back dialog.
 */
@Composable
internal fun RoutineEditorLoading(modifier: Modifier = Modifier) {
    ScreenSkeleton(modifier = modifier.testTag(RoutineEditorStateTags.LOADING), cards = 3)
}

/** Resume: edits restored after process death or a leave that could not land. */
@Composable
internal fun RoutineRecoveryBanner(resumed: Boolean) {
    Text(
        text = stringResource(if (resumed) R.string.routine_resumed else R.string.routine_unsaved_title),
        style = InstrumentType.caption,
        color = Volt,
        modifier = Modifier.testTag(RoutineEditorStateTags.RESUMED),
    )
}

/**
 * Back with edits that did not land (RoutineExitOutcome.Unsaved). Keep editing is the plain
 * dismissal, so a tap outside never drops work; Discard changes is the red one. When the draft
 * is kept on the device the confirm keeps it instead of discarding it.
 */
@Composable
internal fun RoutineUnsavedDialog(
    body: String,
    keepsFields: Boolean,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    ConfirmActionDialog(
        title = stringResource(R.string.routine_unsaved_title),
        body = body,
        confirmLabel = if (keepsFields) {
            stringResource(R.string.routine_unsaved_keep_fields)
        } else {
            stringResource(R.string.routine_unsaved_discard)
        },
        destructive = !keepsFields,
        onConfirm = onDiscard,
        onDismiss = onKeepEditing,
        dismissLabel = stringResource(R.string.routine_unsaved_keep),
    )
}

object RoutineEditorStateTags {
    const val LOADING = "routine-editor-loading"
    const val RESUMED = "routine-editor-resumed"
}
