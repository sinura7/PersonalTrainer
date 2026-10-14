package com.sinura.personaltrainer.data.draft

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.data.repository.MissedWorkProposal
import com.sinura.personaltrainer.data.repository.validate
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.MissedWorkChoice
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.util.runCatchingCancellable
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class MissedWorkHandle internal constructor(
    internal val claim: Long,
    internal var checkpoint: DraftCheckpoint<MissedWorkProposal, MissedWorkProposal>?,
) {
    val proposal: MissedWorkProposal? get() = checkpoint?.draft ?: checkpoint?.pending?.value
    val pending: Boolean get() = checkpoint?.pending != null
    val readable: Boolean get() = checkpoint != null
}

/** One retained proposal for the owning schedule, with its immutable week and command identity.
 * Home and Plan share this owner; a second visit cannot replace an outstanding proposal silently.
 */
internal class MissedWorkDrafts(
    store: DurableDraftStore,
    private val maintenance: DbMaintenance,
    private val restoreOpen: () -> Boolean,
    private val time: TimePort,
    private val preview: suspend (MissedWorkChoice, CivilDate, Long, Int, String, Long) -> MissedWorkProposal,
    private val inspectSaved: suspend (MissedWorkProposal) -> DraftRecoveryOutcome,
    private val commitLocked: suspend (MissedWorkProposal) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val binding = store.bind(DraftOwner(DraftForm.SCHEDULE, "schedule:missed-work"), MissedWorkCodec(time))
    private val serial = Mutex()
    private var claim = 0L

    /** Reconstruct privately. No opening, inspection or recovery action replays a business write. */
    suspend fun open(): MissedWorkHandle = ordered {
        MissedWorkHandle(++claim, runCatchingCancellable { read() }.getOrNull())
    }

    suspend fun propose(handle: MissedWorkHandle, choice: MissedWorkChoice, weekStart: Long) = ordered {
        current(handle)
        val prior = checkNotNull(handle.checkpoint) { "The retained proposal is unavailable" }
        check(prior.pending == null && !prior.needsReconciliation) { "Review the outstanding update first" }
        check(prior.draft == null || prior.draft.weekStartEpochDay == weekStart) { "Review the retained week first" }
        val now = time.captureNow()
        val proposal = preview(choice, CivilDate.fromEpochDay(weekStart), now.localEpochDay,
            time.wallMinutesOfDay(now.instantMillis, now.zoneId), now.zoneId, now.instantMillis)
        proposal.validate(time)
        val stored = read()
        check(stored.version == prior.version) { "The retained proposal changed" }
        val result = binding.checkpoint(stored.version, proposal)
        handle.checkpoint = if (result is DraftChange.Written) result.checkpoint else read().also {
            check(it.draft == proposal && !it.needsReconciliation) { "The preview was not kept" }
        }
    }

    /** Readback establishes retry admission. A new civil day/zone requires another reviewed preview. */
    suspend fun inspect(handle: MissedWorkHandle): DraftRecoveryOutcome = ordered {
        current(handle)
        inspectLocked(handle)
    }

    suspend fun confirm(handle: MissedWorkHandle): DraftRecoveryOutcome = ordered {
        current(handle)
        var checkpoint = checkNotNull(handle.checkpoint)
        val proposal = checkNotNull(handle.proposal) { "No proposal is ready" }
        if (checkpoint.pending == null) {
            check(!checkpoint.needsReconciliation) { "Review the restored schedule first" }
            val identity = UUID.randomUUID().toString()
            val admitted = binding.beginSave(checkpoint.version, proposal, identity, proposal)
            checkpoint = if (admitted is DraftChange.Written) admitted.checkpoint else read().also {
                check(it.pending?.identity == identity && it.pending?.value == proposal && !it.needsReconciliation) {
                    "The update command was not confirmed"
                }
            }
            handle.checkpoint = checkpoint
        }
        val observed = inspectLocked(handle)
        if (observed != DraftRecoveryOutcome.UNCOMMITTED) return@ordered observed
        checkpoint = checkNotNull(handle.checkpoint)
        val pending = checkNotNull(checkpoint.pending)
        check(pending.phase == DraftCommandPhase.RETRYABLE)
        handle.checkpoint = written(binding.retry(checkpoint.version, pending.identity))
        // An acknowledgement may fail after Room committed. Inspect before exposing Retry.
        runCatchingCancellable { commitLocked(pending.value) }
        inspectLocked(handle)
    }

    /** Explicit fresh review replaces a retained proposal only after a new preview is readable. */
    suspend fun reviewCurrent(handle: MissedWorkHandle, weekStart: Long) = ordered {
        current(handle)
        var stored = read()
        check(stored.version == checkNotNull(handle.checkpoint).version)
        val old = checkNotNull(handle.proposal)
        val observed = inspectSaved(old)
        check(observed != DraftRecoveryOutcome.UNAVAILABLE) { "The schedule could not be read" }
        if (observed == DraftRecoveryOutcome.EXACT_SAVED) {
            inspectLocked(handle)
            return@ordered
        }
        val now = time.captureNow()
        val fresh = preview(old.choice, CivilDate.fromEpochDay(weekStart), now.localEpochDay,
            time.wallMinutesOfDay(now.instantMillis, now.zoneId), now.zoneId, now.instantMillis)
        fresh.validate(time)
        // Release does not grant mutation. The old draft stays intact if the following
        // checkpoint fails; the new command still requires an explicit Confirm.
        if (stored.pending != null) {
            stored = written(binding.releaseForReview(stored.version, stored.pending!!.identity, stored.currentGeneration))
        } else if (stored.needsReconciliation) {
            stored = written(binding.reconcileOwner(stored.version, stored.currentGeneration))
        }
        handle.checkpoint = stored
        val replacement = binding.checkpoint(stored.version, fresh)
        handle.checkpoint = if (replacement is DraftChange.Written) replacement.checkpoint else read().also {
            check(it.draft == fresh && !it.needsReconciliation) { "The new preview was not kept" }
        }
    }

    private suspend fun inspectLocked(handle: MissedWorkHandle): DraftRecoveryOutcome {
        val before = checkNotNull(handle.checkpoint) { "The retained proposal is unavailable" }
        var stored = read()
        check(stored.version == before.version) { "A newer visit owns this proposal" }
        handle.checkpoint = stored
        val proposal = checkNotNull(stored.pending?.value ?: stored.draft)
        val actual = runCatchingCancellable { inspectSaved(proposal) }.getOrElse { return DraftRecoveryOutcome.UNAVAILABLE }
        val now = time.captureNow()
        val outcome = when {
            actual == DraftRecoveryOutcome.EXACT_SAVED -> actual
            stored.pending?.phase == DraftCommandPhase.ACCEPTED -> DraftRecoveryOutcome.CONFLICT
            actual == DraftRecoveryOutcome.UNCOMMITTED &&
                (now.localEpochDay != proposal.todayEpochDay || now.zoneId != proposal.deviceZoneId) -> DraftRecoveryOutcome.CONFLICT
            else -> actual
        }
        if (outcome != DraftRecoveryOutcome.EXACT_SAVED && outcome != DraftRecoveryOutcome.UNCOMMITTED) return outcome
        if (stored.needsReconciliation) {
            stored = written(binding.reconcileOwner(stored.version, stored.currentGeneration))
            handle.checkpoint = stored
        }
        val pending = stored.pending
        if (pending != null && pending.phase != DraftCommandPhase.ACCEPTED) {
            stored = written(binding.inspect(stored.version, pending.identity, outcome))
            handle.checkpoint = stored
        }
        if (outcome == DraftRecoveryOutcome.EXACT_SAVED) {
            handle.checkpoint = written(binding.clear(stored.version))
        }
        return outcome
    }

    /** Explicitly discard only private work. Any already committed schedule/history remains. */
    suspend fun discard(handle: MissedWorkHandle) = ordered {
        current(handle)
        val loaded = binding.read()
        handle.checkpoint = written(if (loaded is DraftRead.PresentCheckpoint)
            binding.discard(loaded.checkpoint.version, loaded.checkpoint.currentGeneration)
        else binding.discardUnreadable())
    }

    private fun current(handle: MissedWorkHandle) { check(handle.claim == claim) { "A newer visit owns this proposal" } }
    private suspend fun read(): DraftCheckpoint<MissedWorkProposal, MissedWorkProposal> =
        (binding.read() as? DraftRead.PresentCheckpoint)?.checkpoint ?: error("The retained proposal could not be read")
    private fun written(change: DraftChange<MissedWorkProposal, MissedWorkProposal>): DraftCheckpoint<MissedWorkProposal, MissedWorkProposal> =
        (change as? DraftChange.Written)?.checkpoint ?: error("The private proposal operation was not confirmed")
    private suspend fun <T> ordered(block: suspend () -> T): T = withContext(dispatcher) {
        maintenance.withMaintenanceLock { serial.withLock { check(!restoreOpen()) { "Restore needs reconciliation" }; block() } }
    }
}

/** Canonical round-trip rejects unknown/missing fields, coerced types and fabricated policy results. */
internal class MissedWorkCodec(private val time: TimePort) : DraftCodec<MissedWorkProposal, MissedWorkProposal> {
    private val gson = Gson()
    override val format = "schedule-missed-work"
    override val version = 1
    override fun encodeDraft(value: MissedWorkProposal): JsonElement = gson.toJsonTree(value)
    override fun encodeCommand(value: MissedWorkProposal): JsonElement = encodeDraft(value)
    override fun decodeDraft(value: JsonElement): MissedWorkProposal {
        check(value.isJsonObject)
        checkNumbers(value)
        val decoded = checkNotNull(gson.fromJson(value, MissedWorkProposal::class.java))
        check(encodeDraft(decoded) == value)
        decoded.validate(time)
        return decoded
    }
    override fun decodeCommand(value: JsonElement): MissedWorkProposal = decodeDraft(value)
    private fun checkNumbers(value: JsonElement) {
        when {
            value.isJsonObject -> value.asJsonObject.entrySet().forEach { checkNumbers(it.value) }
            value.isJsonArray -> value.asJsonArray.forEach(::checkNumbers)
            value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> check(value.asString.matches(Regex("-?[0-9]+")))
        }
    }
}
