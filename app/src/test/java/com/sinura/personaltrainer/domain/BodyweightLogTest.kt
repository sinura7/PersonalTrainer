package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The question the guided setup was asking for nothing.
 *
 * A bodyweight figure feeds no calculation — bodyweight lifts are measured in reps, so nothing
 * needs to price the body doing the lifting. What it is actually for is the companion question
 * to a block: twelve weeks of added reps means one thing at a steady bodyweight and something
 * else at plus four kilos.
 */
class BodyweightLogTest {
    private fun entry(day: Long, kg: Double) = BodyweightEntry(day, kg)

    @Test
    fun aLogSurvivesTheRoundTrip() {
        val log = listOf(entry(20_000, 78.0), entry(20_030, 79.5))
        assertEquals(log, BodyweightLog.decode(BodyweightLog.encode(log)))
    }

    @Test
    fun nothingLoggedIsNoEntries() {
        assertEquals(emptyList<BodyweightEntry>(), BodyweightLog.decode(null))
        assertEquals(emptyList<BodyweightEntry>(), BodyweightLog.decode(""))
    }

    @Test
    fun aMalformedEntryIsDroppedRatherThanThrown() {
        val decoded = BodyweightLog.decode("20000:78,rubbish,20030:heavy,,20060:80")
        assertEquals(listOf(20_000L, 20_060L), decoded.map { it.epochDay })
    }

    @Test
    fun anImpossibleWeightIsNotAWeighIn() {
        assertTrue(BodyweightLog.decode("20000:2").isEmpty())
        assertTrue(BodyweightLog.decode("20000:900").isEmpty())
    }

    @Test
    fun oneEntryPerDayAndTheLastOneWins() {
        // Bodyweight swings a kilo between morning and evening on water alone. Several readings
        // from one day are noise dressed as a trend, and the last one typed is the one meant.
        var log = BodyweightLog.record(emptyList(), entry(20_000, 78.0))
        log = BodyweightLog.record(log, entry(20_000, 79.0))
        assertEquals(1, log.size)
        assertEquals(79.0, log.single().kg, 0.001)
    }

    @Test
    fun theLogIsBoundedAndDropsTheOldest() {
        var log = emptyList<BodyweightEntry>()
        repeat(BodyweightLog.MAX_ENTRIES + 10) { n ->
            log = BodyweightLog.record(log, entry(20_000L + n, 80.0))
        }
        assertEquals(BodyweightLog.MAX_ENTRIES, log.size)
        assertEquals(20_010L, log.first().epochDay)
    }

    @Test
    fun aWeightIsTrueUntilItIsMeasuredAgain() {
        val log = listOf(entry(20_000, 78.0), entry(20_060, 82.0))
        assertEquals(78.0, BodyweightLog.nearest(log, 20_030)!!.kg, 0.001)
        assertEquals(82.0, BodyweightLog.nearest(log, 20_090)!!.kg, 0.001)
    }

    @Test
    fun aDateBeforeEveryWeighInFallsForwardToTheFirst() {
        // A block that started before anyone stepped on a scale is better described by the
        // first reading than by nothing at all.
        val log = listOf(entry(20_050, 78.0))
        assertEquals(78.0, BodyweightLog.nearest(log, 20_000)!!.kg, 0.001)
        assertNull(BodyweightLog.nearest(emptyList(), 20_000))
    }

    @Test
    fun halfAKiloIsWaterNotATrend() {
        assertTrue(BodyweightChange(fromKg = 78.0, toKg = 78.3).isFlat)
        assertTrue(!BodyweightChange(fromKg = 78.0, toKg = 81.0).isFlat)
        assertEquals(3.0, BodyweightChange(fromKg = 78.0, toKg = 81.0).deltaKg, 0.001)
    }
}
