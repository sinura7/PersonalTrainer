package com.sinura.personaltrainer

import android.app.Application
import com.sinura.personaltrainer.data.local.PreMigrationSnapshot
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.timer.RestTimerNotifications
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    override fun onCreate() {
        super.onCreate()
        // FIRST, before anything can open the database: AppContainer's constructor builds the
        // Room instance and Room migrates on open, so a copy taken any later is a copy of the
        // already-migrated file — and that copy is the only rollback path the v2 migration has.
        PreMigrationSnapshot.ensure(this)
        container = AppContainer(this)
        // Created up front (not lazily on first rest) so the channels exist for the user to
        // configure, and so the legacy sounding "rest complete" channel is deleted even if
        // no timer runs this session.
        RestTimerNotifications.ensureChannels(this)
        // A rest can outlive its process. Recover it before any screen asks for timer state.
        container.restTimerController.rehydrate()
        applicationScope.launch {
            try {
                container.backupRepository.recoverInterruptedRestore()
            } catch (error: Exception) {
                AppLog.e(TAG, "Finishing an interrupted restore failed", error)
            }
            try {
                container.dbMaintenance.seedCatalog()
            } catch (error: Exception) {
                // The catalog is a convenience; the app is fully usable without it.
                AppLog.e(TAG, "Seeding the default exercise catalog failed", error)
            }
        }
    }

    private companion object {
        const val TAG = "PT/App"
    }
}
