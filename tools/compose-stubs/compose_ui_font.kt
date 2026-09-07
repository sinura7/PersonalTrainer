// androidx.compose.ui.text.font.Font(resId) — declaration-only. See compose-stubs/README.md.
//
// The app bundles five static faces under res/font and builds FontFamily from resource ids. The
// JetBrains Compose jar has Font(String)/Font(File)/Font(ByteArray) but no resource-id overload,
// because desktop Compose has no R.
//
// The `resId` parameter NAME matters: it is the real Android parameter name, so a call written
// `Font(resId = R.font.x)` keeps compiling and `Font(id = ...)` keeps failing, exactly as under
// Gradle. `variationSettings` is omitted (the app passes none) — see the omission rule in
// compose-stubs/README.md.

package androidx.compose.ui.text.font

fun Font(
    resId: Int,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
): Font = TODO("compile-only stub")
