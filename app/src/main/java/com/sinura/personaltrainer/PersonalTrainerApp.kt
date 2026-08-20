package com.sinura.personaltrainer

import android.app.Application
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
        container = AppContainer(this)
        // Created up front (not lazily on first rest) so the channels exist for the user to
        // configure, and so the legacy sounding "rest complete" channel is deleted even if
        // no timer runs this session.
        RestTimerNotifications.ensureChannels(this)
        // A rest can outlive its process. Recover it before any screen asks for timer state.
        container.restTimerController.rehydrate()
        applicationScope.launch {
            try {
                container.exerciseRepository.seedDefaultsIfEmpty()
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
