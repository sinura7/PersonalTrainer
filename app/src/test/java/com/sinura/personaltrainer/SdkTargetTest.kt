package com.sinura.personaltrainer

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-of-truth parse of the shipping SDK triple. The preflight script
 * is the ratchet; this suite fails in the same Gradle lane.
 */
class SdkTargetTest {
    @Test
    fun compileTargetAndMinMatchTheSignedTriple() {
        val gradle = source("build.gradle.kts")
        val text = gradle.readText()
        assertTrue("compileSdk must be 36", Regex("""compileSdk\s*=\s*36""").containsMatchIn(text))
        assertTrue("targetSdk must be 36", Regex("""targetSdk\s*=\s*36""").containsMatchIn(text))
        assertTrue("minSdk must be 26", Regex("""minSdk\s*=\s*26""").containsMatchIn(text))
    }

    @Test
    fun manifestEnablesPredictiveBack() {
        val manifest = source("src/main/AndroidManifest.xml")
        assertTrue(
            manifest.readText().contains("android:enableOnBackInvokedCallback=\"true\""),
        )
    }

    @Test
    fun robolectricLaneEmulatesTheNewestSdkJava17Supports() {
        val properties = source("src/test/resources/robolectric.properties")
        assertTrue(
            "Robolectric must emulate API 35, not 36: 4.16 maps Baklava to Java 21 " +
                "and this project is Java 17, so sdk=36 fails every sandbox with " +
                "\"Android SDK 36 requires Java 21 (have Java 17)\". Raise with the JDK.",
            Regex("""(?m)^sdk=35\s*$""").containsMatchIn(properties.readText()),
        )
    }

    private fun source(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.first { it.isFile }
    }
}
