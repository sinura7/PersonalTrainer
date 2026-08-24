package com.sinura.personaltrainer.testutil

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import org.junit.Assert.fail

data class PixelDiff(
    val differentPixels: Int,
    val totalPixels: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val ratio: Double
        get() = if (totalPixels == 0) 0.0 else differentPixels.toDouble() / totalPixels
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

    fun assertMatches(name: String, actualImage: ImageBitmap) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val actual = actualImage.asAndroidBitmap()
        if (InstrumentationRegistry.getArguments().getString("recordGoldens").toBoolean()) {
            val path = writeArtifact(name, "recorded", actual)
            println("GOLDEN_RECORDED $path")
            return
        }

        val expected = checkNotNull(instrumentation.context.assets
            .open("$AssetFolder/$name.png")
            .use(BitmapFactory::decodeStream)) {
            "Golden $name decoded to null"
        }

        val diff = compare(expected, actual)
        if (diff.differentPixels == 0) return

        val actualPath = writeArtifact(name, "actual", actual)
        val diffPath = writeArtifact(name, "diff", diffBitmap(expected, actual))
        fail(
            "Golden $name changed: ${diff.differentPixels}/${diff.totalPixels} " +
                "pixels (${String.format(Locale.US, "%.3f", diff.ratio * 100)}%), " +
                "bounds=[${diff.left},${diff.top}..${diff.right},${diff.bottom}]. " +
                "actual=$actualPath diff=$diffPath",
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
        val expectedPixels = IntArray(width * height)
        val actualPixels = IntArray(width * height)
        expected.getPixels(expectedPixels, 0, width, 0, 0, width, height)
        actual.getPixels(actualPixels, 0, width, 0, 0, width, height)

        var count = 0
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (index in expectedPixels.indices) {
            if (expectedPixels[index] == actualPixels[index]) continue
            count++
            val x = index % width
            val y = index / width
            left = minOf(left, x)
            top = minOf(top, y)
            right = maxOf(right, x)
            bottom = maxOf(bottom, y)
        }
        return PixelDiff(
            differentPixels = count,
            totalPixels = expectedPixels.size,
            left = if (count == 0) -1 else left,
            top = if (count == 0) -1 else top,
            right = right,
            bottom = bottom,
        )
    }

    private fun diffBitmap(expected: Bitmap, actual: Bitmap): Bitmap {
        val width = actual.width
        val height = actual.height
        val expectedPixels = IntArray(width * height)
        val actualPixels = IntArray(width * height)
        expected.getPixels(expectedPixels, 0, width, 0, 0, width, height)
        actual.getPixels(actualPixels, 0, width, 0, 0, width, height)
        val diff = IntArray(width * height) { index ->
            if (expectedPixels[index] == actualPixels[index]) {
                Color.TRANSPARENT
            } else {
                Color.rgb(255, 0, 85)
            }
        }
        return Bitmap.createBitmap(diff, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun writeArtifact(name: String, suffix: String, bitmap: Bitmap): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = checkNotNull(context.getExternalFilesDir(null))
        val folder = File(root, AssetFolder).apply { mkdirs() }
        val file = File(folder, "$name-$suffix.png")
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Could not encode ${file.absolutePath}"
            }
        }
        // connectedAndroidTest uninstalls both APKs after the run, which also
        // deletes their external-files directories. Copy through UiAutomation
        // (the shell identity) so a recorded/mismatch artifact survives long
        // enough for `adb pull`.
        val persistent = "/sdcard/Download/${file.name}"
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation
            .executeShellCommand("cp ${file.absolutePath} $persistent")
            .use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        return persistent
    }
}
