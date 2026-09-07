// androidx.activity.compose — declaration-only. See compose-stubs/README.md.
//
// NOTE ON BackHandler: this is NOT androidx.compose.ui.backhandler.BackHandler, which the
// JetBrains Compose 1.8.2 jar does have. That one is the Compose Multiplatform API and takes the
// same arguments, so leaving it to resolve would have looked fine and silently checked a
// different declaration. It is stubbed here under its real Android package instead, and the app's
// own `import androidx.activity.compose.BackHandler` is what selects it.

package androidx.activity.compose

import androidx.activity.ComponentActivity
import androidx.activity.result.ManagedActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable

fun ComponentActivity.setContent(content: @Composable () -> Unit) {
    TODO("compile-only stub")
}

/** `enabled` defaults to true and `onBack` is the trailing lambda, as in the real declaration. */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    TODO("compile-only stub")
}

@Composable
fun <I, O> rememberLauncherForActivityResult(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit,
): ManagedActivityResultLauncher<I, O> = TODO("compile-only stub")
