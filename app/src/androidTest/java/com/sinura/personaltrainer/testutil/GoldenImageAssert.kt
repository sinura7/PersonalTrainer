package com.sinura.personaltrainer.testutil

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import kotlin.math.abs
import org.junit.Assert.fail

data class PixelDiff(
    /** Pixels whose colour moved further than the rounding allowance. A change. */
    val differentPixels: Int,
    /**
     * Pixels that moved by at most [GoldenImageAssert.ROUNDING_LEVELS] on every
     * channel. Rasteriser rounding, unless there are more than
     * [GoldenImageAssert.ROUNDING_BUDGET] of them.
     */
    val roundingPixels: Int,
    val totalPixels: Int,
    /** Over every pixel that moved at all, rounding included. Where to look. */
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val ratio: Double
        get() = if (totalPixels == 0) 0.0 else differentPixels.toDouble() / totalPixels

    /** True when the two images are the same drawing, rounding aside. */
    val matches: Boolean
        get() = differentPixels == 0 && roundingPixels <= GoldenImageAssert.ROUNDING_BUDGET
}

/**
 * Small, dependency-free golden harness built on Compose captureToImage.
 *
 * It is tied to one recorded emulator profile (API, density, font renderer)
 * through the evidence manifest. That is more honest than pretending a
 * device PNG is portable across renderers. Record with:
 *
 * ./gradlew connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.recordGoldens=true
 *
 * Then pull targetContext/externalFilesDir/goldens into
 * app/src/androidTest/assets/goldens and rerun without the flag.
 */
object GoldenImageAssert {
    private const val AssetFolder = "goldens"

    /**
     * Per-channel difference the comparator reads as the same colour.
     *
     * The emulator this golden is recorded on has no GPU: it draws with
     * SwiftShader, whose edge coverage on a rounded corner is not reproducible
     * between runs. Two runs of one commit differed by seventeen pixels, each by
     * exactly one level on one channel, all on the Volt button's corners; the
     * golden passed on the first and failed on the second. One level is that
     * rounding and nothing else.
     *
     * This is a rounding allowance, not a tolerance. Two levels fail. A colour
     * that actually moved fails on the first pixel: `TextTertiary`'s contrast fix
     * (F3, `6787b17`) moved thirty-two levels across 7,074 pixels, and
     * [FoundationGoldenTest.deliberateTokenChangeProducesSmallLocatedDiff] pins a
     * deliberate token change at over a thousand pixels — three orders of
     * magnitude above what is forgiven here.
     */
    const val ROUNDING_LEVELS = 1

    /**
     * How many one-level pixels are rounding before they are a change.
     *
     * A whole surface nudged by one level is a real edit and must fail, so the
     * allowance is capped rather than unlimited. Seventeen is what the renderer
     * actually produces; this is fifteen times that, and still 0.016% of the
     * capture.
     */
    const val ROUNDING_BUDGET = 256

    fun assertMatches(name: String, actualImage: ImageBitmap) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val actual = actualImage.asAndroidBitmap()
        // Renderer baselines are explicit. Never silently fall back to another
        // host's PNG or let a missing reference become a passing/skipped test.
        val profile = InstrumentationRegistry.getArguments().getString("goldenProfile", "legacy")
        require(profile in setOf("legacy", "windows-swiftshader37")) { "Unknown golden profile: $profile" }
        val asset = if (profile == "legacy") "$AssetFolder/$name.png" else "$AssetFolder/$profile/$name.png"
        if (InstrumentationRegistry.getArguments().getString("recordGoldens").toBoolean()) {
            val path = writeArtifact(name, "recorded", actual)
            println("GOLDEN_RECORDED $path")
            return
        }

        val expected = checkNotNull(instrumentation.context.assets
            .open(asset)
            .use(BitmapFactory::decodeStream)) {
            "Golden $name decoded to null"
        }

        val diff = compare(expected, actual)
        if (diff.matches) return

        val actualPath = writeArtifact(name, "actual", actual)
        val expectedPath = writeArtifact(name, "expected", expected)
        val diffPath = writeArtifact(name, "diff", diffBitmap(expected, actual))
        val rounding = if (diff.roundingPixels == 0) {
            ""
        } else {
            " ${diff.roundingPixels} pixel(s) within the ${ROUNDING_LEVELS}-level " +
                "rounding allowance (budget $ROUNDING_BUDGET)."
        }
        fail(
            "Golden $name changed: ${diff.differentPixels}/${diff.totalPixels} " +
                "pixels (${String.format(Locale.US, "%.3f", diff.ratio * 100)}%), " +
                "bounds=[${diff.left},${diff.top}..${diff.right},${diff.bottom}].$rounding " +
                "expected=$expectedPath actual=$actualPath diff=$diffPath",
        )
    }

    fun compare(expected: ImageBitmap, actual: ImageBitmap): PixelDiff =
        compare(expected.asAndroidBitmap(), actual.asAndroidBitmap())

    fun compare(expected: Bitmap, actual: Bitmap): PixelDiff {
        if (expected.width != actual.width || expected.height != actual.height) {
            fail(
                "Golden dimensions changed: expected ${expected.width}×${expected.height}, " +
                    "actual ${actual.width}×${actual.height}",
            )
        }
        val width = expected.width
        val height = expected.height
        // Bitmaps already own the image storage. Read one scanline at a time:
        // duplicating two full frames on the managed heap exhausted the API 29
        // test process before it could report an actual visual difference.
        val expectedPixels = IntArray(width)
        val actualPixels = IntArray(width)

        var count = 0
        var rounding = 0
        var moved = 0
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            expected.getPixels(expectedPixels, 0, width, 0, y, width, 1)
            actual.getPixels(actualPixels, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val before = expectedPixels[x]
                val after = actualPixels[x]
                if (before == after) continue
                if (withinRounding(before, after)) rounding++ else count++
                moved++
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        return PixelDiff(
            differentPixels = count,
            roundingPixels = rounding,
            totalPixels = width * height,
            left = if (moved == 0) -1 else left,
            top = if (moved == 0) -1 else top,
            right = right,
            bottom = bottom,
        )
    }

    /** True when no channel of the two packed ARGB pixels moved past the allowance. */
    private fun withinRounding(before: Int, after: Int): Boolean {
        for (shift in intArrayOf(24, 16, 8, 0)) {
            val a = (before ushr shift) and 0xFF
            val b = (after ushr shift) and 0xFF
            if (abs(a - b) > ROUNDING_LEVELS) return false
        }
        return true
    }

    private fun diffBitmap(expected: Bitmap, actual: Bitmap): Bitmap {
        val width = actual.width
        val height = actual.height
        val expectedPixels = IntArray(width)
        val actualPixels = IntArray(width)
        val diff = IntArray(width)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            expected.getPixels(expectedPixels, 0, width, 0, y, width, 1)
            actual.getPixels(actualPixels, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                diff[x] = if (expectedPixels[x] == actualPixels[x]) {
                    Color.TRANSPARENT
                } else {
                    Color.rgb(255, 0, 85)
                }
            }
            bitmap.setPixels(diff, 0, width, 0, y, width, 1)
        }
        return bitmap
    }

    private fun writeArtifact(name: String, suffix: String, bitmap: Bitmap): String =
        NativeArtifacts.write("$name-$suffix", bitmap)
}
