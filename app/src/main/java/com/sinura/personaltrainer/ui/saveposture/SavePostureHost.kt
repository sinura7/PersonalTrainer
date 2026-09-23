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
    val context = LocalContext.current

    SavePostureChooser(
        modifier = modifier,
        syncPaused = account.sync.paused,
        // Back leaves the app, as Back from Home does on Android 12 and later, and never reaches
        // the screen beneath. Nothing is chosen, so the question is asked again next time. The
        // cost: no predictive back-to-home animation while the chooser is up.
        onBack = { context.findActivity().moveTaskToBack(true) },
        // Account and Drive: the choice is saved through DataStore (it lands a moment later)
        // and the Settings page is requested at once. The permission walk waits on that pending
        // page (LaunchPermissions.walkMayShow), so the request must not move after the save.
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
