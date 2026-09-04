package com.sinura.personaltrainer.domain

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runtime seam types. Banned platform imports live in
 * `tools/check-domain-seams.py` (J4 remainder).
 */
class DomainSeamPolicyTest {
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
}
