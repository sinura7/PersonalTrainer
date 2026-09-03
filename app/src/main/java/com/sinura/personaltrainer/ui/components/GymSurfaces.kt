package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

/**
 * The instrument label voice: REST, LAST 7 DAYS, VOLUME.
 *
 * Always uppercased here rather than at the call site, so the tracking in
 * [InstrumentType.kicker] is never applied to mixed-case text — tracked lowercase looks
 * like a mistake, tracked caps look machined.
 */
@Composable
fun Kicker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextSecondary,
    textAlign: TextAlign = TextAlign.Unspecified,
    asHeading: Boolean = true,
) {
    Text(
        text.uppercase(),
        modifier = if (asHeading) modifier.semantics { heading() } else modifier,
        style = InstrumentType.kicker,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A section's label, and optionally the one action that belongs to the whole section.
 *
 * Sections used to be titled in the same weight as card titles, so a section and the card
 * under it competed. A kicker cannot compete with anything: it labels, and gets out of the
 * way of the numbers.
 */
@Composable
fun GymSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Kicker(
            title,
            modifier = Modifier.weight(1f),
            // A sub-section sits one step quieter than the section that contains it.
            color = if (compact) TextTertiary else TextSecondary,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = Metrics.touchMin),
            ) {
                Text(
                    actionLabel,
                    style = InstrumentType.bodyStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The default surface: one luminance step up from the window, with a hairline.
 *
 * The hairline is what makes a card read as a machined panel rather than a grey blob —
 * on a near-black field a 5% lightness difference alone is not a reliable edge, and a
 * shadow would be invisible.
 */
@Composable
fun GymCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    colors: CardColors = CardDefaults.cardColors(containerColor = Surface2),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.md)
    val border = BorderStroke(Metrics.hairline, Hairline)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            colors = colors,
            shape = shape,
            border = border,
        ) {
            Column(
                modifier = Modifier.padding(Metrics.cardPadding),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                content = content,
            )
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = colors,
            shape = shape,
            border = border,
        ) {
            Column(
                modifier = Modifier.padding(Metrics.cardPadding),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                content = content,
            )
        }
    }
}

/**
 * A one-pixel rule.
 *
 * Deliberately a [Box] rather than Material's divider: it is one line of layout, it takes
 * the hairline token directly, and it cannot drift when the Material component's defaults
 * change underneath it.
 */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier, startIndent: Dp = Metrics.space4) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(Metrics.hairline)
            .background(Hairline),
    )
}

/**
 * One container holding many rows, instead of one card per row.
 *
 * A list of ten sessions used to be ten separate floating cards, which reads as ten
 * unrelated objects; grouping them into a single panel with hairlines between says they are
 * ten instances of one thing — and gives back the vertical space ten sets of card margins
 * were spending.
 */
@Composable
fun GroupedList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(Surface1)
            .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.sm)),
        content = content,
    )
}

/**
 * A number with its label underneath, and its unit whispering beside it.
 *
 * This is the component that replaces the app's habit of writing metrics as prose —
 * "3 working sets · 1,240 kg · 42 min" set in body text, where nothing aligns down a list
 * and the word "working" carries the same weight as the number next to it. Values are
 * baseline-aligned with their units so the numeral keeps the eye.
 */
@Composable
fun MetricCluster(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueStyle: TextStyle = InstrumentType.numeralSm,
    valueColor: Color = TextPrimary,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                modifier = Modifier.alignByBaseline(),
                style = valueStyle,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (unit != null) {
                Text(
                    unit,
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(start = Metrics.space1),
                    style = InstrumentType.unit,
                    color = TextSecondary,
                    maxLines = 1,
                )
            }
        }
        Kicker(label, color = TextTertiary, asHeading = false)
    }
}

