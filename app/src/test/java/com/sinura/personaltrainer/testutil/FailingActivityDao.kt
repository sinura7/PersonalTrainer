package com.sinura.personaltrainer.testutil

import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.relation.ActivitySessionGraph

/** Flipped by a test between reads, so the same screen can see one failure and then recover. */
class ActivityReadGate(var shouldFail: Boolean)

/**
 * The activity DAO with its single-row read made to throw on demand. Every other method
 * delegates, so writes and the rest of the screen's reads are the real thing. The same seam
 * the routine editor's hydration tests use: a Room fault without a broken database.
 */
class FailingGetGraphDao(
    private val delegate: ActivityDao,
    private val gate: ActivityReadGate,
) : ActivityDao by delegate {
    override suspend fun getSessionGraph(id: String): ActivitySessionGraph? {
        if (gate.shouldFail) error("boom: Room could not read activity $id")
        return delegate.getSessionGraph(id)
    }
}
