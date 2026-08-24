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
import com.sinura.personaltrainer.data.backup.BackupException
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.DriveBackupFile
import com.sinura.personaltrainer.data.backup.SafetySnapshotMeta
import com.sinura.personaltrainer.data.repository.RestorePlan
import com.sinura.personaltrainer.data.repository.RestoreResult
import com.sinura.personaltrainer.domain.BackupPrompt
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.TrainingEmphasis
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.timer.exactAlarmSettingsIntent as buildExactAlarmSettingsIntent
import com.sinura.personaltrainer.util.runCatchingCancellable
import java.time.DayOfWeek
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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
)

private const val TAG = "PT/SettingsVM"

class SettingsViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    val weightUnit: StateFlow<WeightUnit> = container.preferencesRepository.weightUnit
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeightUnit.LBS,
        )

    val schedulePreferences: StateFlow<SchedulePreferences> =
        container.preferencesRepository.schedulePreferences
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SchedulePreferences.DEFAULT,
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
     * "no filtering", so turning the last one back on and off again lands back where it started
     * rather than silencing every suggestion.
     */
    fun toggleEquipment(equipment: EquipmentType) {
        viewModelScope.launch {
            val current = container.preferencesRepository.coachPreferences.first().availableEquipment
            val next = if (equipment.name in current) current - equipment.name else current + equipment.name
            container.preferencesRepository.setAvailableEquipment(next)
        }
    }

    private val isBusy = MutableStateFlow(false)
    private val busyLabel = MutableStateFlow<String?>(null)
    private val status = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val backups = MutableStateFlow<List<DriveBackupFile>>(emptyList())
    private val safetySnapshots = MutableStateFlow<List<SafetySnapshotMeta>>(emptyList())
    private val pendingPlan = MutableStateFlow<RestorePlan?>(null)
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
        combine(isBusy, busyLabel, status, error, pendingPlan) { busy, label, note, err, plan ->
            BackupFlags(busy, label, note, err, plan?.toPreview())
        },
        backups,
        container.workoutRepository.observeInProgress(),
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
            sessionLive = live != null,
            backupStale = BackupPrompt.isStale(meta.lastAt, System.currentTimeMillis()),
            safetySnapshots = snaps,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BackupUiState(),
    )

    /**
     * Sends the lifter back through the guided setup.
     *
     * Clearing the flag is the whole mechanism — the gate in the nav host observes it, so the
     * setup screen replaces the app on the next frame with no navigation involved. Nothing
     * else is touched: their routines, schedule and history are all still there when they come
     * out the other side. Back on the fork restores the flag so the gate returns to APP.
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

    fun setWeekStart(day: DayOfWeek) {
        viewModelScope.launch {
            container.preferencesRepository.setWeekStart(day)
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

    fun createBackup(activity: Activity) {
        runBackupAction("Uploading backup…") {
            val file = container.backupRepository.createBackup(activity, ::awaitResolution)
            backups.value = listOf(file) + backups.value.filterNot { it.id == file.id }
            status.value = "Backup saved as ${file.name}." + unfinishedWorkoutNote()
        }
    }

    fun refreshBackups(activity: Activity) {
        runBackupAction("Loading backups…") {
            backups.value = container.backupRepository.listBackups(activity, ::awaitResolution)
            status.value = if (backups.value.isEmpty()) {
                "No backups in Drive yet."
            } else {
                "Found ${backups.value.size} backup${if (backups.value.size == 1) "" else "s"}."
            }
        }
    }

    fun requestRestore(activity: Activity, file: DriveBackupFile) {
        runBackupAction("Checking backup…") {
            pendingPlan.value = container.backupRepository.prepareDriveRestore(
                activity,
                file,
                ::awaitResolution,
            )
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
            reloadSafetySnapshots()
            status.value = describeRestore(result)
        }
    }

    init {
        viewModelScope.launch {
            runCatchingCancellable { container.backupRepository.recoverInterruptedRestore() }
                .onFailure { AppLog.w(TAG, "Finishing an interrupted restore failed", it) }
            refreshSafetySnapshots()
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
        runBackupAction("Saving safety copy…") {
            val json = container.backupRepository.readSafetySnapshot(id)
            withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                resolver.openOutputStream(uri, "wt")?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                    stream.flush()
                } ?: throw BackupException("Couldn't write to that location. Pick another folder.")
            }
            status.value = "Safety copy saved to your chosen file."
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
        runBackupAction("Saving backup file…") {
            val json = container.backupRepository.exportJson()
            withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                resolver.openOutputStream(uri, "wt")?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                    stream.flush()
                } ?: throw BackupException("Couldn't write to that location. Pick another folder.")
            }
            container.preferencesRepository.setLastBackup(
                displayName(uri),
                System.currentTimeMillis(),
            )
            status.value = "Backup saved to your chosen file. It restores without signing in." +
                unfinishedWorkoutNote()
        }
    }

    /** Importing replaces everything, so it gets the same explicit confirm as a Drive restore. */
    fun requestFileRestore(uri: Uri) {
        runBackupAction("Checking backup…") {
            val json = withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                resolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                } ?: throw BackupException("Couldn't read that file. Pick another one.")
            }
            pendingPlan.value = container.backupRepository.prepareRestore(
                json,
                sourceName = displayName(uri),
            )
        }
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
        return if (result.preferencesRestored) {
            base
        } else {
            "$base Your training data is in; settings couldn't be applied, so check units and rest defaults."
        }
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
        super.onCleared()
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
