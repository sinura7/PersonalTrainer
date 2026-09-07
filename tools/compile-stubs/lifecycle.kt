// androidx.lifecycle — declaration-only stubs for tools/compile-check.sh.
// See compile-stubs/README.md. Nothing here executes.
//
// WHY STUBBED RATHER THAN DOWNLOADED: JetBrains publishes org.jetbrains.androidx.lifecycle to
// Maven Central, but the JVM/desktop artifacts are metadata-only placeholders — verified:
// lifecycle-viewmodel-desktop-2.11.0.jar contains no .class files at all, and no other version
// of that artifact is published. The real androidx.lifecycle only lives on Google's Maven,
// which this environment cannot reach. So these four declarations are hand-written.
//
// The surface is small and every ViewModel in the app depends on it: 21 files import
// viewModelScope and 14 import SavedStateHandle.

package androidx.lifecycle

import android.app.Application
import kotlinx.coroutines.CoroutineScope

abstract class ViewModel {
    protected open fun onCleared() {}
}

/** `getApplication` is generic with an upper bound of Application, exactly as the real one is,
 *  so `getApplication<Application>().contentResolver` resolves and a bogus type argument does
 *  not. The constructor parameter is non-null Application: passing null must not compile. */
open class AndroidViewModel(application: Application) : ViewModel() {
    open fun <T : Application> getApplication(): T = TODO("compile-only stub")
}

/** An extension VAL on ViewModel, as in lifecycle-viewmodel-ktx — not a member, so it cannot
 *  be overridden, and not on Any, so `viewModelScope` outside a ViewModel stays unresolved. */
val ViewModel.viewModelScope: CoroutineScope
    get() = TODO("compile-only stub")

/**
 * SavedStateHandle is key-by-String, so it cannot type-check key/value correspondence — that
 * is true of the real class too and is not a weakness introduced here. What IS load-bearing:
 *
 *  * `get` returns T?. Every read in the repo relies on it (`?: incomingId`, `.orEmpty()`,
 *    `?.let { ... }`); a non-null T would hide a missing null-check.
 *  * `set` takes T? and is `operator`, because the repo both writes `handle[KEY] = value` with
 *    a nullable value and calls `handle.set(KEY, value)` directly.
 *  * `contains` returns Boolean and takes a String key.
 *
 *  * `remove` is generic and returns T?, as the real one does: the repo calls
 *    `handle.remove<ArrayList<Bundle>>(KEY)` with an explicit type argument and discards the result.
 *
 * DELIBERATELY NARROWER: getStateFlow / getLiveData / keys / setSavedStateProvider are not
 * declared, because no main source uses them. A new use is a visible RED, not a silent pass.
 */
class SavedStateHandle {
    /** Both of the real constructors. The Map one is what app/src/test uses to seed a
     *  ViewModel's arguments (`SavedStateHandle(mapOf("mode" to "mixed"))`); its value type is
     *  Any?, exactly as androidx declares it, so a null seed value stays legal. */
    constructor()
    constructor(initialState: Map<String, Any?>)

    operator fun <T> get(key: String): T? = TODO("compile-only stub")
    operator fun <T> set(key: String, value: T?) { TODO("compile-only stub") }
    fun <T> remove(key: String): T? = TODO("compile-only stub")
    fun contains(key: String): Boolean = TODO("compile-only stub")
}
