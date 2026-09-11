package com.sinura.personaltrainer.testutil

import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.dao.RecordSetRow
import com.sinura.personaltrainer.data.local.entity.ActivitySummaryRow
import com.sinura.personaltrainer.data.local.relation.ActivitySessionGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

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

/**
 * The completed-summaries observation made to throw on demand. History's sidecar
 * must mark stale (not vanish the strength list) and retry must not duplicate rows.
 */
class FailingObserveCompletedSummariesDao(
    private val delegate: ActivityDao,
    private val gate: ActivityReadGate,
) : ActivityDao by delegate {
    override fun observeCompletedSummaries(): Flow<List<ActivitySummaryRow>> = flow {
        if (gate.shouldFail) error("boom: Room could not read activity history")
        emitAll(delegate.observeCompletedSummaries())
    }
}

/**
 * Activity record-set observation made to throw on demand. History's records
 * sidecar must mark the page stale without dropping the list, and retry must
 * not duplicate a row.
 */
class FailingObserveActivityRecordSetsDao(
    private val delegate: ActivityDao,
    private val gate: ActivityReadGate,
) : ActivityDao by delegate {
    override fun observeCompletedStrengthSetRecords(): Flow<List<RecordSetRow>> = flow {
        if (gate.shouldFail) error("boom: Room could not read activity records")
        emitAll(delegate.observeCompletedStrengthSetRecords())
    }
}
