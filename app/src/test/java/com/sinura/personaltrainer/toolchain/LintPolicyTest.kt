package com.sinura.personaltrainer.toolchain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LintPolicyTest {
    @Test
    fun lintGateIsWarningsAsErrorsWithSignedWaiversOnly() {
        val gradle = source("app/build.gradle.kts").readText()
        assertTrue(gradle.contains("warningsAsErrors = true"))
        assertTrue(gradle.contains("AndroidGradlePluginVersion"))
        assertTrue(gradle.contains("UseKtx"))
        assertTrue(gradle.contains("GradleDependency"))
    }

    @Test
    fun baselineHasNoLeftoverIssues() {
        val baseline = source("app/lint-baseline.xml").readText()
        assertFalse(Regex("""<issue\s+id=""").containsMatchIn(baseline))
    }

    @Test
    fun verificationLedgerExists() {
        val ledger = source("gradle/verification-metadata.xml").readText()
        assertTrue(ledger.contains("<verify-metadata>true</verify-metadata>"))
        assertTrue(ledger.contains("<sha256"))
        assertTrue(ledger.contains("""<trust file=".*-sources[.]jar" regex="true"/>"""))
        assertTrue(ledger.contains("""<trust file=".*-javadoc[.]jar" regex="true"/>"""))
        assertTrue(ledger.contains("""<trust group="gradle" name="gradle" file=".*-src[.]zip" regex="true"/>"""))
        assertTrue(ledger.contains("aapt2-8.9.2-12782657-linux.jar"))
        assertTrue(ledger.contains("aapt2-8.9.2-12782657-windows.jar"))
        assertTrue(ledger.contains("aapt2-8.9.2-12782657-osx.jar"))
    }

    private fun source(relative: String): File {
        val candidates = listOf(
            File(relative),
            File("../$relative"),
            File(relative.removePrefix("app/")),
        )
        return candidates.first { it.isFile }
    }
}
