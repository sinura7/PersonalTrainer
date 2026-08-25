package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the MP-11 (DP-0) fan-out contract: every AccessibilityMatrix page×state
 * has one golden name, the substrate gallery is the only committed PNG, and
 * renaming a page does not silently drop its goldens.
 */
class GoldenPageCatalogTest {
    @Test
    fun everyPageStateHasADeterministicApi29Name() {
        val homePopulated = GoldenPageCatalog.assetName("home", "populated")
        assertEquals("home-populated-api29", homePopulated)
        assertEquals(
            "activity-composer-error-api29",
            GoldenPageCatalog.assetName("activity-composer", "error"),
        )
        assertEquals(
            AccessibilityMatrix.pages.size * AccessibilityMatrix.requiredStates.size,
            GoldenPageCatalog.requiredPageGoldens.size,
        )
        assertEquals(
            GoldenPageCatalog.requiredPageGoldens.toSet().size,
            GoldenPageCatalog.requiredPageGoldens.size,
        )
    }

    @Test
    fun everyMatrixPageIsInTheCatalog() {
        AccessibilityMatrix.pages.forEach { page ->
            page.states.forEach { state ->
                val name = GoldenPageCatalog.assetName(page.id, state)
                assertTrue(name, name in GoldenPageCatalog.requiredPageGoldens)
                assertTrue(name, name.endsWith("-${GoldenPageCatalog.PROFILE_SUFFIX}"))
            }
        }
    }

    @Test
    fun onlyTheSubstrateGalleryIsCommitted() {
        assertEquals(setOf(GoldenPageCatalog.SUBSTRATE_GALLERY), GoldenPageCatalog.committed)
        assertFalse(GoldenPageCatalog.isCommitted("home-populated-api29"))
        assertEquals(
            GoldenPageCatalog.requiredPageGoldens,
            GoldenPageCatalog.missingPageGoldens,
        )
        assertTrue(GoldenPageCatalog.missingPageGoldens.contains("goals-empty-api29"))
        assertTrue(GoldenPageCatalog.missingPageGoldens.contains("active-cardio-populated-api29"))
        assertTrue(GoldenPageCatalog.missingPageGoldens.contains("activity-composer-error-api29"))
    }

    @Test
    fun namingIsStableAcrossRepeatedReads() {
        assertEquals(GoldenPageCatalog.requiredPageGoldens, GoldenPageCatalog.requiredPageGoldens)
    }
}
