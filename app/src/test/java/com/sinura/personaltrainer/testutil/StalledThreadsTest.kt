package com.sinura.personaltrainer.testutil

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A diagnostic that quietly reports nothing is worse than no diagnostic at all: the next
 * wedge would burn its one reproduction and teach us the same nothing the last one did.
 *
 * [stalledThreads] is only ever read from a wait that has already timed out, so it can never
 * be exercised by the failure it exists for. This exercises it deliberately instead, against
 * the same graph the ViewModel tests build, and pins the one thread the open investigation
 * turns on — Room's single-slot transaction executor, whose state (BLOCKED on a connection
 * versus IDLE in `getTask` with work still queued) is the fact that would settle it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StalledThreadsTest {
    private lateinit var deps: FakeAppDependencies

    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun theRoomThreadsTheInvestigationTurnsOnAreNamedAndTheirStateReported() = runBlocking {
        // Touch Room so both executors have actually started their threads: a pool that has
        // never run a task has no threads to dump, and a dump of nothing proves nothing.
        deps.routineRepository.getById("no-such-routine")
        deps.routineRepository.observeAll()

        val dump = stalledThreads()

        assertTrue(dump, dump.contains("room-txn-test"))
        assertTrue(dump, dump.contains("threads that could be holding this up"))
        // The state is the whole point — parked-and-idle and parked-and-stuck look identical
        // without it.
        assertTrue(dump, Regex("\"room-txn-test\" [A-Z_]+").containsMatchIn(dump))
    }

    @Test
    fun theDumpIsShortEnoughToReadInACiLog() = runBlocking {
        // Against a live graph, not an empty JVM: a dump of nothing is trivially short and
        // would let an unfiltered fifty-thread dump through unnoticed.
        deps.routineRepository.getById("no-such-routine")
        deps.routineRepository.observeAll()

        val dump = stalledThreads()

        assertTrue(dump, dump.contains("room-txn-test"))
        assertTrue("dump was ${dump.length} chars", dump.length < 20_000)
    }

    @Test
    fun aLargeDumpKeepsTheTransactionStateAndReportsItsTruncation() {
        val threads = (1..80).associate { number ->
            Thread("DefaultDispatcher-worker-$number") to Array(16) {
                StackTraceElement("kotlinx.coroutines.${"LongFrame".repeat(24)}", "dispatch", "Dispatcher.kt", it)
            }
        }.toMutableMap()
        threads[Thread("room-txn-test")] = arrayOf(
            StackTraceElement("androidx.room.TransactionExecutor", "execute", "TransactionExecutor.kt", 38),
        )
        val dump = renderStalledThreads(threads)
        assertTrue("dump was ${dump.length} chars", dump.length < 20_000)
        assertTrue(dump, dump.contains("\"room-txn-test\" NEW"))
        assertTrue(dump, dump.contains("androidx.room.TransactionExecutor.execute"))
        assertTrue(dump, dump.indexOf("room-txn-test") < dump.indexOf("DefaultDispatcher-worker"))
        assertTrue(dump, dump.contains("thread dump truncated"))
    }

    @Test
    fun twoGraphsWithTheSameExecutorNameKeepBothStacks() {
        val dump = renderStalledThreads(mapOf(
            Thread("room-txn-test") to arrayOf(StackTraceElement("androidx.room.FirstGraph", "execute", "Room.kt", 1)),
            Thread("room-txn-test") to arrayOf(StackTraceElement("androidx.room.SecondGraph", "execute", "Room.kt", 2)),
        ))
        assertTrue(dump, dump.contains("androidx.room.FirstGraph.execute"))
        assertTrue(dump, dump.contains("androidx.room.SecondGraph.execute"))
    }
}