/** A [MetricCluster] promoted to its own panel, for the two or three numbers that lead a screen. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = TextPrimary,
    onClick: (() -> Unit)? = null,
) {
    val spoken = buildString {
        append(label)
        append(", ")
        append(value)
        if (!unit.isNullOrBlank()) {
            append(' ')
            append(unit)
        }
    }
    GymCard(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = spoken
        },
        onClick = onClick,
    ) {
        Kicker(label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                modifier = Modifier.alignByBaseline(),
                style = LogLoopScale.tileNumeral(LocalDensity.current.fontScale),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (unit != null) {
                Text(
                    unit,
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(start = Metrics.space1),
                    style = InstrumentType.unit,
                    color = TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A row in a [GroupedList]: identity on the left, measurements on the right.
 *
 * [trailing] is a slot rather than a string so callers pass [MetricCluster]s, which is what
 * keeps values in a column aligned with each other down the whole list.
 */
@Composable
fun InstrumentRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val interaction = when {
        checked != null && onCheckedChange != null ->
            Modifier
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .semantics(mergeDescendants = true) {}
        selected != null && onClick != null ->
            Modifier
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                .semantics(mergeDescendants = true) {}
        onClick != null -> Modifier.clickable(role = Role.Button, onClick = onClick)
        else -> Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .then(interaction)
            .padding(horizontal = Metrics.space4, vertical = Metrics.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        if (leading != null) leading()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            Text(
                title,
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = InstrumentType.caption,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/** Stable semantics for constrained History identity tests (FND-006). */
object SessionLogTags {
    const val ROW = "session-log-row"
    const val TITLE = "session-log-title"
    const val DATE = "session-log-date"
    const val METRICS = "session-log-metrics"
}

/**
 * A finished session, as a readout rather than a receipt.
 *
 * Identity (title, date) owns the first line. Metrics live on a second
 * line with fixed columns so they still compare down a list — they wrap
 * before a 360 dp row can erase the workout's name (FND-006).
 */
@Composable
fun SessionLogRow(
    title: String,
    dateLabel: String,
    workingSets: Int,
    work: SetWork,
    durationMinutes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unit: WeightUnit = LocalWeightUnit.current,
    onRepeat: (() -> Unit)? = null,
) {
    var menuOpen by rememberSaveable(title, dateLabel) { mutableStateOf(false) }
    val spoken = sessionRowSpoken(title, dateLabel, workingSets, work, durationMinutes, unit)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Metrics.space4,
                    top = Metrics.space3,
                    end = if (onRepeat != null) Metrics.space2 else Metrics.space4,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .testTag(SessionLogTags.ROW)
                    .semantics(mergeDescendants = true) { contentDescription = spoken },
                verticalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                Text(
                    title,
                    modifier = Modifier.testTag(SessionLogTags.TITLE),
                    style = InstrumentType.title,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    dateLabel,
                    modifier = Modifier.testTag(SessionLogTags.DATE),
                    style = InstrumentType.caption,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Optional, and absent by default: Home's recent list is a glance, not a console.
            // History opts in. The menu sits with identity so it cannot steal metric columns.
            if (onRepeat != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = "Session options",
                            tint = TextSecondary,
                        )
                    }
                    InstrumentMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Repeat workout",
                                    style = InstrumentType.bodyStrong,
                                    color = TextPrimary,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onRepeat()
                            },
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    start = Metrics.space4,
                    end = Metrics.space4,
                    bottom = Metrics.space3,
                )
                .testTag(SessionLogTags.METRICS),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
            verticalAlignment = Alignment.Bottom,
        ) {
            Spacer(Modifier.weight(1f))
            MetricCluster(
                value = workingSets.toString(),
                label = "sets",
                modifier = Modifier.width(COUNT_COLUMN),
            )
            val column = SetCopy.workColumn(work, unit)
            MetricCluster(
                value = column.value,
                label = column.label,
                modifier = Modifier.width(VOLUME_COLUMN),
            )
            MetricCluster(
                value = durationMinutes.toString(),
                label = "min",
                modifier = Modifier.width(COUNT_COLUMN),
            )
        }
    }
}

/** Column widths for [SessionLogRow], so its three metrics line up down a list. */
private val COUNT_COLUMN = 48.dp
private val VOLUME_COLUMN = 88.dp

fun sessionRowSpoken(
    title: String,
    dateLabel: String,
    workingSets: Int,
    work: SetWork,
    durationMinutes: Int,
    unit: WeightUnit,
): String {
    val column = SetCopy.workColumn(work, unit)
    return "$title, $dateLabel, $workingSets sets, ${column.value} ${column.label}, " +
        "$durationMinutes min"
}
