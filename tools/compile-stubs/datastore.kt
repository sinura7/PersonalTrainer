// androidx.datastore — declaration-only stubs for tools/compile-check.sh.
// See compile-stubs/README.md for the general rule. Nothing here executes.
//
// WHY THIS FILE MATTERS MOST. data/repository/PreferencesRepository.kt does ~90 typed writes
// and ~60 typed reads through Preferences.Key<T>. Those seven `*PreferencesKey` factory
// functions ARE the type system for that file: if any of them returned Key<*> or Key<Any>,
// then `prefs[BOOLEAN_KEY] = "yes"` and `val x: Long = prefs[INT_KEY]` would both compile and
// the lane would report GREEN on precisely the mistakes it exists to find. Every generic here
// is invariant and every return type is exact, on purpose.

package androidx.datastore.core

import kotlinx.coroutines.flow.Flow

/**
 * T is INVARIANT, exactly as the real interface declares it. Writing `out T` here would make
 * DataStore<MutablePreferences> assignable where DataStore<Preferences> is required.
 *
 * Both `suspend`s on updateData are required: the function suspends and so does its transform.
 * No extra members are declared — anything invented here becomes overridable and could absorb
 * a typo'd override in a test double.
 */
interface DataStore<T> {
    val data: Flow<T>
    suspend fun updateData(transform: suspend (t: T) -> T): T
}
