package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.Gym
import com.sinura.personaltrainer.domain.GymCopy
import com.sinura.personaltrainer.domain.GymVisit
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.OutlineSolid
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.units.DateCopy

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
                    modifier = Modifier.testTag(GymSettingsTags.PICKER_RETRY),
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
                    onClick = { onPick(GymSettingsTags.UNASSIGNED_PICK) },
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
    val baseTitle = gym?.name ?: stringResource(R.string.gym_unassigned_label)
    val title = if (gym?.archived == true) {
        baseTitle + stringResource(R.string.gym_archived_suffix)
    } else {
        baseTitle
    }
    InstrumentRow(
        title = title,
        subtitle = caption,
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .testTag(if (unassigned) GymSettingsTags.PICKER_ROW_UNASSIGNED else GymSettingsTags.pickerRow(id))
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
                testId = gym?.let { GymSettingsTags.pickerBadge(it.id) },
            )
        },
        trailing = { GymPickerRadioDot(selected = selected, pending = pending) },
    )
}

@Composable
private fun GymPickerRadioDot(selected: Boolean, pending: Boolean) {
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
