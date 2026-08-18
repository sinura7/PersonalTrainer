package com.sinura.personaltrainer.ui.settings

import android.app.Activity
import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.data.backup.BackupException
import com.sinura.personaltrainer.data.backup.DriveBackupFile
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.WeightUnit
import java.time.DayOfWeek
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BackupUiState(
    val accountEmail: String? = null,
    val lastBackupAt: Long? = null,
    val lastBackupName: String? = null,
    val backups: List<DriveBackupFile> = emptyList(),
    val isBusy: Boolean = false,
    val busyLabel: String? = null,
    val status: String? = null,
    val error: String? = null,
    val pendingRestore: DriveBackupFile? = null,
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
    private var resolutionWaiter: CompletableDeferred<Boolean>? = null

    val resolutionRequest = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)

    val backupState: StateFlow<BackupUiState> = combine(
        combine(
            container.preferencesRepository.driveAccountEmail,
            container.preferencesRepository.lastBackupAt,
            container.preferencesRepository.lastBackupName,
            backups,
        ) { email, lastAt, lastName, files ->
            BackupMeta(email, lastAt, lastName, files)
        },
        combine(isBusy, busyLabel, status, error, pendingRestore) { busy, label, note, err, restore ->
            BackupFlags(busy, label, note, err, restore)
        },
    ) { meta, flags ->
        BackupUiState(
            accountEmail = meta.email,
            lastBackupAt = meta.lastAt,
            lastBackupName = meta.lastName,
            backups = meta.files,
            isBusy = flags.busy,
            busyLabel = flags.label,
            status = flags.status,
            error = flags.error,
            pendingRestore = flags.pendingRestore,
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
            status.value = "Backup saved as ${file.name}."
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
            container.backupRepository.restoreBackup(activity, file, ::awaitResolution)
            status.value = "Restored ${file.name}. Home, routines, and history now match that backup."
        }
    }

    fun onResolutionFinished(ok: Boolean) {
        resolutionWaiter?.complete(ok)
        resolutionWaiter = null
    }

    private suspend fun awaitResolution(sender: IntentSender): Boolean {
        val waiter = CompletableDeferred<Boolean>()
        resolutionWaiter = waiter
        resolutionRequest.emit(sender)
        return waiter.await()
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

    private data class BackupMeta(
        val email: String?,
        val lastAt: Long?,
        val lastName: String?,
        val files: List<DriveBackupFile>,
    )

    private data class BackupFlags(
        val busy: Boolean,
        val label: String?,
        val status: String?,
        val error: String?,
        val pendingRestore: DriveBackupFile?,
    )
}
