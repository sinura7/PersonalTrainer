package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H3: six gym-floor populated goldens, not the 108-name matrix fan-out.
 * PNGs remain an owner emulator gate; this catalog is the JVM contract.
 */
class GoldenPageCatalogTest {
    @Test
    fun gymFloorIsSixPopulatedPagesNotTheMatrixFanOut() {
        assertEquals(6, GoldenPageCatalog.gymFloorPageIds.size)
        assertEquals(6, GoldenPageCatalog.requiredPageGoldens.size)
        assertEquals(
            GoldenPageCatalog.requiredPageGoldens.toSet().size,
            GoldenPageCatalog.requiredPageGoldens.size,
        )
        GoldenPageCatalog.gymFloorPageIds.forEach { id ->
            val name = GoldenPageCatalog.assetName(id, "populated")
            assertTrue(name, name in GoldenPageCatalog.requiredPageGoldens)
            assertTrue(name, name.endsWith("-${GoldenPageCatalog.PROFILE_SUFFIX}"))
            assertTrue(id, AccessibilityMatrix.pages.any { it.id == id })
        }
        assertEquals("home-populated-api29", GoldenPageCatalog.assetName("home", "populated"))
        assertFalse(
            GoldenPageCatalog.requiredPageGoldens.contains("activity-composer-error-api29"),
        )
        val matrixFanOut =
            AccessibilityMatrix.pages.size * AccessibilityMatrix.requiredStates.size
        assertTrue(
            "gym-floor catalog ($matrixFanOut matrix names) must be smaller than the fan-out",
            GoldenPageCatalog.requiredPageGoldens.size < matrixFanOut,
        )
    }

    @Test
    fun supportingGoldensNameTheComponentGalleryAndThreeThemePreviews() {
        assertEquals(
            listOf(
                GoldenPageCatalog.COMPONENT_GALLERY,
                GoldenPageCatalog.THEME_COLOUR_ROLES,
                GoldenPageCatalog.THEME_INSTRUMENT_TOKENS,
                GoldenPageCatalog.THEME_TYPE_RAMP,
            ),
            GoldenPageCatalog.requiredSupportingGoldens,
        )
        assertEquals(4, GoldenPageCatalog.missingSupportingGoldens.size)
    }

    @Test
    fun onlyTheSubstrateGalleryIsCommitted() {
        assertEquals(setOf(GoldenPageCatalog.SUBSTRATE_GALLERY), GoldenPageCatalog.committed)
        assertFalse(GoldenPageCatalog.isCommitted("home-populated-api29"))
        assertEquals(
            GoldenPageCatalog.requiredPageGoldens,
            GoldenPageCatalog.missingPageGoldens,
        )
        assertTrue(GoldenPageCatalog.missingPageGoldens.contains("home-populated-api29"))
        assertTrue(GoldenPageCatalog.missingPageGoldens.contains("active-strength-populated-api29"))
        assertFalse(GoldenPageCatalog.missingPageGoldens.contains("settings-empty-api29"))
    }

    @Test
    fun namingIsStableAcrossRepeatedReads() {
        assertEquals(GoldenPageCatalog.requiredPageGoldens, GoldenPageCatalog.requiredPageGoldens)
    }
}
