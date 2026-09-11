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
    val selectedUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val clockFormat by viewModel.clockFormat.collectAsStateWithLifecycle()
    val schedulePrefs by viewModel.schedulePreferences.collectAsStateWithLifecycle()
    val reminderPrefs by viewModel.reminderPreferences.collectAsStateWithLifecycle()
    val restPrefs by viewModel.restTimerPreferences.collectAsStateWithLifecycle()
    val offerExactAlarmAccess by viewModel.offerExactAlarmAccess.collectAsStateWithLifecycle()
    val coachPrefs by viewModel.coachPreferences.collectAsStateWithLifecycle()
    val bodyweightKg by viewModel.bodyweightKg.collectAsStateWithLifecycle()
    val preferredDays by viewModel.preferredDays.collectAsStateWithLifecycle()
    val checkInWeekday by viewModel.bodyweightCheckInWeekday.collectAsStateWithLifecycle()
    val backup by viewModel.backupState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()

    LaunchedEffect(Unit) {
        viewModel.refreshAlarmCapability()
    }

    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onResolutionFinished(result.resultCode == Activity.RESULT_OK)
    }

    // The lock-screen challenge before the backup password is shown.
    val revealLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onPasswordRevealAuthenticated(result.resultCode == Activity.RESULT_OK)
    }

    // Local file export/import. Deliberately independent of Google: if the OAuth client or
    // the signing key is ever lost, this is still a complete way in and out of the data.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupJson.MIME_TYPE),
    ) { uri ->
        if (uri != null) viewModel.exportToFile(uri) else viewModel.cancelProtect()
    }

    LaunchedEffect(backup.launchExportPicker) {
        if (backup.launchExportPicker) {
            exportLauncher.launch(viewModel.exportFileName())
            viewModel.onExportPickerLaunched()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.requestFileRestore(it) } }

    var pendingSafetyExportId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSafetyDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val exportSafetyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupJson.MIME_TYPE),
    ) { uri ->
        val id = pendingSafetyExportId
        pendingSafetyExportId = null
        if (uri != null && id != null) {
            viewModel.exportSafetySnapshot(id, uri)
        } else {
            viewModel.cancelProtect()
        }
    }

    LaunchedEffect(backup.launchSafetyExportPicker) {
        if (backup.launchSafetyExportPicker) {
            exportSafetyLauncher.launch(viewModel.exportSafetyFileName())
            viewModel.onSafetyExportPickerLaunched()
        }
    }

    // State, not an event: a consent request raised while this screen was recomposing or
    // rotating used to be dropped, leaving the backup UI stuck busy forever.
    val pendingResolution by viewModel.pendingResolution.collectAsStateWithLifecycle()
    LaunchedEffect(pendingResolution) {
        val sender = pendingResolution ?: return@LaunchedEffect
        resolutionLauncher.launch(IntentSenderRequest.Builder(sender).build())
        viewModel.onResolutionLaunched()
    }

    val pendingPasswordReveal by viewModel.pendingPasswordReveal.collectAsStateWithLifecycle()
    LaunchedEffect(pendingPasswordReveal) {
        val challenge = pendingPasswordReveal ?: return@LaunchedEffect
        revealLauncher.launch(challenge)
        viewModel.onPasswordRevealLaunched()
    }

    val revealedPassword by viewModel.revealedPassword.collectAsStateWithLifecycle()
    revealedPassword?.let { shown ->
        RevealedPasswordDialog(
            password = shown,
            onDismiss = viewModel::dismissRevealedPassword,
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
                    onTick = viewModel::setRestTickEnabled,
                    onDefaultRest = viewModel::setDefaultRestSeconds,
                    onCustomDefault = viewModel::setDefaultRestCustom,
                )
            }
            item(key = "backup") {
                BackupRestoreSection(
                    state = backup,
                    clock = clockFormat,
                    onSignIn = { viewModel.signIn(activity) },
                    onSignOut = { viewModel.signOut(activity) },
                    onCreateBackup = { viewModel.beginDriveBackup() },
                    onAutoBackupChange = viewModel::setAutoBackupEnabled,
                    onShowBackupPassword = viewModel::beginRevealBackupPassword,
                    onRefresh = { viewModel.refreshBackups(activity) },
                    onRestore = { file -> viewModel.requestRestore(activity, file) },
                    onExportFile = { viewModel.beginFileExport() },
                    onExportPlaintext = { viewModel.beginPlaintextExport() },
                    onImportFile = {
                        // Some file managers hand back JSON as octet-stream or text/plain.
                        importLauncher.launch(arrayOf(BackupJson.MIME_TYPE, "text/plain", "*/*"))
                    },
                    onExportSafety = { id ->
                        pendingSafetyExportId = id
                        viewModel.beginSafetyExport()
                    },
                    onRestoreSafety = viewModel::requestSafetyRestore,
                    onDeleteSafety = { id -> pendingSafetyDeleteId = id },
                    onDismissError = viewModel::dismissError,
                    onFinishRestore = viewModel::finishRestore,
                    onDismissRestoreNote = viewModel::dismissRestoreNote,
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
            onConfirm = { viewModel.confirmRestore() },
            onDismiss = viewModel::cancelRestore,
        )
    }

    backup.pendingProtect?.let { kind ->
        ProtectBackupDialog(
            drive = kind == BackupProtectKind.DRIVE_BACKUP,
            onConfirm = { password, confirm ->
                viewModel.submitProtect(password, confirm, activity)
            },
            onAdvanced = viewModel::beginPlaintextExport,
            onDismiss = viewModel::cancelProtect,
        )
    }

    if (backup.pendingAutoBackupArm) {
        ProtectBackupDialog(
            drive = true,
            onConfirm = { password, confirm ->
                viewModel.submitAutoBackupPassphrase(password, confirm)
            },
            onAdvanced = null,
            onDismiss = viewModel::cancelAutoBackupArm,
            arming = true,
        )
    }

    if (backup.pendingUnlock) {
        UnlockBackupDialog(
            onConfirm = viewModel::unlockPending,
            onDismiss = viewModel::cancelUnlock,
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
            onConfirm = { viewModel.confirmPlaintextWarning(activity) },
            onDismiss = viewModel::cancelPlaintextWarning,
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
                viewModel.deleteSafetySnapshot(snap.id)
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
}
