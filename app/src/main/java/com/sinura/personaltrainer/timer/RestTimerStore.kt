package com.sinura.personaltrainer.timer

import com.sinura.personaltrainer.domain.RestTimerSnapshot
import com.sinura.personaltrainer.util.IdFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the running rest timer.
 *
 * Memory is the fast path. Disk and alarm arming are the controller's
 * ordered IO job (publish snapshot, then persist, then arm) so a Log tap
 * does not `commit()` SharedPreferences on the main thread.
 *
 * It is written from more than one thread: the screens and the notification's ±15 on Main,
 * the alarm's completion on `Dispatchers.Default`. The two changes that depend on what was
 * there — [adjust] and [clearIfCurrent] — are compare-and-set, so neither lands on a snapshot
 * it did not read (ADR-012 decision 1). [start], [restore] and [clear] replace whatever is
 * there by design.
 *
 * It remembers the last few rests it has held ([hasHeld]). An empty store that once held a
 * rest was emptied by a Skip, a stop or a -15 to zero, or by a finish that already announced
 * it: either way there is nothing left to finish. A process started after death has held
 * nothing, so a rest that ran out while it was dead still completes.
 */
class RestTimerStore(
    private val ids: IdFactory = IdFactory.Uuid,
) {
    private val snapshotState = MutableStateFlow(RestTimerSnapshot())
    val snapshot: StateFlow<RestTimerSnapshot> = snapshotState.asStateFlow()
    private val heldLock = Any()
    private val held = ArrayDeque<String>()

    fun current(): RestTimerSnapshot = snapshotState.value

    fun start(
        totalSeconds: Int,
        sessionId: String?,
        nowElapsedRealtime: Long,
        nowWallClockMillis: Long = System.currentTimeMillis(),
    ) {
        val safe = totalSeconds.coerceAtLeast(1)
        publish(
            RestTimerSnapshot(
                running = true,
                endsAtElapsedRealtime = nowElapsedRealtime + safe * 1000L,
                totalSeconds = safe,
                sessionId = sessionId,
                timerId = ids.newId(),
            ),
        )
    }

    /**
     * Add or remove time from a RUNNING rest, and say what came of it.
     *
     * A no-op when idle, in either direction. Starting rest is [start]'s job: previously a
     * positive delta on a cleared store would spawn a brand-new running timer with a null
     * session, so tapping "+15s" on a stale notification just as rest completed resurrected
     * a phantom countdown detached from any workout.
     *
     * The replacement is written only over the snapshot it was computed from. It used to read,
     * compute and then write plainly, so a completion that cleared the store in between was
     * overwritten and the finished rest came back under a new id.
     */
    fun adjust(
        deltaSeconds: Int,
        nowElapsedRealtime: Long,
        nowWallClockMillis: Long = System.currentTimeMillis(),
    ): RestAdjustment {
        while (true) {
            val current = snapshotState.value
            if (!current.running) return RestAdjustment.Idle
            val remaining = current.remainingSeconds(nowElapsedRealtime)
            val next = (remaining + deltaSeconds).coerceAtLeast(0)
            val replacement = if (next == 0) {
                RestTimerSnapshot()
            } else {
                RestTimerSnapshot(
                    running = true,
                    endsAtElapsedRealtime = nowElapsedRealtime + next * 1000L,
                    totalSeconds = maxOf(current.totalSeconds, next),
                    sessionId = current.sessionId,
                    timerId = ids.newId(),
                ).also { remember(it.timerId) }
            }
            if (snapshotState.compareAndSet(current, replacement)) {
                return if (next == 0) RestAdjustment.Ended(current.timerId) else RestAdjustment.Running(replacement)
            }
        }
    }

    /** Restore a timer read back from disk; [endsAtElapsedRealtime] must already be rebased. */
    fun restore(
        endsAtElapsedRealtime: Long,
        totalSeconds: Int,
        sessionId: String?,
        nowElapsedRealtime: Long,
        nowWallClockMillis: Long = System.currentTimeMillis(),
        timerId: String,
    ) {
        publish(
            RestTimerSnapshot(
                running = true,
                endsAtElapsedRealtime = endsAtElapsedRealtime,
                totalSeconds = totalSeconds.coerceAtLeast(1),
                sessionId = sessionId,
                timerId = timerId,
            ),
        )
    }

    fun clear() {
        snapshotState.value = RestTimerSnapshot()
    }

    /**
     * Clears the store when it holds [timerId], or nothing is running. Returns what was there,
     * or null — and leaves it alone — when a newer timer has replaced [timerId]: a stop or a
     * completion for an old id must never clear its replacement (ADR-012 decision 1).
     */
    fun clearIfCurrent(timerId: String): RestTimerSnapshot? {
        while (true) {
            val current = snapshotState.value
            if (current.running && current.timerId != timerId) return null
            if (snapshotState.compareAndSet(current, RestTimerSnapshot())) return current
        }
    }

    /**
     * Whether [timerId] is among the last [HELD_MEMORY] rests this store has held in this
     * process. Remembered before the rest is published, so nothing can see it running before it
     * counts as held. Only the newest can meet an empty store: the alarm, the row and the
     * snapshot all follow the latest rest.
     */
    fun hasHeld(timerId: String): Boolean = synchronized(heldLock) { timerId in held }

    private fun publish(next: RestTimerSnapshot) {
        if (next.running) remember(next.timerId)
        snapshotState.value = next
    }

    private fun remember(timerId: String) {
        if (timerId.isBlank()) return
        synchronized(heldLock) {
            held.remove(timerId)
            held.addLast(timerId)
            while (held.size > HELD_MEMORY) held.removeFirst()
        }
    }

    internal companion object {
        /** Far more than one session's in-flight alarms; old ids are stale by their row. */
        const val HELD_MEMORY = 16
    }
}

/** What a [RestTimerStore.adjust] did: nothing was running, the rest ended, or it runs on as [snapshot]. */
sealed interface RestAdjustment {
    data object Idle : RestAdjustment

    /** The adjustment took the rest to zero; [timerId] is the rest that ended. */
    data class Ended(val timerId: String) : RestAdjustment

    data class Running(val snapshot: RestTimerSnapshot) : RestAdjustment
}
