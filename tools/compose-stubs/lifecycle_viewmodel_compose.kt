// androidx.lifecycle.viewmodel.compose.viewModel — declaration-only.
// See compose-stubs/README.md.
//
// The type parameter's upper bound is the REAL constraint being checked: `VM : ViewModel`, with
// ViewModel resolved from tools/compile-stubs/lifecycle.kt. Every screen writes
// `viewModel: HomeViewModel = viewModel()` and the bound is what proves the annotated type is
// actually a ViewModel. `reified` matches the real declaration, so a non-reified type argument
// fails here as it does under Gradle.
//
// DELIBERATELY NARROWER: the real overload also takes viewModelStoreOwner, key, factory and
// extras. No source in this app passes any of them (AppViewModel.kt records that production
// uses the default factory), so they are omitted and a new use is a visible RED.

package androidx.lifecycle.viewmodel.compose

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel

@Composable
inline fun <reified VM : ViewModel> viewModel(): VM = TODO("compile-only stub")
