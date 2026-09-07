// androidx.compose.ui.platform — the Android-only composition locals, plus one API that
// postdates the substituted Compose build. Declaration-only. See compose-stubs/README.md.
//
// LocalContext / LocalView have no Compose Multiplatform equivalent: they hand out
// android.content.Context and android.view.View, which desktop Compose does not have.
// LocalResources (Compose UI 1.9) and LocalLocale (1.11, adopted in
// docs/architecture/compose-toolchain.md decision 6) postdate JetBrains Compose 1.8.2.
//
// TYPED AS `CompositionLocal<T>`, NOT `ProvidableCompositionLocal<T>`. The real declarations are
// providable; these are deliberately narrower, because no source in this app provides any of
// them — only reads `.current`. `X provides y` for one of these is a RED here and a green under
// Gradle, which is the safe direction to be wrong in. Widen a line here the day the app really
// does provide one.

package androidx.compose.ui.platform

import android.content.Context
import android.content.res.Resources
import android.view.View
import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.text.intl.Locale

val LocalContext: CompositionLocal<Context> get() = TODO("compile-only stub")

val LocalView: CompositionLocal<View> get() = TODO("compile-only stub")

val LocalResources: CompositionLocal<Resources> get() = TODO("compile-only stub")

val LocalLocale: CompositionLocal<Locale> get() = TODO("compile-only stub")
