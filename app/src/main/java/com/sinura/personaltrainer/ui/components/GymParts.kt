package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.Gym
import com.sinura.personaltrainer.domain.GymCopy
import com.sinura.personaltrainer.domain.GymFilter
import com.sinura.personaltrainer.domain.GymVisit
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.OutlineSolid
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.RestContainer
import com.sinura.personaltrainer.ui.theme.SectionEdge
import com.sinura.personaltrainer.ui.theme.SpaceGrotesk
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltContainer
import com.sinura.personaltrainer.ui.theme.VoltDim
import com.sinura.personaltrainer.ui.theme.Warn

enum class GymBadgeSize(val box: Dp, val radius: Dp, val textStyle: TextStyle) {
    Large(Metrics.space8, Radius.sm, InstrumentType.title),
    Small(Metrics.space5 + Metrics.space1, Radius.xs, InstrumentType.caption),
    Tiny(Metrics.space5, Radius.xs, InstrumentType.kicker),
}

enum class GymBadgeVisual { Normal, Selected, Unassigned, Archived }

@Composable
fun GymBadge(
    badge: String,
    size: GymBadgeSize,
    visual: GymBadgeVisual,
    modifier: Modifier = Modifier,
    testId: String? = null,
) {
    val shape = RoundedCornerShape(size.radius)
    val fill = when (visual) {
        GymBadgeVisual.Selected -> VoltContainer
        else -> Surface3
    }
    val borderColor = when (visual) {
        GymBadgeVisual.Selected -> Volt
        else -> SectionEdge
    }
    val textColor = when (visual) {
        GymBadgeVisual.Selected -> Volt
        GymBadgeVisual.Unassigned, GymBadgeVisual.Archived -> TextTertiary
        GymBadgeVisual.Normal -> TextPrimary
    }
    val label = if (visual == GymBadgeVisual.Unassigned) GymCopy.UNASSIGNED_TAG else badge
    Box(
        modifier = modifier
            .size(size.box)
            .then(if (testId != null) Modifier.testTag(testId) else Modifier)
            .clip(shape)
            .background(fill)
            .border(Metrics.hairline, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = size.textStyle.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold),
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
fun GymTag(
    gym: Gym?,
    modifier: Modifier = Modifier,
    archived: Boolean = gym?.archived == true,
    unassigned: Boolean = gym == null,
) {
    val tagId = when {
        unassigned -> GymTags.TAG_UNASSIGNED
        gym != null -> GymTags.tag(gym.id)
        else -> GymTags.TAG_UNASSIGNED
    }
    val short = when {
        unassigned -> stringResource(R.string.gym_unassigned_label)
        gym != null -> gym.shortName
        else -> ""
    }
    val suffix = if (archived && !unassigned) stringResource(R.string.gym_archived_suffix) else ""
    Row(
        modifier = modifier
            .height(Metrics.space6)
            .widthIn(max = Metrics.stepperNumeralMinWidth)
            .clip(RoundedCornerShape(Radius.xs))
            .background(Surface2)
            .border(Metrics.hairline, SectionEdge, RoundedCornerShape(Radius.xs))
            .padding(horizontal = Metrics.space2, vertical = Metrics.hairline)
            .testTag(tagId),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        GymBadge(
            badge = gym?.badge ?: GymCopy.UNASSIGNED_TAG,
            size = GymBadgeSize.Tiny,
            visual = when {
                unassigned -> GymBadgeVisual.Unassigned
                archived -> GymBadgeVisual.Archived
                else -> GymBadgeVisual.Normal
            },
        )
        Text(
            short + suffix,
            style = InstrumentType.caption,
            color = if (unassigned || archived) TextTertiary else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GymChip(
    gym: Gym?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    missingGym: Boolean = false,
    labelOverride: String? = null,
) {
    val shape = RoundedCornerShape(Radius.xs)
    val border = if (missingGym) Warn else SectionEdge
    val spoken = labelOverride ?: gym?.shortName ?: stringResource(R.string.gym_pick_gym)
    Row(
        modifier = modifier
            .heightIn(min = GymBadgeSize.Small.box)
            .clip(shape)
            .background(Surface2)
            .border(Metrics.hairline, border, shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(onClick = onClick)
                        .heightIn(min = Metrics.touchMin)
                        .padding(horizontal = Metrics.space2)
                } else Modifier.padding(horizontal = Metrics.space2),
            )
            .testTag(GymTags.CHIP)
            .semantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        GymBadge(
            badge = gym?.badge ?: GymCopy.UNASSIGNED_TAG,
            size = GymBadgeSize.Tiny,
            visual = when {
                missingGym -> GymBadgeVisual.Unassigned
                gym != null -> GymBadgeVisual.Normal
                else -> GymBadgeVisual.Unassigned
            },
        )
        Text(
            spoken,
            style = InstrumentType.caption,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClick != null) {
            Text("▾", style = InstrumentType.caption, color = TextSecondary)
        }
    }
}

@Composable
fun GymPickerList(
    gyms: List<Gym>,
    visits: Map<String?, GymVisit>,
    selectedId: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    includeUnassigned: Boolean = false,
    pendingId: String? = null,
    enabled: Boolean = true,
) {
    when {
        loading -> ScreenSkeleton(modifier = modifier, cards = 5)
        error != null -> Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            Text(error, style = InstrumentType.body, color = TextSecondary)
            if (onRetry != null) {
                GhostButton(
                    label = stringResource(R.string.gym_picker_retry),
                    onClick = onRetry,
                    modifier = Modifier.testTag(GymTags.PICKER_RETRY),
                )
            }
        }
        else -> GroupedList(modifier = modifier) {
            gyms.forEachIndexed { index, gym ->
                val visit = visits[gym.id]
                val selected = gym.id == selectedId
                val rowEnabled = enabled && (pendingId == null || pendingId == gym.id)
                GymPickerRow(
                    gym = gym,
                    caption = DateCopy.gymLastHere(visit?.lastAtMs),
                    selected = selected,
                    enabled = rowEnabled,
                    pending = pendingId == gym.id,
                    onClick = { if (rowEnabled) onPick(gym.id) },
                )
                if (index < gyms.lastIndex || includeUnassigned) HairlineDivider()
            }
            if (includeUnassigned) {
                GymPickerRow(
                    gym = null,
                    caption = stringResource(R.string.gym_unassigned_caption),
                    selected = selectedId == null && includeUnassigned,
                    enabled = enabled,
                    pending = false,
                    onClick = { onPick(GymTags.UNASSIGNED_PICK) },
                    unassigned = true,
                )
            }
        }
    }
}

@Composable
private fun GymPickerRow(
    gym: Gym?,
    caption: String,
    selected: Boolean,
    enabled: Boolean,
    pending: Boolean,
    onClick: () -> Unit,
    unassigned: Boolean = false,
) {
    val id = gym?.id ?: "unassigned"
    val title = gym?.name ?: stringResource(R.string.gym_unassigned_label)
    InstrumentRow(
        title = title,
        subtitle = caption,
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .testTag(if (unassigned) GymTags.ROW_UNASSIGNED else GymTags.row(id))
            .semantics { this.selected = selected },
        leading = {
            GymBadge(
                badge = gym?.badge ?: GymCopy.UNASSIGNED_TAG,
                size = GymBadgeSize.Large,
                visual = when {
                    unassigned -> GymBadgeVisual.Unassigned
                    selected -> GymBadgeVisual.Selected
                    gym?.archived == true -> GymBadgeVisual.Archived
                    else -> GymBadgeVisual.Normal
                },
                testId = gym?.let { GymTags.badge(it.id) },
            )
        },
        trailing = { RadioDot(selected = selected, pending = pending) },
    )
}

@Composable
private fun RadioDot(selected: Boolean, pending: Boolean) {
    val ring = if (selected) Volt else OutlineSolid
    Box(
        modifier = Modifier
            .size(Metrics.icon)
            .border(Metrics.emphasisBorder, ring, RoundedCornerShape(Metrics.icon / 2)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            pending -> Text("…", style = InstrumentType.caption, color = TextSecondary)
            selected -> Box(
                Modifier
                    .size(Metrics.space2)
                    .clip(RoundedCornerShape(Metrics.space1))
                    .background(Volt),
            )
        }
    }
}

@Composable
fun GymFilterRow(
    gyms: List<Gym>,
    selected: GymFilter,
    onSelect: (GymFilter) -> Unit,
    modifier: Modifier = Modifier,
    showUnassigned: Boolean = false,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(vertical = Metrics.space1),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        FilterChip(stringResource(R.string.gym_filter_all), selected is GymFilter.All, GymTags.filter("all")) {
            onSelect(GymFilter.All)
        }
        gyms.filterNot { it.archived }.forEach { gym ->
            FilterChip(gym.shortName, selected == GymFilter.One(gym.id), GymTags.filter(gym.id)) {
                onSelect(GymFilter.One(gym.id))
            }
        }
        if (showUnassigned) {
            FilterChip(
                stringResource(R.string.gym_unassigned_label),
                selected is GymFilter.Unassigned,
                GymTags.filter("unassigned"),
            ) {
                onSelect(GymFilter.Unassigned)
            }
        }
    }
}

@Composable
private fun RowScope.FilterChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Metrics.space4)
    val fill = if (selected) VoltDim else Surface2
    val border = if (selected) Volt else SectionEdge
    val textColor = if (selected) Volt else TextSecondary
    Box(
        modifier = Modifier
            .heightIn(min = Metrics.touchMin)
            .clip(shape)
            .background(fill)
            .border(Metrics.hairline, border, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(
                horizontal = Metrics.space3,
                vertical = (Metrics.touchMin - Metrics.filterChip) / 2,
            )
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = InstrumentType.caption, color = textColor, maxLines = 1)
    }
}

@Composable
fun NewHereHintCard(
    exerciseName: String,
    gymShortName: String?,
    otherGymLine: String?,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
) {
    if (collapsed) {
        Text(
            stringResource(R.string.gym_new_here_short),
            modifier = modifier,
            style = InstrumentType.caption,
            color = RestCyan,
        )
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(RestContainer)
            .padding(Metrics.space3),
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        Text(stringResource(R.string.gym_new_here_lead), style = InstrumentType.bodyStrong, color = RestCyan)
        val body = if (gymShortName != null) {
            stringResource(R.string.gym_new_here_body, exerciseName, gymShortName)
        } else {
            stringResource(R.string.gym_new_here_no_history, exerciseName)
        }
        Text(body, style = InstrumentType.body, color = TextPrimary)
        if (otherGymLine != null) {
            Text(otherGymLine, style = InstrumentType.body, color = TextSecondary)
        }
    }
}
