package com.sinura.personaltrainer

import android.app.Application
import androidx.lifecycle.AndroidViewModel

fun Application.appContainer(): AppContainer = (this as PersonalTrainerApp).container

/**
 * Shared ViewModel base. Dependencies are constructor parameters, not looked up from
 * [Application] inside the class — that lookup is what made every screen untestable.
 *
 * Production still uses the default `viewModel()` factory: subclasses keep an
 * `Application`-only constructor (via `@JvmOverloads`) that resolves [container] from
 * [appContainer]. Tests call the full constructor and pass a fake [AppDependencies].
 */
abstract class AppViewModel(
    application: Application,
    protected val container: AppDependencies,
) : AndroidViewModel(application)
