package com.sinura.personaltrainer.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.BuildConfig
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.DriveBackupFile
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.DayLabel
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.CustomRestDialog
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.GymStatusBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.NumberEntryDialog
import com.sinura.personaltrainer.ui.components.RestPresetChips
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.components.TemperMark
import com.sinura.personaltrainer.ui.plan.PreferenceBlock
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.Warn
import java.text.DateFormat
import java.time.DayOfWeek
import java.util.Date

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val selectedUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val schedulePrefs by viewModel.schedulePreferences.collectAsStateWithLifecycle()
    val restPrefs by viewModel.restTimerPreferences.collectAsStateWithLifecycle()
    val coachPrefs by viewModel.coachPreferences.collectAsStateWithLifecycle()
    val bodyweightKg by viewModel.bodyweightKg.collectAsStateWithLifecycle()
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

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsHeader(onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = Metrics.gutter,
                    end = Metrics.gutter,
                    top = Metrics.space2,
                    bottom = Metrics.space8,
                ),
            verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
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
            )
            CoachingSection(
                preferences = coachPrefs,
                bodyweightKg = bodyweightKg,
                unit = selectedUnit,
                onGoal = viewModel::setTrainingGoal,
                onToggleEquipment = viewModel::toggleEquipment,
                onRecordBodyweight = viewModel::recordBodyweight,
                onClearBodyweight = viewModel::clearBodyweight,
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
            PlanSetupSection(onRerun = viewModel::rerunGuidedSetup)
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
private fun SettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .padding(start = Metrics.space2, end = Metrics.space4, bottom = Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = TextSecondary)
        }
        Text("Settings", style = InstrumentType.title, color = TextPrimary, maxLines = 1)
    }
}

/**
 * One preference group: a label, its controls, and one line of explanation.
 *
 * Every section here used to be a naked column of paragraphs on the window colour, separated
 * from the next by the same gap that separated two lines of its own prose — so the boundary
 * between "weight units" and "weekly schedule" existed only for someone reading the words.
 * The kicker labels the group, the container draws it, and the explanations collapse to a
 * single caption underneath: the settings that used to be described are now shown.
 */
@Composable
private fun SettingsGroup(
    title: String,
    caption: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap),
    ) {
        Kicker(title)
        content()
        Text(caption, style = InstrumentType.caption, color = TextTertiary)
    }
}

/** The trailing verb of a row that destroys or replaces data. */
@Composable
private fun DangerAction(label: String, enabled: Boolean) {
    Text(
        label,
        style = InstrumentType.bodyStrong,
        color = if (enabled) Danger else TextTertiary,
        maxLines = 1,
    )
}

