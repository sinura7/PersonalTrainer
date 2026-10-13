package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SetEntryPanel
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.rememberFullSheetState
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.LocalWorkoutNumbers
import com.sinura.personaltrainer.ui.workout.WorkoutSaveState
import com.sinura.personaltrainer.ui.workout.WorkoutSavePhase

object SetEditTestTags {
    const val DELETE = "set-edit-delete"
    const val IDENTITY = "set-edit-identity"
    const val NAME = "set-edit-name"
    const val CONTENT = "set-edit-content"
    const val CANCEL = "set-edit-cancel"
}

/**
 * One set, in a sheet, long after the workout ended.
 *
 * A logged set is a measurement, and measurements get mistyped — 1150 kg where 115 belonged,
 * a working set marked warm-up, an RPE remembered on the drive home. Until now the record was
 * frozen the moment the session finished, so the only repair available was deleting the
 * session, and a wrong number that cannot be corrected quietly poisons every average, every
 * heat window and every personal record computed from it.
 *
 * The sheet hosts the task; the alert is kept for confirming destruction only. Deleting a set
 * from here is immediate and undoable by snackbar rather than gated behind a dialog, which is
 * the same trade the live workout now makes: an undo costs a tap only when you were wrong,
 * where a confirm costs one every single time.
 *
 * Editing is deliberately narrow. `completedAt`, `setNumber`, and every session-level
 * timestamp stay untouched, because they are what anchors this set to the day it was actually
 * performed — a repair must not silently move last month's squat into this week's heat map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetEditSheet(
    exercise: Exercise,
    initial: SetLog?,
    onSave: (weightKg: Double, reps: Int, rpe: Int?, isWarmup: Boolean, durationSeconds: Int?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    prefillWeightKg: Double = 0.0,
    prefillReps: Int = DEFAULT_REPS,
    loadClass: LoadClass = LoadClass.LOADED,
    plated: Boolean = false,
    saveState: WorkoutSaveState = WorkoutSaveState(),
    validationMessage: String? = null,
    onRetry: () -> Unit = {},
    onEditValues: () -> Unit = {},
) {
    // Keyed on the set being edited: the sheet is one composable serving every row, so without
    // the key, opening set 2 after set 1 would show set 1's numbers.
    val draftKey = initial?.id ?: "$ADD_MODE_KEY:${exercise.id}"
    val privateForm = LocalFinishedSetForm.current
    val discardPrivate = LocalFinishedSetDiscard.current
    val readPrivate = LocalFinishedSetReadAgain.current
    val privateState = privateForm?.recovery?.collectAsState()?.value
    val retained = privateState?.draft?.values
    val allowEntry = privateForm?.allowsInput() != false
    val submitted = saveState.command?.values
    var localWeightKg by rememberSaveable(draftKey) { mutableDoubleStateOf(submitted?.weightKg ?: initial?.weightKg ?: prefillWeightKg) }
    var localReps by rememberSaveable(draftKey) { mutableIntStateOf(submitted?.reps ?: initial?.reps ?: prefillReps) }
    var localRpe by rememberSaveable(draftKey) { mutableStateOf(if (submitted != null) submitted.rpe else initial?.rpe) }
    var localIsWarmup by rememberSaveable(draftKey) { mutableStateOf(submitted?.isWarmup ?: initial?.isWarmup ?: false) }
    // The saved measurement determines the editor. A timed repetition set still edits reps;
    // neither planned hold metadata nor a draft change may reclassify the original result.
    val timedOriginal = initial != null && initial.reps < 1 && (initial.durationSeconds ?: 0) > 0
    var localDurationSeconds by rememberSaveable(draftKey) { mutableIntStateOf(submitted?.durationSeconds ?: initial?.durationSeconds ?: 1) }
    val weightKg = retained?.weightKg ?: localWeightKg
    val reps = retained?.reps ?: localReps
    val rpe = if (retained != null) retained.rpe else localRpe
    val isWarmup = retained?.isWarmup ?: localIsWarmup
    val durationSeconds = retained?.durationSeconds ?: localDurationSeconds
    fun updateValues(change: (WorkoutSetValues) -> WorkoutSetValues) {
        if (!allowEntry || privateForm?.allowsInput() == false) return
        val latest = privateForm?.recovery?.value?.draft?.values
            ?: WorkoutSetValues(localWeightKg, localReps, localRpe, localIsWarmup, if (timedOriginal) localDurationSeconds else initial?.durationSeconds)
        val updated = change(latest)
        if (privateForm != null) privateForm.update(updated) else {
            localWeightKg = updated.weightKg; localReps = updated.reps; localRpe = updated.rpe
            localIsWarmup = updated.isWarmup; localDurationSeconds = updated.durationSeconds ?: 1
        }
    }
    // Accepted history may exceed the new-entry fumble guard. Both typing and nudging
    // must correct the captured measurement rather than reduce it to that guard.
    val repLimit = if (initial == null) NumericEntry.MAX_REPS else Int.MAX_VALUE

    ModalBottomSheet(
        onDismissRequest = { if (!saveState.pending) onDismiss() },
        sheetState = rememberFullSheetState(LocalReducedMotion.current) { !saveState.pending },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SetEditTestTags.CONTENT)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space7),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().testTag(SetEditTestTags.IDENTITY),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExerciseThumb(
                    exercise = exercise,
                    size = ThumbSize.header,
                    modifier = Modifier.testTag("set-edit-art-${exercise.id}"),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap),
                ) {
                    Text(
                        exercise.name,
                        modifier = Modifier.testTag(SetEditTestTags.NAME),
                        style = InstrumentType.title,
                        color = TextPrimary,
                    )
                    Text(
                        if (initial == null) "Add set" else "Edit set ${initial.setNumber}",
                        style = InstrumentType.body,
                        color = TextPrimary,
                    )
                }
            }
            // Key child keypad state too: switching rows must not carry an open draft or
            // typing dialog from the preceding set into this one's save payload.
            if (submitted != null) {
                Text(
                    SetCopy.setLine(weightKg = submitted.weightKg, reps = submitted.reps, loadClass = loadClass,
                        unit = LocalWeightUnit.current, durationSeconds = submitted.durationSeconds, entryPrecision = true) +
                        (submitted.rpe?.let { " · RPE $it" } ?: "") + (if (submitted.isWarmup) " · Warm-up" else ""),
                    style = InstrumentType.title, color = TextPrimary,
                    modifier = Modifier.testTag("set-edit-submitted-values"),
                )
            } else if (privateForm != null && !allowEntry) {
                // A conflicting retained keypad cannot dismiss while its owner refuses input.
                // Show its literal fields and reachable recovery without opening that keypad.
                HistoricalSetRecovery(formState = checkNotNull(privateState), enabled = !saveState.busy && !privateForm.isClosing,
                    onRead = { readPrivate?.invoke() ?: privateForm.readAgain() },
                    onDiscard = { discardPrivate?.invoke() ?: privateForm.discard {} },
                    onResume = { privateForm.showKept() })
            } else key(draftKey) {
                CompositionLocalProvider(LocalWorkoutNumbers provides privateForm) {
                SetEntryPanel(
                    weightKg = weightKg,
                    reps = reps,
                    onWeightKgChange = { value -> updateValues { it.copy(weightKg = value) } },
                    onRepsAdjust = { delta ->
                        updateValues { it.copy(reps = (it.reps.toLong() + delta.toLong()).coerceIn(1L, repLimit.toLong()).toInt()) }
                    },
                    onRepsChange = { typed -> updateValues { it.copy(reps = typed.coerceIn(1, repLimit)) } },
                    loadClass = loadClass,
                    plated = plated,
                    durationSeconds = if (timedOriginal) durationSeconds else null,
                    onDurationSecondsChange = { value -> updateValues { it.copy(durationSeconds = value) } },
                    capturedReps = initial != null,
                )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                Kicker("Effort")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    RPE_VALUES.forEach { value ->
                        InstrumentChip(
                            label = value.toString(),
                            selected = (if (submitted != null) submitted.rpe else rpe) == value,
                            enabled = !saveState.pending && allowEntry,
                            // Clear a mistaken selection. Working repetition sets require
                            // effort; timed originals and warm-ups may leave it unrecorded.
                            onClick = { updateValues { it.copy(rpe = if (it.rpe == value) null else value) } },
                        )
                    }
                }
            }
            InstrumentChip(
                label = "Warm-up",
                selected = submitted?.isWarmup ?: isWarmup,
                enabled = !saveState.pending && allowEntry,
                onClick = { updateValues { it.copy(isWarmup = !it.isWarmup) } },
            )
            (saveState.message ?: validationMessage ?: privateState?.message?.takeIf { allowEntry || submitted != null })?.let { message ->
                Text(message, style = InstrumentType.body, color = Danger,
                    modifier = Modifier.testTag("set-edit-save-error").semantics { liveRegion = LiveRegionMode.Polite })
            }
            PrimaryGymButton(
                text = when {
                    saveState.phase == WorkoutSavePhase.CHECKING -> "Checking saved set…"
                    saveState.busy -> "Saving…"
                    saveState.pending -> "Retry save"
                    else -> "Save"
                },
                enabled = !saveState.busy && saveState.phase != WorkoutSavePhase.CONFLICT && (saveState.pending || allowEntry),
                onClick = {
                    if (saveState.pending) onRetry() else onSave(
                        weightKg,
                        if (timedOriginal) checkNotNull(initial).reps else reps,
                        rpe,
                        isWarmup,
                        if (timedOriginal) durationSeconds else null,
                    )
                },
            )
            if (saveState.pending && !saveState.busy) {
                TextButton(onClick = onEditValues, modifier = Modifier.fillMaxWidth().heightIn(min = Metrics.touchMin)) {
                    Text(if (saveState.phase == WorkoutSavePhase.CONFLICT) "Review saved sets" else "Edit values", style = InstrumentType.bodyStrong, color = TextPrimary)
                }
            }
            if (privateForm != null && (allowEntry || submitted != null)) {
                if (privateState?.resumed == true) Text("Resumed unfinished set", style = InstrumentType.caption, color = TextPrimary)
                if (privateState?.message != null) TextButton(onClick = { readPrivate?.invoke() ?: privateForm.readAgain() },
                    enabled = !saveState.busy && !privateForm.isClosing, modifier = Modifier.fillMaxWidth().heightIn(min = Metrics.touchMin)) {
                    Text("Read again", style = InstrumentType.bodyStrong, color = TextPrimary)
                }
            }
            if (privateForm != null && (allowEntry || submitted != null)) {
                TextButton(onClick = { discardPrivate?.invoke() ?: privateForm.discard {} }, enabled = !saveState.busy && !privateForm.isClosing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Metrics.touchMin).testTag("set-edit-discard-draft")) {
                    Text("Discard unfinished edit", style = InstrumentType.bodyStrong, color = TextPrimary)
                }
            }
            if (onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    enabled = !saveState.pending && allowEntry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Metrics.touchMin)
                        .testTag(SetEditTestTags.DELETE),
                ) {
                    Text("Delete set", style = InstrumentType.bodyStrong, color = Danger)
                }
            }
            TextButton(
                onClick = onDismiss,
                enabled = !saveState.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = Metrics.touchMin).testTag(SetEditTestTags.CANCEL),
            ) {
                Text(if (privateForm == null) "Cancel" else "Close · keep edit", style = InstrumentType.bodyStrong, color = TextPrimary)
            }
        }
    }
}

private const val ADD_MODE_KEY = "add"
private const val DEFAULT_REPS = 5
private val RPE_VALUES = listOf(6, 7, 8, 9, 10)
