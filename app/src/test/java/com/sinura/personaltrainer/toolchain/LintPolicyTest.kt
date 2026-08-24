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
