package com.sinura.personaltrainer.domain

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Completion is claimed by timer id. A stale or early delivery cannot end
 * the rest that is actually running.
 */
class RestTimerClaimLedgerTest {
    private val ledger = RestTimerClaimLedger()

    @Test
    fun currentAndDueClaimsOnce() {
        assertEquals(
            RestTimerClaim.CLAIMED,
            ledger.decide("t1", "t1", nowElapsedRealtime = 90_000L, deadlineElapsedRealtime = 90_000L),
        )
        assertEquals(
            RestTimerClaim.ALREADY_CLAIMED,
            ledger.decide("t1", "t1", nowElapsedRealtime = 91_000L, deadlineElapsedRealtime = 90_000L),
        )
    }

    @Test
    fun staleIdIsIgnoredEvenWhenDue() {
        assertEquals(
            RestTimerClaim.STALE,
            ledger.decide("old", "current", nowElapsedRealtime = 90_000L, deadlineElapsedRealtime = 10_000L),
        )
        assertEquals(
            RestTimerClaim.CLAIMED,
            ledger.decide("current", "current", nowElapsedRealtime = 90_000L, deadlineElapsedRealtime = 90_000L),
        )
    }

    @Test
    fun blankIncomingIdIsStale() {
        assertEquals(
            RestTimerClaim.STALE,
            ledger.decide("", "t1", nowElapsedRealtime = 90_000L, deadlineElapsedRealtime = 90_000L),
        )
    }

    @Test
    fun earlyMatchingIdIsNotClaimed() {
        assertEquals(
            RestTimerClaim.EARLY,
            ledger.decide("t1", "t1", nowElapsedRealtime = 89_999L, deadlineElapsedRealtime = 90_000L),
        )
        assertEquals(
            RestTimerClaim.CLAIMED,
            ledger.decide("t1", "t1", nowElapsedRealtime = 90_000L, deadlineElapsedRealtime = 90_000L),
        )
    }

    @Test
    fun replaceLeavesThePreviousIdUnableToClaim() {
        assertEquals(
            RestTimerClaim.STALE,
            ledger.decide("first", "second", nowElapsedRealtime = 50_000L, deadlineElapsedRealtime = 50_000L),
        )
        assertEquals(
            RestTimerClaim.CLAIMED,
            ledger.decide("second", "second", nowElapsedRealtime = 50_000L, deadlineElapsedRealtime = 50_000L),
        )
    }

    @Test
    fun skipRaceDoesNotClaimAClearedRest() {
        assertEquals(
            RestTimerClaim.STALE,
            ledger.decide("gone", "", nowElapsedRealtime = 50_000L, deadlineElapsedRealtime = 50_000L),
        )
    }

    @Test
    fun concurrentClaimersProduceOneWinner() {
        val start = CountDownLatch(1)
        val done = CountDownLatch(16)
        val claimed = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(8) { runnable ->
            Thread(runnable, "claim-race").apply { isDaemon = true }
        }
        try {
            repeat(16) {
                pool.execute {
                    start.await()
                    if (ledger.decide("race", "race", 100L, 100L) == RestTimerClaim.CLAIMED) {
                        claimed.incrementAndGet()
                    }
                    done.countDown()
                }
            }
            start.countDown()
            assertEquals(true, done.await(2, TimeUnit.SECONDS))
            assertEquals(1, claimed.get())
        } finally {
            pool.shutdownNow()
        }
    }
}
