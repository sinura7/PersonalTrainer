package com.sinura.personaltrainer.testutil

import com.sinura.personaltrainer.data.local.dao.BodyweightDao
import com.sinura.personaltrainer.data.local.dao.TrainingBlockDao
import com.sinura.personaltrainer.data.local.entity.BodyweightEntryEntity
import com.sinura.personaltrainer.data.local.entity.TrainingBlockEntity
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Flipped by a test between reads. Read on Room's threads, so it is volatile. */
class ReadGate(@Volatile var shouldFail: Boolean) {
    /** How many reads the gate has refused, so a test can wait for the failure to happen. */
    val refusals = AtomicInteger()

    fun check(what: String) {
        if (shouldFail) {
            refusals.incrementAndGet()
            error("boom: Room could not read $what")
        }
    }
}

/**
 * The finished blocks observation, each value of it made to throw while the gate is shut, so
 * a screen can meet the failure on its first read or on a later one. Every other method is
 * the real thing.
 */
class FailingPastBlocksDao(
    private val delegate: TrainingBlockDao,
    private val gate: ReadGate,
) : TrainingBlockDao by delegate {
    override fun observePast(): Flow<List<TrainingBlockEntity>> =
        delegate.observePast().map { rows -> rows.also { gate.check("past blocks") } }
}

/** The weigh-in observation made to throw while the gate is shut, as for past blocks. */
class FailingWeighInsDao(
    private val delegate: BodyweightDao,
    private val gate: ReadGate,
) : BodyweightDao by delegate {
    override fun observeAll(): Flow<List<BodyweightEntryEntity>> =
        delegate.observeAll().map { rows -> rows.also { gate.check("the weigh-ins") } }
}
