package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.sinura.personaltrainer.domain.Gym
import com.sinura.personaltrainer.domain.GymCopy
import com.sinura.personaltrainer.domain.GymFilter
import com.sinura.personaltrainer.domain.GymVisit
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.OutlineSolid
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.SectionEdge
import com.sinura.personaltrainer.ui.theme.SpaceGrotesk
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltContainer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val REFRESH_GYM_PLATES = "refresh-gym-plates"

private fun refreshGymPlateTag(gymId: String) = "refresh-gym-plate-$gymId"
private fun refreshGymRowTag(gymId: String) = "refresh-gym-row-$gymId"
private fun refreshGymFilterTag(key: String) = "refresh-gym-filter-$key"
private const val REFRESH_GYM_TAG_PREFIX = "refresh-kit-gym-tag"

enum class GymBadgeSize(val box: Dp, val radius: Dp, val textStyle: TextStyle) {
    Large(Metrics.space8, Radius.sm, InstrumentType.title),
    Plate(Metrics.gymPlateBadge, Radius.sm, InstrumentType.numeralSm),
    Small(Metrics.space5 + Metrics.space1, Radius.xs, InstrumentType.caption),
    Tiny(Metrics.space5, Radius.xs, InstrumentType.kicker),
}

enum class GymBadgeVisual { Normal, Selected, SelectedOnPlate, Unassigned, Archived }

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
        GymBadgeVisual.SelectedOnPlate -> Volt
        else -> Surface3
    }
    val borderColor = when (visual) {
        GymBadgeVisual.Selected, GymBadgeVisual.SelectedOnPlate -> Volt
        GymBadgeVisual.Unassigned -> HairlineStrong
        else -> HairlineStrong
    }
    val textColor = when (visual) {
        GymBadgeVisual.Selected -> Volt
        GymBadgeVisual.SelectedOnPlate -> Pit
        GymBadgeVisual.Unassigned, GymBadgeVisual.Archived -> TextTertiary
        GymBadgeVisual.Normal -> TextPrimary
    }
    val label = if (visual == GymBadgeVisual.Unassigned) GymCopy.UNASSIGNED_TAG else badge
    val weight = if (size == GymBadgeSize.Plate) FontWeight.Bold else size.textStyle.fontWeight
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
            style = size.textStyle.copy(fontFamily = SpaceGrotesk, fontWeight = weight),
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
        unassigned -> "$REFRESH_GYM_TAG_PREFIX-unassigned"
        gym != null -> "$REFRESH_GYM_TAG_PREFIX-${gym.id}"
        else -> "$REFRESH_GYM_TAG_PREFIX-unassigned"
    }
    val short = when {
        unassigned -> "Unassigned"
        gym != null -> gym.shortName
        else -> ""
    }
    val suffix = if (archived && !unassigned) " archived" else ""
    Row(
        modifier = modifier
            .heightIn(min = Metrics.space6)
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
            style = InstrumentType.caption.copy(fontWeight = FontWeight.SemiBold),
            color = if (unassigned || archived) TextTertiary else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

data class GymPlateRowState(
    val gyms: List<Gym>,
    val visits: Map<String?, GymVisit>,
    val selectedId: String?,
    val onPick: (String) -> Unit,
    val hintBeforePick: String,
    val hintAfterPick: String?,
    val enabled: Boolean = true,
)

@Composable
fun GymPlateRow(
    state: GymPlateRowState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val screenWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val plateWidth = (screenWidth - Metrics.gutter * 2 - Metrics.gymPlateGap * 4) / 5
    val useList = state.gyms.size >= 10 ||
        fontScale >= 1.3f ||
        (state.gyms.size in 2..9 && plateWidth < Metrics.gymPlateMinCell)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        if (useList) {
            PlateListFallback(
                gyms = state.gyms,
                visits = state.visits,
                selectedId = state.selectedId,
                onPick = state.onPick,
                enabled = state.enabled,
            )
        } else {
            val active = state.gyms.filterNot { it.archived }
            val plateModifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .testTag(REFRESH_GYM_PLATES)
            if (active.size <= 5) {
                Row(
                    modifier = plateModifier,
                    horizontalArrangement = Arrangement.spacedBy(Metrics.gymPlateGap),
                ) {
                    active.forEachIndexed { index, gym ->
                        GymPlate(
                            gym = gym,
                            visit = state.visits[gym.id],
                            selected = gym.id == state.selectedId,
                            enabled = state.enabled,
                            position = index + 1,
                            total = active.size,
                            modifier = Modifier.weight(1f),
                            onPick = { state.onPick(gym.id) },
                        )
                    }
                }
            } else {
                val columnPlateWidth = (screenWidth - Metrics.gutter * 2 - Metrics.gymPlateGap * 2) / 3
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = plateModifier,
                    horizontalArrangement = Arrangement.spacedBy(Metrics.gymPlateGap),
                    verticalArrangement = Arrangement.spacedBy(Metrics.gymPlateGap),
                    maxItemsInEachRow = 3,
                ) {
                    active.forEachIndexed { index, gym ->
                        GymPlate(
                            gym = gym,
                            visit = state.visits[gym.id],
                            selected = gym.id == state.selectedId,
                            enabled = state.enabled,
                            position = index + 1,
                            total = active.size,
                            modifier = Modifier.width(columnPlateWidth),
                            onPick = { state.onPick(gym.id) },
                        )
                    }
                }
            }
        }
        Text(
            if (state.selectedId == null) state.hintBeforePick else (state.hintAfterPick ?: state.hintBeforePick),
            style = InstrumentType.caption,
            color = TextSecondary,
        )
    }
}

