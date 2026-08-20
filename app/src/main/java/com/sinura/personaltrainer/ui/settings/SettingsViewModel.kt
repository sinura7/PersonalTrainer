package com.sinura.personaltrainer.ui.settings

import android.app.Activity
import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.data.backup.BackupException
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.DriveBackupFile
import com.sinura.personaltrainer.data.repository.RestoreResult
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.WeightUnit
import java.time.DayOfWeek
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes

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
    val pendingRestore: DriveBackupFile? = null,
    val pendingFileRestore: Uri? = null,
)

class SettingsViewModel(application: Application) : AppViewModel(application) {
    val weightUnit: StateFlow<WeightUnit> = container.preferencesRepository.weightUnit
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeightUnit.KG,
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

    private val isBusy = MutableStateFlow(false)
    private val busyLabel = MutableStateFlow<String?>(null)
    private val status = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val backups = MutableStateFlow<List<DriveBackupFile>>(emptyList())
    private val pendingRestore = MutableStateFlow<DriveBackupFile?>(null)
    private val pendingFileRestore = MutableStateFlow<Uri?>(null)
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
        combine(isBusy, busyLabel, status, error, pendingRestore) { busy, label, note, err, restore ->
            BackupFlags(busy, label, note, err, restore)
        },
        pendingFileRestore,
        backups,
    ) { meta, flags, fileRestore, files ->
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
            pendingRestore = flags.pendingRestore,
            pendingFileRestore = fileRestore,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BackupUiState(),
    )

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

    fun requestRestore(file: DriveBackupFile) {
        pendingRestore.value = file
        error.value = null
    }

    fun cancelRestore() {
        pendingRestore.value = null
    }

    fun confirmRestore(activity: Activity) {
        val file = pendingRestore.value ?: return
        pendingRestore.value = null
        runBackupAction("Restoring backup…") {
            val result = container.backupRepository.restoreBackup(activity, file, ::awaitResolution)
            status.value = describeRestore(result)
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
        pendingFileRestore.value = uri
        error.value = null
    }

    fun cancelFileRestore() {
        pendingFileRestore.value = null
    }

    fun confirmFileRestore(uri: Uri) {
        pendingFileRestore.value = null
        importFromFile(uri)
    }

    private fun importFromFile(uri: Uri) {
        runBackupAction("Reading backup file…") {
            val json = withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                resolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                } ?: throw BackupException("Couldn't read that file. Pick another one.")
            }
            val name = displayName(uri)
            val result = container.backupRepository.restoreFromJson(json, sourceName = name)
            container.preferencesRepository.setLastRestore(name, System.currentTimeMillis())
            status.value = describeRestore(result)
        }
    }

    /** Backups exclude the live session on purpose; say so instead of letting the user assume. */
    private suspend fun unfinishedWorkoutNote(): String =
        if (container.backupRepository.hasUnfinishedWorkout()) {
            " Your in-progress workout was left out — back up again once you finish it."
        } else {
            ""
        }

    private fun describeRestore(result: RestoreResult): String {
        val base = "Restored ${result.sourceName} — ${result.summary.describe()}."
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
        val pendingRestore: DriveBackupFile?,
    )
}
