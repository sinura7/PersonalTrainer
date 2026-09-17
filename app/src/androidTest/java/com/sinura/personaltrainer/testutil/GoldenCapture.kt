package com.sinura.personaltrainer.testutil

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme

/**
 * Shared 360×800 Instrument mount for page goldens. P1.2 used this shape
 * inline in [com.sinura.personaltrainer.ui.preview.FoundationGoldenTest];
 * H3 names six gym-floor populated PNGs plus the component gallery and
 * three ThemeGallery previews in [com.sinura.personaltrainer.domain.GoldenPageCatalog].
 * Do not add [GoldenImageAssert.assertMatches] callers until the PNG is
 * committed — a missing asset fails the connected suite. Every later
 * surface×state capture should go through here so the viewport and theme
 * cannot drift per page.
 */
object GoldenCapture {
    val ViewportWidth: Dp = 360.dp
    val ViewportHeight: Dp = 800.dp
    const val DefaultTag = "golden-capture-root"

    /** Dialogs and IME live in native windows; do not fake their window size with ForcedSize. */
    @OptIn(ExperimentalTestApi::class)
    fun mountDevice(
        compose: ComposeContentTestRule,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                PersonalTrainerTheme {
                    Box(Modifier.fillMaxSize().testTag(DefaultTag)) { content() }
                }
            }
        }
        compose.waitForIdle()
    }

    /** New references use an explicit, unclipped logical viewport. Legacy mount stays intact. */
    @OptIn(ExperimentalTestApi::class)
    fun mountViewport(
        compose: ComposeContentTestRule,
        width: Dp,
        height: Dp,
        fontScale: Float = 1f,
        reduceMotion: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width, height))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                    PersonalTrainerTheme(reduceMotion = reduceMotion) {
                        Box(Modifier.size(width, height).testTag(DefaultTag)) { content() }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    fun mount(
        compose: ComposeContentTestRule,
        tag: String = DefaultTag,
        reduceMotion: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            PersonalTrainerTheme(reduceMotion = reduceMotion) {
                Box(
                    modifier = Modifier
                        .size(ViewportWidth, ViewportHeight)
                        .testTag(tag),
                ) {
                    content()
                }
            }
        }
        compose.waitForIdle()
    }

    fun capture(compose: ComposeContentTestRule, tag: String = DefaultTag): ImageBitmap {
        compose.waitForIdle()
        var last: Throwable? = null
        repeat(8) { attempt ->
            try {
                return compose.onNodeWithTag(tag).captureToImage()
            } catch (error: ComposeTimeoutException) {
                last = error
                compose.waitForIdle()
                SystemClock.sleep(400L * (attempt + 1))
            }
        }
        throw checkNotNull(last)
    }
}
