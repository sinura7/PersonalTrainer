package com.sinura.personaltrainer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.data.repository.GymArchiveOutcome
import com.sinura.personaltrainer.data.repository.GymRestoreOutcome
import com.sinura.personaltrainer.data.repository.GymSaveOutcome
import com.sinura.personaltrainer.domain.Gym
import com.sinura.personaltrainer.domain.GymBadge
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.GymSettingsTags
import com.sinura.personaltrainer.ui.components.DangerGymButton
import com.sinura.personaltrainer.ui.components.GhostButton
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GymEditSheet(
    gym: Gym?,
    viewModel: GymsViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(gym?.id) { mutableStateOf(gym?.name ?: "") }
    var shortName by rememberSaveable(gym?.id) { mutableStateOf(gym?.shortName ?: "") }
    var badge by rememberSaveable(gym?.id) {
        mutableStateOf(gym?.badge ?: GymBadge.defaultForShortName(shortName))
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingArchive by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(shortName, gym) {
        if (gym == null && badge == GymBadge.defaultForShortName("")) {
            badge = GymBadge.defaultForShortName(shortName)
        }
    }

    val baseline = remember(gym) {
        Triple(gym?.name ?: "", gym?.shortName ?: "", gym?.badge ?: "")
    }
    val changed = name.trim() != baseline.first.trim() ||
        shortName.trim() != baseline.second.trim() ||
        badge.trim().uppercase() != baseline.third.trim().uppercase()
    val valid = name.trim().isNotEmpty() && shortName.trim().isNotEmpty() && GymBadge.isValid(badge)
    val msgNameRequired = stringResource(R.string.gym_edit_name_required)
    val msgBadgeInvalid = stringResource(R.string.gym_edit_badge_invalid)
    val msgNameTaken = stringResource(R.string.gym_edit_name_taken)
    val msgBadgeTaken = stringResource(R.string.gym_edit_badge_taken)
    val msgArchiveLast = stringResource(R.string.gym_archive_last)
    val msgArchiveLive = stringResource(R.string.gym_archive_live)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(GymSettingsTags.EDIT_SHEET),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space8),
            verticalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text(stringResource(R.string.gym_edit_name)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(GymSettingsTags.editField("name")),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                singleLine = true,
            )
            OutlinedTextField(
                value = shortName,
                onValueChange = { shortName = it; error = null },
                label = { Text(stringResource(R.string.gym_edit_short)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(GymSettingsTags.editField("short")),
                singleLine = true,
            )
            OutlinedTextField(
                value = badge,
                onValueChange = { badge = it.take(2).uppercase(); error = null },
                label = { Text(stringResource(R.string.gym_edit_badge)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(GymSettingsTags.editField("badge")),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                ),
                singleLine = true,
            )
            error?.let {
                Text(it, style = InstrumentType.caption, color = Danger)
            }
            PrimaryGymButton(
                text = stringResource(R.string.gym_edit_save),
                onClick = {
                    scope.launch {
                        val outcome = if (gym == null) {
                            viewModel.saveNew(name, shortName, badge)
                        } else {
                            viewModel.saveExisting(gym.id, name, shortName, badge)
                        }
                        when (outcome) {
                            is GymSaveOutcome.Saved -> onDismiss()
                            GymSaveOutcome.Invalid -> error = when {
                                name.trim().isEmpty() -> msgNameRequired
                                !GymBadge.isValid(badge) -> msgBadgeInvalid
                                else -> msgNameRequired
                            }
                            GymSaveOutcome.NameTaken -> error = msgNameTaken
                            GymSaveOutcome.BadgeTaken -> error = msgBadgeTaken
                        }
                    }
                },
                enabled = valid && changed,
                modifier = Modifier.testTag(GymSettingsTags.EDIT_SAVE),
            )
            when {
                gym == null -> GhostButton(label = "Cancel", onClick = onDismiss)
                gym.archived -> GhostButton(
                    label = stringResource(R.string.gym_edit_restore),
                    onClick = {
                        scope.launch {
                            when (viewModel.restore(gym.id)) {
                                GymRestoreOutcome.Restored -> onDismiss()
                                is GymRestoreOutcome.NameTaken,
                                is GymRestoreOutcome.BadgeTaken,
                                -> error = msgNameTaken
                            }
                        }
                    },
                    modifier = Modifier.testTag(GymSettingsTags.EDIT_RESTORE),
                )
                else -> DangerGymButton(
                    text = stringResource(R.string.gym_edit_archive),
                    onClick = { pendingArchive = true },
                    modifier = Modifier.testTag(GymSettingsTags.EDIT_ARCHIVE),
                )
            }
        }
    }

    if (pendingArchive && gym != null) {
        ConfirmActionDialog(
            title = stringResource(R.string.gym_edit_archive),
            body = stringResource(R.string.gym_archive_confirm, gym.name),
            confirmLabel = stringResource(R.string.gym_edit_archive),
            onConfirm = {
                pendingArchive = false
                scope.launch {
                    when (viewModel.archive(gym.id)) {
                        GymArchiveOutcome.Archived -> onDismiss()
                        GymArchiveOutcome.LastActiveGym -> error = msgArchiveLast
                        GymArchiveOutcome.InUseByLiveWorkout -> error = msgArchiveLive
                    }
                }
            },
            onDismiss = { pendingArchive = false },
        )
    }
}
