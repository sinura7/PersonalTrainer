package com.sinura.personaltrainer.ui.workout


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.ui.components.SetTable
import com.sinura.personaltrainer.ui.components.SetTableLine
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
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
) {
    if (sets.isEmpty()) return
    // Which row is showing its actions. The actions used to hang off `isLatest`, so the
    // fourth set of a lift could be revised and the first three could not — the numbers were
    // on screen, and the only way to correct a mistyped set 1 was to delete back to it.
    // Selection is view state, not session state: it is deliberately not persisted, and a set
    // that disappears under it (deleted here, or by a restore) releases it below.
    var selectedSetId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedSetId?.takeIf { id -> sets.any { it.id == id } }
    LaunchedEffect(sets, editingSetId) {
        if (selectedSetId != null && sets.none { it.id == selectedSetId }) selectedSetId = null
        // The edit sheet owns the row while it is open; leaving it selected underneath would
        // offer Delete on the very set being saved.
        if (editingSetId != null) selectedSetId = null
    }
    val unit = LocalWeightUnit.current
    val rows = sets.map { set ->
        SetTableLine.fromLog(
            set = set,
            loadClass = loadClassOf(set),
            unit = unit,
            isLatest = set.id == latestSetId,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        SetTable(
            rows = rows,
            selectedId = selected,
            editingId = editingSetId,
            onSelect = { id ->
                selectedSetId = if (selected == id) null else id
            },
            trailing = { row ->
                if (selected == row.id) {
                    SetRowAction(
                        icon = TemperIcons.Edit,
                        label = "Revise set ${row.number}",
                        tint = TextSecondary,
                        onClick = {
                            selectedSetId = null
                            onEdit(row.id)
                        },
                    )
                    SetRowAction(
                        icon = TemperIcons.Delete,
                        label = "Remove set ${row.number}",
                        tint = Danger,
                        onClick = {
                            selectedSetId = null
                            onDelete(row.id)
                        },
                    )
                }
            },
        )
        if (showAddSet) {
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
        }
    }
}

/**
 * One action on a selected set row.
 *
 * Symbols, not words: two labelled buttons on every row is most of the row's width at the
 * font scales the log loop supports, and "Delete" set in Danger red next to "Edit" reads as
 * a warning rather than a choice. The plate marks carry the meaning and the label goes to
 * TalkBack, which is where a word is worth more than a glyph.
 */
@Composable
internal fun SetRowAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = tint)
    }
}
