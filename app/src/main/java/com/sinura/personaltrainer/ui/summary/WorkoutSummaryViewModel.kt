package com.sinura.personaltrainer.ui.summary

import android.app.Activity
import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.AutoBackupPolicy
import com.sinura.personaltrainer.domain.WorkoutSummary
import com.sinura.personaltrainer.domain.WorkoutSummaryBuilder
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PT/WorkoutSummaryVM"

data class WorkoutSummaryUiState(
    val isLoading: Boolean = true,
    /** The session is gone — discarded, or restored over between finishing and arriving here. */
    val missing: Boolean = false,
    val summary: WorkoutSummary = WorkoutSummary(),
    /** One quiet line about the unattended Drive copy, or null when there is nothing to say. */
    val autoBackup: String? = null,
)

/**
 * Reads once, not as a live flow.
 *
 * A finished session does not change while its summary is on screen, and the record check
 * behind it walks each lift's whole history — re-running that on every unrelated database
 * write would be pure waste.
 */
class WorkoutSummaryViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()

    private val _uiState = MutableStateFlow(WorkoutSummaryUiState())
    val uiState: StateFlow<WorkoutSummaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatchingCancellable {
                val session = container.workoutRepository.getSession(sessionId)
                if (session == null) {
                    _uiState.value = WorkoutSummaryUiState(isLoading = false, missing = true)
                    return@runCatchingCancellable
                }
                val exerciseIds = session.sets.filterNot { it.isWarmup }.map { it.exerciseId }
                val prior = container.workoutRepository.historyBefore(sessionId, exerciseIds)
                val summary = withContext(container.computeDispatcher) {
                    WorkoutSummaryBuilder.build(session, prior)
                }
                _uiState.value = WorkoutSummaryUiState(isLoading = false, summary = summary)
            }.onFailure { thrown ->
                AppLog.w(TAG, "Building the workout summary failed", thrown)
                // Never strand the user on a spinner because a summary would not compute: the
                // workout is saved either way, and this screen is a celebration, not a gate.
                _uiState.value = WorkoutSummaryUiState(isLoading = false, missing = true)
            }
        }
    }

    /**
     * Copies this finished workout to Drive, if the owner armed that and nothing is in the
     * way. Called once from the screen, which is the only place an Activity is reachable.
     *
     * Three properties this deliberately has:
     *
     * 1. **It never shows a consent sheet.** [launchResolution] answers false, so a lapsed
     *    Google grant can never hijack the moment after a workout with a Google dialog. It
     *    becomes a line here and a note in Settings instead.
     * 2. **It cannot upload twice.** The session it covered is persisted, not remembered, so
     *    process death rebuilding this screen with the same session does not re-upload.
     * 3. **It cannot break the summary.** It runs in its own coroutine with its own catch and
     *    touches only [WorkoutSummaryUiState.autoBackup] — never isLoading or missing. A Drive
     *    failure must not render "Nothing to summarise" over a workout that happened.
     *
     * It runs after the finish is committed, which is the only correct moment: the snapshot
     * behind a backup keeps finished sessions only, so a copy taken any earlier would omit
     * the very workout being celebrated.
     */
    fun maybeAutoBackup(activity: Activity) {
        viewModelScope.launch {
            val settings = container.preferencesRepository.autoBackupSettings()
            val sealed = settings.sealedPassphrase
            val armed = AutoBackupPolicy.shouldBackUp(
                enabled = settings.enabled,
                hasStoredPassphrase = sealed != null,
                lastBackedUpSessionId = settings.lastBackedUpSessionId,
                sessionId = sessionId,
            )
            if (!armed || sealed == null) return@launch

            val passphrase = container.backupPassphraseSealer.open(sealed)
            if (passphrase == null) {
                // A reinstall or a cleared Keystore. Disarm rather than half-run: the next
                // Settings visit shows the toggle off, which is the truth.
                AppLog.e(TAG, "Sealed backup passphrase would not open; disarming auto-backup")
                container.preferencesRepository.disarmAutoBackup()
                return@launch
            }

            // Set by the resolver below, which is the structural signal that Google wanted
            // consent — more robust than matching the copy of the exception that follows.
            var consentWanted = false
            val declineConsent: suspend (IntentSender) -> Boolean = {
                consentWanted = true
                false
            }

            _uiState.value = _uiState.value.copy(autoBackup = AutoBackupPolicy.RUNNING)
            try {
                container.backupRepository.createBackup(
                    activity = activity,
                    launchResolution = declineConsent,
                    password = passphrase,
                )
                container.preferencesRepository.setAutoBackupLastSession(sessionId)
                container.preferencesRepository.setAutoBackupNeedsSignIn(false)
                _uiState.value = _uiState.value.copy(autoBackup = AutoBackupPolicy.DONE)
            } catch (thrown: CancellationException) {
                // Leaving the summary mid-upload. Not a failure, and not something to caption.
                throw thrown
            } catch (thrown: Exception) {
                AppLog.e(TAG, "Automatic backup after a finished workout failed", thrown)
                container.preferencesRepository.setAutoBackupNeedsSignIn(consentWanted)
                _uiState.value = _uiState.value.copy(
                    autoBackup = if (consentWanted) {
                        AutoBackupPolicy.NEEDS_SIGN_IN
                    } else {
                        AutoBackupPolicy.FAILED
                    },
                )
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }
}
