package com.sinura.personaltrainer.timer

import com.sinura.personaltrainer.domain.RestTimerSnapshot
import com.sinura.personaltrainer.util.IdFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the running rest timer.
 *
 * Every mutation writes through to [persistence] (when one is wired) so the timer survives
 * process death — the in-memory flow is the fast path, disk is the durable one. The default
 * null persistence keeps this constructible in plain JVM tests.
 */
class RestTimerStore(
    private val persistence: RestTimerStatePersistence? = null,
    private val ids: IdFactory = IdFactory.Uuid,
) {
    private val snapshotState = MutableStateFlow(RestTimerSnapshot())
    val snapshot: StateFlow<RestTimerSnapshot> = snapshotState.asStateFlow()

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
            nowElapsedRealtime,
            nowWallClockMillis,
        )
    }

    /**
     * Add or remove time from a RUNNING rest.
     *
     * A no-op when idle, in either direction. Starting rest is [start]'s job: previously a
     * positive delta on a cleared store would spawn a brand-new running timer with a null
     * session, so tapping "+15s" on a stale notification just as rest completed resurrected
     * a phantom countdown detached from any workout.
     */
    fun adjust(
        deltaSeconds: Int,
        nowElapsedRealtime: Long,
        nowWallClockMillis: Long = System.currentTimeMillis(),
    ) {
        val current = snapshotState.value
        if (!current.running) return
        val remaining = current.remainingSeconds(nowElapsedRealtime)
        val next = (remaining + deltaSeconds).coerceAtLeast(0)
        if (next == 0) {
            clear()
            return
        }
        publish(
            RestTimerSnapshot(
                running = true,
                endsAtElapsedRealtime = nowElapsedRealtime + next * 1000L,
                totalSeconds = maxOf(current.totalSeconds, next),
                sessionId = current.sessionId,
                timerId = ids.newId(),
            ),
            nowElapsedRealtime,
            nowWallClockMillis,
        )
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
            nowElapsedRealtime,
            nowWallClockMillis,
        )
    }

    fun clear() {
        snapshotState.value = RestTimerSnapshot()
        persistence?.clear()
    }

    private fun publish(
        next: RestTimerSnapshot,
        nowElapsedRealtime: Long,
        nowWallClockMillis: Long,
    ) {
        snapshotState.value = next
        persistence?.save(
            RestTimerRehydrator.toPersisted(
                endsAtElapsedRealtime = next.endsAtElapsedRealtime,
                totalSeconds = next.totalSeconds,
                sessionId = next.sessionId,
                nowElapsedRealtime = nowElapsedRealtime,
                nowWallClockMillis = nowWallClockMillis,
                timerId = next.timerId,
            ),
        )
    }
}
