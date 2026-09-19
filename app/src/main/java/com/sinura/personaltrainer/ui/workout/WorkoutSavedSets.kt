package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentMenu
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt

/** Ordinals are computed once per saved-list revision, independently for each set type. */
private fun savedSetLabels(sets: List<SetLog>, targetSets: Int): Map<String, String> {
    var working = 0
    var warmup = 0
    return sets.associate { set ->
        set.id to if (set.isWarmup) "Warm-up ${++warmup}" else {
            working++
            if (targetSets > 0 && working > targetSets) "Extra working set ${working - targetSets}"
            else "Working set $working" + if (targetSets > 0) " of $targetSets" else ""
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutSetsSheet(
    exerciseName: String,
    sets: List<SetLog>,
    latestSetId: String?,
    editingSetId: String?,
    targetSets: Int,
    loadClass: LoadClass,
    unit: WeightUnit,
    showAddSet: Boolean,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddSet: () -> Unit,
    onDismiss: () -> Unit,
) {
    val labels = remember(sets, targetSets) { savedSetLabels(sets, targetSets) }
    var menuId by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface3,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Metrics.gutter).testTag(WorkoutTestTags.SAVED_SETS_SHEET),
            verticalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Saved sets", modifier = Modifier.weight(1f), style = InstrumentType.title, color = TextPrimary)
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Metrics.touchMin)) {
                    Text("Done", style = InstrumentType.bodyStrong, color = TextPrimary)
                }
            }
            LazyColumn(modifier = Modifier.weight(1f, fill = false).testTag("workout-saved-sets-list")) {
                item(key = "exercise-identity") {
                    Text(exerciseName, modifier = Modifier.padding(bottom = Metrics.space3), style = InstrumentType.bodyStrong, color = TextSecondary)
                }
                items(sets, key = { it.id }) { set ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Metrics.space2).testTag("workout-saved-${set.id}"),
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                            Text(
                                labels[set.id].orEmpty() + if (set.id == latestSetId) " · Latest" else "",
                                style = InstrumentType.caption,
                                color = TextSecondary,
                            )
                            Text(
                                SetCopy.setLine(set.weightKg, set.reps, loadClass, unit, durationSeconds = set.durationSeconds, entryPrecision = true),
                                style = InstrumentType.bodyStrong,
                                color = TextPrimary,
                            )
                            set.rpe?.let { Text("RPE $it", style = InstrumentType.caption, color = TextSecondary) }
                            if (set.id == editingSetId) Text("Editing", style = InstrumentType.caption, color = Volt)
                        }
                        Box {
                            IconButton(
                                onClick = { menuId = set.id },
                                modifier = Modifier.size(Metrics.touchMin).testTag(WorkoutTestTags.setOptions(set.id)),
                            ) {
                                Icon(TemperIcons.More, contentDescription = "${labels[set.id]} actions", tint = TextSecondary)
                            }
                            InstrumentMenu(expanded = menuId == set.id, onDismissRequest = { menuId = null }) {
                                DropdownMenuItem(
                                    text = { Text("Edit set", style = InstrumentType.bodyStrong, color = TextPrimary) },
                                    onClick = { menuId = null; onEdit(set.id) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete set", style = InstrumentType.bodyStrong, color = Danger) },
                                    onClick = { menuId = null; onDelete(set.id) },
                                )
                            }
                        }
                    }
                    HairlineDivider()
                }
                if (showAddSet) item(key = "add-set") {
                    SecondaryGymButton(text = "Add another set", onClick = onAddSet, modifier = Modifier.padding(top = Metrics.space3))
                }
            }
        }
    }
}
