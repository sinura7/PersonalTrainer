package com.sinura.personaltrainer.ui.workout


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
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
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        GroupedList {
            sets.forEachIndexed { index, set ->
                if (index > 0) HairlineDivider()
                SetRow(
                    set = set,
                    isLatest = set.id == latestSetId,
                    isEditing = editingSetId == set.id,
                    isSelected = selected == set.id,
                    loadClass = loadClassOf(set),
                    onSelect = {
                        selectedSetId = if (selected == set.id) null else set.id
                    },
                    onEdit = {
                        selectedSetId = null
                        onEdit(set.id)
                    },
                    onDelete = {
                        selectedSetId = null
                        onDelete(set.id)
                    },
                )
            }
        }
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
 * A logged set.
 *
 * State is carried by the design rather than narrated in the text. Latest wears a Volt rail.
 * Warm-up wears a cyan tick. Editing is outlined in Volt, matching the log button.
 */
@Composable
internal fun SetRow(
    set: SetLog,
    isLatest: Boolean,
    isEditing: Boolean,
    isSelected: Boolean,
    loadClass: LoadClass,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = LocalWeightUnit.current
    val rowLabel = SetCopy.setLine(set.weightKg, set.reps, loadClass, unit)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isEditing, onClick = onSelect)
            .semantics {
                selected = isSelected
                contentDescription = if (isSelected) {
                    "Set ${set.setNumber}, $rowLabel, selected. Revise or Remove."
                } else {
                    "Set ${set.setNumber}, $rowLabel. Tap to revise or remove."
                }
            }
            .then(
                if (isEditing) {
                    Modifier.border(Metrics.emphasisBorder, Volt)
                } else {
                    Modifier
                },
            )
            .padding(end = Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = LATEST_RULE_WIDTH, height = LATEST_RULE_HEIGHT)
                .background(if (isLatest) Volt else Color.Transparent),
        )
        if (set.isWarmup) {
            Box(
                modifier = Modifier
                    .padding(start = Metrics.space2)
                    .size(WARMUP_TICK)
                    .clip(CircleShape)
                    .background(RestCyan)
                    .semantics { contentDescription = "Warm-up" },
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Metrics.space3, top = Metrics.space3, bottom = Metrics.space3),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(
                SetCopy.setLine(set.weightKg, set.reps, loadClass, unit),
                style = InstrumentType.numeralSm,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val extras = buildList {
                add("Set ${set.setNumber}")
                set.rpe?.let { add("RPE $it") }
            }.joinToString(" · ")
            Text(
                extras,
                style = InstrumentType.caption,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isSelected) {
            SetRowAction(
                icon = TemperIcons.Edit,
                label = "Revise set ${set.setNumber}",
                tint = TextSecondary,
                onClick = onEdit,
            )
            SetRowAction(
                icon = TemperIcons.Delete,
                label = "Remove set ${set.setNumber}",
                tint = Danger,
                onClick = onDelete,
            )
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

private val LATEST_RULE_WIDTH = 3.dp
private val LATEST_RULE_HEIGHT = 44.dp
private val WARMUP_TICK = 6.dp
