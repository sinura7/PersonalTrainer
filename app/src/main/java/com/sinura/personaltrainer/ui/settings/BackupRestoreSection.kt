package com.sinura.personaltrainer.ui.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.data.backup.DriveBackupFile
import com.sinura.personaltrainer.data.backup.SafetySnapshotMeta
import com.sinura.personaltrainer.domain.ClockFormat
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.domain.DayLabel
import com.sinura.personaltrainer.domain.OneFilledVolt
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.GymStatusBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentMenu
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.InstrumentSwitch
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextDisabled
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.Warn
import com.sinura.personaltrainer.util.JvmTime

/**
 * Backup, with the hierarchy the right way round.
 *
 * Exporting a file is the page Volt (ADR-014 §4). Restore still replaces
 * every byte on the phone, so it stays a Danger row, not a second fill.
 */
@Composable
internal fun BackupRestoreSection(
    state: BackupUiState,
    clock: ClockFormat,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onCreateBackup: () -> Unit,
    onAutoBackupChange: (Boolean) -> Unit,
    onShowBackupPassword: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: (DriveBackupFile) -> Unit,
    onExportFile: () -> Unit,
    onExportPlaintext: () -> Unit,
    onImportFile: () -> Unit,
    onExportSafety: (String) -> Unit,
    onRestoreSafety: (String) -> Unit,
    onDeleteSafety: (String) -> Unit,
    onDismissError: () -> Unit,
    onFinishRestore: () -> Unit,
    onDismissRestoreNote: () -> Unit,
) {
    var dismissedStatus by rememberSaveable { mutableStateOf<String?>(null) }
    // Cleared the moment an action starts, so the memo only ever suppresses a message left
    // over from a previous visit. Comparing by value alone meant a second action whose
    // outcome text was identical to the first — "Found 3 backups." twice — showed nothing.
    LaunchedEffect(state.isBusy) {
        if (state.isBusy) dismissedStatus = null
    }
    SettingsGroup(
        title = "Backup",
        caption = state.backupCaption,
    ) {
        GroupedList {
            BackupStampRow(
                title = "Last backup",
                atMillis = state.lastBackupAt,
                name = state.lastBackupName,
                clock = clock,
                stale = state.backupStale,
            )
            // Restores are tracked separately: a restore is not a backup, and saying so here used
            // to mute the only nag that gets the user to actually make one.
            if (state.lastRestoreAt != null) {
                HairlineDivider()
                BackupStampRow(
                    title = "Last restore",
                    atMillis = state.lastRestoreAt,
                    name = state.lastRestoreName,
                    clock = clock,
                )
            }
        }

        if (state.isBusy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(SPINNER_SIZE),
                    color = Volt,
                    strokeWidth = SPINNER_STROKE,
                )
                Text(state.busyLabel ?: "Working…", style = InstrumentType.body, color = TextSecondary)
            }
        }
        // While an action runs its label is already under the spinner; the banner is for the
        // outcome, which is the only status worth interrupting the layout for. Nothing clears
        // status in the ViewModel, so the last dismissed message is remembered here — otherwise
        // returning to this screen re-announces a backup made an hour ago.
        val status = state.status?.takeIf { !state.isBusy && it != dismissedStatus }
        if (status != null) {
            GymStatusBanner(status, onDismissed = { dismissedStatus = status })
        }
        state.error?.let { GymErrorBanner(it, onDismiss = onDismissError) }
        // Durable, not a dwell banner: recovery wrote it because the owner has something
        // to check, and it stays until they say they have.
        state.restoreNote?.let { note -> GymErrorBanner(message = note, onDismiss = onDismissRestoreNote) }
        state.restorePending?.let { source ->
            Text(
                "Training data from $source is in. Settings still need to be applied.",
                style = InstrumentType.caption,
                color = Warn,
            )
            SecondaryGymButton(
                text = "Finish restore",
                onClick = onFinishRestore,
                enabled = !state.isBusy,
            )
        }

        if (state.sessionLive) {
            Text(
                "Finish or discard the live session before restoring. A restore would " +
                    "delete the session you are standing in.",
                style = InstrumentType.caption,
                color = Warn,
            )
        }

        GymSectionHeader("Backup file", compact = true)
        if (OneFilledVolt.SETTINGS_EXPORT_IS_PRIMARY) {
            PrimaryGymButton(
                text = "Export to file",
                onClick = onExportFile,
                enabled = !state.isBusy,
                modifier = Modifier.testTag(SettingsTags.EXPORT_FILE),
            )
        } else {
            SecondaryGymButton(
                text = "Export to file",
                onClick = onExportFile,
                enabled = !state.isBusy,
                modifier = Modifier.testTag(SettingsTags.EXPORT_FILE),
            )
        }
        TextButton(
            onClick = onExportPlaintext,
            enabled = !state.isBusy,
        ) {
            Text(
                "Export without a password",
                style = InstrumentType.caption,
                color = if (state.isBusy) TextDisabled else TextSecondary,
            )
        }
        val restoreBlocked = state.isBusy || state.sessionLive
        GroupedList {
            InstrumentRow(
                title = "Import from file",
                subtitle = if (state.sessionLive) {
                    "Finish the live session first"
                } else {
                    "Replaces everything on this phone"
                },
                onClick = if (restoreBlocked) null else onImportFile,
                trailing = { DangerAction("Replace", enabled = !restoreBlocked) },
            )
        }

        GymSectionHeader("Google Drive", compact = true)
        if (state.accountEmail == null) {
            SecondaryGymButton(
                text = "Sign in with Google",
                onClick = onSignIn,
                enabled = !state.isBusy,
            )
        } else {
            // Deliberately not a nested if-expression inside the row: the paused case is the
            // one that has to read clearly, and it is the one a nag would bury.
            val autoBackupSubtitle = if (state.autoBackupNeedsSignIn) {
                "Paused — sign in to Drive again"
            } else if (state.autoBackupEnabled) {
                "Each finished workout goes to Drive"
            } else {
                "Off. Backups happen only when you tap."
            }
            GroupedList {
                InstrumentRow(
                    title = "Signed in",
                    subtitle = state.accountEmail,
                    onClick = if (state.isBusy) null else onSignOut,
                    trailing = { DangerAction("Sign out", enabled = !state.isBusy) },
                )
                InstrumentRow(
                    title = "Back up after each workout",
                    subtitle = autoBackupSubtitle,
                    modifier = Modifier.testTag(SettingsTags.AUTO_BACKUP),
                    checked = state.autoBackupEnabled,
                    onCheckedChange = if (state.isBusy) null else onAutoBackupChange,
                    trailing = {
                        InstrumentSwitch(
                            checked = state.autoBackupEnabled,
                            onCheckedChange = null,
                        )
                    },
                )
                if (state.autoBackupEnabled) {
                    // Arming stopped the app ever asking for the password again. Without a way
                    // back to it, a forgotten password seals every Drive backup permanently.
                    InstrumentRow(
                        title = "Show backup password",
                        subtitle = "Asks for your screen lock first",
                        modifier = Modifier.testTag(SettingsTags.SHOW_BACKUP_PASSWORD),
                        onClick = if (state.isBusy) null else onShowBackupPassword,
                    )
                }
            }
            SecondaryGymButton(
                text = "Create backup now",
                onClick = onCreateBackup,
                enabled = !state.isBusy,
            )
            SecondaryGymButton(
                text = "View existing backups",
                onClick = onRefresh,
                enabled = !state.isBusy,
            )
        }

        if (state.backups.isNotEmpty()) {
            GymSectionHeader("Drive backups", compact = true)
            GroupedList {
                state.backups.forEachIndexed { index, file ->
                    if (index > 0) HairlineDivider()
                    InstrumentRow(
                        title = file.name,
                        subtitle = file.modifiedAtMillis
                            .takeIf { it > 0 }
                            ?.let { DateCopy.dateTime(it, clock) },
                        onClick = if (restoreBlocked) null else ({ onRestore(file) }),
                        trailing = { DangerAction("Restore", enabled = !restoreBlocked) },
                    )
                }
            }
        }

        GymSectionHeader("Safety copies on this phone", compact = true)
        if (state.safetySnapshots.isEmpty()) {
            Text(
                "None yet. A verified copy is saved each time you restore.",
                style = InstrumentType.caption,
                color = TextTertiary,
            )
        } else {
            GroupedList {
                state.safetySnapshots.forEachIndexed { index, snap ->
                    if (index > 0) HairlineDivider()
                    SafetyCopyRow(
                        snapshot = snap,
                        clock = clock,
                        enabled = !state.isBusy,
                        restoreBlocked = restoreBlocked,
                        onExport = { onExportSafety(snap.id) },
                        onRestore = { onRestoreSafety(snap.id) },
                        onDelete = { onDeleteSafety(snap.id) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SafetyCopyRow(
    snapshot: SafetySnapshotMeta,
    clock: ClockFormat,
    enabled: Boolean,
    restoreBlocked: Boolean,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    InstrumentRow(
        title = snapshot.title,
        subtitle = snapshot.subtitle(DateCopy.dateTime(snapshot.createdAtMillis, clock)),
        trailing = {
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "Safety copy options",
                        tint = if (enabled) TextSecondary else TextDisabled,
                    )
                }
                InstrumentMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text("Export", style = InstrumentType.bodyStrong, color = TextPrimary)
                        },
                        onClick = {
                            menuOpen = false
                            onExport()
                        },
                    )
                    DropdownMenuItem(
                        enabled = !restoreBlocked,
                        text = {
                            Text(
                                "Restore…",
                                style = InstrumentType.bodyStrong,
                                color = if (restoreBlocked) TextDisabled else Danger,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onRestore()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text("Delete…", style = InstrumentType.bodyStrong, color = TextSecondary)
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        },
    )
}

/**
 * When a backup or a restore last happened, as a readout rather than a sentence.
 *
 * Recency is what the question actually is, so the last week reads as "Yesterday" and only
 * older stamps fall back to a date. Never having backed up is the one state worth a colour:
 * it wears [Warn], because it is the only thing on this screen that can lose data.
 */
@Composable
internal fun BackupStampRow(
    title: String,
    atMillis: Long?,
    name: String?,
    clock: ClockFormat,
    stale: Boolean = atMillis == null,
) {
    val stamp = remember(atMillis, clock) {
        atMillis?.let { at ->
            DayLabel.relative(at, JvmTime.nowMillis(), JvmTime) ?: DateCopy.dateTime(at, clock)
        }
    }
    InstrumentRow(
        title = title,
        subtitle = name,
        trailing = {
            Text(
                stamp ?: "Never",
                style = InstrumentType.numeralSm,
                color = if (stale) Warn else TextPrimary,
                maxLines = 1,
            )
        },
    )
}

private val SPINNER_SIZE = 20.dp
private val SPINNER_STROKE = 2.dp
