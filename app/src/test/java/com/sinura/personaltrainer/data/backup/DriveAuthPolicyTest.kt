package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Drive About JSON parser. Source policy (no Google Sign-In, catalog pin)
 * lives in `tools/check-sdk-target.py` (J4 remainder).
 */
class DriveAuthPolicyTest {
    @Test
    fun aboutJsonYieldsEmailAndIgnoresJunk() {
        assertEquals(
            "owner@example.com",
            DriveAboutJson.parseAccountEmail(
                """{"user":{"emailAddress":"owner@example.com","displayName":"Owner"}}""",
            ),
        )
        assertNull(DriveAboutJson.parseAccountEmail("""{"kind":"drive#about"}"""))
        assertNull(DriveAboutJson.parseAccountEmail("not-json"))
        assertNull(DriveAboutJson.parseAccountEmail("""{"user":{"emailAddress":"  "}}"""))
    }
}
