package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.domain.FloorStatCopy
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.SetOrdinalCopy
import com.sinura.personaltrainer.domain.SetRowCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.InstrumentMenu
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextDisabled
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt

/** The set about to be logged, as the strip's last chip: its ring mark and its ordinal. */
internal data class CurrentSetMark(
    val mark: String,
    val label: String,
)

/**
 * Today's sets for this lift, as a row of chips: saved ones with a tick, the one being
 * logged with a Volt ring, and Add set once the plan is met.
 *
 * Every chip is the saved row it stands for. Tapping a saved chip opens its Edit / Delete
 * menu — a menu, not a swipe, so a sweaty thumb cannot delete by accident. Edit opens
 * the full labelled sheet. Ordinals are derived ([SetOrdinalCopy]), never the stored
 * number, so a deleted warm-up renumbers the rest.
 */
@Composable
internal fun SetHistoryStrip(
    sets: List<SetLog>,
    targetSets: Int,
    loadClass: LoadClass,
    unit: WeightUnit,
    editingSetId: String?,
    receiptSetId: String?,
    current: CurrentSetMark?,
    showAddSet: Boolean,
    enabled: Boolean,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenAll: () -> Unit,
    onAddSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sets.isEmpty() && current == null && !showAddSet) return
    var openMenuFor by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(sets, editingSetId, enabled) {
        if (openMenuFor != null && sets.none { it.id == openMenuFor }) openMenuFor = null
        if (editingSetId != null || !enabled) openMenuFor = null
    }
    val ordinals = SetOrdinalCopy.loggedLines(warmupFlags = sets.map { it.isWarmup }, targetSets = targetSets)
    val marks = SetOrdinalCopy.marks(sets.map { it.isWarmup })
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WorkoutTestTags.SET_HISTORY),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Kicker(text = SET_HISTORY_KICKER, modifier = Modifier.weight(1f))
            if (sets.isNotEmpty()) {
                TextButton(
                    enabled = enabled,
                    onClick = onOpenAll,
                    modifier = Modifier
                        .heightIn(min = Metrics.touchMin)
                        .testTag(WorkoutTestTags.VIEW_SETS)
                        .semantics { contentDescription = EDIT_ALL_SPOKEN },
                ) {
                    Icon(
                        TemperIcons.Edit,
                        contentDescription = null,
                        tint = if (enabled) TextSecondary else TextDisabled,
                        modifier = Modifier.size(Metrics.chevron),
                    )
                    Spacer(Modifier.width(Metrics.space1))
                    Text("Edit", style = InstrumentType.bodyStrong, color = if (enabled) TextSecondary else TextDisabled)
                }
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            sets.forEachIndexed { index, set ->
                val ordinal = ordinals.getOrNull(index) ?: SetOrdinalCopy.working(index + 1, targetSets)
                val number = index + 1
                Box {
                    SavedSetChip(
                        set = set,
                        mark = marks.getOrNull(index) ?: number.toString(),
                        ordinal = ordinal,
                        loadClass = loadClass,
                        unit = unit,
                        editing = set.id == editingSetId,
                        saved = set.id == receiptSetId,
                        enabled = enabled,
                        onClick = { openMenuFor = set.id },
                        spokenAction = SetRowCopy.actionsFor(ordinal),
                    )
                    InstrumentMenu(
                        expanded = openMenuFor == set.id,
                        onDismissRequest = { openMenuFor = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text(SetRowCopy.revise(ordinal), style = InstrumentType.bodyStrong, color = TextPrimary) },
                            onClick = {
                                openMenuFor = null
                                onEdit(set.id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(SetRowCopy.delete(ordinal), style = InstrumentType.bodyStrong, color = Danger) },
                            onClick = {
                                openMenuFor = null
                                onDelete(set.id)
                            },
                        )
                    }
                }
            }
            if (current != null) {
                Row(
                    modifier = Modifier
                        .heightIn(min = Metrics.touchMin)
                        .padding(horizontal = Metrics.space2)
                        .testTag(WorkoutTestTags.CURRENT_SET)
                        .semantics(mergeDescendants = true) { contentDescription = "Current set, ${current.label}" },
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SetMarker(mark = current.mark, ring = Volt, ink = Volt)
                    Column {
                        Text(CURRENT, style = InstrumentType.bodyStrong, color = Volt)
                        Text(current.label, style = InstrumentType.caption, color = TextSecondary)
                    }
                }
            }
            if (showAddSet) {
                Row(
                    modifier = Modifier
                        .heightIn(min = Metrics.touchMin)
                        .clip(RoundedCornerShape(Radius.xs))
                        .clickable(enabled = enabled, role = Role.Button, onClick = onAddSet)
                        .padding(horizontal = Metrics.space2)
                        .testTag(WorkoutTestTags.ADD_SET)
                        .semantics { contentDescription = ADD_SET },
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SetMarker(mark = "+", ring = HairlineStrong, ink = TextSecondary)
                    Text(ADD_SET, style = InstrumentType.bodyStrong, color = if (enabled) TextSecondary else TextDisabled)
                }
            }
        }
    }
}

