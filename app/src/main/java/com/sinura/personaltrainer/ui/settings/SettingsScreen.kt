package com.sinura.personaltrainer.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.BuildConfig
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.DriveBackupFile
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.CustomRestDialog
import com.sinura.personaltrainer.ui.components.RestPresetChips
import java.time.DayOfWeek
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.schedule.PreferenceBlock
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSchedule: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val selectedUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val schedulePrefs by viewModel.schedulePreferences.collectAsStateWithLifecycle()
    val restPrefs by viewModel.restTimerPreferences.collectAsStateWithLifecycle()
    val backup by viewModel.backupState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onResolutionFinished(result.resultCode == Activity.RESULT_OK)
    }

    // Local file export/import. Deliberately independent of Google: if the OAuth client or
    // the signing key is ever lost, this is still a complete way in and out of the data.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupJson.MIME_TYPE),
    ) { uri -> uri?.let(viewModel::exportToFile) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.requestFileRestore(it) } }

    // State, not an event: a consent request raised while this screen was recomposing or
    // rotating used to be dropped, leaving the backup UI stuck busy forever.
    val pendingResolution by viewModel.pendingResolution.collectAsStateWithLifecycle()
    LaunchedEffect(pendingResolution) {
        val sender = pendingResolution ?: return@LaunchedEffect
        resolutionLauncher.launch(IntentSenderRequest.Builder(sender).build())
        viewModel.onResolutionLaunched()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            WeightUnitsSection(
                selectedUnit = selectedUnit,
                onSelect = viewModel::setWeightUnit,
            )
            SchedulePrefsSection(
                preferences = schedulePrefs,
                onDays = viewModel::setTrainingDays,
                onSplit = viewModel::setSplitStyle,
                onWeekStart = viewModel::setWeekStart,
                onOpenSchedule = onOpenSchedule,
            )
            RestTimerPrefsSection(
                preferences = restPrefs,
                onSound = viewModel::setRestSoundEnabled,
                onVibrate = viewModel::setRestVibrationEnabled,
                onDefaultRest = viewModel::setDefaultRestSeconds,
                onCustomDefault = viewModel::setDefaultRestCustom,
            )
            BackupRestoreSection(
                state = backup,
                dateTimeFormat = dateTimeFormat,
                onSignIn = { viewModel.signIn(activity) },
                onSignOut = { viewModel.signOut(activity) },
                onCreateBackup = { viewModel.createBackup(activity) },
                onRefresh = { viewModel.refreshBackups(activity) },
                onRestore = viewModel::requestRestore,
                onExportFile = { exportLauncher.launch(viewModel.exportFileName()) },
                onImportFile = {
                    // Some file managers hand back JSON as octet-stream or text/plain.
                    importLauncher.launch(arrayOf(BackupJson.MIME_TYPE, "text/plain", "*/*"))
                },
            )
            AboutSection()
        }
    }

    backup.pendingRestore?.let { file ->
        ConfirmActionDialog(
            title = "Replace all training data?",
            body = "Restoring ${file.name} replaces every exercise, routine, and workout on this phone. " +
                "A copy of your current data is saved on this phone first, but this cannot be undone from here.",
            confirmLabel = "Restore backup",
            destructive = true,
            onConfirm = { viewModel.confirmRestore(activity) },
            onDismiss = viewModel::cancelRestore,
        )
    }

    backup.pendingFileRestore?.let { uri ->
        ConfirmActionDialog(
            title = "Replace all training data?",
            body = "Importing this file replaces every exercise, routine, and workout on this phone. " +
                "The file is checked before anything is written, and a copy of your current data is " +
                "saved on this phone first.",
            confirmLabel = "Import and replace",
            destructive = true,
            onConfirm = { viewModel.confirmFileRestore(uri) },
            onDismiss = viewModel::cancelFileRestore,
        )
    }
}

