package com.sinura.personaltrainer.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P5.1 ratchet: shared-target domain types do not import platform time, locale,
 * Android, Room, or Compose (ADR-003, ADR-011).
 */
class DomainSeamPolicyTest {
    @Test
    fun domainSourcesDoNotImportPlatformTimeOrLocale() {
        val root = sourceRoot()
        val banned = listOf(
            Regex("""^import\s+java\.time\."""),
            Regex("""^import\s+java\.text\.NumberFormat"""),
            Regex("""^import\s+java\.util\.Locale"""),
            Regex("""^import\s+android\."""),
            Regex("""^import\s+androidx\.room"""),
            Regex("""^import\s+androidx\.compose"""),
        )
        val offenders = mutableListOf<String>()
        root.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                if (banned.any { it.containsMatchIn(line) }) {
                    offenders += "${file.name}:${index + 1}: ${line.trim()}"
                }
            }
        }
        assertTrue(
            "domain/ still imports a banned platform type:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun timeAndIdPortsExistAsDomainTypes() {
        assertTrue(TimePort::class.java.isInterface)
        assertTrue(IdPort::class.java.isInterface)
        val sample = CapturedCivilTime(
            instantMillis = 0L,
            zoneId = "UTC",
            offsetSeconds = 0,
            localEpochDay = 0L,
        )
        assertTrue(sample.localDate.year == 1970)
    }

    private fun sourceRoot(): File {
        val candidates = listOf(
            File("src/main/java/com/sinura/personaltrainer/domain"),
            File("app/src/main/java/com/sinura/personaltrainer/domain"),
        )
        return candidates.first { it.isDirectory }
    }
}
