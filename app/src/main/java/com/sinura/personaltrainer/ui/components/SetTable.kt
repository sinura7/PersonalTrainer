package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * One logged set, ready for the shared table.
 *
 * Workout and History used to each invent a row. The numbers are the
 * same [SetCopy] sentence; trailing actions are the only difference.
 */
data class SetTableLine(
    val id: String,
    val number: Int,
    val line: String,
    val extras: String,
    val isWarmup: Boolean,
    val isLatest: Boolean = false,
) {
    companion object {
        fun fromLog(
            set: SetLog,
            loadClass: LoadClass,
            unit: WeightUnit,
            isLatest: Boolean = false,
        ): SetTableLine = SetTableLine(
            id = set.id,
            number = set.setNumber,
            line = SetCopy.setLine(set.weightKg, set.reps, loadClass, unit),
            extras = SetCopy.tableExtras(set.setNumber, set.rpe),
            isWarmup = set.isWarmup,
            isLatest = isLatest,
        )
    }
}

/**
 * The one set history. Latest wears a Volt rail. Warm-up wears a cyan
 * tick. Trailing is a slot so the floor can offer Revise/Remove and a
 * finished session can offer Edit without a second table.
 */
@Composable
fun SetTable(
    rows: List<SetTableLine>,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    editingId: String? = null,
    onSelect: ((String) -> Unit)? = null,
    trailing: @Composable RowScope.(SetTableLine) -> Unit = {},
) {
    if (rows.isEmpty()) return
    GroupedList(modifier = modifier) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HairlineDivider()
            SetTableRow(
                row = row,
                isEditing = editingId == row.id,
                isSelected = selectedId == row.id,
                onSelect = onSelect?.let { select -> { select(row.id) } },
                trailing = { trailing(row) },
            )
        }
    }
}

@Composable
private fun SetTableRow(
    row: SetTableLine,
    isEditing: Boolean,
    isSelected: Boolean,
    onSelect: (() -> Unit)?,
    trailing: @Composable RowScope.() -> Unit,
) {
    val selectable = onSelect != null && !isEditing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selectable && onSelect != null) {
                    Modifier.clickable(onClick = onSelect)
                } else {
                    Modifier
                },
            )
            .semantics {
                selected = isSelected
                contentDescription = when {
                    onSelect == null -> "Set ${row.number}, ${row.line}"
                    isSelected -> "Set ${row.number}, ${row.line}, selected. Revise or Remove."
                    else -> "Set ${row.number}, ${row.line}. Tap to revise or remove."
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
                .background(if (row.isLatest) Volt else Color.Transparent),
        )
        if (row.isWarmup) {
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
                row.line,
                style = InstrumentType.numeralSm,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.extras,
                style = InstrumentType.caption,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

private val LATEST_RULE_WIDTH = 3.dp
private val LATEST_RULE_HEIGHT = 44.dp
private val WARMUP_TICK = 6.dp