@Composable
private fun WeightUnitsSection(
    selectedUnit: WeightUnit,
    onSelect: (WeightUnit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Weight units", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Currently showing ${selectedUnit.displayName}. Numbers on Home, workouts, and history all use this unit.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Workouts stay stored in kilograms. This only changes how weights appear and how you enter them.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WeightUnit.entries.forEach { unit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedUnit == unit,
                            onClick = { onSelect(unit) },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(
                        selected = selectedUnit == unit,
                        onClick = null,
                    )
                    Text(unit.displayName, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun SchedulePrefsSection(
    preferences: SchedulePreferences,
    onDays: (Int) -> Unit,
    onSplit: (SplitStyle) -> Unit,
    onWeekStart: (DayOfWeek) -> Unit,
    onOpenSchedule: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Weekly schedule", style = MaterialTheme.typography.headlineSmall)
        Text(
            "How many days you want to train, and the split the planner should use. The week itself is built from your heat map and routines.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PreferenceBlock(
            preferences = preferences,
            onDays = onDays,
            onSplit = onSplit,
            onWeekStart = onWeekStart,
        )
        TextButton(onClick = onOpenSchedule) { Text("Open this week’s plan") }
    }
}

@Composable
private fun RestTimerPrefsSection(
    preferences: RestTimerPreferences,
    onSound: (Boolean) -> Unit,
    onVibrate: (Boolean) -> Unit,
    onDefaultRest: (Int) -> Unit,
    onCustomDefault: (String) -> Boolean,
) {
    var showCustom by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Rest timer", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Plays when rest ends. Default rest is used after a working set if the lift has none and you haven’t picked a preset.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sound", style = MaterialTheme.typography.titleMedium)
            Switch(checked = preferences.soundEnabled, onCheckedChange = onSound)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Vibration", style = MaterialTheme.typography.titleMedium)
            Switch(checked = preferences.vibrationEnabled, onCheckedChange = onVibrate)
        }
        Text("Default rest", style = MaterialTheme.typography.titleMedium)
        RestPresetChips(
            selectedSeconds = preferences.defaultRestSeconds,
            onSelect = onDefaultRest,
            onCustom = { showCustom = true },
        )
    }
    if (showCustom) {
        CustomRestDialog(
            title = "Default rest",
            confirmLabel = "Save",
            onConfirm = { input ->
                val ok = onCustomDefault(input)
                if (ok) showCustom = false
                ok
            },
            onDismiss = { showCustom = false },
        )
    }
}

@Composable
private fun BackupRestoreSection(
    state: BackupUiState,
    dateTimeFormat: DateFormat,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onCreateBackup: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: (DriveBackupFile) -> Unit,
    onExportFile: () -> Unit,
    onImportFile: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Backup & restore", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Save everything to a file on this phone, or to your own Google Drive. Training " +
                "always works offline — a backup is only read when you ask for one.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.lastBackupAt != null) {
            Text(
                "Last backup: ${dateTimeFormat.format(Date(state.lastBackupAt))}" +
                    (state.lastBackupName?.let { " · $it" }.orEmpty()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "No backup has been made from this phone yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Restores are tracked separately: a restore is not a backup, and saying so here used
        // to mute the only nag that gets the user to actually make one.
        if (state.lastRestoreAt != null) {
            Text(
                "Last restore: ${dateTimeFormat.format(Date(state.lastRestoreAt))}" +
                    (state.lastRestoreName?.let { " · $it" }.orEmpty()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.isBusy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(state.busyLabel ?: "Working…")
            }
        }
        state.status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.error?.let { GymErrorBanner(it) }

        Text("Backup file", style = MaterialTheme.typography.titleMedium)
        Text(
            "Works with no Google account. Save the file to Drive, a PC, or anywhere you keep " +
                "things safe — this is the path that still works if Google sign-in ever breaks.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        PrimaryGymButton(
            text = "Export to file",
            onClick = onExportFile,
            enabled = !state.isBusy,
        )
        OutlinedButton(
            onClick = onImportFile,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Import from file")
        }

        Text("Google Drive", style = MaterialTheme.typography.titleMedium)
        Text(
            if (state.accountEmail != null) {
                "Signed in as ${state.accountEmail}"
            } else {
                "Not signed in"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.accountEmail == null) {
            OutlinedButton(
                onClick = onSignIn,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign in with Google")
            }
        } else {
            PrimaryGymButton(
                text = "Create backup now",
                onClick = onCreateBackup,
                enabled = !state.isBusy,
            )
            OutlinedButton(
                onClick = onRefresh,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("View existing backups")
            }
            TextButton(onClick = onSignOut, enabled = !state.isBusy) {
                Text("Sign out of Google")
            }
        }
        if (state.backups.isNotEmpty()) {
            Text("Drive backups", style = MaterialTheme.typography.titleLarge)
            state.backups.forEach { file ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(file.name, style = MaterialTheme.typography.titleMedium)
                        if (file.modifiedAtMillis > 0) {
                            Text(
                                dateTimeFormat.format(Date(file.modifiedAtMillis)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = { onRestore(file) },
                            enabled = !state.isBusy,
                        ) {
                            Text("Restore this backup")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("About", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Install or update the APK yourself, or let Obtainium watch GitHub Releases. The Play Store is not required.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Context.findActivity(): Activity {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    error("Settings must run in an Activity")
}
