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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PT/WorkoutSummaryVM"

/**
 * Four answers, each tied to what the read established.
 *
 * [missing] is a read that succeeded and found no row: the session is not on this phone.
 * [failed] is a read or a computation that threw: the session may well be there. They used to
 * be one flag, and the screen said "Workout saved. It is in your history." for both — which
 * for a missing row was untrue, and for a Room fault was a guess dressed as a receipt.
 *
 * [savedConfirmed] is the evidence for the word "saved": the finished row was read back from
 * Room during this load. It is true alongside [failed] when the row came back but the
 * history read or the summary build threw, which is the one case where "saved, summary
 * unavailable" is both honest and useful.
 */
data class WorkoutSummaryUiState(
    val isLoading: Boolean = true,
    val sessionId: String = "",
    val missing: Boolean = false,
    val failed: Boolean = false,
    val savedConfirmed: Boolean = false,
    val summary: WorkoutSummary = WorkoutSummary(),
    /** One quiet line about the unattended Drive copy, or null when there is nothing to say. */
    val autoBackup: String? = null,
)

/**
 * Reads once, not as a live flow.
 *
 * A finished session does not change while its summary is on screen, and the record check
 * behind it walks each lift's whole history — re-running that on every unrelated database
 * write would be pure waste. [retry] re-runs the same read; nothing here ever writes, so a
 * retry can never finish a workout twice or create one.
 */
class WorkoutSummaryViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()

    private val _uiState = MutableStateFlow(WorkoutSummaryUiState(sessionId = sessionId))

    /** The automatic backup in flight, so a recreated screen cannot start a second one. */
    private var autoBackupJob: Job? = null
    val uiState: StateFlow<WorkoutSummaryUiState> = _uiState.asStateFlow()

    private var loading: Job? = null

    init {
        load()
    }

    /** Re-read after a failed load. A no-op unless the last read actually threw. */
    fun retry() {
        if (!_uiState.value.failed) return
        load()
    }

    /**
     * Every outcome of a read is written as a whole state, so no flag from the previous
     * answer can survive into the next one — that is what keeps missing, failed and
     * "saved, summary unavailable" three distinct states rather than a smear of leftovers.
     *
     * The one field carried across is [WorkoutSummaryUiState.autoBackup]. It does not belong
     * to the read at all: [maybeAutoBackup] owns it from its own coroutine, and a Retry of
     * the summary must not silently erase what the Drive copy last reported.
     */
    private fun settle(
        isLoading: Boolean = false,
        missing: Boolean = false,
        failed: Boolean = false,
        savedConfirmed: Boolean = false,
        summary: WorkoutSummary = WorkoutSummary(),
    ) {
        _uiState.value = WorkoutSummaryUiState(
            isLoading = isLoading,
            sessionId = sessionId,
            missing = missing,
            failed = failed,
            savedConfirmed = savedConfirmed,
            summary = summary,
            autoBackup = _uiState.value.autoBackup,
        )
    }

    private fun load() {
        loading?.cancel()
        settle(isLoading = true)
        loading = viewModelScope.launch {
            // Blank means the route was reached with no id at all. There is nothing to read
            // and so nothing to claim; the honest answer is the same as a row that is not there.
            if (sessionId.isBlank()) {
                settle(missing = true)
                return@launch
            }
            val session = runCatchingCancellable { container.workoutRepository.getSession(sessionId) }
                .getOrElse { thrown ->
                    AppLog.w(TAG, "Reading the finished session failed", thrown)
                    settle(failed = true, savedConfirmed = false)
                    return@launch
                }
            if (session == null) {
                settle(missing = true)
                return@launch
            }
            // The row is in hand: from here on "saved" is a fact, whatever the summary does.
            val saved = session.isFinished
            runCatchingCancellable {
                val exerciseIds = session.sets.filterNot { it.isWarmup }.map { it.exerciseId }
                val prior = container.workoutRepository.historyBefore(sessionId, exerciseIds)
                withContext(container.computeDispatcher) {
                    WorkoutSummaryBuilder.build(session, prior)
                }
            }.onSuccess { summary ->
                settle(savedConfirmed = saved, summary = summary)
            }.onFailure { thrown ->
                AppLog.w(TAG, "Building the workout summary failed", thrown)
                // Never strand the user on a spinner because a summary would not compute — but
                // never call the row missing either. It was just read.
                settle(failed = true, savedConfirmed = saved)
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
        // The persisted guard below closes only when the upload FINISHES, so for the whole of
        // a long upload it is still open. This screen calls in from `LaunchedEffect(Unit)`, and
        // MainActivity declares no `configChanges`, so a rotation — or a dark/light switch, or
        // a font-size change — destroys the composition and re-runs the effect while this view
        // model survives on its nav entry. That started a SECOND concurrent snapshot, encrypt
        // and upload of the same session.
        //
        // Skipping, not restarting: `activity` is wanted only by `rememberAuthorizedSession`
        // at the top of the upload, so a run already past that point does not need the new
        // one, and cancelling a live upload to start again could leave a half-written file in
        // Drive.
        if (autoBackupJob?.isActive == true) return
        autoBackupJob = viewModelScope.launch {
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
                container.backupService.createBackup(
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
