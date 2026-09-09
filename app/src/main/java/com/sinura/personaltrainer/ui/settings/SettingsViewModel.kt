package com.sinura.personaltrainer.ui.settings

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.backup.AuthoredInventory
import com.sinura.personaltrainer.data.backup.BackupEnvelope
import com.sinura.personaltrainer.data.backup.BackupException
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.BackupScaleBudget
import com.sinura.personaltrainer.data.backup.DriveBackupFile
import com.sinura.personaltrainer.data.backup.DriveBackupListing
import com.sinura.personaltrainer.data.backup.RestoreJournal
import com.sinura.personaltrainer.data.backup.RestoreRecovery
import com.sinura.personaltrainer.data.backup.SafetySnapshotMeta
import com.sinura.personaltrainer.data.backup.readAtMost
import com.sinura.personaltrainer.data.repository.RestorePlan
import com.sinura.personaltrainer.data.repository.RestoreResult
import com.sinura.personaltrainer.domain.BackupPrompt
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.ReminderPreferences
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.TrainingEmphasis
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.TrainingPlace
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.timer.exactAlarmSettingsIntent as buildExactAlarmSettingsIntent
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.domain.Weekday
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class BackupUiState(
    val accountEmail: String? = null,
    val lastBackupAt: Long? = null,
    val lastBackupName: String? = null,
    val lastRestoreAt: Long? = null,
    val lastRestoreName: String? = null,
    val backups: List<DriveBackupFile> = emptyList(),
    val isBusy: Boolean = false,
    val busyLabel: String? = null,
    val status: String? = null,
    val error: String? = null,
    val pendingPreview: RestorePreviewUi? = null,
    /** Restore would wipe the live session. Say so before the tap, not after the refuse. */
    val sessionLive: Boolean = false,
    /** No stamp, or older than 14 days. Caption nags; Export stays the tap. */
    val backupStale: Boolean = true,
    val safetySnapshots: List<SafetySnapshotMeta> = emptyList(),
    val pendingProtect: BackupProtectKind? = null,
    /** The toggle is asking for a passphrase to seal before arming automatic backup. */
    val pendingAutoBackupArm: Boolean = false,
    /** Automatic backup after a finished workout is armed. */
    val autoBackupEnabled: Boolean = false,
    /** An unattended copy found the Drive grant lapsed and stopped rather than prompting. */
    val autoBackupNeedsSignIn: Boolean = false,
    val pendingUnlock: Boolean = false,
    val pendingPlaintextWarning: Boolean = false,
    val launchExportPicker: Boolean = false,
    val launchSafetyExportPicker: Boolean = false,
    /**
     * A restore whose Room half committed but whose settings are still owed: the journal is
     * open at `room`/`prefs` and Finish restore retries it. Null when nothing is pending.
     */
    val restorePending: String? = null,
    /** A durable note from restore recovery, shown until the owner dismisses it. */
    val restoreNote: String? = null,
)

enum class BackupProtectKind { FILE_EXPORT, DRIVE_BACKUP, SAFETY_EXPORT }

private const val TAG = "PT/SettingsVM"

class SettingsViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
    private val envelopeIterations: Int = BackupEnvelope.DEFAULT_ITERATIONS,
) : AppViewModel(application, container) {
    val weightUnit: StateFlow<WeightUnit> = container.preferencesRepository.weightUnit
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeightUnit.LBS,
        )

    val clockFormat: StateFlow<com.sinura.personaltrainer.domain.ClockFormat> =
        container.preferencesRepository.clockFormat
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = com.sinura.personaltrainer.domain.ClockFormat.TWELVE,
            )

    val preferredDays: StateFlow<Set<Weekday>> =
        container.preferencesRepository.preferredDays
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptySet(),
            )

    val bodyweightCheckInWeekday: StateFlow<Weekday?> =
        container.preferencesRepository.bodyweightCheckInWeekday
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    val schedulePreferences: StateFlow<SchedulePreferences> =
        container.preferencesRepository.schedulePreferences
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SchedulePreferences.DEFAULT,
            )

    val reminderPreferences: StateFlow<ReminderPreferences> =
        container.preferencesRepository.reminderPreferences
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ReminderPreferences.DEFAULT,
            )

    val restTimerPreferences: StateFlow<RestTimerPreferences> =
        container.preferencesRepository.restTimerPreferences
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = RestTimerPreferences.DEFAULT,
            )

    /**
     * Honest inexact copy + Settings tap. Shown only after rest is used or
     * configured, and only while the policy would take the best-effort path.
     * Never says the fallback is reliable.
     */
    val offerExactAlarmAccess: StateFlow<Boolean> = combine(
        container.preferencesRepository.restAlarmEligible,
        container.restTimerController.exactAlarmAttempt,
    ) { eligible, attempt ->
        eligible && attempt == ExactAlarmAttempt.BEST_EFFORT
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun refreshAlarmCapability() {
        container.restTimerController.refreshAlarmCapability()
    }

    fun exactAlarmSettingsIntent(): Intent? =
        buildExactAlarmSettingsIntent(getApplication<Application>().packageName)

    val coachPreferences: StateFlow<CoachPreferences> =
        container.preferencesRepository.coachPreferences
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = CoachPreferences.DEFAULT,
            )

    /**
     * What the lifter currently weighs, or null when they have not said.
     *
     * Asked once during setup and, until this existed, unchangeable without re-running the
     * whole questionnaire — a stored value with no way to correct it.
     */
    val bodyweightKg: StateFlow<Double?> =
        container.preferencesRepository.bodyweightKg
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Record a weigh-in for today.
     *
     * A weigh-in rather than an overwrite: the block review reads the history to say what
     * bodyweight did across twelve weeks, and it can only do that if each change is kept.
     */
    fun recordBodyweight(kg: Double) {
        viewModelScope.launch {
            runCatchingCancellable {
                container.preferencesRepository.recordBodyweight(kg, todayEpochDay())
            }.onFailure { AppLog.w(TAG, "Recording bodyweight failed", it) }
        }
    }

    fun clearBodyweight() {
        viewModelScope.launch {
            runCatchingCancellable { container.preferencesRepository.setBodyweightKg(null) }
                .onFailure { AppLog.w(TAG, "Clearing bodyweight failed", it) }
        }
    }

    fun setTrainingGoal(goal: TrainingGoal) {
        viewModelScope.launch { container.preferencesRepository.setTrainingGoal(goal) }
    }

    fun setTrainingEmphasis(emphasis: TrainingEmphasis) {
        viewModelScope.launch { container.preferencesRepository.setTrainingEmphasis(emphasis) }
    }

    /**
     * Toggling equipment off tells the coach not to name lifts you cannot do. An empty set is
     * gym-floor (everything except Hyper Pro). Expanding that to an explicit gym set before
     * toggling is what lets someone add the Hyper Pro without wiping the gym kit down to one chip.
     */
    fun toggleEquipment(equipment: EquipmentType) {
        viewModelScope.launch {
            val current = container.preferencesRepository.coachPreferences.first().availableEquipment
            val expanded = if (current.isEmpty()) {
                TrainingPlace.GYM_FLOOR.map { it.name }.toSet()
            } else {
                current
            }
            val next = if (equipment.name in expanded) {
                expanded - equipment.name
            } else {
                expanded + equipment.name
            }
            val gymFloor = TrainingPlace.GYM_FLOOR.map { it.name }.toSet()
            container.preferencesRepository.setAvailableEquipment(
                if (next == gymFloor) emptySet() else next,
            )
        }
    }

    private val isBusy = MutableStateFlow(false)
    private val busyLabel = MutableStateFlow<String?>(null)
    private val status = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val backups = MutableStateFlow<List<DriveBackupFile>>(emptyList())
    private val safetySnapshots = MutableStateFlow<List<SafetySnapshotMeta>>(emptyList())
    private val restorePending = MutableStateFlow<String?>(null)
    private val pendingPlan = MutableStateFlow<RestorePlan?>(null)
    private val dialogs = MutableStateFlow(BackupDialogs())
    private var heldPassword: CharArray? = null
    private var pendingCiphertext: String? = null
    private var pendingCipherName: String? = null
    private var plaintextKind: BackupProtectKind? = null

    /**
     * One-shot approvals from the plaintext warning dialog. exportToFile /
     * exportSafetySnapshot write plaintext ONLY while one is armed: after
     * process death in the system file picker both this and [heldPassword]
     * are gone, and the export refuses instead of silently writing the
     * unprotected file the user never chose.
     */
    private var plaintextExportApproved = false
    private var plaintextSafetyApproved = false
    private var resolutionWaiter: CompletableDeferred<Boolean>? = null

    /**
     * The pending Google consent intent, as state rather than an event.
     *
     * It was a replay-0 SharedFlow collected from a LaunchedEffect, so an emission arriving
     * while the screen was not composed — a rotation, or the screen briefly leaving
     * composition — was dropped on the floor. awaitResolution then waited forever on a
     * CompletableDeferred that nothing would ever complete, isBusy stayed true, and every
     * backup button was disabled until the process died. A StateFlow survives recreation and
     * is re-read by the new composition.
     */
    private val _pendingResolution = MutableStateFlow<IntentSender?>(null)
    val pendingResolution: StateFlow<IntentSender?> = _pendingResolution.asStateFlow()

    fun onResolutionLaunched() {
        _pendingResolution.value = null
    }

    val backupState: StateFlow<BackupUiState> = combine(
        combine(
            container.preferencesRepository.driveAccountEmail,
            container.preferencesRepository.lastBackupAt,
            container.preferencesRepository.lastBackupName,
            container.preferencesRepository.lastRestoreAt,
            container.preferencesRepository.lastRestoreName,
        ) { email, lastAt, lastName, restoreAt, restoreName ->
            BackupMeta(email, lastAt, lastName, restoreAt, restoreName)
        },
        combine(
            combine(isBusy, busyLabel, status, error, pendingPlan) { busy, label, note, err, plan ->
                BackupFlags(busy, label, note, err, plan?.toPreview())
            },
            dialogs,
            combine(restorePending, container.preferencesRepository.restoreRecoveryNote) { pending, note ->
                RecoveryUi(pendingSource = pending, note = note)
            },
            container.preferencesRepository.autoBackupEnabled,
            container.preferencesRepository.autoBackupNeedsSignIn,
            // Folded in here rather than into the outer combine, which is already at the
            // five-flow ceiling of the typed combine overloads.
        ) { flags, gate, recovery, autoOn, needsSignIn ->
            flags.copy(
                dialogs = gate,
                recovery = recovery,
                autoBackupEnabled = autoOn,
                autoBackupNeedsSignIn = needsSignIn,
            )
        },
        backups,
        combine(
            container.workoutRepository.observeInProgress(),
            container.activityRepository.observeLive(),
        ) { workout, activity -> workout != null || activity != null },
        safetySnapshots,
    ) { meta, flags, files, live, snaps ->
        BackupUiState(
            accountEmail = meta.email,
            lastBackupAt = meta.lastAt,
            lastBackupName = meta.lastName,
            lastRestoreAt = meta.restoreAt,
            lastRestoreName = meta.restoreName,
            backups = files,
            isBusy = flags.busy,
            busyLabel = flags.label,
            status = flags.status,
            error = flags.error,
            pendingPreview = flags.pendingPreview,
            sessionLive = live,
            backupStale = BackupPrompt.isStale(meta.lastAt, System.currentTimeMillis()),
            safetySnapshots = snaps,
            pendingProtect = flags.dialogs.protect,
            pendingAutoBackupArm = flags.dialogs.armAutoBackup,
            autoBackupEnabled = flags.autoBackupEnabled,
            autoBackupNeedsSignIn = flags.autoBackupNeedsSignIn,
            pendingUnlock = flags.dialogs.unlock,
            pendingPlaintextWarning = flags.dialogs.plaintextWarning,
            launchExportPicker = flags.dialogs.launchExportPicker,
            launchSafetyExportPicker = flags.dialogs.launchSafetyExportPicker,
            restorePending = flags.recovery.pendingSource,
            restoreNote = flags.recovery.note,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BackupUiState(),
    )

    /**
     * Kept for tests and any leftover caller. The Settings row now navigates
     * to the questionnaire without flipping the launch gate — Home is always
     * the shell, and the sheet only appears when setup is still incomplete.
     */
    fun rerunGuidedSetup() {
        viewModelScope.launch {
            runCatchingCancellable { container.preferencesRepository.setOnboardingComplete(false) }
                .onFailure { AppLog.w(TAG, "Reopening guided setup failed", it) }
        }
    }

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch {
            container.preferencesRepository.setWeightUnit(unit)
        }
    }

    fun setClockFormat(format: com.sinura.personaltrainer.domain.ClockFormat) {
        viewModelScope.launch {
            container.preferencesRepository.setClockFormat(format)
        }
    }

    fun setBodyweightCheckInWeekday(day: Weekday?) {
        viewModelScope.launch {
            runCatchingCancellable { container.preferencesRepository.setBodyweightCheckInWeekday(day) }
                .onFailure { AppLog.w(TAG, "Saving bodyweight check-in day failed", it) }
        }
    }

    fun setTrainingDays(days: Int) {
        viewModelScope.launch {
            container.preferencesRepository.setTrainingDaysPerWeek(days)
        }
    }

    fun setSplitStyle(style: SplitStyle) {
        viewModelScope.launch {
            container.preferencesRepository.setSplitStyle(style)
        }
    }

    fun setWeekStart(day: Weekday) {
        viewModelScope.launch {
            container.preferencesRepository.setWeekStart(day)
        }
    }

    fun setReminderOptOut(optOut: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setReminderOptOut(optOut)
        }
    }

    fun setReminderQuietHours(startHour: Int, endHour: Int) {
        viewModelScope.launch {
            runCatchingCancellable {
                container.preferencesRepository.setReminderQuietHours(startHour, endHour)
            }.onFailure { AppLog.w(TAG, "Saving reminder quiet hours failed", it) }
        }
    }

    fun setRestSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setRestSoundEnabled(enabled)
        }
    }

    fun setRestVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setRestVibrationEnabled(enabled)
        }
    }

    fun setDefaultRestSeconds(seconds: Int) {
        viewModelScope.launch {
            container.preferencesRepository.setDefaultRestSeconds(seconds)
        }
    }

    fun setDefaultRestCustom(input: String): Boolean {
        val seconds = RestTimer.parseCustom(input) ?: return false
        setDefaultRestSeconds(seconds)
        return true
    }

    fun signIn(activity: Activity) {
        runBackupAction("Signing in…") {
            container.backupRepository.signIn(activity, ::awaitResolution)
            status.value = "Signed in. Backups stay in your PersonalTrainer Backups Drive folder."
        }
    }

    fun signOut(activity: Activity) {
        runBackupAction("Signing out…") {
            container.backupRepository.signOut(activity)
            backups.value = emptyList()
            status.value = "Signed out. Training data on this phone is unchanged."
        }
    }

    fun beginFileExport() {
        dialogs.value = BackupDialogs(protect = BackupProtectKind.FILE_EXPORT)
    }

    /**
     * A safety copy is the same full history as any export, so it gets the
     * same protect-or-warn gate — it used to write plaintext straight to the
     * picked file with neither.
     */
    fun beginSafetyExport() {
        dialogs.value = BackupDialogs(protect = BackupProtectKind.SAFETY_EXPORT)
    }

    fun beginDriveBackup() {
        dialogs.value = BackupDialogs(protect = BackupProtectKind.DRIVE_BACKUP)
    }

    /**
     * Turning automatic backup on. Asks for the passphrase once; turning it off forgets it.
     *
     * The toggle is the only place the passphrase is ever sealed. There is deliberately no
     * plaintext escape here: an unattended copy that quietly wrote a readable file would
     * reverse the signed default in ADR-009 §9 without anyone being asked.
     */
    fun setAutoBackupEnabled(enabled: Boolean) {
        if (enabled) {
            dialogs.value = BackupDialogs(armAutoBackup = true)
        } else {
            runBackupAction("Turning off automatic backup…") {
                container.preferencesRepository.disarmAutoBackup()
                status.value = "Automatic backup is off. Create backup now still works."
            }
        }
    }

    fun cancelAutoBackupArm() {
        dialogs.value = BackupDialogs()
    }

    /**
     * Seals the typed passphrase and arms automatic backup. Returns false, leaving the dialog
     * up, when the passphrase is refused or the phone would not seal it.
     */
    fun submitAutoBackupPassphrase(password: String, confirm: String): Boolean {
        val reason = BackupEnvelope.validateNewPassword(password, confirm)
        if (reason != null) {
            error.value = reason
            return false
        }
        val chars = password.toCharArray()
        val sealed = try {
            container.backupPassphraseSealer.seal(chars)
        } finally {
            chars.fill('\u0000')
        }
        if (sealed == null) {
            error.value = "This phone would not store the backup password. " +
                "Automatic backup stays off; Create backup now still works."
            return false
        }
        dialogs.value = BackupDialogs()
        runBackupAction("Turning on automatic backup…") {
            container.preferencesRepository.armAutoBackup(sealed)
            status.value = "Automatic backup is on. Each finished workout goes to Drive."
        }
        return true
    }

    fun beginPlaintextExport() {
        plaintextKind = dialogs.value.protect ?: BackupProtectKind.FILE_EXPORT
        wipeHeldPassword()
        dialogs.value = BackupDialogs(plaintextWarning = true)
    }

    fun cancelProtect() {
        wipeHeldPassword()
        plaintextKind = null
        plaintextExportApproved = false
        plaintextSafetyApproved = false
        dialogs.value = BackupDialogs()
    }

    fun cancelPlaintextWarning() {
        plaintextKind = null
        dialogs.value = BackupDialogs()
    }

    fun confirmPlaintextWarning(activity: Activity? = null) {
        val kind = plaintextKind ?: BackupProtectKind.FILE_EXPORT
        plaintextKind = null
        wipeHeldPassword()
        when (kind) {
            BackupProtectKind.FILE_EXPORT -> {
                plaintextExportApproved = true
                dialogs.value = BackupDialogs(launchExportPicker = true)
            }
            BackupProtectKind.SAFETY_EXPORT -> {
                plaintextSafetyApproved = true
                dialogs.value = BackupDialogs(launchSafetyExportPicker = true)
            }
            BackupProtectKind.DRIVE_BACKUP -> {
                dialogs.value = BackupDialogs()
                if (activity != null) createBackup(activity)
            }
        }
    }

    /**
     * Accepts a new backup password. File export then opens the picker.
     * Drive upload starts here when [activity] is present.
     */
    fun submitProtect(password: String, confirm: String, activity: Activity? = null): Boolean {
        val reason = BackupEnvelope.validateNewPassword(password, confirm)
        if (reason != null) {
            error.value = reason
            return false
        }
        wipeHeldPassword()
        heldPassword = password.toCharArray()
        val kind = dialogs.value.protect
        plaintextKind = null
        dialogs.value = when (kind) {
            BackupProtectKind.FILE_EXPORT -> BackupDialogs(launchExportPicker = true)
            BackupProtectKind.SAFETY_EXPORT -> BackupDialogs(launchSafetyExportPicker = true)
            BackupProtectKind.DRIVE_BACKUP, null -> BackupDialogs()
        }
        if (kind == BackupProtectKind.DRIVE_BACKUP && activity != null) {
            createBackup(activity)
        }
        return true
    }

    fun onSafetyExportPickerLaunched() {
        dialogs.value = dialogs.value.copy(launchSafetyExportPicker = false)
    }

    fun onExportPickerLaunched() {
        dialogs.value = dialogs.value.copy(launchExportPicker = false)
    }

    fun dismissError() {
        error.value = null
    }

    fun createBackup(activity: Activity) {
        val password = heldPassword
        heldPassword = null
        runBackupAction("Uploading backup…") {
            try {
                val file = container.backupRepository.createBackup(
                    activity,
                    ::awaitResolution,
                    password = password,
                    iterations = envelopeIterations,
                )
                backups.value = listOf(file) + backups.value.filterNot { it.id == file.id }
                status.value = if (password != null) {
                    "Protected backup saved as ${file.name}." + unfinishedWorkoutNote()
                } else {
                    "Backup saved as ${file.name}." + unfinishedWorkoutNote()
                }
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    fun refreshBackups(activity: Activity) {
        runBackupAction("Loading backups…") {
            val listing = container.backupRepository.listBackups(activity, ::awaitResolution)
            backups.value = listing.files
            status.value = describeListing(listing)
        }
    }

    /**
     * "Found N" is a claim about the whole folder. A listing that hit its ceiling while
     * Drive still had a page to give is only the newest N, and says so (R13).
     */
    private fun describeListing(listing: DriveBackupListing): String {
        val count = listing.files.size
        val noun = if (count == 1) "backup" else "backups"
        return when {
            listing.truncated -> "Showing the newest $count $noun. Older ones exist in Drive."
            count == 0 -> "No backups in Drive yet."
            else -> "Found $count $noun."
        }
    }

    fun requestRestore(activity: Activity, file: DriveBackupFile) {
        runBackupAction("Checking backup…") {
            val raw = container.backupRepository.downloadDriveBackup(
                activity,
                file,
                ::awaitResolution,
            )
            ingestRaw(raw, file.name)
        }
    }

    fun cancelRestore() {
        pendingPlan.value = null
    }

    fun confirmRestore() {
        val plan = pendingPlan.value ?: return
        pendingPlan.value = null
        runBackupAction("Restoring backup…") {
            val result = container.backupRepository.commitRestore(plan)
            container.preferencesRepository.setLastRestore(
                plan.sourceName,
                System.currentTimeMillis(),
            )
            restorePending.value = container.backupRepository.pendingRecovery()
            reloadSafetySnapshots()
            status.value = describeRestore(result)
        }
    }

    init {
        viewModelScope.launch {
            runCatchingCancellable { container.backupRepository.recoverInterruptedRestore() }
                .onFailure { AppLog.w(TAG, "Finishing an interrupted restore failed", it) }
            runCatchingCancellable {
                restorePending.value = container.backupRepository.pendingRecovery()
            }.onFailure { AppLog.w(TAG, "Reading the restore journal failed", it) }
            refreshSafetySnapshots()
        }
    }

    /**
     * Retries the post-commit half of a restore whose preferences write failed. The Room
     * half is already in; the journal at `room` keeps the incoming copy for exactly this.
     */
    fun finishRestore() {
        runBackupAction("Finishing restore…") {
            val recovery = container.backupRepository.recoverInterruptedRestore()
            restorePending.value = container.backupRepository.pendingRecovery()
            reloadSafetySnapshots()
            status.value = describeRecovery(recovery)
        }
    }

    fun dismissRestoreNote() {
        viewModelScope.launch {
            runCatchingCancellable { container.preferencesRepository.setRestoreRecoveryNote(null) }
                .onFailure { AppLog.w(TAG, "Clearing the restore note failed", it) }
        }
    }

    fun refreshSafetySnapshots() {
        viewModelScope.launch {
            runCatchingCancellable { reloadSafetySnapshots() }
                .onFailure { AppLog.w(TAG, "Listing safety copies failed", it) }
        }
    }

    private suspend fun reloadSafetySnapshots() {
        safetySnapshots.value = container.backupRepository.listSafetySnapshots()
    }

    fun requestSafetyRestore(id: String) {
        runBackupAction("Checking safety copy…") {
            val json = container.backupRepository.readSafetySnapshot(id)
            pendingPlan.value = container.backupRepository.prepareRestore(
                json,
                sourceName = SafetySnapshotMeta.TITLE,
            )
        }
    }

    fun exportSafetyFileName(): String = BackupJson.fileName()

    fun exportSafetySnapshot(id: String, uri: Uri) {
        if (isBusy.value) {
            // runBackupAction drops its lambda while busy. Consuming the held
            // protection first and then doing nothing ate the export silently —
            // no file, no error, and the password wiped without explanation.
            wipeHeldPassword()
            plaintextSafetyApproved = false
            error.value = "Another backup task is still running. Start the export again when it finishes."
            return
        }
        val password = heldPassword
        heldPassword = null
        val plaintextApproved = plaintextSafetyApproved
        plaintextSafetyApproved = false
        runBackupAction("Saving safety copy…") {
            try {
                if (password == null && !plaintextApproved) {
                    // Process death in the file picker dropped the chosen protection.
                    // Never downgrade to plaintext on the user's behalf.
                    throw BackupException(
                        "That export lost its protection choice. Start it again.",
                    )
                }
                val json = container.backupRepository.readSafetySnapshot(id)
                val payload = withContext(container.computeDispatcher) {
                    // A safety copy is written by restore without a size check. Refuse a
                    // copy the document budget would not let back in before spending the
                    // key derivation on it: the protected file would be refused at import.
                    BackupScaleBudget.requireExportable(payload = json, protected = false)
                    // 600,000 PBKDF2 iterations plus AES-GCM over the whole history. This
                    // ran on Main — the regular protected export already went through
                    // the repository's IO dispatcher, this path called wrap() directly.
                    val built = if (password != null) {
                        BackupEnvelope.wrap(json, password, envelopeIterations)
                    } else {
                        json
                    }
                    // A safety copy is written by restore without a size check; the export
                    // of it is held to the same contract as every other export. The check
                    // is a byte count over the whole payload, so it stays off Main too.
                    BackupScaleBudget.requireExportable(payload = built, protected = password != null)
                    built
                }
                withContext(container.ioDispatcher) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri, "wt")?.use { stream ->
                        stream.write(payload.toByteArray(Charsets.UTF_8))
                        stream.flush()
                    } ?: throw BackupException("Couldn't write to that location. Pick another folder.")
                }
                status.value = if (password != null) {
                    "Protected safety copy saved. It opens with the password you chose."
                } else {
                    "Safety copy saved to your chosen file. Anyone who can read it can read your history."
                }
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    fun deleteSafetySnapshot(id: String) {
        runBackupAction("Deleting safety copy…") {
            container.backupRepository.deleteSafetySnapshot(id)
            reloadSafetySnapshots()
            status.value = "Safety copy deleted. Training data on this phone is unchanged."
        }
    }

    // ---- Local file export / import (no Google account required) ----

    /** Suggested filename for the system file picker. */
    fun exportFileName(): String = BackupJson.fileName()

    fun exportToFile(uri: Uri) {
        if (isBusy.value) {
            // Same guard as exportSafetySnapshot: never consume the protection
            // choice for a lambda runBackupAction is about to drop.
            wipeHeldPassword()
            plaintextExportApproved = false
            error.value = "Another backup task is still running. Start the export again when it finishes."
            return
        }
        val password = heldPassword
        heldPassword = null
        val plaintextApproved = plaintextExportApproved
        plaintextExportApproved = false
        runBackupAction("Saving backup file…") {
            try {
                if (password == null && !plaintextApproved) {
                    // Process death in the file picker dropped the chosen protection.
                    // Never downgrade to plaintext on the user's behalf.
                    throw BackupException(
                        "That export lost its protection choice. Start it again.",
                    )
                }
                val payload = if (password != null) {
                    container.backupRepository.exportProtected(password, envelopeIterations)
                } else {
                    container.backupRepository.exportJson()
                }
                withContext(container.ioDispatcher) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri, "wt")?.use { stream ->
                        stream.write(payload.toByteArray(Charsets.UTF_8))
                        stream.flush()
                    } ?: throw BackupException("Couldn't write to that location. Pick another folder.")
                }
                container.preferencesRepository.setLastBackup(
                    displayName(uri),
                    System.currentTimeMillis(),
                )
                status.value = if (password != null) {
                    "Protected backup saved to your chosen file. It opens with the password you chose." +
                        unfinishedWorkoutNote()
                } else {
                    "Backup saved to your chosen file. Anyone who can read it can read your history." +
                        unfinishedWorkoutNote()
                }
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    /** Importing replaces everything, so it gets the same explicit confirm as a Drive restore. */
    fun requestFileRestore(uri: Uri) {
        runBackupAction("Checking backup…") {
            val json = withContext(container.ioDispatcher) {
                val resolver = getApplication<Application>().contentResolver
                resolver.openInputStream(uri)?.use { stream ->
                    // Bounded read: a multi-hundred-MB pick must fail with copy,
                    // not OOM-kill the app. The +1 detects over-budget cleanly.
                    val bytes = stream.readAtMost(BackupScaleBudget.IMPORT_BYTES_MAX + 1)
                    if (bytes.size > BackupScaleBudget.IMPORT_BYTES_MAX) {
                        throw BackupException(BackupScaleBudget.TOO_BIG_TO_IMPORT)
                    }
                    bytes.toString(Charsets.UTF_8)
                } ?: throw BackupException("Couldn't read that file. Pick another one.")
            }
            ingestRaw(json, displayName(uri))
        }
    }

    fun unlockPending(password: String) {
        val raw = pendingCiphertext ?: return
        val name = pendingCipherName ?: return
        runBackupAction("Checking backup…") {
            // The one passphrase in this file that was never wiped. Every other CharArray here
            // is cleared in a finally; this one was handed to prepareRestore and dropped, so a
            // wrong password left the owner's real passphrase readable on the heap for as long
            // as the array survived — and a wrong password is the case that repeats.
            val secret = password.toCharArray()
            try {
                pendingPlan.value = container.backupRepository.prepareRestore(
                    raw,
                    sourceName = name,
                    password = secret,
                )
                pendingCiphertext = null
                pendingCipherName = null
                dialogs.value = BackupDialogs()
            } finally {
                secret.fill('\u0000')
            }
        }
    }

    fun cancelUnlock() {
        pendingCiphertext = null
        pendingCipherName = null
        dialogs.value = BackupDialogs()
    }

    private suspend fun ingestRaw(raw: String, sourceName: String, password: CharArray? = null) {
        if (BackupEnvelope.looksLike(raw) && password == null) {
            pendingCiphertext = raw
            pendingCipherName = sourceName
            dialogs.value = BackupDialogs(unlock = true)
            return
        }
        pendingPlan.value = container.backupRepository.prepareRestore(
            raw,
            sourceName = sourceName,
            password = password,
        )
    }

    fun cancelFileRestore() {
        pendingPlan.value = null
    }

    fun confirmFileRestore() {
        confirmRestore()
    }

    /** Backups exclude the live session on purpose; say so instead of letting the user assume. */
    private suspend fun unfinishedWorkoutNote(): String =
        if (container.backupRepository.hasUnfinishedWorkout()) {
            " Your in-progress workout was left out — back up again once you finish it."
        } else {
            ""
        }

    private fun describeRestore(result: RestoreResult): String {
        val base = "Restored ${result.sourceName} — ${result.summary.describe()}. " +
            "A verified copy of the previous data is under Safety copies on this phone."
        return when {
            result.preferencesRestored -> base
            result.settingsPending -> "$base ${RestoreJournal.SETTINGS_PENDING}"
            else -> "$base Your training data is in; settings couldn't be applied, so check units and rest defaults."
        }
    }

    private fun describeRecovery(recovery: RestoreRecovery): String = when (recovery) {
        RestoreRecovery.None -> "Nothing was left to finish."
        RestoreRecovery.NothingChanged -> RestoreJournal.NOTHING_CHANGED
        is RestoreRecovery.Finished ->
            "Finished restoring ${recovery.sourceName}. Settings are applied."
        is RestoreRecovery.SettingsPending -> RestoreJournal.SETTINGS_PENDING
        is RestoreRecovery.SettingsLost -> RestoreJournal.SETTINGS_LOST
        is RestoreRecovery.Unresolved -> RestoreJournal.UNRESOLVED
    }

    private fun displayName(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "backup file"

    fun onResolutionFinished(ok: Boolean) {
        resolutionWaiter?.complete(ok)
        resolutionWaiter = null
        _pendingResolution.value = null
    }

    private suspend fun awaitResolution(sender: IntentSender): Boolean {
        val waiter = CompletableDeferred<Boolean>()
        resolutionWaiter = waiter
        _pendingResolution.value = sender
        // Bounded: if the consent screen never returns a result — the user wandered off, or
        // the sender was never launched — this resolves to "declined" instead of pinning the
        // backup UI in a busy state forever. DriveAuthClient turns false into a normal
        // "sign-in was cancelled" error, which runBackupAction surfaces and then clears.
        return try {
            withTimeoutOrNull(RESOLUTION_TIMEOUT) { waiter.await() } ?: false
        } finally {
            _pendingResolution.value = null
            resolutionWaiter = null
        }
    }

    override fun onCleared() {
        // A waiter still parked when the ViewModel dies would keep its coroutine suspended.
        resolutionWaiter?.complete(false)
        resolutionWaiter = null
        wipeHeldPassword()
        pendingCiphertext = null
        pendingCipherName = null
        super.onCleared()
    }

    private fun wipeHeldPassword() {
        heldPassword?.fill('\u0000')
        heldPassword = null
    }

    private fun runBackupAction(label: String, block: suspend () -> Unit) {
        if (isBusy.value) return
        viewModelScope.launch {
            isBusy.value = true
            busyLabel.value = label
            error.value = null
            status.value = label
            try {
                block()
            } catch (thrown: Exception) {
                // Mapping the throwable to a sentence and dropping it is why a failed Drive
                // sign-in left no trace anywhere. AppLog.e is the level that also feeds the
                // diagnostics hook; the message is redacted, the tag and stack are not.
                AppLog.e(TAG, "Backup action failed: $label", thrown)
                error.value = (thrown as? BackupException)?.message
                    ?: thrown.message
                    ?: "Something went wrong. Try again."
                status.value = null
            } finally {
                isBusy.value = false
                busyLabel.value = null
            }
        }
    }

    private companion object {
        /** Long enough for a real consent flow, short enough that a lost one still recovers. */
        val RESOLUTION_TIMEOUT = 5.minutes
    }

    private data class BackupMeta(
        val email: String?,
        val lastAt: Long?,
        val lastName: String?,
        val restoreAt: Long?,
        val restoreName: String?,
    )

    private data class BackupFlags(
        val busy: Boolean,
        val label: String?,
        val status: String?,
        val error: String?,
        val pendingPreview: RestorePreviewUi?,
        val dialogs: BackupDialogs = BackupDialogs(),
        val recovery: RecoveryUi = RecoveryUi(),
        val autoBackupEnabled: Boolean = false,
        val autoBackupNeedsSignIn: Boolean = false,
    )

    private data class RecoveryUi(
        val pendingSource: String? = null,
        val note: String? = null,
    )

    private data class BackupDialogs(
        val protect: BackupProtectKind? = null,
        /** Arming automatic backup. A separate flag, not a fourth [BackupProtectKind]: this
         *  one seals a passphrase instead of writing a file, and must never offer plaintext. */
        val armAutoBackup: Boolean = false,
        val unlock: Boolean = false,
        val plaintextWarning: Boolean = false,
        val launchExportPicker: Boolean = false,
        val launchSafetyExportPicker: Boolean = false,
    )
}

data class RestorePreviewUi(
    val sourceName: String,
    val incoming: AuthoredInventory,
    val local: AuthoredInventory,
) {
    val body: String
        get() = AuthoredInventory.confirmBody(sourceName, incoming, local)
}

private fun RestorePlan.toPreview(): RestorePreviewUi =
    RestorePreviewUi(sourceName = sourceName, incoming = incoming, local = local)
