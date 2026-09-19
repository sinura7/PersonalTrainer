package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneFilledVoltTest {
    @Test
    fun leftoverNoPlanNeverShowsTwoFilledButtons() {
        for (setup in listOf(true, false)) {
            for (live in listOf(true, false)) {
                for (quiet in listOf(true, false)) {
                    for (offer in listOf(true, false)) {
                        val n = OneFilledVolt.leftoverNoPlanFilledCount(
                            setupComplete = setup,
                            sessionLive = live,
                            quietStart = quiet,
                            offerSetupActions = offer,
                        )
                        assertTrue(
                            "setup=$setup live=$live quiet=$quiet offer=$offer count=$n",
                            n <= 1,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun leftoverFreeStartIsPrimaryWhenThereIsNoRecoveryVolt() {
        assertEquals(
            FreeStartRank.PRIMARY,
            OneFilledVolt.leftoverFreeStart(
                sessionLive = false,
                setupComplete = true,
                hasRecoveryVolt = true,
                quietStart = false,
            ),
        )
        assertEquals(
            FreeStartRank.TEXT,
            OneFilledVolt.leftoverFreeStart(
                sessionLive = false,
                setupComplete = true,
                hasRecoveryVolt = true,
                quietStart = true,
            ),
        )
        assertEquals(
            FreeStartRank.HIDDEN,
            OneFilledVolt.leftoverFreeStart(
                sessionLive = true,
                setupComplete = true,
                hasRecoveryVolt = true,
                quietStart = false,
            ),
        )
        assertEquals(
            FreeStartRank.PRIMARY,
            OneFilledVolt.leftoverFreeStart(
                sessionLive = false,
                setupComplete = false,
                hasRecoveryVolt = false,
                quietStart = false,
            ),
        )
    }

    @Test
    fun planEmptyIsCompactAndSettingsExportIsTheVolt() {
        assertTrue(OneFilledVolt.PLAN_EMPTY_COMPACT)
        assertTrue(OneFilledVolt.SETTINGS_EXPORT_IS_PRIMARY)
        assertEquals("Export to file", AccessibilityMatrix.page("settings").voltAction)
    }
}
