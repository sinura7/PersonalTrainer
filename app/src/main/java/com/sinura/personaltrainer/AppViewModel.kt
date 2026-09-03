package com.sinura.personaltrainer

import android.app.Application
import androidx.lifecycle.AndroidViewModel

import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.domain.todayEpochDay as domainTodayEpochDay

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
) : AndroidViewModel(application) {
    protected val time: TimePort get() = container.time

    protected fun todayEpochDay(): Long =
        domainTodayEpochDay(nowMs = time.nowMillis(), time = time)

    protected fun civilToday(): CivilDate = time.civilDate(time.nowMillis())
}
