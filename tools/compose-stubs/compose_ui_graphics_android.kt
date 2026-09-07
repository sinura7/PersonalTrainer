// androidx.compose.ui.graphics.asImageBitmap() on android.graphics.Bitmap — declaration-only.
// See compose-stubs/README.md.
//
// The JetBrains Compose jar declares asImageBitmap() for org.jetbrains.skia types. This adds the
// Android receiver only; different receiver types, so nothing is shadowed.

package androidx.compose.ui.graphics

import android.graphics.Bitmap

fun Bitmap.asImageBitmap(): ImageBitmap = TODO("compile-only stub")
