// androidx.test.* — declaration-only stubs for the TEST stage of tools/compile-check.sh.
// Nothing here executes. See tools/test-stubs/README.md.
//
// androidx.test:core and androidx.test:monitor are Google-only artifacts with no Maven Central
// mirror, and they supply exactly two declarations app/src/test uses: ApplicationProvider
// (194 call sites) and InstrumentationRegistry (5). Both are stubbed at the signature the real
// artifacts declare.

package androidx.test.core.app

/**
 * The real signature is `public static <T extends Context> T getApplicationContext()`, i.e. the
 * return type is inferred from the call site or given explicitly. The repo uses BOTH shapes —
 * `val context: Context = ApplicationProvider.getApplicationContext()` and
 * `ApplicationProvider.getApplicationContext<Application>()` — and only a generic method with an
 * upper bound of Context accepts both. The bound matters: it keeps
 * `ApplicationProvider.getApplicationContext<String>()` from compiling, as the real one does.
 */
object ApplicationProvider {
    @JvmStatic
    fun <T : android.content.Context> getApplicationContext(): T = TODO("compile-only stub")
}
