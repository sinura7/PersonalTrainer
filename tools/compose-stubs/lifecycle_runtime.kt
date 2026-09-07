// androidx.lifecycle — the Lifecycle half, used only by Compose sources.
// Declaration-only; see compose-stubs/README.md. tools/compile-stubs/lifecycle.kt has the
// ViewModel half, which the non-Compose stage needs too; these are kept apart so the non-Compose
// stage's classpath stays exactly as narrow as it was.

package androidx.lifecycle

interface LifecycleObserver

/**
 * `Event` and `State` are the two enums the app names. Event.ON_RESUME is compared with `==`
 * against the event a LifecycleEventObserver is handed, and State.STARTED is the default
 * minActiveState of collectAsStateWithLifecycle.
 */
abstract class Lifecycle {
    enum class Event { ON_CREATE, ON_START, ON_RESUME, ON_PAUSE, ON_STOP, ON_DESTROY, ON_ANY }

    enum class State { DESTROYED, INITIALIZED, CREATED, STARTED, RESUMED }

    abstract fun addObserver(observer: LifecycleObserver)
    abstract fun removeObserver(observer: LifecycleObserver)
}

interface LifecycleOwner {
    val lifecycle: Lifecycle
}

/** `fun interface`, as the real one is, so `LifecycleEventObserver { _, event -> ... }` converts. */
fun interface LifecycleEventObserver : LifecycleObserver {
    fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event)
}
