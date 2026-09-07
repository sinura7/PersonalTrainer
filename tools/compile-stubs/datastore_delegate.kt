// androidx.datastore.preferences.preferencesDataStore — declaration-only stub.
// NOTE the package: `androidx.datastore.preferences`, NOT `...preferences.core`. The import at
// PreferencesRepository.kt:13 is exact, and getting this wrong makes the lane blame the repo
// for a stub bug. See compile-stubs/README.md.

package androidx.datastore.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlin.properties.ReadOnlyProperty

/**
 * The FIRST type argument of ReadOnlyProperty must be Context, not Any?. With
 * ReadOnlyProperty<Any?, ...> the delegate would attach to an extension property on any
 * receiver, so a refactor moving this onto a repository or a ViewModel would compile here and
 * fail in CI. The parameter is named `name` because the call site uses a named argument.
 *
 * DELIBERATELY NARROWER: the real function also takes corruptionHandler / produceMigrations /
 * scope. Modelling them would mean inventing loose placeholder types for
 * ReplaceFileCorruptionHandler and DataMigration, which is surface that can absorb a wrong
 * argument. No call site uses them.
 */
fun preferencesDataStore(
    name: String,
): ReadOnlyProperty<Context, DataStore<Preferences>> = TODO("compile-only stub")
