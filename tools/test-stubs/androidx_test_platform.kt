// androidx.test.platform.app.InstrumentationRegistry — declaration-only. See README.md.
//
// getInstrumentation() returns the REAL android.app.Instrumentation from the Robolectric
// android-all jar, so what the migration tests hand to MigrationTestHelper is type-checked
// against Android's actual class rather than an opaque placeholder.

package androidx.test.platform.app

import android.app.Instrumentation

object InstrumentationRegistry {
    @JvmStatic
    fun getInstrumentation(): Instrumentation = TODO("compile-only stub")
}
