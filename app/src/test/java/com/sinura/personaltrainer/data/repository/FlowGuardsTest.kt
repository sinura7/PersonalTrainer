package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.domain.DataHealth
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowGuardsTest {
    @Test
    fun emptyListIsAvailable() = runTest {
        val health = flow {
            emit(emptyList<String>())
        }.observeHealth("workout history").toList()
        assertEquals(1, health.size)
        assertTrue(health.single() is DataHealth.Available)
        assertEquals(emptyList<String>(), health.single().presentValue())
    }

    @Test
    fun firstFailureIsUnavailable() = runTest {
        val health = flow<List<String>> {
            throw IOException("disk")
        }.observeHealth("workout history").toList()
        assertEquals(1, health.size)
        assertTrue(health.single() is DataHealth.Unavailable)
        assertEquals("workout history", (health.single() as DataHealth.Unavailable).what)
    }

    @Test
    fun laterFailureKeepsTheLastSuccessfulValue() = runTest {
        val health = flow {
            emit(listOf("squat"))
            throw IOException("disk")
        }.observeHealth("workout history").toList()
        assertEquals(2, health.size)
        assertTrue(health[0] is DataHealth.Available)
        assertTrue(health[1] is DataHealth.Degraded)
        assertEquals(listOf("squat"), health[1].presentValue())
    }

    @Test
    fun successfulNullThenFailureIsDegraded() = runTest {
        val health = flow {
            emit(null as String?)
            throw IOException("disk")
        }.observeHealth("the in-progress session").toList()
        assertEquals(2, health.size)
        assertTrue(health[1] is DataHealth.Degraded)
        assertNull(health[1].presentValue())
    }

    @Test
    fun presentValuesDropsUnavailableAndNeverTurnsItIntoEmpty() = runTest {
        val values = flow {
            emit(DataHealth.Unavailable("workout history"))
        }.presentValues<List<String>>().toList()
        assertTrue(values.isEmpty())
    }

    @Test
    fun presentValuesEmitsAvailableAndDegraded() = runTest {
        val values = flow {
            emit(DataHealth.Available("a"))
            emit(DataHealth.Degraded("b", "workout history"))
            emit(DataHealth.Unavailable("workout history"))
        }.presentValues().toList()
        assertEquals(listOf("a", "b"), values)
    }

    @Test
    fun cancellationIsNotAReadFault() = runTest {
        val seen = mutableListOf<DataHealth<Int>>()
        val job = launch {
            flow {
                emit(1)
                awaitCancellation()
            }.observeHealth("session activity").collect { seen.add(it) }
        }
        testScheduler.advanceUntilIdle()
        job.cancelAndJoin()
        assertEquals(1, seen.size)
        assertTrue(seen.single() is DataHealth.Available)
    }

    @Test
    fun unavailableIsReportedWhileTheOtherRequiredInputIsStillWaiting() = runTest {
        val waiting = flow<DataHealth<String>> { awaitCancellation() }
        val result = combineHealth(flowOf(DataHealth.Unavailable("history")), waiting) {
            a: String, b: String -> a + b
        }.first()
        assertEquals(DataHealth.Unavailable("history"), result)
    }

    @Test
    fun availableNullIsAnAnswerAndDegradedKeepsItsMeaning() = runTest {
        val result = combineHealth(
            flowOf(DataHealth.Available(null as String?)),
            flowOf(DataHealth.Degraded(4, "history")),
        ) { a, b -> a to b }.first()
        assertEquals(DataHealth.Degraded(null to 4, "history"), result)
    }

    @Test
    fun unreadInputDoesNotProduceAnEmptyResult() = runTest {
        var computations = 0
        val values = mutableListOf<DataHealth<Int>>()
        val job = launch {
            combineHealth(
                flowOf(DataHealth.Available(emptyList<String>())),
                flow<DataHealth<Int>> { awaitCancellation() },
            ) { a, b -> computations++; a.size + b }.collect { values += it }
        }
        testScheduler.runCurrent()
        assertEquals(0, computations)
        assertTrue(values.isEmpty())
        job.cancelAndJoin()
    }

    @Test
    fun successfulNullThenFailureEndsWaitingWhileTheOtherRequiredInputHasNeverAnswered() = runTest {
        for (reverse in listOf(false, true)) {
            val values = MutableSharedFlow<DataHealth<String?>>()
            val waiting = flow<DataHealth<Int>> { awaitCancellation() }
            var transforms = 0
            val seen = mutableListOf<DataHealth<String>>()
            val combined = if (reverse) {
                combineHealth(waiting, values) { _, _ -> transforms++; "a full value" }
            } else {
                combineHealth(values, waiting) { _, _ -> transforms++; "a full value" }
            }
            val job = launch { combined.collect { seen += it } }
            try {
                testScheduler.runCurrent()
                values.emit(DataHealth.Available(null))
                testScheduler.runCurrent()
                assertTrue("successful null must wait for the unread sibling", seen.isEmpty())
                assertEquals(0, transforms)
                values.emit(DataHealth.Degraded(null, "live activity"))
                testScheduler.runCurrent()
                assertEquals("a known fault must end waiting in either input order", listOf(DataHealth.Unavailable("live activity")), seen)
                assertEquals("partial inputs must never become a full or empty value", 0, transforms)
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun mappingFailureDoesNotTerminateLaterEmissions() = runTest {
        val upstream = MutableSharedFlow<DataHealth<Int>>()
        val seen = mutableListOf<DataHealth<Int>>()
        val job = launch {
            upstream.mapHealthCatching("summary") {
                if (it == 2) error("failed calculation")
                it * 10
            }.collect { seen += it }
        }
        testScheduler.runCurrent()
        upstream.emit(DataHealth.Available(1))
        testScheduler.runCurrent()
        upstream.emit(DataHealth.Available(2))
        testScheduler.runCurrent()
        upstream.emit(DataHealth.Available(3))
        testScheduler.runCurrent()
        assertEquals(listOf(DataHealth.Available(10), DataHealth.Degraded(10, "summary"), DataHealth.Available(30)), seen)
        job.cancelAndJoin()
    }

    @Test
    fun mappingCancellationIsNotUnavailable() = runTest {
        val seen = mutableListOf<DataHealth<Int>>()
        val job = launch {
            flowOf(DataHealth.Available(1)).mapHealthCatching("summary") {
                awaitCancellation()
            }.collect { seen += it }
        }
        testScheduler.runCurrent()
        job.cancelAndJoin()
        assertTrue(seen.isEmpty())
    }

    @Test
    fun requiredFailurePublishesBeforeSlowCancellationAndObsoleteSuccessCannotOverwriteIt() = runTest {
        val upstream = MutableSharedFlow<DataHealth<Int>>()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val seen = mutableListOf<DataHealth<Int>>()
        var heldOnce = false
        val job = launch {
            upstream.mapHealthCatching("summary") { value ->
                if (value == 2 && !heldOnce) {
                    heldOnce = true
                    withContext(NonCancellable) {
                        started.complete(Unit)
                        release.await()
                    }
                }
                value * 10
            }.collect { seen += it }
        }
        try {
            testScheduler.runCurrent()
            upstream.emit(DataHealth.Available(1))
            testScheduler.runCurrent()
            assertEquals(DataHealth.Available(10), seen.last())
            upstream.emit(DataHealth.Available(2))
            testScheduler.runCurrent()
            assertTrue(started.isCompleted)
            upstream.emit(DataHealth.Available(3))
            testScheduler.runCurrent()
            val fault = launch { upstream.emit(DataHealth.Degraded(3, "routines")) }
            testScheduler.runCurrent()
            assertTrue("a join blocked failure observation", fault.isCompleted)
            assertFalse(release.isCompleted)
            assertEquals(DataHealth.Degraded(10, "routines"), seen.last())
            val firstFailure = seen.lastIndex
            release.complete(Unit)
            testScheduler.runCurrent()
            assertTrue("a cancelled old calculation published success after failure", seen.drop(firstFailure).none {
                it is DataHealth.Available
            })
            assertEquals(DataHealth.Degraded(30, "routines"), seen.last())
        } finally {
            release.complete(Unit)
            job.cancelAndJoin()
        }
    }
}
