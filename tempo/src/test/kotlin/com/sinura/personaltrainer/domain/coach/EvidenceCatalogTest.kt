package com.sinura.personaltrainer.domain.coach

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class EvidenceCatalogTest {
    private val doiPattern = Pattern.compile("""^10\.\d{4,9}/[-._;()/:A-Za-z0-9]+$""")

    @Test
    fun nonHeuristicEntriesHaveValidDoiOrExplicitNull() {
        for (entry in EvidenceCatalog.all()) {
            if (entry.heuristic) {
                assertNull("${entry.id} heuristic must not carry DOI", entry.doi)
                continue
            }
            if (entry.id == "jeffreys-2007-ramp") {
                assertNull(entry.doi)
                continue
            }
            assertNotNull("${entry.id} missing DOI", entry.doi)
            assertTrue(
                "${entry.id} DOI format",
                doiPattern.matcher(checkNotNull(entry.doi)).matches(),
            )
        }
    }

    @Test
    fun heuristicRowsAreMarked() {
        val heuristics = EvidenceCatalog.all().filter { it.heuristic }
        assertTrue(heuristics.isNotEmpty())
        assertTrue(heuristics.all { it.doi == null })
    }

    @Test
    fun policyMapsOnlyToKnownIds() {
        val known = EvidenceCatalog.all().map { it.id }.toSet()
        val reasons = listOf(
            com.sinura.personaltrainer.domain.SetMicroRecCalculator.IN_TANK,
            com.sinura.personaltrainer.domain.SetMicroRecCalculator.RPE_HOLD,
            com.sinura.personaltrainer.domain.SetMicroRecCalculator.NO_HISTORY,
        )
        for (reason in reasons) {
            for (id in CoachPolicyEvidence.evidenceIdsForReason(reason)) {
                assertTrue("$reason cites unknown $id", id in known)
            }
        }
    }

    @Test
    fun noDuplicateCatalogIds() {
        val ids = EvidenceCatalog.all().map { it.id }
        org.junit.Assert.assertEquals(ids.size, ids.toSet().size)
    }
}
