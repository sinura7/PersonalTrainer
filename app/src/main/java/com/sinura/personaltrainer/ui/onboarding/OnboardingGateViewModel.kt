package com.sinura.personaltrainer.ui.onboarding

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the app should be showing before anything else is decided. */
enum class OnboardingGate {
    /** The flag has not been read yet. Show nothing — see the note in the view model. */
    UNKNOWN,
    SETUP,
    APP,
}

/**
 * Decides whether a launch goes to setup or to the app.
 *
 * [OnboardingGate.UNKNOWN] is a real state and the host must render nothing for it. DataStore
 * reads are asynchronous, so defaulting to APP would show Home for a frame or two on every
 * cold start and then replace it — which on a first install is a flash of exactly the empty,
 * planless screen this whole phase exists to stop anyone seeing. Defaulting to SETUP would be
 * worse still, flashing a questionnaire at everybody who already finished it.
 */
class OnboardingGateViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    val gate: StateFlow<OnboardingGate> = container.preferencesRepository.onboardingComplete
        .map { complete -> if (complete) OnboardingGate.APP else OnboardingGate.SETUP }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OnboardingGate.UNKNOWN,
        )
}
