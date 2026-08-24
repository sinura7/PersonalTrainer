package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.domain.DataHealth
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
