package com.sinura.personaltrainer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.Gym
import com.sinura.personaltrainer.domain.GymCopy
import com.sinura.personaltrainer.domain.GymVisit
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.GymBadge
import com.sinura.personaltrainer.ui.components.GymBadgeSize
import com.sinura.personaltrainer.ui.components.GymBadgeVisual
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.plan.ReorderRow
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.units.DateCopy

@Composable
internal fun GymsSection(
    modifier: Modifier = Modifier,
    viewModel: GymsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<Gym?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var showUnassignedInfo by rememberSaveable { mutableStateOf(false) }

    val active = state.gyms.filterNot { it.archived }.sortedBy { it.sortOrder }
    val archived = state.gyms.filter { it.archived }.sortedBy { it.sortOrder }
    val activeIds = active.map { it.id }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(GymsTags.SECTION),
        contentPadding = PaddingValues(
            start = Metrics.gutter,
            end = Metrics.gutter,
            bottom = Metrics.space8,
        ),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        item("caption") {
            Text(
                stringResource(R.string.gym_settings_caption),
                style = InstrumentType.caption,
                color = TextTertiary,
            )
        }
        item("reorder-kicker") {
            Kicker(text = stringResource(R.string.gym_settings_reorder_kicker))
        }
        item("active-list") {
            GroupedList {
                active.forEachIndexed { index, gym ->
                    val visit = state.visits[gym.id]
                    GymManageRow(
                        gym = gym,
                        caption = gymVisitCaption(visit),
                        onClick = { editing = gym },
                        trailing = {
                            ReorderRow(
                                title = gym.name,
                                occurrenceId = gym.id,
                                index = index,
                                lastIndex = active.lastIndex,
                                onMove = { id, delta -> viewModel.move(activeIds, id, delta) },
                            )
                        },
                    )
                    if (index < active.lastIndex) HairlineDivider()
                }
            }
        }
        item("add") {
            SecondaryGymButton(
                text = stringResource(R.string.gym_settings_add),
                onClick = { adding = true },
                modifier = Modifier.testTag(GymsTags.ADD),
            )
        }
        if (state.unassignedSessions > 0) {
            item("unassigned-kicker") {
                Kicker(text = stringResource(R.string.gym_settings_before_gyms))
            }
            item("unassigned") {
                GroupedList {
                    InstrumentRow(
                        title = stringResource(R.string.gym_unassigned_label),
                        subtitle = stringResource(R.string.gym_unassigned_caption) +
                            " · ${state.unassignedSessions} workouts",
                        onClick = { showUnassignedInfo = true },
                        modifier = Modifier.testTag(GymsTags.UNASSIGNED),
                        leading = {
                            GymBadge(
                                badge = GymCopy.UNASSIGNED_TAG,
                                size = GymBadgeSize.Large,
                                visual = GymBadgeVisual.Unassigned,
                            )
                        },
                        trailing = { Text("›", style = InstrumentType.body, color = TextSecondary) },
                    )
                }
            }
        }
        if (archived.isNotEmpty()) {
            item("archived-kicker") {
                Kicker(
                    text = stringResource(R.string.gym_settings_archived_kicker),
                    modifier = Modifier.testTag(GymsTags.ARCHIVED_KICKER),
                )
            }
            item("archived-list") {
                GroupedList {
                    archived.forEachIndexed { index, gym ->
                        val visit = state.visits[gym.id]
                        GymManageRow(
                            gym = gym,
                            caption = "Archived · ${visit?.sessions ?: 0} workouts",
                            onClick = { editing = gym },
                            archived = true,
                            modifier = Modifier.testTag(GymsTags.archivedRow(gym.id)),
                        )
                        if (index < archived.lastIndex) HairlineDivider()
                    }
                }
            }
        }
    }

    if (adding) {
        GymEditSheet(gym = null, viewModel = viewModel, onDismiss = { adding = false })
    }
    editing?.let { gym ->
        GymEditSheet(gym = gym, viewModel = viewModel, onDismiss = { editing = null })
    }
    if (showUnassignedInfo) {
        ConfirmActionDialog(
            title = stringResource(R.string.gym_unassigned_label),
            body = stringResource(R.string.gym_unassigned_info),
            confirmLabel = "OK",
            onConfirm = { showUnassignedInfo = false },
            onDismiss = { showUnassignedInfo = false },
            dismissLabel = null,
        )
    }
}

@Composable
private fun GymManageRow(
    gym: Gym,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    archived: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstrumentRow(
            title = gym.name,
            subtitle = caption,
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .testTag(GymsTags.row(gym.id))
                .semantics { contentDescription = gym.name },
            leading = {
                GymBadge(
                    badge = gym.badge,
                    size = GymBadgeSize.Large,
                    visual = if (archived) GymBadgeVisual.Archived else GymBadgeVisual.Normal,
                )
            },
            trailing = {
                trailing?.invoke()
                Text("›", style = InstrumentType.body, color = TextSecondary)
            },
        )
    }
}

private fun gymVisitCaption(visit: GymVisit?): String = when {
    visit == null || visit.sessions == 0 -> "No workouts yet"
    visit.lastAtMs != null -> "${visit.sessions} workouts · ${DateCopy.gymLastHere(visit.lastAtMs)}"
    else -> "${visit.sessions} workouts"
}
