package com.sinura.personaltrainer

import android.app.Application
import com.sinura.personaltrainer.data.local.PreMigrationSnapshot
import com.sinura.personaltrainer.diagnostics.DiagnosticRedaction
import com.sinura.personaltrainer.diagnostics.DiagnosticRing
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
            } catch (error: Exception) {
                AppLog.w(TAG, "Marking a reminder delivery started failed", error)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // FIRST, before anything can open the database: AppContainer's constructor builds the
        // Room instance and Room migrates on open, so a copy taken any later is a copy of the
        // already-migrated file — and that copy is the only rollback path the v2 migration has.
        PreMigrationSnapshot.ensure(this)
        installDiagnosticCapture()
        container = AppContainer(this)
        // Created up front (not lazily on first rest) so the channels exist for the user to
        // configure, and so the legacy sounding "rest complete" channel is deleted even if
        // no timer runs this session.
        RestTimerNotifications.ensureChannels(this)
        ReminderNotifications.ensureChannel(this)
        // Two 768x768 webp decodes plus a per-pixel pass each — 50-150 ms of
        // main-thread work only the Body tab needs. The drawing code already
        // handles the cache being empty until this lands.
        applicationScope.launch { TemperStillCache.bind(resources) }
        // A rest can outlive its process. Recover it before any screen asks for timer state.
        container.restTimerController.rehydrate()
        applicationScope.launch {
            try {
                container.backupRepository.recoverInterruptedRestore()
            } catch (error: Exception) {
                AppLog.e(TAG, "Finishing an interrupted restore failed", error)
            }
            try {
                container.preferencesRepository.importEncodedHistoryIfNeeded()
            } catch (error: Exception) {
                AppLog.e(TAG, "Importing encoded bodyweight and blocks failed", error)
            }
            try {
                PendingOccurrence.restore(container)
                container.plannerRepository.importSlotsIfNeeded()
                val weekStart = container.preferencesRepository.schedulePreferences
                    .first().weekStart
                val today = com.sinura.personaltrainer.util.JvmTime.captureNow()
                val todayDate = com.sinura.personaltrainer.util.JvmTime.civilDate(
                    today.instantMillis,
                    today.zoneId,
                )
                container.plannerRepository.ensureWeek(todayDate.previousOrSame(weekStart))
            } catch (error: Exception) {
                AppLog.e(TAG, "Importing schedule rules failed", error)
            }
            try {
                container.dbMaintenance.seedCatalog()
            } catch (error: Exception) {
                // The catalog is a convenience; the app is fully usable without it.
                AppLog.e(TAG, "Seeding the default exercise catalog failed", error)
            }
        }
    }

    private fun installDiagnosticCapture() {
        AppLog.onError = { tag, error -> recordDiagnostic(tag, error) }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            recordDiagnostic("PT/Uncaught", error)
            previous?.uncaughtException(thread, error)
        }
    }

    private fun recordDiagnostic(tag: String, error: Throwable) {
        DiagnosticRing.shared.record(
            DiagnosticRedaction.fromThrowable(
                error = error,
                tag = tag,
                nowMs = System.currentTimeMillis(),
                id = java.util.UUID.randomUUID().toString(),
            ),
        )
    }

    private companion object {
        const val TAG = "PT/App"
    }
}
