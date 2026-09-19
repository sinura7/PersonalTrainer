package com.sinura.personaltrainer.testutil

import android.os.SystemClock
import android.os.Build
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.WindowInsets
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
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

    /** ForcedSize can update density across a parent remeasure. Require the
     * requested viewport before reading coordinates or accepting pixels. */
    fun awaitViewport(compose: ComposeContentTestRule, widthDp: Int, heightDp: Int) {
        compose.waitUntil(5_000) {
            val node = compose.onNodeWithTag(DefaultTag).fetchSemanticsNode()
            val density = node.layoutInfo.density.density
            val bounds = node.boundsInRoot
            kotlin.math.abs(bounds.width / density - widthDp) <= 1f &&
                kotlin.math.abs(bounds.height / density - heightDp) <= 1f
        }
    }

    /** Dialogs and IME live in native windows; do not fake their window size with ForcedSize. */
    @OptIn(ExperimentalTestApi::class)
    fun mountDevice(
        compose: ComposeContentTestRule,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        configureEnforcedEdgeToEdgeHost(compose)
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
        statusBar: Dp = 0.dp,
        navigationBar: Dp = 0.dp,
        content: @Composable () -> Unit,
    ) {
        configureEnforcedEdgeToEdgeHost(compose)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width, height))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                    // The host's portrait pixel insets cannot be reused after
                    // ForcedSize changes density/shape. Pin logical system-bar
                    // insets explicitly; actual-window observations use mountDevice.
                    val density = LocalDensity.current
                    val insets = WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, with(density) { statusBar.roundToPx() }, 0, 0))
                        .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, with(density) { navigationBar.roundToPx() }))
                        .build()
                    DeviceConfigurationOverride(DeviceConfigurationOverride.WindowInsets(insets)) {
                        PersonalTrainerTheme(reduceMotion = reduceMotion) {
                            Box(Modifier.size(width, height).testTag(DefaultTag)) { content() }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun configureEnforcedEdgeToEdgeHost(compose: ComposeContentTestRule) {
        if (Build.VERSION.SDK_INT < 35) return
        // API 35+ makes the generic test Activity edge-to-edge but leaves its
        // default light navigation contrast scrim. MainActivity explicitly uses
        // these dark transparent styles. Match that policy before capture; the
        // legacy API 29 reference window and logical inset fixture stay pinned.
        compose.runOnUiThread {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<ComponentActivity>().single()
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            )
        }
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
