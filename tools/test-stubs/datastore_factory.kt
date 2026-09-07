// androidx.datastore.preferences.core.PreferenceDataStoreFactory — declaration-only.
// See README.md.
//
// This is a NEW top-level declaration in a package tools/compile-stubs already stubs, not a
// change to one of that directory's classes: main sources build their store through the
// `preferencesDataStore` property delegate, and only the test source set constructs one
// directly. Keeping it here means the base and compose stages cannot resolve it, so a main
// source that started calling it would be a visible RED rather than an unnoticed widening.
//
// DELIBERATELY NARROWER: the real create() also takes corruptionHandler and migrations. Both
// need types (ReplaceFileCorruptionHandler, DataMigration) that nothing in this repo names, and
// declaring parameters no call site passes only widens what compiles. `produceFile` is LAST and
// has no default, exactly as in the real factory, so the trailing-lambda form keeps working.

package androidx.datastore.preferences.core

import androidx.datastore.core.DataStore
import java.io.File
import kotlinx.coroutines.CoroutineScope

object PreferenceDataStoreFactory {
    fun create(
        scope: CoroutineScope = TODO("compile-only stub"),
        produceFile: () -> File,
    ): DataStore<Preferences> = TODO("compile-only stub")
}
