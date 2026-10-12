package com.sinura.personaltrainer.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.InstrumentChoiceChip
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.LoadTypeTags
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.rememberFullSheetState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RecoverableLiftEditorSheet(
    draft: ExerciseEditorDraft,
    muscleOptions: List<String>,
    status: LiftEditorStatus,
    error: String?,
    libraryReady: Boolean,
    onDraftChange: (ExerciseEditorDraft) -> Unit,
    onSave: () -> Unit,
    onCheck: () -> Unit,
    onEditInstead: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
    onLibraryRetry: (() -> Unit)?,
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val groups = muscleOptions.ifEmpty { MuscleGroups.catalog }
    val primary = when {
        !libraryReady && onLibraryRetry != null -> "Retry library"
        !libraryReady -> "Loading library…"
        status.phase == LiftEditorPhase.OPENING -> "Opening…"
        status.phase == LiftEditorPhase.SAVING -> "Saving…"
        status.phase == LiftEditorPhase.CHECKING -> "Checking…"
        status.phase == LiftEditorPhase.CHECKPOINT_FAILED -> "Keep draft"
        status.phase == LiftEditorPhase.UNCERTAIN -> "Check save"
        status.phase == LiftEditorPhase.DISCARD_UNCERTAIN -> "Check discard"
        status.phase == LiftEditorPhase.RETRYABLE -> "Retry save"
        status.phase == LiftEditorPhase.READ_FAILED -> "Check again"
        status.phase == LiftEditorPhase.CONFLICT -> "Return to library"
        draft.id == null -> "Create lift"
        else -> "Save changes"
    }
    val missing = when {
        draft.name.isBlank() -> "Give this lift a name."
        draft.muscleGroup.isBlank() -> MuscleGroups.MISSING_MESSAGE
        else -> null
    }
    ModalBottomSheet(
        onDismissRequest = { if (!status.busy) onDismiss() },
        sheetState = rememberFullSheetState(LocalReducedMotion.current, canDismiss = {
            if (!status.busy) onDismiss()
            false
        }),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).imePadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Metrics.gutter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (draft.id == null) "New lift" else "Edit lift", style = InstrumentType.title, color = TextPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss, enabled = !status.busy, modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(LiftEditorTags.CLOSE)) {
                    Text("Close", color = TextSecondary)
                }
            }
            HorizontalDivider()
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = Metrics.gutter, vertical = Metrics.space4),
                verticalArrangement = Arrangement.spacedBy(Metrics.space4),
            ) {
                status.message?.let {
                    Text(it, style = InstrumentType.body, color = TextSecondary,
                        modifier = Modifier.testTag(LiftEditorTags.STATUS).semantics { liveRegion = LiveRegionMode.Polite })
                }
                error?.let { Text(it, style = InstrumentType.body, color = Danger) }
                OutlinedTextField(
                    value = draft.name, onValueChange = { onDraftChange(draft.copy(name = it)) },
                    label = { Text("Name") }, singleLine = status.editable, readOnly = !status.editable,
                    modifier = Modifier.fillMaxWidth().testTag(LiftEditorTags.NAME),
                )
                Column {
                    Kicker("Muscle group")
                    TextButton(onClick = { menu = true }, enabled = status.editable,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Metrics.touchMin).testTag(LiftEditorTags.MUSCLE)) {
                        Text(draft.muscleGroup.ifBlank { "Choose muscle" }, style = InstrumentType.bodyStrong, color = TextPrimary)
                    }
                    DropdownMenu(expanded = menu && status.editable, onDismissRequest = { menu = false },
                        modifier = Modifier.heightIn(max = Metrics.control * 5)) {
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(if (group.equals(draft.muscleGroup, ignoreCase = true)) "✓ $group" else group) },
                                onClick = { menu = false; onDraftChange(draft.copy(muscleGroup = group)) },
                                modifier = Modifier.heightIn(min = Metrics.touchMin),
                            )
                        }
                    }
                    if (MuscleGroups.otherSelected(draft.muscleGroup)) {
                        OutlinedTextField(value = draft.muscleGroup, onValueChange = { onDraftChange(draft.copy(muscleGroup = it)) },
                            label = { Text("Muscle group name") }, singleLine = true, readOnly = !status.editable, modifier = Modifier.fillMaxWidth())
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                    Kicker("Load type · ${draft.loadType.label}")
                    FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Metrics.space2), verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                        LoadType.entries.forEach { type ->
                            InstrumentChoiceChip(label = type.label, selected = draft.loadType == type,
                                onClick = { onDraftChange(draft.copy(loadType = type)) }, enabled = status.editable,
                                modifier = Modifier.testTag(LoadTypeTags.chip(type)))
                        }
                    }
                }
                OutlinedTextField(value = draft.notes, onValueChange = { onDraftChange(draft.copy(notes = it)) },
                    label = { Text("Notes (optional)") }, minLines = 2, readOnly = !status.editable,
                    modifier = Modifier.fillMaxWidth().testTag(LiftEditorTags.NOTES))
                if (status.phase == LiftEditorPhase.EDITING && missing != null) Text(missing, style = InstrumentType.caption, color = TextSecondary)
                if (status.phase == LiftEditorPhase.RETRYABLE) {
                    TextButton(onClick = onEditInstead, modifier = Modifier.heightIn(min = Metrics.touchMin)) { Text("Edit values instead") }
                }
            }
            HorizontalDivider()
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Metrics.gutter, vertical = Metrics.space2)) {
                PrimaryGymButton(
                    text = primary,
                    onClick = {
                        when {
                            !libraryReady -> onLibraryRetry?.invoke()
                            status.phase in setOf(LiftEditorPhase.CHECKPOINT_FAILED, LiftEditorPhase.UNCERTAIN, LiftEditorPhase.DISCARD_UNCERTAIN, LiftEditorPhase.READ_FAILED) -> onCheck()
                            status.phase == LiftEditorPhase.CONFLICT -> onDismiss()
                            else -> onSave()
                        }
                    },
                    enabled = !status.busy && (libraryReady || onLibraryRetry != null) && (status.phase != LiftEditorPhase.EDITING || missing == null),
                    disabledReason = if (status.phase == LiftEditorPhase.EDITING) missing else status.message,
                    modifier = Modifier.testTag(LiftEditorTags.PRIMARY),
                )
                TextButton(onClick = { discard = true }, enabled = !status.busy && status.phase != LiftEditorPhase.READ_FAILED,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Metrics.touchMin).testTag(LiftEditorTags.DISCARD)) {
                    Text("Discard draft", color = TextSecondary)
                }
            }
        }
    }
    if (discard) {
        ConfirmActionDialog(title = "Discard this draft?", body = "Only unfinished fields are removed. Any lift already saved remains saved.",
            confirmLabel = "Discard draft", destructive = true, onConfirm = { discard = false; onDiscard() }, onDismiss = { discard = false })
    }
}

internal object LiftEditorTags {
    const val NAME = "lift-editor-name"
    const val NOTES = "lift-editor-notes"
    const val MUSCLE = "lift-editor-muscle"
    const val PRIMARY = "lift-editor-primary"
    const val CLOSE = "lift-editor-close"
    const val DISCARD = "lift-editor-discard"
    const val STATUS = "lift-editor-status"
}
