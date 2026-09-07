// androidx.compose.ui.res.painterResource(Int) — declaration-only. See compose-stubs/README.md.
//
// The JetBrains Compose jar has painterResource(String) (a classpath resource path); the Android
// artifact's overload takes an @DrawableRes Int. Only the Int overload is added here — adding it
// as an overload rather than shadowing keeps the String one intact, and the two cannot be
// confused because Int and String do not convert.

package androidx.compose.ui.res

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
fun painterResource(@DrawableRes id: Int): Painter = TODO("compile-only stub")
