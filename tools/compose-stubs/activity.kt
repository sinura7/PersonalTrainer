// androidx.activity — declaration-only. See compose-stubs/README.md.
//
// activity / activity-compose are Google-only artifacts with no Maven Central mirror.
//
// ComponentActivity extends android.app.Activity from the REAL android jar, so every override in
// MainActivity and RestLockActivity (onCreate(Bundle?), onNewIntent(Intent), onResume,
// onDestroy) is checked against Android's actual signatures — including the non-null `Intent`
// parameter of onNewIntent, which is what the real ComponentActivity narrows it to. The real
// class also implements LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner and
// ActivityResultCaller; those are omitted because no source in this app uses the activity as
// one, so a new such use is a visible RED.

package androidx.activity

import androidx.annotation.ColorInt

open class ComponentActivity : android.app.Activity()

/**
 * `dark`, `light` and `auto` are the three factories. Each takes @ColorInt scrims, and `light`
 * requires BOTH scrims — passing one is an error in the real API and must stay one here.
 */
class SystemBarStyle private constructor() {
    companion object {
        @JvmStatic
        fun dark(@ColorInt scrim: Int): SystemBarStyle = TODO("compile-only stub")

        @JvmStatic
        fun light(@ColorInt scrim: Int, @ColorInt darkScrim: Int): SystemBarStyle =
            TODO("compile-only stub")

        @JvmStatic
        fun auto(
            @ColorInt lightScrim: Int,
            @ColorInt darkScrim: Int,
            detectDarkMode: (android.content.res.Resources) -> Boolean = { true },
        ): SystemBarStyle = TODO("compile-only stub")
    }
}

fun ComponentActivity.enableEdgeToEdge(
    statusBarStyle: SystemBarStyle = TODO("compile-only stub"),
    navigationBarStyle: SystemBarStyle = TODO("compile-only stub"),
) {
    TODO("compile-only stub")
}
