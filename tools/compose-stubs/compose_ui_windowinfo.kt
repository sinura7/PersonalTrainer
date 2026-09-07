// androidx.compose.ui.platform.WindowInfo — SHADOWS the interface in the JetBrains Compose 1.8.2
// jar. Declaration-only. See compose-stubs/README.md.
//
// WHY A WHOLE INTERFACE AND NOT AN EXTENSION: the app reads
// `LocalWindowInfo.current.containerDpSize` with no import beyond LocalWindowInfo, so
// containerDpSize must resolve as a MEMBER — an extension property declared here would need an
// import at every use site, and there is none. containerDpSize is Compose UI 1.9+; the 1.8.2
// interface has only isWindowFocused, keyboardModifiers and containerSize.
//
// A source declaration with this fully-qualified name shadows the one in the jar for the whole
// compilation, so `LocalWindowInfo` (still the jar's property) yields THIS type. That is the
// only shadowing this lane does, and tools/compile-check.sh asserts it is still in force: its
// --self-test compiles `LocalWindowInfo.current.containerDpSize` and fails the run if it stops
// resolving, so the shadow cannot silently stop applying.
//
// DELIBERATELY NARROWER than the real interface: keyboardModifiers is not declared (nothing in
// the app reads it), so a new use is a visible RED rather than an unchecked pass.

package androidx.compose.ui.platform

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize

interface WindowInfo {
    val isWindowFocused: Boolean
    val containerSize: IntSize get() = TODO("compile-only stub")
    /** Compose UI 1.9+. `containerSize` in dp, so callers can compare against Dp thresholds. */
    val containerDpSize: DpSize get() = TODO("compile-only stub")
}