@Composable
private fun GymPlate(
    gym: Gym,
    visit: GymVisit?,
    selected: Boolean,
    enabled: Boolean,
    position: Int,
    total: Int,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.md)
    val lastHere = gymLastHere(visit?.lastAtMs)
    val spoken = buildString {
        append(gym.shortName)
        append(", ")
        append(gym.name)
        append(", ")
        append(lastHere)
        if (selected) append(", selected")
        append(", ")
        append(position)
        append(" of ")
        append(total)
    }
    Column(
        modifier = modifier
            .heightIn(min = if (lastHere == "Not yet") Metrics.gymPlateMinHeight else Metrics.gymPlateMinHeight - Metrics.space4)
            .clip(shape)
            .background(if (selected) VoltContainer else Surface2)
            .border(
                if (selected) Metrics.emphasisBorder else Metrics.hairline,
                if (selected) Volt else SectionEdge,
                shape,
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onPick,
            )
            .testTag(refreshGymPlateTag(gym.id))
            .semantics {
                contentDescription = spoken
                this.selected = selected
            }
            .padding(
                top = Metrics.gymPlatePaddingTop,
                bottom = Metrics.gymPlatePaddingBottom,
                start = Metrics.space2,
                end = Metrics.space2,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        Box(Modifier.fillMaxWidth()) {
            GymBadge(
                badge = gym.badge,
                size = GymBadgeSize.Plate,
                visual = if (selected) GymBadgeVisual.SelectedOnPlate else GymBadgeVisual.Normal,
                modifier = Modifier.align(Alignment.Center),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(Metrics.gymPlateCheck)
                        .clip(Radius.full)
                        .background(Volt),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", style = InstrumentType.kicker, color = Pit)
                }
            }
        }
        Text(
            gym.shortName.uppercase(Locale.ENGLISH),
            style = InstrumentType.kicker,
            color = if (selected) Volt else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            lastHere,
            style = InstrumentType.roleTag,
            color = TextTertiary,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlateListFallback(
    gyms: List<Gym>,
    visits: Map<String?, GymVisit>,
    selectedId: String?,
    onPick: (String) -> Unit,
    enabled: Boolean,
) {
    GroupedList {
        gyms.forEachIndexed { index, gym ->
            val selected = gym.id == selectedId
            InstrumentRow(
                title = gym.name,
                subtitle = gymLastHere(visits[gym.id]?.lastAtMs),
                selected = selected,
                onClick = { if (enabled) onPick(gym.id) },
                enabled = enabled,
                modifier = Modifier
                    .testTag(refreshGymRowTag(gym.id))
                    .semantics { this.selected = selected },
                leading = {
                    GymBadge(
                        badge = gym.badge,
                        size = GymBadgeSize.Large,
                        visual = when {
                            selected -> GymBadgeVisual.Selected
                            gym.archived -> GymBadgeVisual.Archived
                            else -> GymBadgeVisual.Normal
                        },
                    )
                },
                trailing = { RadioDot(selected = selected, pending = false) },
            )
            if (index < gyms.lastIndex) HairlineDivider()
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean, pending: Boolean) {
    val ring = if (selected) Volt else OutlineSolid
    Box(
        modifier = Modifier
            .size(Metrics.icon)
            .border(Metrics.emphasisBorder, ring, Radius.full),
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
        FilterChip("All gyms", selected is GymFilter.All, refreshGymFilterTag("all")) {
            onSelect(GymFilter.All)
        }
        gyms.filterNot { it.archived }.forEach { gym ->
            FilterChip(gym.shortName, selected == GymFilter.One(gym.id), refreshGymFilterTag(gym.id)) {
                onSelect(GymFilter.One(gym.id))
            }
        }
        if (showUnassigned) {
            FilterChip("Unassigned", selected is GymFilter.Unassigned, refreshGymFilterTag("unassigned")) {
                onSelect(GymFilter.Unassigned)
            }
        }
    }
}

@Composable
private fun RowScope.FilterChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.xs)
    val fill = if (selected) VoltContainer else Surface2
    val border = if (selected) Volt else SectionEdge
    val textColor = if (selected) Volt else TextPrimary
    Box(
        modifier = Modifier
            .heightIn(min = Metrics.touchMin)
            .clip(shape)
            .background(fill)
            .border(Metrics.hairline, border, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(
                horizontal = Metrics.filterChipPaddingH,
                vertical = (Metrics.touchMin - Metrics.filterChip) / 2,
            )
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = InstrumentType.bodyStrong, color = textColor, maxLines = 1)
    }
}

private val LAST_HERE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

private fun gymLastHere(lastAtMs: Long?): String =
    if (lastAtMs == null) "Not yet"
    else LAST_HERE.format(Instant.ofEpochMilli(lastAtMs).atZone(ZoneId.systemDefault()))
