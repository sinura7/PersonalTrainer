// androidx.navigation — declaration-only. See compose-stubs/README.md.
//
// androidx.navigation is a Google-only artifact. JetBrains publishes a Compose Multiplatform
// port (org.jetbrains.androidx.navigation:navigation-compose-desktop 2.9.2, real classes) and it
// is deliberately NOT used: NavBackStackEntry there implements LifecycleOwner,
// ViewModelStoreOwner, SavedStateRegistryOwner and HasDefaultViewModelProviderFactory, and every
// one of those interfaces comes from the lifecycle/savedstate artifacts whose JVM builds on
// Maven Central are empty placeholders. Loading it would have meant hand-writing that whole
// supertype chain to match a binary — more stub surface, less control, and a mismatch there
// produces confusing REDs. The app's own navigation surface is small and pinned to string
// routes (docs/architecture/compose-toolchain.md decision 3), so it is written out here instead.
//
// Modelled on Navigation 2.9.8, the version gradle/libs.versions.toml pins.

package androidx.navigation

import android.os.Bundle

/**
 * `route` is NULLABLE — the app's tab-selection test is
 * `currentDestination?.hierarchy?.any { it.route == tab.matchPattern }`, and comparing a
 * String? to a String is exactly what that line is doing. A non-null route here would make the
 * `?.` chain look redundant and could hide a genuine null.
 */
open class NavDestination {
    open var route: String? = null
    open var id: Int = 0

    companion object {
        /** Self, then parent, then parent's parent. Declared on the companion, so the import is
         *  `androidx.navigation.NavDestination.Companion.hierarchy`, as in the real artifact. */
        val NavDestination.hierarchy: Sequence<NavDestination> get() = TODO("compile-only stub")
    }
}

open class NavGraph : NavDestination() {
    companion object {
        fun NavGraph.findStartDestination(): NavDestination = TODO("compile-only stub")
    }
}

class NavBackStackEntry private constructor() {
    /** Non-null: an entry always has a destination. */
    val destination: NavDestination get() = TODO("compile-only stub")

    /** Nullable, and the app relies on it: every read is `entry.arguments?.getLong(...)`. */
    val arguments: Bundle? get() = TODO("compile-only stub")
}

class PopUpToBuilder internal constructor() {
    var inclusive: Boolean = false
    var saveState: Boolean = false
}

class NavOptionsBuilder internal constructor() {
    var launchSingleTop: Boolean = false
    var restoreState: Boolean = false

    fun popUpTo(route: String, popUpToBuilder: PopUpToBuilder.() -> Unit = {}) {
        TODO("compile-only stub")
    }

    fun popUpTo(id: Int, popUpToBuilder: PopUpToBuilder.() -> Unit = {}) {
        TODO("compile-only stub")
    }
}

/**
 * DELIBERATELY NARROWER than the real NavController: only the five entry points the app calls.
 * navigate(route, NavOptions) / navigateUp() / the id-based navigate overloads / currentDestination
 * / the back-stack accessors are all omitted, so a new use is a visible RED rather than an
 * unchecked pass.
 */
open class NavController internal constructor() {
    open var graph: NavGraph
        get() = TODO("compile-only stub")
        set(_) = TODO("compile-only stub")

    val currentBackStackEntry: NavBackStackEntry? get() = TODO("compile-only stub")

    fun navigate(route: String) { TODO("compile-only stub") }

    fun navigate(route: String, builder: NavOptionsBuilder.() -> Unit) { TODO("compile-only stub") }

    open fun popBackStack(): Boolean = TODO("compile-only stub")

    fun popBackStack(route: String, inclusive: Boolean, saveState: Boolean = false): Boolean =
        TODO("compile-only stub")
}

class NavHostController internal constructor() : NavController()

/**
 * NavType is invariant in T, as the real one is, and each constant below pins the T that the
 * matching Bundle getter returns: StringType is `NavType<String?>` (a string argument can be
 * absent), LongType and BoolType are not nullable.
 */
abstract class NavType<T> {
    companion object {
        val StringType: NavType<String?> get() = TODO("compile-only stub")
        val IntType: NavType<Int> get() = TODO("compile-only stub")
        val LongType: NavType<Long> get() = TODO("compile-only stub")
        val BoolType: NavType<Boolean> get() = TODO("compile-only stub")
        val FloatType: NavType<Float> get() = TODO("compile-only stub")
    }
}

class NavArgument internal constructor()

class NamedNavArgument internal constructor()

class NavArgumentBuilder internal constructor() {
    var type: NavType<*> = TODO("compile-only stub")
    var nullable: Boolean = false
    var defaultValue: Any? = null
}

fun navArgument(name: String, builder: NavArgumentBuilder.() -> Unit): NamedNavArgument =
    TODO("compile-only stub")

class NavDeepLink internal constructor()

class NavGraphBuilder internal constructor()