@Composable
private fun WeightUnitsSection(
    selectedUnit: WeightUnit,
    onSelect: (WeightUnit) -> Unit,
) {
    SettingsGroup(
        title = "Weight",
        caption = "Everything is stored in kilograms whichever you pick. This changes only how " +
            "weights are shown and entered — on Home, in workouts, and in history.",
    ) {
        GroupedList(modifier = Modifier.selectableGroup()) {
            WeightUnit.entries.forEachIndexed { index, unit ->
                if (index > 0) HairlineDivider()
                val selected = selectedUnit == unit
                InstrumentRow(
                    title = unit.displayName,
                    modifier = Modifier.selectable(
                        selected = selected,
                        onClick = { onSelect(unit) },
                        role = Role.RadioButton,
                    ),
                    trailing = {
                        if (selected) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = Volt)
                        }
                    },
                )
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
) {
    SettingsGroup(
        title = "Schedule",
        // The week itself moved to its own tab. What is left here shapes what a suggestion
        // looks like, which is a preference; the week is a decision, and decisions belong
        // where you can see the thing you are deciding about.
        caption = "Days, split and week start. Pin the week itself on the Plan tab.",
    ) {
        GymCard {
            PreferenceBlock(
                preferences = preferences,
                onDays = onDays,
                onSplit = onSplit,
                onWeekStart = onWeekStart,
            )
        }
    }
}

/**
 * What the coach emphasises, and what you actually have to lift with.
 *
 * Both are inputs to advice rather than to training itself, which is why they live here and
 * not on the Plan tab: nothing on this card changes a single number in your history, and
 * changing your goal is not something you do weekly.
 *
 * Equipment is opt-OUT. Everything counts as available until you say otherwise, because a
 * first run that assumed you owned nothing would silently produce a coach that never names a
 * lift, with nothing on screen to explain the silence.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoachingSection(
    preferences: CoachPreferences,
    bodyweightKg: Double?,
    unit: WeightUnit,
    onGoal: (TrainingGoal) -> Unit,
    onToggleEquipment: (EquipmentType) -> Unit,
    onRecordBodyweight: (Double) -> Unit,
    onClearBodyweight: () -> Unit,
) {
    var weighingIn by rememberSaveable { mutableStateOf(false) }
    if (weighingIn) {
        NumberEntryDialog(
            title = "Bodyweight",
            unitLabel = unit.suffix,
            initial = bodyweightKg
                ?.let { WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(it, unit)) }
                .orEmpty(),
            decimal = true,
            helper = "Today's weigh-in. One a day is kept — the last one you type.",
            parse = { NumericEntry.parseWeightKg(it, unit) },
            onConfirm = {
                weighingIn = false
                onRecordBodyweight(it)
            },
            onDismiss = { weighingIn = false },
        )
    }
    SettingsGroup(
        title = "Coaching",
        caption = "Your goal reorders the suggestions; it never changes what they are. " +
            "Turning equipment off stops the coach naming lifts you cannot do.",
    ) {
        GroupedList(modifier = Modifier.selectableGroup()) {
            TrainingGoal.entries.forEachIndexed { index, goal ->
                if (index > 0) HairlineDivider()
                val selected = preferences.goal == goal
                InstrumentRow(
                    title = goal.displayName,
                    subtitle = goal.blurb,
                    modifier = Modifier.selectable(
                        selected = selected,
                        onClick = { onGoal(goal) },
                        role = Role.RadioButton,
                    ),
                    trailing = {
                        if (selected) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = Volt)
                        }
                    },
                )
            }
        }
        GymCard {
            Kicker("Bodyweight")
            Text(
                "Only used to say what your weight did across a block. Nothing else reads it — " +
                    "bodyweight lifts are counted in reps.",
                style = InstrumentType.caption,
                color = TextSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    bodyweightKg?.toWeightLabel(unit) ?: "Not set",
                    style = InstrumentType.numeralSm,
                    color = if (bodyweightKg != null) TextPrimary else TextTertiary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                    if (bodyweightKg != null) {
                        TextButton(onClick = onClearBodyweight) {
                            Text("Clear", style = InstrumentType.bodyStrong, color = TextSecondary)
                        }
                    }
                    TextButton(onClick = { weighingIn = true }) {
                        Text(
                            if (bodyweightKg != null) "Update" else "Add",
                            style = InstrumentType.bodyStrong,
                            color = Volt,
                        )
                    }
                }
            }
        }
        GymCard {
            Kicker("Equipment you have")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                EquipmentType.entries.forEach { equipment ->
                    InstrumentChip(
                        label = equipment.name.lowercase().replaceFirstChar { it.titlecase() },
                        selected = preferences.allows(equipment),
                        onClick = { onToggleEquipment(equipment) },
                    )
                }
            }
        }
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
    SettingsGroup(
        title = "Rest timer",
        caption = "The cue plays when rest ends. Default rest is used after a working set if the " +
            "lift has none and you haven't picked a preset.",
    ) {
        GroupedList {
            InstrumentRow(
                title = "Sound",
                trailing = { Switch(checked = preferences.soundEnabled, onCheckedChange = onSound) },
            )
            HairlineDivider()
            InstrumentRow(
                title = "Vibration",
                trailing = { Switch(checked = preferences.vibrationEnabled, onCheckedChange = onVibrate) },
            )
            HairlineDivider()
            Column(
                modifier = Modifier.padding(
                    start = Metrics.space4,
                    end = Metrics.space4,
                    top = Metrics.space3,
                    bottom = Metrics.space4,
                ),
                verticalArrangement = Arrangement.spacedBy(Metrics.space3),
            ) {
                Kicker("Default rest")
                RestPresetChips(
                    selectedSeconds = preferences.defaultRestSeconds,
                    onSelect = onDefaultRest,
                    onCustom = { showCustom = true },
                )
            }
        }
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

/**
 * Backup, with the hierarchy the right way round.
 *
 * Exporting a file used to be a 64dp filled hero button while restoring a backup — which
 * replaces every byte of training data on the phone — was a bare text button, visually
 * identical to the link that opened the week's plan. The loud control belongs to the workout
 * flow, so everything safe here is a secondary button, and everything that replaces data is
 * a row whose verb is in [Danger].
 */
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
    var dismissedStatus by rememberSaveable { mutableStateOf<String?>(null) }
    // Cleared the moment an action starts, so the memo only ever suppresses a message left
    // over from a previous visit. Comparing by value alone meant a second action whose
    // outcome text was identical to the first — "Found 3 backups." twice — showed nothing.
    LaunchedEffect(state.isBusy) {
        if (state.isBusy) dismissedStatus = null
    }
    SettingsGroup(
        title = "Backup",
        caption = "Training always works offline — a backup is only read when you ask for one. " +
            "The file path needs no Google account, and still works if sign-in ever breaks.",
    ) {
        GroupedList {
            BackupStampRow(
                title = "Last backup",
                atMillis = state.lastBackupAt,
                name = state.lastBackupName,
                dateTimeFormat = dateTimeFormat,
            )
            // Restores are tracked separately: a restore is not a backup, and saying so here used
            // to mute the only nag that gets the user to actually make one.
            if (state.lastRestoreAt != null) {
                HairlineDivider()
                BackupStampRow(
                    title = "Last restore",
                    atMillis = state.lastRestoreAt,
                    name = state.lastRestoreName,
                    dateTimeFormat = dateTimeFormat,
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
        state.error?.let { GymErrorBanner(it) }

        GymSectionHeader("Backup file", compact = true)
        SecondaryGymButton(
            text = "Export to file",
            onClick = onExportFile,
            enabled = !state.isBusy,
        )
        GroupedList {
            InstrumentRow(
                title = "Import from file",
                subtitle = "Replaces everything on this phone",
                onClick = if (state.isBusy) null else onImportFile,
                trailing = { DangerAction("Replace", enabled = !state.isBusy) },
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
            GroupedList {
                InstrumentRow(
                    title = "Signed in",
                    subtitle = state.accountEmail,
                    onClick = if (state.isBusy) null else onSignOut,
                    trailing = { DangerAction("Sign out", enabled = !state.isBusy) },
                )
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
                            ?.let { dateTimeFormat.format(Date(it)) },
                        onClick = if (state.isBusy) null else ({ onRestore(file) }),
                        trailing = { DangerAction("Restore", enabled = !state.isBusy) },
                    )
                }
            }
        }
    }
}

/**
 * When a backup or a restore last happened, as a readout rather than a sentence.
 *
 * Recency is what the question actually is, so the last week reads as "Yesterday" and only
 * older stamps fall back to a date. Never having backed up is the one state worth a colour:
 * it wears [Warn], because it is the only thing on this screen that can lose data.
 */
@Composable
private fun BackupStampRow(
    title: String,
    atMillis: Long?,
    name: String?,
    dateTimeFormat: DateFormat,
) {
    val stamp = remember(atMillis) {
        atMillis?.let { at ->
            DayLabel.relative(at, System.currentTimeMillis()) ?: dateTimeFormat.format(Date(at))
        }
    }
    InstrumentRow(
        title = title,
        subtitle = name,
        trailing = {
            Text(
                stamp ?: "Never",
                style = InstrumentType.numeralSm,
                color = if (stamp == null) Warn else TextPrimary,
                maxLines = 1,
            )
        },
    )
}

/**
 * The way back into the guided setup.
 *
 * Reachable rather than one-shot because the answers it asks for genuinely change — people
 * move gyms, drop to three days, come back after a layoff. The caption states what it will and
 * will not do, because "rebuild my plan" is exactly the phrase someone would expect to
 * REPLACE what they have, and it does not: nothing here deletes a routine that months of
 * history point at.
 */
@Composable
private fun PlanSetupSection(onRerun: () -> Unit) {
    SettingsGroup(
        title = "Your plan",
        caption = "Answer the setup questions again to generate a fresh week. Your existing " +
            "routines and history are kept — new sessions are added alongside them.",
    ) {
        GroupedList {
            InstrumentRow(
                title = "Rebuild my plan",
                subtitle = "Six questions, then a preview before anything changes",
                onClick = onRerun,
            )
        }
    }
}

@Composable
private fun AboutSection() {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
        TemperMark(size = 64.dp)
        SettingsGroup(
            title = "About",
            caption = "Install or update the APK yourself, or let Obtainium watch GitHub Releases. " +
                "The Play Store is not required.",
        ) {
            GroupedList {
                InstrumentRow(
                    title = "Version",
                    trailing = {
                        Text(
                            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = InstrumentType.numeralSm,
                            color = TextPrimary,
                            maxLines = 1,
                        )
                    },
                )
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

private val SPINNER_SIZE = 20.dp
private val SPINNER_STROKE = 2.dp
