package com.sinura.personaltrainer.ui.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * The supported phone and compact-tablet widths.
 *
 * Use this annotation on state previews that should survive normal text.
 */
@Preview(name = "Phone 360", group = "Widths", widthDp = 360, heightDp = 800)
@Preview(name = "Phone 412", group = "Widths", widthDp = 412, heightDp = 915)
@Preview(name = "Compact tablet 600", group = "Widths", widthDp = 600, heightDp = 960)
annotation class TemperWidthPreviews

/** Stress profiles required by every later UI packet. */
@Preview(
    name = "Phone 360 · font 2.0",
    group = "Accessibility",
    widthDp = 360,
    heightDp = 1000,
    fontScale = 2f,
)
@Preview(
    name = "Phone 360 · RTL",
    group = "Accessibility",
    widthDp = 360,
    heightDp = 800,
    locale = "ar",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class TemperAccessibilityPreviews

/**
 * IDE profile for a reduced-motion composition. The preview function must
 * pass `reduceMotion = true` to PersonalTrainerTheme.
 */
@Preview(
    name = "Phone 360 · reduced motion",
    group = "Accessibility",
    widthDp = 360,
    heightDp = 800,
)
annotation class TemperReducedMotionPreview
