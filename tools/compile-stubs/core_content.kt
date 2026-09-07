// androidx.core.content.ContextCompat — declaration-only stub. See compile-stubs/README.md.
// Only checkSelfPermission is declared; it returns Int (a PackageManager grant constant), which
// is what the repo compares against PackageManager.PERMISSION_GRANTED from the real android jar.

package androidx.core.content

import android.content.Context

object ContextCompat {
    @JvmStatic
    fun checkSelfPermission(context: Context, permission: String): Int = TODO("compile-only stub")
}
