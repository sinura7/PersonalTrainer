// androidx.datastore.preferences.core / .preferences — declaration-only stubs.
// See compile-stubs/README.md. Nothing here executes.

package androidx.datastore.preferences.core

import androidx.datastore.core.DataStore

/**
 * Abstract with no public constructor, so nothing in the repo can build one and mask a missing
 * factory. `get` returns T? — NEVER T and never Any?. That nullability is the single most
 * load-bearing detail in this file: ~60 read sites lean on it (`?: false`, `?.let`,
 * `.orEmpty()`, `?.takeIf`), and a non-null return would turn a genuinely missing null-check
 * into a clean compile. `get` and `contains` are `operator` because every call site uses
 * bracket syntax, and generic per-call in <T> rather than on the class.
 */
abstract class Preferences protected constructor() {
    /** Key<T> is INVARIANT. An `out T` key would let get/set infer T upward to a common
     *  supertype — exactly how a `prefs[intKey] = someString` bug slips through. */
    class Key<T> internal constructor(val name: String)

    abstract operator fun <T> contains(key: Key<T>): Boolean
    abstract operator fun <T> get(key: Key<T>): T?
    abstract fun asMap(): Map<Key<*>, Any>
}

class MutablePreferences internal constructor() : Preferences() {
    override operator fun <T> contains(key: Key<T>): Boolean = TODO("compile-only stub")
    override operator fun <T> get(key: Key<T>): T? = TODO("compile-only stub")
    override fun asMap(): Map<Key<*>, Any> = TODO("compile-only stub")

    /**
     * `<T> set(key: Key<T>, value: T)` and nothing looser. set(key: Key<*>, value: Any) or a
     * non-generic overload set would make every one of the ~90 write sites in
     * PreferencesRepository.kt type-check regardless of value type — e.g.
     * `prefs[BLOCK_WEEKS] = block.startEpochDay` (a Long into an Int key) would compile.
     * `operator` because every write uses bracket-assign syntax.
     */
    operator fun <T> set(key: Key<T>, value: T) { TODO("compile-only stub") }

    /** Generic over Key<T>; `remove(key: Any)` would let a non-key be removed. */
    fun <T> remove(key: Key<T>): T = TODO("compile-only stub")

    fun clear() { TODO("compile-only stub") }
}

/**
 * Three load-bearing properties. (1) `suspend`: a non-suspend stub would let `edit { }` be
 * called from non-suspend code anywhere in the repo — the exact refactor error CI catches.
 * (2) the lambda parameter is MutablePreferences, so `prefs[K] = v` and `prefs.clear()` resolve.
 * (3) the receiver is DataStore<Preferences> exactly — DataStore<*> or a generic DataStore<T>
 * would let `edit { }` be called on an unrelated store. The transform's own `suspend` matches
 * the real API; dropping it would reject a future suspending call that Gradle accepts.
 */
suspend fun DataStore<Preferences>.edit(
    transform: suspend (MutablePreferences) -> Unit,
): Preferences = TODO("compile-only stub")

/** Called fully-qualified at PreferencesRepository.kt:72, so this package name is exact. */
fun emptyPreferences(): Preferences = TODO("compile-only stub")

// The key factories. Each returns an exactly-typed Key, never Key<*> or Key<Any>.
// stringSet must be Key<Set<String>> (not MutableSet, not Collection): the repo does
// `prefs[K] = prefs[K].orEmpty() + id`, whose right side is Set<String>.
// double must be Key<Double> and long must be Key<Long> — swapping Double/Float or Long/Int
// here would compile the repo while Gradle rejects it, or the reverse.
// float/byteArray are NOT declared: no main source uses them, and a stub that guesses at an
// unused API only widens what compiles.
fun intPreferencesKey(name: String): Preferences.Key<Int> = TODO("compile-only stub")
fun doublePreferencesKey(name: String): Preferences.Key<Double> = TODO("compile-only stub")
fun longPreferencesKey(name: String): Preferences.Key<Long> = TODO("compile-only stub")
fun booleanPreferencesKey(name: String): Preferences.Key<Boolean> = TODO("compile-only stub")
fun stringPreferencesKey(name: String): Preferences.Key<String> = TODO("compile-only stub")
fun stringSetPreferencesKey(name: String): Preferences.Key<Set<String>> = TODO("compile-only stub")
