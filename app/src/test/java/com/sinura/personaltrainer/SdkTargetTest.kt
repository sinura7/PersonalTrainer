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
    fun robolectricLanePinsApi35UntilP42() {
        val properties = source("src/test/resources/robolectric.properties")
        assertTrue(
            "Robolectric 4.14.1 must emulate 35, not compileSdk 36",
            Regex("""(?m)^sdk=35\s*$""").containsMatchIn(properties.readText()),
        )
    }

    private fun source(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.first { it.isFile }
    }
}
