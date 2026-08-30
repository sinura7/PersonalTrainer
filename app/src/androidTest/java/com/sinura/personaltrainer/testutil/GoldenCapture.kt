package com.sinura.personaltrainer.testutil

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme

/**
 * Shared 360×800 Instrument mount for page goldens. P1.2 used this shape
 * inline in [com.sinura.personaltrainer.ui.preview.FoundationGoldenTest];
 * every later surface×state capture should go through here so the viewport
 * and theme cannot drift per page.
 */
object GoldenCapture {
    val ViewportWidth: Dp = 360.dp
    val ViewportHeight: Dp = 800.dp
    const val DefaultTag = "golden-capture-root"

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
