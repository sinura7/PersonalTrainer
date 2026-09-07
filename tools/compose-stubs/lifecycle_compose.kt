// androidx.lifecycle.compose — declaration-only. See compose-stubs/README.md.
//
// lifecycle-runtime-compose is Google-only: org.jetbrains.androidx.lifecycle publishes a
// lifecycle-runtime-compose-desktop artifact to Maven Central, but every version of it is a
// metadata-only placeholder with zero .class files (verified on 2.9.6).
//
// BOTH overloads of collectAsStateWithLifecycle are declared, and the distinction is
// load-bearing: the StateFlow one has NO initial value (the flow already has one) while the Flow
// one REQUIRES `initialValue` as its first parameter. RestLockActivity relies on the Flow
// overload (`controller.remainingSeconds.collectAsStateWithLifecycle(0)`, a Flow<Int>); if the
// StateFlow overload were declared loosely enough to swallow that call, a genuine "this Flow is
// not a StateFlow" error would vanish.

package androidx.lifecycle.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.State
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

val LocalLifecycleOwner: CompositionLocal<LifecycleOwner> get() = TODO("compile-only stub")

@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(
    lifecycleOwner: LifecycleOwner = TODO("compile-only stub"),
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    context: CoroutineContext = EmptyCoroutineContext,
): State<T> = TODO("compile-only stub")

@Composable
fun <T> Flow<T>.collectAsStateWithLifecycle(
    initialValue: T,
    lifecycleOwner: LifecycleOwner = TODO("compile-only stub"),
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    context: CoroutineContext = EmptyCoroutineContext,
): State<T> = TODO("compile-only stub")
