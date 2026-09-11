package com.sinura.personaltrainer.data.repository

import android.app.Activity
import android.content.IntentSender
import com.sinura.personaltrainer.data.backup.AuthoredInventory
import com.sinura.personaltrainer.data.backup.BackupDocument
import com.sinura.personaltrainer.data.backup.BackupEnvelope
import com.sinura.personaltrainer.data.backup.OpenBackup
import com.sinura.personaltrainer.data.backup.ProtectBackup
import com.sinura.personaltrainer.data.backup.BackupException
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.BackupScaleBudget
import com.sinura.personaltrainer.data.backup.BackupSummary
import com.sinura.personaltrainer.data.backup.BackupValidation
import com.sinura.personaltrainer.data.backup.BackupValidator
import com.sinura.personaltrainer.data.backup.RestoreJournal
import com.sinura.personaltrainer.data.backup.RestoreJournalRecord
import com.sinura.personaltrainer.data.backup.RestoreJournalStore
import com.sinura.personaltrainer.data.backup.RestoreRecovery
import com.sinura.personaltrainer.data.backup.RestoreWitness
import com.sinura.personaltrainer.data.backup.SafetySnapshotMeta
import com.sinura.personaltrainer.data.backup.DriveAuthClient
import com.sinura.personaltrainer.data.backup.DriveBackupFile
import com.sinura.personaltrainer.data.backup.DriveBackupListing
import com.sinura.personaltrainer.data.backup.DriveRestClient
import com.sinura.personaltrainer.data.backup.DriveSession
import com.sinura.personaltrainer.data.backup.NetworkChecker
import com.sinura.personaltrainer.domain.DataHealthCopy
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backup and restore, orchestrated across Drive, the local snapshot, and the restore journal.
 *
 * Called `BackupRepository` until this packet, which it never was. A repository owns a
 * collection and answers questions about it; this signs in to Google, uploads a file, reads it
 * back to verify it, writes a safety copy, replaces every row in Room, applies preferences,
 * and keeps a journal so a crash halfway through can be finished later. Nine constructor
 * parameters and seven collaborators, because that is what the sequence takes.
 *
 * The name mattered: it is the reason this sat in `data/repository/` next to eighteen classes
 * that really are repositories, and the reason a reader looking for the backup *sequence*
 * had no obvious place to look.
 */
