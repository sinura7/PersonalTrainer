package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SetEntryPanel
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary

object SetEditTestTags {
    const val DELETE = "set-edit-delete"
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
    exerciseName: String,
    initial: SetLog?,
    onSave: (weightKg: Double, reps: Int, rpe: Int?, isWarmup: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    prefillWeightKg: Double = 0.0,
    prefillReps: Int = DEFAULT_REPS,
    loadClass: LoadClass = LoadClass.LOADED,
    plated: Boolean = false,
) {
    // Keyed on the set being edited: the sheet is one composable serving every row, so without
    // the key, opening set 2 after set 1 would show set 1's numbers.
    val key = initial?.id ?: ADD_MODE_KEY
    var weightKg by rememberSaveable(key) { mutableDoubleStateOf(initial?.weightKg ?: prefillWeightKg) }
    var reps by rememberSaveable(key) { mutableIntStateOf(initial?.reps ?: prefillReps) }
    var rpe by rememberSaveable(key) { mutableStateOf(initial?.rpe) }
    var isWarmup by rememberSaveable(key) { mutableStateOf(initial?.isWarmup ?: false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space7),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
                Kicker(exerciseName)
                Text(
                    if (initial == null) "Add set" else "Edit set ${initial.setNumber}",
                    style = InstrumentType.title,
                    color = TextPrimary,
                )
            }
            SetEntryPanel(
                weightKg = weightKg,
                reps = reps,
                onWeightKgChange = { weightKg = it },
                onRepsAdjust = { delta -> reps = (reps + delta).coerceIn(1, NumericEntry.MAX_REPS) },
                loadClass = loadClass,
                plated = plated,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                Kicker("Effort")
                Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                    RPE_VALUES.forEach { value ->
                        InstrumentChip(
                            label = value.toString(),
                            selected = rpe == value,
                            // Tapping the selected chip clears it: RPE is optional, and a
                            // number entered by accident needs a way back to "not recorded".
                            onClick = { rpe = if (rpe == value) null else value },
                        )
                    }
                }
            }
            InstrumentChip(
                label = "Warm-up",
                selected = isWarmup,
                onClick = { isWarmup = !isWarmup },
            )
            PrimaryGymButton(
                text = "Save",
                onClick = { onSave(weightKg, reps, rpe, isWarmup) },
            )
            if (onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SetEditTestTags.DELETE),
                ) {
                    Text("Delete set", style = InstrumentType.bodyStrong, color = Danger)
                }
            }
        }
    }
}

private const val ADD_MODE_KEY = "add"
private const val DEFAULT_REPS = 5
private val RPE_VALUES = listOf(6, 7, 8, 9, 10)