@Composable
private fun SavedSetChip(
    set: SetLog,
    mark: String,
    ordinal: String,
    loadClass: LoadClass,
    unit: WeightUnit,
    editing: Boolean,
    saved: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    spokenAction: String,
) {
    val line = FloorStatCopy.compactSet(
        weightKg = set.weightKg,
        reps = set.reps,
        loadClass = loadClass,
        unit = unit,
        rpe = set.rpe,
        durationSeconds = set.durationSeconds,
    )
    val spokenSet = FloorStatCopy.spokenSet(
        weightKg = set.weightKg,
        reps = set.reps,
        loadClass = loadClass,
        unit = unit,
        rpe = set.rpe,
        durationSeconds = set.durationSeconds,
    )
    val state = when {
        editing -> "editing"
        saved -> "saved"
        else -> "logged"
    }
    val accent = editing || saved
    Row(
        modifier = Modifier
            .heightIn(min = Metrics.touchMin)
            .clip(RoundedCornerShape(Radius.xs))
            .then(if (editing) Modifier.border(Metrics.hairline, Volt, RoundedCornerShape(Radius.xs)) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = spokenAction, onClick = onClick)
            .testTag(WorkoutTestTags.setChip(set.id))
            .semantics(mergeDescendants = true) { contentDescription = "$ordinal, $spokenSet, $state" }
            .padding(horizontal = Metrics.space2),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetMarker(
            mark = mark,
            ring = if (accent) Volt else Hairline,
            ink = if (accent) Volt else TextSecondary,
            filled = !accent,
        )
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(line, style = InstrumentType.numeralSm, color = TextPrimary, maxLines = 1)
                if (!editing) {
                    Icon(
                        TemperIcons.Check,
                        contentDescription = null,
                        tint = if (saved) Volt else TextSecondary,
                        modifier = Modifier.size(Metrics.chevron),
                    )
                }
            }
            // A resting chip says nothing here: the marker ring beside it already carries
            // this set's number, so the caption was the same word twice, on every chip, all
            // session. The ordinal stays in the row's spoken form, which is where a reader
            // who cannot see the ring gets it.
            val caption = when {
                editing -> EDITING
                saved -> "$SAVED · $ordinal"
                else -> null
            }
            if (caption != null) {
                Text(
                    caption,
                    style = InstrumentType.caption,
                    color = if (accent) Volt else TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SetMarker(
    mark: String,
    ring: Color,
    ink: Color,
    filled: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(Metrics.setMarker)
            .clip(Radius.full)
            .then(if (filled) Modifier.background(Surface2) else Modifier)
            .border(Metrics.hairline, ring, Radius.full),
        contentAlignment = Alignment.Center,
    ) {
        Text(mark, style = InstrumentType.caption, color = ink, maxLines = 1)
    }
}

private const val SET_HISTORY_KICKER = "Set history"
private const val CURRENT = "Current"
private const val EDITING = "Editing"
private const val SAVED = "Saved"
private const val ADD_SET = "Add set"
private const val EDIT_ALL_SPOKEN = "Edit saved sets"
