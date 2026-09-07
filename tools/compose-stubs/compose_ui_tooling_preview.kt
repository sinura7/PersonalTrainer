// androidx.compose.ui.tooling.preview.Preview — declaration-only. See compose-stubs/README.md.
//
// ui-tooling-preview is an Android-only artifact. The annotation carries no behaviour that a
// type-check can observe; what matters is that its parameter NAMES and TYPES match, so a
// @Preview(widthDp = "420") or a misspelled parameter fails here as it would under Gradle.
//
// DELIBERATELY NARROWER: only the parameters this app uses are declared (name, group, widthDp,
// heightDp, showBackground, backgroundColor, apiLevel, locale, fontScale, uiMode). device/
// wallpaper/showSystemUi are omitted, so a new use is a visible RED.
//
// uiMode is Int, matching @UiMode in the real annotation — app/src/debug/.../PreviewProfiles.kt
// passes android.content.res.Configuration.UI_MODE_NIGHT_YES.

package androidx.compose.ui.tooling.preview

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Repeatable
annotation class Preview(
    val name: String = "",
    val group: String = "",
    val apiLevel: Int = -1,
    val widthDp: Int = -1,
    val heightDp: Int = -1,
    val locale: String = "",
    val fontScale: Float = 1f,
    val showBackground: Boolean = false,
    val backgroundColor: Long = 0,
    val uiMode: Int = 0,
)
