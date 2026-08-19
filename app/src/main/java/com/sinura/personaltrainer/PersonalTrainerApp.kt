package com.sinura.personaltrainer

import android.app.Application
import com.sinura.personaltrainer.timer.RestTimerNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PersonalTrainerApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            container.exerciseRepository.seedDefaultsIfEmpty()
        }
    }
}
