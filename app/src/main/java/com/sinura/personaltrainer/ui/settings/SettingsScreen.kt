package com.sinura.personaltrainer.ui.settings


import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.BuildConfig
import com.sinura.personaltrainer.diagnostics.DiagnosticMetadata
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.ui.findActivity
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.reminders.ReminderPrefsSection
import com.sinura.personaltrainer.ui.reminders.openAppNotificationSettings
import com.sinura.personaltrainer.ui.reminders.rememberNotificationsEnabled
import com.sinura.personaltrainer.ui.theme.Metrics

@Composable
fun SettingsScreen(
    onOpenGuidedSetup: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val prefs by viewModel.uiState.collectAsStateWithLifecycle()
    val backup by viewModel.backup.uiState.collectAsStateWithLifecycle()
    val selectedUnit = prefs.weightUnit
    val clockFormat = prefs.clockFormat
    val schedulePrefs = prefs.schedule
    val reminderPrefs = prefs.reminders
    val restPrefs = prefs.restTimer
    val offerExactAlarmAccess = prefs.offerExactAlarmAccess
    val coachPrefs = prefs.coach
    val bodyweightKg = prefs.bodyweightKg
    val preferredDays = prefs.preferredDays
    val checkInWeekday = prefs.bodyweightCheckInWeekday
    val context = LocalContext.current
    val activity = context.findActivity()

    LaunchedEffect(Unit) {
        viewModel.refreshAlarmCapability()
    }

    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.backup.onResolutionFinished(result.resultCode == Activity.RESULT_OK)
    }

    // The lock-screen challenge before the backup password is shown.
    val revealLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.backup.onPasswordRevealAuthenticated(result.resultCode == Activity.RESULT_OK)
    }

    // Local file export/import. Deliberately independent of Google: if the OAuth client or
    // the signing key is ever lost, this is still a complete way in and out of the data.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupJson.MIME_TYPE),
    ) { uri ->
        if (uri != null) viewModel.backup.exportToFile(uri) else viewModel.backup.cancelProtect()
    }

    LaunchedEffect(backup.launchExportPicker) {
        if (backup.launchExportPicker) {
            exportLauncher.launch(viewModel.backup.exportFileName())
            viewModel.backup.onExportPickerLaunched()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.backup.requestFileRestore(it) } }

    var pendingSafetyExportId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSafetyDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val exportSafetyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupJson.MIME_TYPE),
    ) { uri ->
        val id = pendingSafetyExportId
        pendingSafetyExportId = null
        if (uri != null && id != null) {
            viewModel.backup.exportSafetySnapshot(id, uri)
        } else {
            viewModel.backup.cancelProtect()
        }
    }

    LaunchedEffect(backup.launchSafetyExportPicker) {
        if (backup.launchSafetyExportPicker) {
            exportSafetyLauncher.launch(viewModel.backup.exportSafetyFileName())
            viewModel.backup.onSafetyExportPickerLaunched()
        }
    }

    // State, not an event: a consent request raised while this screen was recomposing or
    // rotating used to be dropped, leaving the backup UI stuck busy forever.
    val pendingResolution by viewModel.backup.pendingResolution.collectAsStateWithLifecycle()
    LaunchedEffect(pendingResolution) {
        val sender = pendingResolution ?: return@LaunchedEffect
        resolutionLauncher.launch(IntentSenderRequest.Builder(sender).build())
        viewModel.backup.onResolutionLaunched()
    }

    val pendingPasswordReveal by viewModel.backup.pendingPasswordReveal.collectAsStateWithLifecycle()
    LaunchedEffect(pendingPasswordReveal) {
        val challenge = pendingPasswordReveal ?: return@LaunchedEffect
        revealLauncher.launch(challenge)
        viewModel.backup.onPasswordRevealLaunched()
    }

    val revealedPassword by viewModel.backup.revealedPassword.collectAsStateWithLifecycle()
    revealedPassword?.let { shown ->
        RevealedPasswordDialog(
            password = shown,
            onDismiss = viewModel.backup::dismissRevealedPassword,
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsHeader()
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = Metrics.gutter,
                end = Metrics.gutter,
                top = Metrics.space2,
                bottom = Metrics.space8,
            ),
            verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
        ) {
            item(key = "display") {
                DisplayPrefsSection(
                    selectedUnit = selectedUnit,
                    clockFormat = clockFormat,
                    onSelectUnit = viewModel::setWeightUnit,
                    onSelectClock = viewModel::setClockFormat,
                )
            }
            item(key = "schedule") {
                SchedulePrefsSection(
                    preferences = schedulePrefs,
                    onDays = viewModel::setTrainingDays,
                    onSplit = viewModel::setSplitStyle,
                    onWeekStart = viewModel::setWeekStart,
                )
            }
            item(key = "reminders") {
                ReminderPrefsSection(
                    preferences = reminderPrefs,
                    clockFormat = clockFormat,
                    notificationsEnabled = rememberNotificationsEnabled(),
                    onOptOut = viewModel::setReminderOptOut,
                    onQuietHours = viewModel::setReminderQuietHours,
                    onOpenNotificationSettings = {
                        openAppNotificationSettings(context)
                    },
                )
            }
            item(key = "coaching") {
                CoachingSection(
                    preferences = coachPrefs,
                    onGoal = viewModel::setTrainingGoal,
                    onEmphasis = viewModel::setTrainingEmphasis,
                    onToggleEquipment = viewModel::toggleEquipment,
                )
            }
            item(key = "bodyweight") {
                BodyweightPrefsSection(
                    bodyweightKg = bodyweightKg,
                    unit = selectedUnit,
                    weekStart = schedulePrefs.weekStart,
                    daysPerWeek = schedulePrefs.trainingDaysPerWeek,
                    preferredDays = preferredDays,
                    checkInOverride = checkInWeekday,
                    onRecordBodyweight = viewModel::recordBodyweight,
                    onClearBodyweight = viewModel::clearBodyweight,
                    onCheckInDay = viewModel::setBodyweightCheckInWeekday,
                )
            }
            item(key = "rest") {
                RestTimerPrefsSection(
                    preferences = restPrefs,
                    offerExactAlarmAccess = offerExactAlarmAccess,
                    onAllowPreciseRestAlerts = {
                        viewModel.exactAlarmSettingsIntent()?.let { context.startActivity(it) }
                    },
                    onSound = viewModel::setRestSoundEnabled,
                    onVibrate = viewModel::setRestVibrationEnabled,
                    onPreview = viewModel::previewRestCompleteCue,
                    onTick = viewModel::setRestTickEnabled,
                    onDefaultRest = viewModel::setDefaultRestSeconds,
                    onCustomDefault = viewModel::setDefaultRestCustom,
                )
            }
            item(key = "backup") {
                BackupRestoreSection(
                    state = backup,
                    clock = clockFormat,
                    onSignIn = { viewModel.backup.signIn(activity) },
                    onSignOut = { viewModel.backup.signOut(activity) },
                    onCreateBackup = { viewModel.backup.beginDriveBackup() },
                    onAutoBackupChange = viewModel.backup::setAutoBackupEnabled,
                    onShowBackupPassword = viewModel.backup::beginRevealBackupPassword,
                    onRefresh = { viewModel.backup.refreshBackups(activity) },
                    onRestore = { file -> viewModel.backup.requestRestore(activity, file) },
                    onExportFile = { viewModel.backup.beginFileExport() },
                    onExportPlaintext = { viewModel.backup.beginPlaintextExport() },
                    onImportFile = {
                        // Some file managers hand back JSON as octet-stream or text/plain.
                        importLauncher.launch(arrayOf(BackupJson.MIME_TYPE, "text/plain", "*/*"))
                    },
                    onExportSafety = { id ->
                        pendingSafetyExportId = id
                        viewModel.backup.beginSafetyExport()
                    },
                    onRestoreSafety = viewModel.backup::requestSafetyRestore,
                    onDeleteSafety = { id -> pendingSafetyDeleteId = id },
                    onDismissError = viewModel.backup::dismissError,
                    onFinishRestore = viewModel.backup::finishRestore,
                    onDismissRestoreNote = viewModel.backup::dismissRestoreNote,
                )
            }
            item(key = "plan-setup") {
                PlanSetupSection(onRerun = onOpenGuidedSetup)
            }
            if (BuildConfig.DEBUG) {
                item(key = "log") {
                    LogRedactSection()
                }
                item(key = "foundation") {
                    FoundationGenerationSection()
                }
            }
            item(key = "diagnostics") {
                DiagnosticsSection(
                    onShare = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Temper diagnostics")
                            putExtra(Intent.EXTRA_TEXT, DiagnosticMetadata.bundle(context))
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(send, "Share diagnostics"))
                        }
                    },
                    onClear = { DiagnosticMetadata.clear(context) },
                )
            }
            item(key = "about") {
                AboutSection()
            }
        }
    }

    backup.pendingPreview?.let { preview ->
        ConfirmActionDialog(
            title = "Replace all training data?",
            body = preview.body,
            confirmLabel = "Restore backup",
            destructive = true,
            onConfirm = { viewModel.backup.confirmRestore() },
            onDismiss = viewModel.backup::cancelRestore,
        )
    }

    backup.pendingProtect?.let { kind ->
        ProtectBackupDialog(
            drive = kind == BackupProtectKind.DRIVE_BACKUP,
            onConfirm = { password, confirm ->
                viewModel.backup.submitProtect(password, confirm, activity)
            },
            onAdvanced = viewModel.backup::beginPlaintextExport,
            onDismiss = viewModel.backup::cancelProtect,
        )
    }

    if (backup.pendingAutoBackupArm) {
        ProtectBackupDialog(
            drive = true,
            onConfirm = { password, confirm ->
                viewModel.backup.submitAutoBackupPassphrase(password, confirm)
            },
            onAdvanced = null,
            onDismiss = viewModel.backup::cancelAutoBackupArm,
            arming = true,
        )
    }

    if (backup.pendingUnlock) {
        UnlockBackupDialog(
            onConfirm = viewModel.backup::unlockPending,
            onDismiss = viewModel.backup::cancelUnlock,
        )
    }

    if (backup.pendingPlaintextWarning) {
        ConfirmActionDialog(
            title = "Export without a password?",
            body = "Anyone who can read this file can read your training history " +
                "and bodyweight. A password is the only thing that keeps it from " +
                "being readable.",
            confirmLabel = "Export anyway",
            destructive = true,
            onConfirm = { viewModel.backup.confirmPlaintextWarning(activity) },
            onDismiss = viewModel.backup::cancelPlaintextWarning,
        )
    }

    backup.safetySnapshots.firstOrNull { it.id == pendingSafetyDeleteId }?.let { snap ->
        ConfirmActionDialog(
            title = "Delete this safety copy?",
            body = "This removes the copy made on " +
                DateCopy.dateTime(snap.createdAtMillis, clockFormat) +
                ". Training data on this phone is unchanged.",
            confirmLabel = "Delete copy",
            destructive = true,
            onConfirm = {
                pendingSafetyDeleteId = null
                viewModel.backup.deleteSafetySnapshot(snap.id)
            },
            onDismiss = { pendingSafetyDeleteId = null },
        )
    }
}

object SettingsTags {
    const val EXPORT_FILE = "settings-export-file"
    const val SHARE_DIAGNOSTICS = "settings-share-diagnostics"
    const val CLEAR_DIAGNOSTICS = "settings-clear-diagnostics"
    const val DISPLAY = "settings-display"
    const val BODYWEIGHT = "settings-bodyweight"
    const val REDACT_LOGS = "settings-redact-logs"
    const val AUTO_BACKUP = "settings-auto-backup"
    const val SHOW_BACKUP_PASSWORD = "settings-show-backup-password"
    const val PLAY_COMPLETE_CUE = "settings-play-complete-cue"
}
