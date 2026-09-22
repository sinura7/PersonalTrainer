package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LogReceiptCopyTest {
    @Test
    fun receiptNamesTheOrdinalPayloadAndRpe() {
        val payload = LogReceiptCopy.payload(
            weightKg = 100.0,
            reps = 5,
            loadClass = LoadClass.LOADED,
            unit = WeightUnit.KG,
            rpe = 8,
        )
        assertEquals("100 kg × 5 · RPE 8", payload)
        assertEquals(
            "Working set 2 of 4 logged · 100 kg × 5 · RPE 8",
            LogReceiptCopy.line(
                LogReceiptCopy.ordinal(
                    isWarmup = false,
                    warmupAfter = 0,
                    workingAfter = 2,
                    targetSets = 4,
                ),
                payload,
            ),
        )
        assertEquals(
            "Warm-up 1",
            LogReceiptCopy.ordinal(
                isWarmup = true,
                warmupAfter = 1,
                workingAfter = 0,
                targetSets = 4,
            ),
        )
        assertEquals(
            "Extra 1",
            LogReceiptCopy.ordinal(
                isWarmup = false,
                warmupAfter = 0,
                workingAfter = 5,
                targetSets = 4,
            ),
        )
    }
}
