package com.sinura.personaltrainer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerWritesTest {
    @Test fun anOlderFailedAddCannotRetryAfterANewerRemove() {
        val writes = PickerWrites()
        var retried = false
        val add = writes.begin("squat")
        val remove = writes.begin("squat")
        writes.fail(add, "Add failed") { retried = true }
        assertTrue(writes.busy)
        writes.succeed(remove)
        writes.retry()
        assertFalse(retried)
        assertFalse(writes.state.value.canRetry)
        assertFalse(writes.busy)
    }

    @Test fun aDistinctLiftSuccessPreservesTheFailedIntent() {
        val writes = PickerWrites()
        var retries = 0
        val a = writes.begin("a")
        val b = writes.begin("b")
        writes.fail(a, "Add a failed") { retries++ }
        writes.retry()
        assertEquals(0, retries)
        writes.succeed(b)
        assertEquals("Add a failed", writes.state.value.error)
        writes.retry()
        writes.retry()
        assertEquals(1, retries)
        assertEquals(null, writes.state.value.error)
    }

    @Test fun failuresRemainInOrderUntilRetriedOrAcknowledged() {
        val writes = PickerWrites()
        writes.fail(writes.begin("a"), "First")
        writes.fail(writes.begin("b"), "Second")
        assertEquals("First", writes.state.value.error)
        writes.dismissError()
        assertEquals("Second", writes.state.value.error)
        writes.dismissError()
        assertEquals(null, writes.state.value.error)
    }

    @Test fun cancellationReleasesOnlyItsOwnPendingWrite() {
        val writes = PickerWrites()
        val create = writes.begin("create", creating = true)
        val add = writes.begin("row")
        writes.cancel(create)
        assertFalse(writes.creating)
        assertEquals(setOf("row"), writes.state.value.pendingIds)
        writes.succeed(add, created = true)
        assertFalse(writes.busy)
        assertEquals(1, writes.state.value.createdGeneration)
    }
}
