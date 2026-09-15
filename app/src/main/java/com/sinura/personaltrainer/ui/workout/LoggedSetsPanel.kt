package com.sinura.personaltrainer.ui.workout


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.SetOrdinalCopy
import com.sinura.personaltrainer.domain.SetRowCopy
import com.sinura.personaltrainer.ui.components.InstrumentMenu
import com.sinura.personaltrainer.ui.components.SetTable
import com.sinura.personaltrainer.ui.components.SetTableLine
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

@Composable
internal fun LoggedSetsPanel(
    sets: List<SetLog>,
    latestSetId: String?,
    editingSetId: String?,
    loadClassOf: (SetLog) -> LoadClass,
    showAddSet: Boolean,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddSet: () -> Unit,
    modifier: Modifier = Modifier,
    addSetCaption: String? = null,
    targetSets: Int = 0,
) {
    if (sets.isEmpty()) return
    // Packet G: every row carries its own visible 48 dp overflow. Revise/Delete used to hide
    // behind tapping the row to select it first — a second, undiscoverable gesture on top of
    // the switcher tap, and nothing on screen said it existed. The menu names the set it
    // acts on. Which menu is open is view state and deliberately not persisted; a set that
    // disappears under it (deleted here, or by a restore) releases it below.
    var openMenuFor by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(sets, editingSetId) {
        if (openMenuFor != null && sets.none { it.id == openMenuFor }) openMenuFor = null
        // The edit sheet owns the row while it is open; offering Delete on the very set
        // being saved would race the write.
        if (editingSetId != null) openMenuFor = null
    }
    val unit = LocalWeightUnit.current
    var showAllSets by rememberSaveable { mutableStateOf(false) }
    val ordinals = SetOrdinalCopy.loggedLines(
        warmupFlags = sets.map { it.isWarmup },
        targetSets = targetSets,
    )
    val indexed = sets.mapIndexed { index, set -> set to ordinals.getOrNull(index) }
    val visible = if (showAllSets || indexed.size <= 2) indexed else indexed.takeLast(2)
    val rows = visible.map { (set, ordinal) ->
        SetTableLine.fromLog(
            set = set,
            loadClass = loadClassOf(set),
            unit = unit,
            isLatest = set.id == latestSetId,
            ordinal = ordinal,
        )
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        SetTable(
            rows = rows,
            editingId = editingSetId,
        ) { row ->
            Box {
                IconButton(
                    onClick = { openMenuFor = row.id },
                    modifier = Modifier
                        .size(Metrics.touchMin)
                        .testTag(WorkoutTestTags.setOptions(row.id)),
                ) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = SetRowCopy.actionsForSet(row.number),
                        tint = TextSecondary,
                    )
                }
                InstrumentMenu(
                    expanded = openMenuFor == row.id,
                    onDismissRequest = { openMenuFor = null },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                SetRowCopy.reviseSet(row.number),
                                style = InstrumentType.bodyStrong,
                                color = TextPrimary,
                            )
                        },
                        onClick = {
                            openMenuFor = null
                            onEdit(row.id)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                SetRowCopy.deleteSet(row.number),
                                style = InstrumentType.bodyStrong,
                                color = Danger,
                            )
                        },
                        onClick = {
                            openMenuFor = null
                            onDelete(row.id)
                        },
                    )
                }
            }
        }
        if (sets.size > 2) {
            TextButton(
                onClick = { showAllSets = !showAllSets },
                modifier = Modifier.heightIn(min = Metrics.touchMin),
            ) {
                Text(
                    if (showAllSets) "Fewer sets" else "All sets",
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                )
            }
        }
        if (showAddSet) {
            Column {
                TextButton(
                    onClick = onAddSet,
                    modifier = Modifier
                        .heightIn(min = Metrics.touchMin)
                        .testTag(WorkoutTestTags.ADD_SET),
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(Metrics.space4),
                    )
                    Text(
                        "Add set",
                        style = InstrumentType.bodyStrong,
                        color = TextSecondary,
                    )
                }
                if (addSetCaption != null) {
                    Text(
                        addSetCaption,
                        style = InstrumentType.caption,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}
