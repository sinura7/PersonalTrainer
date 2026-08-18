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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.data.backup.DriveBackupFile
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val selectedUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val backup by viewModel.backupState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onResolutionFinished(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(viewModel) {
        viewModel.resolutionRequest.collect { sender ->
            resolutionLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
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
            BackupRestoreSection(
                state = backup,
                dateTimeFormat = dateTimeFormat,
                onSignIn = { viewModel.signIn(activity) },
                onSignOut = { viewModel.signOut(activity) },
                onCreateBackup = { viewModel.createBackup(activity) },
                onRefresh = { viewModel.refreshBackups(activity) },
                onRestore = viewModel::requestRestore,
            )
        }
    }

    backup.pendingRestore?.let { file ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Replace all training data?") },
            text = {
                Text(
                    "Restoring ${file.name} replaces every exercise, routine, and workout on this phone. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRestore(activity) }) {
                    Text("Restore backup")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRestore) { Text("Cancel") }
            },
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
private fun BackupRestoreSection(
    state: BackupUiState,
    dateTimeFormat: DateFormat,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onCreateBackup: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: (DriveBackupFile) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Backup & restore", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Save everything to your own Google Drive. Training still works offline — Drive is only used when you back up or restore.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (state.accountEmail != null) {
                "Signed in as ${state.accountEmail}"
            } else {
                "Not signed in"
            },
            style = MaterialTheme.typography.titleMedium,
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
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.accountEmail == null) {
            PrimaryGymButton(
                text = "Sign in with Google",
                onClick = onSignIn,
                enabled = !state.isBusy,
            )
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

private fun Context.findActivity(): Activity {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    error("Settings must run in an Activity")
}
