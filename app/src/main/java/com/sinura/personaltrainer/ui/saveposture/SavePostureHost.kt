package com.sinura.personaltrainer.ui.saveposture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.ui.settings.SettingsPage
import com.sinura.personaltrainer.ui.settings.SettingsViewModel

/**
 * First-launch save posture, shown once after the process cold-start intro.
 * Reuses Settings navigation for Account and Drive setup instead of duplicating flows.
 */
@Composable
fun SavePostureHost(
    coldStartIntroVisible: Boolean,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.ensureSavePostureReady()
    }

    val savePostureUi by viewModel.savePostureUi.collectAsStateWithLifecycle()
    val account by viewModel.account.uiState.collectAsStateWithLifecycle()
    if (coldStartIntroVisible || !savePostureUi.needsChooser) return

    SavePostureChooser(
        modifier = modifier,
        syncPaused = account.sync.paused,
        onChooseAccount = {
            viewModel.chooseSavePosture(SavePosture.ACCOUNT)
            viewModel.requestSettingsSubpage(SettingsPage.ACCOUNT)
        },
        onChooseLocal = {
            viewModel.chooseSavePosture(SavePosture.LOCAL)
        },
        onSetUpDrive = {
            viewModel.chooseSavePosture(SavePosture.LOCAL)
            viewModel.requestSettingsSubpage(SettingsPage.BACKUP)
        },
    )
}
