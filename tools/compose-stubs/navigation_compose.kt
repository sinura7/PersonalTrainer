// androidx.navigation.compose — declaration-only. See compose-stubs/README.md.
//
// The two shapes that matter and are copied exactly from navigation-compose 2.9.8:
//
//  * NavHost's transition parameters are LAMBDAS WITH AN AnimatedContentTransitionScope RECEIVER
//    returning a NULLABLE transition, not bare EnterTransition values. AppNav.kt writes
//    `enterTransition = { screenEnter }`, and it has to: passing `screenEnter` directly is an
//    error under Gradle and stays one here.
//  * composable's content lambda has an AnimatedContentScope receiver and takes the
//    NavBackStackEntry as its parameter, which is what makes `{ entry -> entry.arguments?... }`
//    resolve. AnimatedContentScope / AnimatedContentTransitionScope come from the REAL
//    androidx.compose.animation jar, not from a stub.
//
// DELIBERATELY NARROWER: dialog(), navigation(), deepLinks and sizeTransform are omitted.

package androidx.navigation.compose

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

@Composable
fun rememberNavController(): NavHostController = TODO("compile-only stub")

@Composable
fun NavController.currentBackStackEntryAsState(): State<NavBackStackEntry?> =
    TODO("compile-only stub")

@Composable
fun NavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    route: String? = null,
    enterTransition:
        (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?) =
        TODO("compile-only stub"),
    exitTransition:
        (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?) =
        TODO("compile-only stub"),
    popEnterTransition:
        (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?) = enterTransition,
    popExitTransition:
        (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?) = exitTransition,
    builder: NavGraphBuilder.() -> Unit,
) {
    TODO("compile-only stub")
}

fun NavGraphBuilder.composable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    TODO("compile-only stub")
}
