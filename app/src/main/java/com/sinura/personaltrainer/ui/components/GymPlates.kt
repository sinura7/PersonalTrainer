package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.Gym
import com.sinura.personaltrainer.domain.GymVisit
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.OutlineSolid
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.SectionEdge
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltContainer
import java.util.Locale

private const val REFRESH_GYM_PLATES = "refresh-gym-plates"

private fun refreshGymPlateTag(gymId: String) = "refresh-gym-plate-$gymId"
private fun refreshGymRowTag(gymId: String) = "refresh-gym-row-$gymId"

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
    val lastHere = DateCopy.gymLastHere(visit?.lastAtMs)
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
                size = GymBadgeSize.Small,
                visual = if (selected) GymBadgeVisual.Selected else GymBadgeVisual.Normal,
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
                subtitle = DateCopy.gymLastHere(visits[gym.id]?.lastAtMs),
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
                trailing = { PlateListRadioDot(selected = selected, pending = false) },
            )
            if (index < gyms.lastIndex) HairlineDivider()
        }
    }
}

@Composable
private fun PlateListRadioDot(selected: Boolean, pending: Boolean) {
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
