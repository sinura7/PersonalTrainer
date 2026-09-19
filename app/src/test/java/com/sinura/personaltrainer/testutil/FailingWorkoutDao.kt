package com.sinura.personaltrainer.testutil

import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Flipped by a test between collects, so Retry can see one failure and then recover. */
class WorkoutReadGate(var shouldFail: Boolean)

/**
 * The workout DAO with its session observation made to throw on demand.
 * Every other method delegates, so writes and the rest of the screen's
 * reads are the real thing — the same seam [FailingGetGraphDao] is for
 * activity detail.
 */
class FailingObserveSessionDao(
    private val delegate: WorkoutDao,
    private val gate: WorkoutReadGate,
) : WorkoutDao by delegate {
    override fun observeSession(id: String): Flow<SessionWithDetails?> = flow {
        if (gate.shouldFail) error("boom: Room could not read session $id")
        emitAll(delegate.observeSession(id))
    }
}
