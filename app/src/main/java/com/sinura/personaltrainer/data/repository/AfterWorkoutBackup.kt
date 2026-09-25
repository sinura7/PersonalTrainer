package com.sinura.personaltrainer.data.repository

import android.app.Activity
import android.content.IntentSender
import androidx.annotation.VisibleForTesting
import com.sinura.personaltrainer.data.repository.prefs.BackupPrefs
import com.sinura.personaltrainer.data.security.BackupPassphraseSealer
import com.sinura.personaltrainer.domain.AutoBackupPolicy
import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "PT/AfterWorkoutBackup"

/** One Drive backup with the owner's password, declining any consent sheet through [launchResolution]. */
fun interface AfterWorkoutUpload {
    suspend fun upload(
        activity: Activity,
        launchResolution: suspend (IntentSender) -> Boolean,
        password: CharArray,
    )
}

/**
 * The copy to Drive after a finished workout, owned by the app rather than by the summary.
 *
 * It ran on the summary's `viewModelScope`, so Done or Back a few seconds after finishing
 * cleared the ViewModel and cancelled the upload at its next suspension: nothing was
 * uploaded, nothing was recorded, and the cancellation was deliberately silent (audit BK-4).
 * Here it runs on [scope], which lives as long as the process; the summary starts it and
 * only watches [status].
 *
 * Three properties it keeps from the summary:
 *
 * 1. **It never shows a consent sheet.** The resolver answers false, so a lapsed Google grant
 *    can never hijack the moment after a workout with a Google dialog. It becomes a line on
 *    the summary and a note in Settings instead.
 * 2. **It cannot upload twice.** One run per session at a time, and the session it covered is
 *    persisted, so a recreated summary or process death does not upload the same one again.
 * 3. **It cannot break the summary.** Its own catch; the summary reads only [status].
 *
 * [start] holds the Activity only until the upload returns: Google's authorization needs it
 * first, and nothing after. A process killed mid-upload loses this copy, not the workout,
 * and the next finished workout's copy carries it, since every backup is the whole history.
 */
class AfterWorkoutBackup(
    private val prefs: BackupPrefs,
    private val sealer: BackupPassphraseSealer,
    private val scope: CoroutineScope,
    private val upload: AfterWorkoutUpload,
) {
    private val lines = MutableStateFlow<Map<String, String>>(emptyMap())
    private val running = HashSet<String>()

    /** The one quiet line about [sessionId]'s copy, or null when there is nothing to say. */
    fun status(sessionId: String): Flow<String?> = lines.map { it[sessionId] }.distinctUntilChanged()

    /**
     * Copies [sessionId] to Drive if the owner armed that and nothing is in the way. A second
     * call for the same session while its copy runs does nothing: the screen calls in from a
     * `LaunchedEffect`, which a rotation or a theme change re-runs.
     */
    fun start(sessionId: String, activity: Activity) {
        synchronized(running) { if (!running.add(sessionId)) return }
        // invokeOnCompletion, not a finally: a run cancelled before it is dispatched never
        // enters its body, and its id would then block this session for the rest of the process.
        scope.launch(crashGuard) { run(sessionId, activity) }.invokeOnCompletion { cause ->
            synchronized(running) { running.remove(sessionId) }
            if (cause is CancellationException) lines.update { it - sessionId }
        }
    }

    /**
     * This scope outlives every screen, so a throw outside [run]'s catch (a settings read or
     * write that fails) must not reach the default handler and take the app down on whatever
     * screen the owner has moved on to. The application scope in `PersonalTrainerApp`
     * does the same.
     */
    private val crashGuard = CoroutineExceptionHandler { _, thrown ->
        AppLog.e(TAG, "Automatic backup after a finished workout stopped", thrown)
    }

    private suspend fun run(sessionId: String, activity: Activity) {
        val settings = prefs.autoBackupSettings()
        val sealed = settings.sealedPassphrase
        val armed = AutoBackupPolicy.shouldBackUp(
            enabled = settings.enabled,
            hasStoredPassphrase = sealed != null,
            lastBackedUpSessionId = settings.lastBackedUpSessionId,
            sessionId = sessionId,
        )
        if (!armed || sealed == null) return
        if (settings.driveAccount == null) {
            // Armed with no Drive account: a sign-out on a build that left automatic backup on
            // (audit BK-1). Signing out now turns it off; finish that here rather than let the
            // copy authorize again behind the owner's back.
            AppLog.w(TAG, "Automatic backup armed with no Drive account; disarming")
            prefs.disarmAutoBackup()
            return
        }

        val passphrase = sealer.open(sealed)
        if (passphrase == null) {
            // A reinstall or a cleared Keystore. Disarm rather than half-run: the next
            // Settings visit shows the toggle off, which is the truth.
            AppLog.e(TAG, "Sealed backup passphrase would not open; disarming auto-backup")
            prefs.disarmAutoBackup()
            return
        }

        // Set by the resolver, which is the structural signal that Google wanted consent —
        // more robust than matching the copy of the exception that follows.
        var consentWanted = false
        val declineConsent: suspend (IntentSender) -> Boolean = {
            consentWanted = true
            false
        }

        say(sessionId, AutoBackupPolicy.RUNNING)
        try {
            upload.upload(activity, declineConsent, passphrase)
            prefs.setAutoBackupLastSession(sessionId)
            prefs.setAutoBackupNeedsSignIn(false)
            say(sessionId, AutoBackupPolicy.DONE)
        } catch (thrown: CancellationException) {
            // Only the process's own scope can cancel this now; there is no one left to tell.
            throw thrown
        } catch (thrown: Exception) {
            AppLog.e(TAG, "Automatic backup after a finished workout failed", thrown)
            try {
                prefs.setAutoBackupNeedsSignIn(consentWanted)
            } finally {
                // Said even if that write throws (to [crashGuard]): a failure never leaves
                // "Backing up…" on screen.
                say(sessionId, if (consentWanted) AutoBackupPolicy.NEEDS_SIGN_IN else AutoBackupPolicy.FAILED)
            }
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /**
     * Stops any copy in flight. Drive sign-out and switching automatic backup off both call
     * this first: a copy that outlives them would write the account, the last-backup lines
     * and its session back over what they just cleared, with a password the owner turned off.
     */
    fun cancelRunning() {
        scope.coroutineContext[Job]?.children?.forEach { it.cancel() }
    }

    /** Waits for every copy in flight to end; tests only, after [cancelRunning] or a release. */
    @VisibleForTesting
    internal suspend fun joinRunningForTest() {
        scope.coroutineContext[Job]?.children?.toList()?.joinAll()
    }

    private fun say(sessionId: String, line: String) {
        lines.update { it + (sessionId to line) }
    }
}
