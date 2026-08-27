package com.sinura.personaltrainer.ui.onboarding

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.DataHealth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the app should be showing before anything else is decided. */
enum class OnboardingGate {
    /** The flag has not been read yet. Show nothing — see the note in the view model. */
    UNKNOWN,
    APP,
    /** Settings could not be read. Not a first install. */
    UNAVAILABLE,
}

/**
 * Decides whether a launch can compose the app shell.
 *
 * First visit lands on Home. The questionnaire is a pushed route from the
 * get-started sheet, not a replacement for the shell. [OnboardingGate.UNKNOWN]
 * still renders nothing: DataStore is asynchronous, and flashing the settings-
 * failed empty state (or a half-built Home) on every cold start is worse than
 * a blank frame.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingGateViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val retryTick = MutableStateFlow(0)

    val gate: StateFlow<OnboardingGate> = retryTick.flatMapLatest {
        container.preferencesRepository.onboardingCompleteHealth.map(::gateFromHealth)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OnboardingGate.UNKNOWN,
        )

    fun retry() {
        retryTick.value += 1
    }
}

internal fun gateFromHealth(health: DataHealth<Boolean>): OnboardingGate = when (health) {
    is DataHealth.Available -> OnboardingGate.APP
    is DataHealth.Degraded -> OnboardingGate.APP
    is DataHealth.Unavailable -> OnboardingGate.UNAVAILABLE
}
