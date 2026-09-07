// androidx.core.content.res.ResourcesCompat — declaration-only. See README.md.
//
// Used by exactly one test (ui/components/TabularNumeralTest.kt) to load a bundled font by
// resource id. Returns Typeface? — nullable, as the real API is: the test writes
// `?: Typeface.DEFAULT`, and a non-null return would let that null-check be dropped silently.
//
// No main source uses ResourcesCompat, so this stays in the test stubs; a main source that
// started using it would be a visible RED.

package androidx.core.content.res

import android.content.Context
import android.graphics.Typeface

object ResourcesCompat {
    @JvmStatic
    fun getFont(context: Context, id: Int): Typeface? = TODO("compile-only stub")
}
