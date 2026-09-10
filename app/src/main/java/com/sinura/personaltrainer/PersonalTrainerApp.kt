package com.sinura.personaltrainer

import android.app.Application
import com.sinura.personaltrainer.data.local.PreMigrationSnapshot
import com.sinura.personaltrainer.diagnostics.DiagnosticMetadata
import com.sinura.personaltrainer.diagnostics.DiagnosticRedaction
import com.sinura.personaltrainer.diagnostics.DiagnosticRing
import com.sinura.personaltrainer.diagnostics.LastCrashStore
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.reminder.ReminderNotifications
import com.sinura.personaltrainer.timer.RestTimerNotifications
import com.sinura.personaltrainer.ui.components.TemperStillCache
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

class PersonalTrainerApp : Application() {
    // Without the handler, a single SQLite failure inside seeding reached the default
    // uncaught-exception handler and took the whole process down on launch — every launch,
    // because the seed runs unconditionally.
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, error -> AppLog.e(TAG, "Background work failed", error) },
    )

    lateinit var container: AppContainer
        private set

    /**
     * Generates the current week's occurrences if today crossed into a week
     * that has none yet. A resume with no new (rule, date) rows writes
     * nothing and skips the reminder pass.
     */
    fun ensureCurrentWeek() {
        applicationScope.launch {
            try {
                ensureCurrentWeekBlocking()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.w(TAG, "Ensuring the current week failed", error)
            }
        }
    }

    private suspend fun ensureCurrentWeekBlocking() {
        val weekStart = container.preferencesRepository.schedulePreferences
            .first().weekStart
        val today = com.sinura.personaltrainer.util.JvmTime.captureNow()
        val todayDate = com.sinura.personaltrainer.util.JvmTime.civilDate(
            today.instantMillis,
            today.zoneId,
        )
        container.plannerRepository.ensureWeek(todayDate.previousOrSame(weekStart))
    }

    /**
     * Marks a reminder delivery STARTED on the app scope, so the write
     * survives the activity that consumed the notification tap.
     */
    fun markReminderStarted(deliveryId: String) {
        applicationScope.launch {
            try {
                container.plannerRepository.markDeliveryStatus(
                    deliveryId,
                    com.sinura.personaltrainer.domain.ReminderDeliveryStatus.STARTED,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.w(TAG, "Marking a reminder delivery started failed", error)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Before anything that can log: the snapshot and container construction below
        // (database open, migrations) are exactly the paths whose messages carry
        // user-authored titles and internal file paths. Temper Debug is the daily
        // install, so this is always on — not a release-only switch.
        AppLog.redactMessages = true
        // FIRST among the heavy steps, before anything can open the database: AppContainer's
        // constructor builds the Room instance and Room migrates on open, so a copy taken any
        // later is a copy of the already-migrated file — and that copy is the only rollback
        // path the v2 migration has.
        PreMigrationSnapshot.ensure(this)
        installDiagnosticCapture()
        container = AppContainer(this)
        // Created up front (not lazily on first rest) so the channels exist for the user to
        // configure, and so the legacy sounding "rest complete" channel is deleted even if
        // no timer runs this session.
        RestTimerNotifications.ensureChannels(this)
        ReminderNotifications.ensureChannel(this)
        // Two 1024 unlit stills plus a per-pixel pit punch — Body-only work
        // that used to be two 768 decodes. The drawing code already handles
        // the cache being empty until this lands.
        applicationScope.launch { TemperStillCache.bind(resources) }
        // A rest can outlive its process. Recover it before any screen asks for timer state.
        container.restTimerController.rehydrate()
        applicationScope.launch {
            try {
                container.backupRepository.recoverInterruptedRestore()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.e(TAG, "Finishing an interrupted restore failed", error)
            }
            try {
                container.preferencesRepository.importEncodedHistoryIfNeeded()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.e(TAG, "Importing encoded bodyweight and blocks failed", error)
            }
            try {
                PendingOccurrence.restore(container)
                container.plannerRepository.importSlotsIfNeeded()
                ensureCurrentWeekBlocking()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.e(TAG, "Importing schedule rules failed", error)
            }
            try {
                container.dbMaintenance.seedCatalog()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // The catalog is a convenience; the app is fully usable without it.
                AppLog.e(TAG, "Seeding the default exercise catalog failed", error)
            }
        }
    }

    private fun installDiagnosticCapture() {
        val crashStore = DiagnosticMetadata.crashStore(this)
        // The ring dies with its process, so the fatal event of the last run — the one the
        // owner most wants in a bundle — comes back from disk first, before anything in this
        // process can record over it. Original time, new kind; the file stays until the next
        // crash replaces it or Settings clears it.
        runCatching {
            crashStore.load()?.let { crash ->
                DiagnosticRing.shared.record(crash.copy(kind = LastCrashStore.PREVIOUS_CRASH_KIND))
            }
        }
        AppLog.onError = { tag, error -> recordDiagnostic(tag, error) }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val captured = AtomicBoolean(false)
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // First crash wins and nothing re-enters. A second uncaught exception — another
            // thread failing during teardown, or a failure inside this block itself — must
            // neither overwrite the file nor record again; it goes straight to the handler
            // that was installed before ours, which is always called.
            if (captured.compareAndSet(false, true)) {
                runCatching {
                    val event = redacted(tag = "PT/Uncaught", error = error)
                    DiagnosticRing.shared.record(event)
                    crashStore.save(event)
                }
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun recordDiagnostic(tag: String, error: Throwable) {
        DiagnosticRing.shared.record(redacted(tag = tag, error = error))
    }

    private fun redacted(tag: String, error: Throwable) = DiagnosticRedaction.fromThrowable(
        error = error,
        tag = tag,
        nowMs = System.currentTimeMillis(),
        id = java.util.UUID.randomUUID().toString(),
    )

    private companion object {
        const val TAG = "PT/App"
    }
}
