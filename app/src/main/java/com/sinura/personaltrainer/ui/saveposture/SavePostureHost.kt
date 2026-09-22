package com.sinura.personaltrainer.ui.saveposture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.ui.findActivity
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

    val account by viewModel.account.uiState.collectAsStateWithLifecycle()
    if (!savePostureChooserShowing(coldStartIntroVisible, viewModel)) return
    val activity = LocalContext.current.findActivity()

    SavePostureChooser(
        modifier = modifier,
        syncPaused = account.sync.paused,
        // Back leaves the app, as it does from Home, and never reaches the screen beneath.
        // Nothing is chosen, so the question is asked again next time.
        onBack = { activity.moveTaskToBack(true) },
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

/** Whether the chooser is up. MainActivity hides the app beneath it from TalkBack while it is. */
@Composable
fun savePostureChooserShowing(
    coldStartIntroVisible: Boolean,
    viewModel: SettingsViewModel = viewModel(),
): Boolean {
    val savePostureUi by viewModel.savePostureUi.collectAsStateWithLifecycle()
    return !coldStartIntroVisible && savePostureUi.needsChooser
}