class BackupService(
    private val localBackupRepository: LocalBackupRepository,
    private val preferencesRepository: PreferencesRepository,
    private val dbMaintenance: DbMaintenance,
    private val driveAuthClient: DriveAuthClient,
    private val driveRestClient: DriveRestClient,
    private val networkChecker: NetworkChecker,
    private val restoreJournal: RestoreJournalStore,
    /** Nullable so tests without a planner skip the reminder rebuild. */
    private val plannerRepository: PlannerRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Main-safe. Authorization itself is Task-based and thread-agnostic, but
     * [rememberAuthorizedSession] then reads Drive About over the network, and Settings
     * called this straight from viewModelScope — a NetworkOnMainThreadException that the
     * broad runCatching around that read turned into the "Google Drive" fallback label.
     * The consent sheet is launched through [launchResolution], which hands the
     * IntentSender to state the screen collects on Main; nothing here touches a view.
     */
    suspend fun signIn(
        activity: Activity,
        launchResolution: suspend (IntentSender) -> Boolean,
    ): DriveSession = withContext(ioDispatcher) {
        networkChecker.requireOnline()
        rememberAuthorizedSession(activity, launchResolution)
    }

    suspend fun signOut(activity: Activity) {
        driveAuthClient.signOut(activity)
        preferencesRepository.clearDriveSession()
    }

    suspend fun createBackup(
        activity: Activity,
        launchResolution: suspend (IntentSender) -> Boolean,
        password: CharArray? = null,
        iterations: Int = BackupEnvelope.DEFAULT_ITERATIONS,
    ): DriveBackupFile = withContext(ioDispatcher) {
        networkChecker.requireOnline()
        val session = rememberAuthorizedSession(activity, launchResolution)
        val snapshot = localBackupRepository.createSnapshot()
        val json = BackupJson.encode(snapshot)
        val payload = if (password != null) {
            ProtectBackup()(plaintext = json, password = password, iterations = iterations)
        } else {
            BackupScaleBudget.requireExportable(payload = json, protected = false)
            json
        }
        val fileName = BackupJson.fileName()
        val folderId = driveRestClient.ensureBackupFolder(
            accessToken = session.accessToken,
            knownFolderId = preferencesRepository.driveFolderId(),
        )
        preferencesRepository.setDriveFolderId(folderId)
        val uploaded = driveRestClient.uploadBackup(
            accessToken = session.accessToken,
            folderId = folderId,
            fileName = fileName,
            json = payload,
        )
        val writtenAt = System.currentTimeMillis()
        preferencesRepository.setLastBackup(uploaded.name, writtenAt)
        // An upload that returned 200 proves Drive accepted bytes. It does not prove those
        // bytes come back, decrypt with the password we hold, or contain this history. Until
        // one is read back, a backup is a hypothesis. This is the only place that can check
        // cheaply, while the password is still in hand.
        if (verifyUploadedBackup(session, uploaded, password, expected = AuthoredInventory.fromDocument(snapshot))) {
            preferencesRepository.setLastVerifiedBackup(uploaded.name, writtenAt)
        }
        uploaded
    }

    /**
     * Reads the file back out of Drive and proves it is the history that just went up.
     *
     * Round-trip fidelity, deliberately not restore-safety: [BackupValidator] refuses a
     * document with no authored rows, which is correct before a restore and wrong here — a
     * genuinely empty history backs up to a genuinely empty file, and that is not a fault.
     * What matters is that the bytes Drive returns decrypt, decode, and carry the same counts
     * that were serialised.
     *
     * Never throws. A failed verification is not a failed backup: the file is uploaded and may
     * well be fine, so the upload stands and only the verified stamp is withheld. Settings then
     * shows a backup that has not been proven readable, which is the honest state.
     */
    private suspend fun verifyUploadedBackup(
        session: DriveSession,
        uploaded: DriveBackupFile,
        password: CharArray?,
        expected: AuthoredInventory,
    ): Boolean = try {
        val raw = driveRestClient.downloadBackup(session.accessToken, uploaded.id)
        val plaintext = OpenBackup()(raw = raw, password = password)
        val actual = AuthoredInventory.fromDocument(BackupJson.decode(plaintext))
        val same = actual == expected
        if (!same) {
            AppLog.e(TAG, "Backup read back with different counts than were written")
        }
        same
    } catch (thrown: CancellationException) {
        throw thrown
    } catch (thrown: Exception) {
        AppLog.e(TAG, "Backup could not be read back and verified", thrown)
        false
    }

    suspend fun listBackups(
        activity: Activity,
        launchResolution: suspend (IntentSender) -> Boolean,
    ): DriveBackupListing = withContext(ioDispatcher) {
        networkChecker.requireOnline()
        val session = rememberAuthorizedSession(activity, launchResolution)
        val folderId = driveRestClient.ensureBackupFolder(
            accessToken = session.accessToken,
            knownFolderId = preferencesRepository.driveFolderId(),
        )
        preferencesRepository.setDriveFolderId(folderId)
        driveRestClient.listBackups(session.accessToken, folderId)
    }

    suspend fun downloadDriveBackup(
        activity: Activity,
        file: DriveBackupFile,
        launchResolution: suspend (IntentSender) -> Boolean,
    ): String = withContext(ioDispatcher) {
        networkChecker.requireOnline()
        val session = rememberAuthorizedSession(activity, launchResolution)
        driveRestClient.downloadBackup(session.accessToken, file.id)
    }

    /**
     * Serialises the current database for a local plaintext export.
     *
     * Refuses, before anything is written, a document the app's own import would refuse:
     * the plaintext budget is the one every import enforces after it opens the file.
     */
    suspend fun exportJson(): String = withContext(ioDispatcher) {
        val json = BackupJson.encode(localBackupRepository.createSnapshot())
        BackupScaleBudget.requireExportable(payload = json, protected = false)
        json
    }

    /**
     * The document as a protected envelope, checked against the raw-file budget the
     * bounded import reads use — base64 makes it about a third larger than the document.
     */
    suspend fun exportProtected(
        password: CharArray,
        iterations: Int = BackupEnvelope.DEFAULT_ITERATIONS,
    ): String = withContext(ioDispatcher) {
        ProtectBackup()(
            plaintext = exportJson(),
            password = password,
            iterations = iterations,
        )
    }

    suspend fun authoredInventory(): AuthoredInventory = withContext(ioDispatcher) {
        localBackupRepository.authoredInventory()
    }

    /**
     * True when a workout is in progress. Snapshots deliberately exclude unfinished sessions,
     * so the UI can say so rather than letting the user assume today's session is in the file.
     */
    suspend fun hasUnfinishedWorkout(): Boolean =
        try {
            localBackupRepository.inProgressSessionId() != null
        } catch (thrown: CancellationException) {
            throw thrown
        } catch (thrown: Exception) {
            throw BackupException(DataHealthCopy.RESTORE_UNAVAILABLE)
        }

    /**
     * Decode, validate, and compare authored counts. Does not write.
     *
     * The confirm dialog reads [RestorePlan.incoming] and [RestorePlan.local].
     * First tap must land here, not in [commitRestore].
     */
    suspend fun prepareRestore(
        json: String,
        sourceName: String,
        allowEmptyDestructiveRestore: Boolean = false,
        password: CharArray? = null,
    ): RestorePlan = withContext(ioDispatcher) {
        refuseIfLive()
        val plaintext = OpenBackup()(raw = json, password = password)
        BackupScaleBudget.requireDocumentFits(plaintext)
        val document = BackupJson.decode(plaintext)
        val local = localBackupRepository.authoredInventory()
        val summary = validateOrThrow(document, local, allowEmptyDestructiveRestore)
        RestorePlan(
            sourceName = sourceName,
            document = document,
            incoming = AuthoredInventory.fromDocument(document),
            local = local,
            summary = summary,
        )
    }

    /**
     * Writes the already-prepared document. Re-checks the live-session refuse.
     *
     * The phases and what each one owes are written down in
     * docs/archive/handoffs/HANDOFF-2026-09-06.md §2. In short: stage → wiping → one Room transaction →
     * room → preferences and history tables → prefs → catalog reconcile and reminders →
     * done → delete the two journal files. Room's transaction is the only atomic step;
     * everything after it is idempotent and is finished by [recoverInterruptedRestore]
     * on the next launch if this process dies first.
     */
    suspend fun commitRestore(plan: RestorePlan): RestoreResult = withContext(ioDispatcher) {
        dbMaintenance.withMaintenanceLock {
            recoverInterruptedRestoreLocked()
            refuseIfLive()
            val current = localBackupRepository.createSnapshot()
            val snapshot = localBackupRepository.writeVerifiedSafetySnapshot(current)
            val incomingJson = BackupJson.encode(plan.document)
            val record = RestoreJournalRecord(
                phase = RestoreJournal.STAGED,
                sourceName = plan.sourceName,
                snapshotId = snapshot.id,
                beforeFingerprint = localBackupRepository.roomFingerprint(),
                afterFingerprint = RestoreJournal.fingerprint(plan.document),
                // The deciding evidence for a crash inside the wipe. Both come from the
                // same projection; the phone's own snapshot is the one that was just
                // verified as the safety copy.
                beforeWitness = RestoreWitness.of(current),
                afterWitness = RestoreWitness.of(plan.document),
            )
            restoreJournal.stage(record, incomingJson)
            try {
                // Its own guard, outside the commit path below. A disk failure here throws
                // before anything on the phone has been touched, and the catch at the bottom
                // then read phase STAGED, fell through to "a restore was interrupted, Temper
                // is finishing it from the copy already on this phone" — about a restore that
                // never started — and left the STAGED journal open, which refuses every
                // workout start until the next launch runs recovery.
                try {
                    restoreJournal.mark(RestoreJournal.WIPING)
                } catch (thrown: CancellationException) {
                    throw thrown
                } catch (_: Exception) {
                    restoreJournal.clear()
                    throw BackupException(RestoreJournal.NOTHING_STARTED)
                }
                try {
                    localBackupRepository.replaceRoom(plan.document)
                } catch (thrown: CancellationException) {
                    throw thrown
                } catch (thrown: Exception) {
                    // The replace is one Room transaction: a throw here means it rolled
                    // back and the phone is unchanged. Close the journal (nothing to
                    // recover, and an open one blocks every workout start) and say the
                    // truth instead of RECOVERED_MIXED's "was replaced".
                    restoreJournal.clear()
                    throw BackupException(
                        (thrown as? BackupException)?.message ?: RestoreJournal.NOTHING_CHANGED,
                    )
                }
                restoreJournal.mark(RestoreJournal.ROOM)
                val completion = finishFromRoom(
                    record.copy(phase = RestoreJournal.ROOM),
                    plan.document,
                )
                RestoreResult(
                    sourceName = plan.sourceName,
                    summary = plan.summary,
                    incoming = plan.incoming,
                    local = plan.local,
                    preferencesRestored = completion == Completion.Done,
                    settingsPending = completion == Completion.SettingsPending,
                    safetySnapshotId = snapshot.id,
                )
            } catch (thrown: CancellationException) {
                throw thrown
            } catch (thrown: BackupException) {
                throw namedCommitFailure(thrown)
            } catch (thrown: Exception) {
                throw namedCommitFailure(thrown)
            }
        }
    }

    /**
     * Finish a restore that died after Room committed. Safe to call on every
     * process start. Holds the same lock as start and restore.
     */
    suspend fun recoverInterruptedRestore(): RestoreRecovery = withContext(ioDispatcher) {
        dbMaintenance.withMaintenanceLock { recoverInterruptedRestoreLocked() }
    }

    /** True while any journal is open, whatever phase it is in. */
    fun restoreInProgress(): Boolean = restoreJournal.isOpen()

    /**
     * True only while a journal is in a phase that is about to replace Room. From `room`
     * on, the training data is final and a workout may begin; recovery's remaining work
     * runs under the maintenance lock a start also takes. See [RestoreJournal.blocksStart].
     */
    fun restoreBlocksStart(): Boolean = RestoreJournal.blocksStart(restoreJournal.read()?.phase)

    /** The source name of a restore whose post-commit work is still owed, or null. */
    fun pendingRecovery(): String? =
        restoreJournal.read()?.takeIf { RestoreJournal.awaitsFinish(it.phase) }?.sourceName

    private suspend fun recoverInterruptedRestoreLocked(): RestoreRecovery {
        val record = restoreJournal.read() ?: return RestoreRecovery.None
        if (record.phase == RestoreJournal.DONE) {
            // Everything required is already on the phone; only the deletes were cut short.
            restoreJournal.clear()
            return RestoreRecovery.Finished(record.sourceName)
        }
        return when (record.phase) {
            RestoreJournal.STAGED -> {
                restoreJournal.clear()
                RestoreRecovery.NothingChanged
            }
            RestoreJournal.WIPING -> recoverWiping(record)
            RestoreJournal.ROOM, RestoreJournal.PREFS -> finish(record)
            else -> {
                restoreJournal.clear()
                RestoreRecovery.NothingChanged
            }
        }
    }

    private suspend fun recoverWiping(record: RestoreJournalRecord): RestoreRecovery =
        when (decideWiping(record)) {
            Wipe.ROLLED_BACK -> {
                restoreJournal.clear()
                RestoreRecovery.NothingChanged
            }
            Wipe.COMMITTED -> {
                restoreJournal.mark(RestoreJournal.ROOM)
                finish(record.copy(phase = RestoreJournal.ROOM))
            }
            Wipe.UNRESOLVED -> {
                // Neither witness matched. Applying the incoming preferences over a
                // database that might be the original is the exact harm the witness
                // exists to prevent, so nothing is applied; the journal is closed so
                // the phone can train; the note tells the owner where the safety copy is.
                restoreJournal.clear()
                preferencesRepository.setRestoreRecoveryNote(RestoreJournal.UNRESOLVED)
                AppLog.w(TAG, "Restore recovery could not prove which database the crash left; nothing applied")
                RestoreRecovery.Unresolved(record.sourceName, record.snapshotId)
            }
        }

    private enum class Wipe { COMMITTED, ROLLED_BACK, UNRESOLVED }

    /**
     * Which side of the Room transaction the crash landed on.
     *
     * With current witnesses on the journal the answer is a content comparison. Identical
     * before and after witnesses mean the same tables either way, so finishing is right
     * regardless. A journal from a build without witnesses only carries counts, and is
     * resolved on the old rule.
     */
    private suspend fun decideWiping(record: RestoreJournalRecord): Wipe {
        val before = record.beforeWitness
        val after = record.afterWitness
        if (!RestoreWitness.isCurrent(before) || !RestoreWitness.isCurrent(after)) {
            return if (localBackupRepository.roomFingerprint() == record.afterFingerprint) {
                Wipe.COMMITTED
            } else {
                Wipe.ROLLED_BACK
            }
        }
        if (before == after) return Wipe.COMMITTED
        return when (localBackupRepository.roomWitness()) {
            after -> Wipe.COMMITTED
            before -> Wipe.ROLLED_BACK
            else -> Wipe.UNRESOLVED
        }
    }

    private suspend fun finish(record: RestoreJournalRecord): RestoreRecovery {
        val document = if (record.phase == RestoreJournal.ROOM) readIncomingDocument() else null
        return when (finishFromRoom(record, document)) {
            Completion.Done -> RestoreRecovery.Finished(record.sourceName)
            Completion.SettingsPending -> RestoreRecovery.SettingsPending(record.sourceName)
            Completion.SettingsLost -> RestoreRecovery.SettingsLost(record.sourceName)
        }
    }

    private fun readIncomingDocument(): BackupDocument? {
        val raw = restoreJournal.readIncomingOrNull() ?: return null
        return try {
            BackupJson.decode(raw)
        } catch (thrown: BackupException) {
            AppLog.w(TAG, "The journal's incoming copy could not be decoded", thrown)
            null
        }
    }

    private enum class Completion { Done, SettingsPending, SettingsLost }

    /**
     * Everything owed once Room holds the incoming tables. [record] must be at `room` or
     * `prefs`; the phase says what is still owed.
     *
     * Preferences are required: a failure keeps the journal at `room` and the incoming
     * copy on disk, and the caller reports settings pending. The retry is idempotent —
     * `setRestoredPreferences` writes every key unconditionally and replaces the history
     * tables wholesale — so this can run on every launch until it succeeds. Cancellation
     * is never caught here. The catalog reconcile is idempotent too, and `seedCatalog`
     * would run it anyway because the wipe set the catalog version to 0. Reminders are
     * best effort. `done` is marked before the deletes so a crash between them is a
     * journal the next pass simply sweeps.
     */
    private suspend fun finishFromRoom(
        record: RestoreJournalRecord,
        document: BackupDocument?,
    ): Completion {
        if (record.phase == RestoreJournal.ROOM) {
            if (document == null) {
                // Only a journal written by the old cleanup order, or an externally deleted
                // file, gets here: Room is restored and the settings half can never be.
                dbMaintenance.reconcileCatalogLocked()
                rebuildRestoredReminders()
                restoreJournal.mark(RestoreJournal.DONE)
                restoreJournal.clear()
                preferencesRepository.setRestoreRecoveryNote(RestoreJournal.SETTINGS_LOST)
                AppLog.w(TAG, "Restore finished without its settings: the incoming copy was gone")
                return Completion.SettingsLost
            }
            if (!localBackupRepository.applyPreferences(document)) {
                return Completion.SettingsPending
            }
            restoreJournal.mark(RestoreJournal.PREFS)
        }
        dbMaintenance.reconcileCatalogLocked()
        rebuildRestoredReminders()
        restoreJournal.mark(RestoreJournal.DONE)
        restoreJournal.clear()
        return Completion.Done
    }

    /**
     * Restored PENDING deliveries exist only as rows until something hands
     * them to the scheduler; without this they silently did not fire until
     * the next reboot or launch. Best-effort — a scheduling failure must not
     * fail a restore that already committed.
     */
    private suspend fun rebuildRestoredReminders() {
        try {
            plannerRepository?.rebuildReminders()
        } catch (thrown: CancellationException) {
            throw thrown
        } catch (_: Exception) {
            // The boot/time-change receiver rebuilds again later.
        }
    }

    /**
     * What to tell the owner about a restore that threw.
     *
     * The phase says how far it got, and only the phases past the wipe have changed anything.
     * STAGED and a closed journal have not: the honest answer there is that nothing was
     * changed, and it must not be allowed to inherit [RestoreJournal.INTERRUPTED] from a
     * journal-write failure underneath — "Temper is finishing it from the copy already on this
     * phone" describes a recovery that is not going to happen, about data that was never
     * touched.
     */
    private fun namedCommitFailure(thrown: Throwable): BackupException {
        val phase = restoreJournal.read()?.phase
        // A STAGED journal left open refuses every workout start until the next launch runs
        // recovery, and there is nothing in it to recover — close it here rather than making
        // the owner relaunch to lift a block they should never have hit.
        if (phase == RestoreJournal.STAGED) runCatching { restoreJournal.clear() }
        val message = RestoreJournal.commitFailureMessage(
            phase = phase,
            reported = (thrown as? BackupException)?.message ?: thrown.message,
        )
        return if (thrown is BackupException && thrown.message == message) {
            thrown
        } else {
            BackupException(message)
        }
    }

    suspend fun listSafetySnapshots(): List<SafetySnapshotMeta> = withContext(ioDispatcher) {
        localBackupRepository.listSafetySnapshots()
    }

    suspend fun readSafetySnapshot(id: String): String = withContext(ioDispatcher) {
        localBackupRepository.readSafetySnapshot(id)
    }

    suspend fun deleteSafetySnapshot(id: String) = withContext(ioDispatcher) {
        localBackupRepository.deleteSafetySnapshot(id)
    }

    /**
     * The single validated restore path. Drive downloads and local file imports both land
     * here, so neither can skip a check the other performs.
     *
     * Order: prepare (no write) then commit. Tests that need a one-shot call still use this.
     */
    suspend fun restoreFromJson(
        json: String,
        sourceName: String,
        allowEmptyDestructiveRestore: Boolean = false,
        password: CharArray? = null,
    ): RestoreResult = commitRestore(
        prepareRestore(json, sourceName, allowEmptyDestructiveRestore, password),
    )

    private suspend fun refuseIfLive() {
        val liveId = try {
            localBackupRepository.inProgressSessionId()
        } catch (thrown: CancellationException) {
            throw thrown
        } catch (thrown: Exception) {
            throw BackupException(DataHealthCopy.RESTORE_UNAVAILABLE)
        }
        if (liveId != null) {
            throw BackupException(
                "You have a session in progress. Finish or discard it before restoring, " +
                    "so a restore can't delete the session you're standing in.",
            )
        }
    }

    private suspend fun validateOrThrow(
        document: BackupDocument,
        local: AuthoredInventory,
        allowEmptyDestructiveRestore: Boolean,
    ): BackupSummary {
        val validation = BackupValidator.validate(
            document = document,
            localAuthored = local,
            allowEmptyDestructiveRestore = allowEmptyDestructiveRestore,
        )
        return when (validation) {
            is BackupValidation.Valid -> validation.summary
            is BackupValidation.Invalid -> throw BackupException(validation.reason)
        }
    }

    /**
     * AuthorizationClient does not return an account email. Drive About
     * does, still under `drive.file`. A failed About read must not undo
     * a successful token; Settings treats a non-null email as signed in.
     */
    private suspend fun rememberAuthorizedSession(
        activity: Activity,
        launchResolution: suspend (IntentSender) -> Boolean,
    ): DriveSession {
        val session = driveAuthClient.authorize(activity, launchResolution)
        // The lookup names the account; it does not authorise anything. A failure keeps the
        // sign-in and falls back to the label, but is logged so a 401 or a dead network is
        // distinguishable from success in the diagnostics — and cancellation propagates
        // instead of being read as "no email".
        val looked = runCatchingCancellable { driveRestClient.fetchAccountEmail(session.accessToken) }
            .onFailure { thrown -> AppLog.w(TAG, "Drive account lookup failed; using the label", thrown) }
            .getOrNull()
            ?.ifBlank { null }
            ?: session.email
        // A different account at the chooser used to be written straight over the old one.
        // Under drive.file this app can only see files it created, so the previous account's
        // backups become invisible from inside Temper and the next upload silently starts a
        // second "PersonalTrainer Backups" folder somewhere else. Nothing is deleted, but the
        // history stops being where the app looks. Refuse instead, and name both accounts.
        //
        // Only a real address may disagree: [DRIVE_LABEL] is what a failed About read falls
        // back to, and comparing it would refuse on a dead network rather than a wrong account.
        val stored = preferencesRepository.driveAccountEmailOnce()
        if (looked != null && stored != null && stored != DRIVE_LABEL &&
            !looked.equals(stored, ignoreCase = true)
        ) {
            AppLog.e(TAG, "Drive account changed since the last backup")
            throw BackupException(
                "This is a different Google account. Temper backed up to $stored, and can only " +
                    "see backups it made there. Sign in as $stored, or sign out first if you " +
                    "meant to switch.",
            )
        }
        val named = session.copy(email = looked ?: DRIVE_LABEL)
        preferencesRepository.setDriveAccountEmail(named.email)
        return named
    }

    private companion object {
        const val TAG = "PT/BackupService"

        /** Shown when Drive About could not be read. Never compared as an account. */
        const val DRIVE_LABEL = "Google Drive"
    }
}

data class RestorePlan(
    val sourceName: String,
    val document: BackupDocument,
    val incoming: AuthoredInventory,
    val local: AuthoredInventory,
    val summary: BackupSummary,
) {
    val confirmBody: String
        get() = AuthoredInventory.confirmBody(sourceName, incoming, local)
}

data class RestoreResult(
    val sourceName: String,
    val summary: BackupSummary,
    val preferencesRestored: Boolean,
    val safetySnapshotId: String?,
    val incoming: AuthoredInventory = AuthoredInventory.EMPTY,
    val local: AuthoredInventory = AuthoredInventory.EMPTY,
    /**
     * Room is restored but the preferences write failed; the journal stays open at `room`
     * with the incoming copy so the next launch, or Finish restore in Settings, retries.
     * Distinct from [preferencesRestored] being false with a closed journal.
     */
    val settingsPending: Boolean = false,
)
