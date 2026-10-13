package com.sinura.personaltrainer.data.draft

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.HistoryPeriodSelection
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class HistoryPeriodHandle internal constructor(
    internal val claim: Long,
    internal var checkpoint: DraftCheckpoint<HistoryPeriodSelection, Unit>?,
) {
    internal var retirementRevision = 0L
    val remembered: HistoryPeriodSelection? get() = checkpoint?.draft
    val readable: Boolean get() = checkpoint != null
}

/** Shared ownership and CAS prevent a retired History from replacing a newer selection. */
internal class HistoryPeriodDrafts(store: DurableDraftStore) {
    private val binding = store.bind(DraftOwner(DraftForm.HISTORY, "selected-period"), HistoryPeriodCodec)
    private val serial = Mutex()
    private var claim = 0L

    suspend fun open(): HistoryPeriodHandle = serial.withLock {
        val admitted = ++claim
        // Even a failed reconstruction owns a lease for explicit private-only Reset.
        HistoryPeriodHandle(admitted, runCatchingCancellable { reconciled(read()) }.getOrNull())
    }

    suspend fun reload(handle: HistoryPeriodHandle) = serial.withLock {
        requireCurrent(handle)
        handle.checkpoint = null
        handle.checkpoint = reconciled(read())
    }

    suspend fun keep(handle: HistoryPeriodHandle, value: HistoryPeriodSelection) {
        val admittedRevision = handle.retirementRevision
        serial.withLock {
            requireCurrent(handle)
            check(admittedRevision == handle.retirementRevision) { "History period preference was reset" }
            val before = checkNotNull(handle.checkpoint) { "History period preference needs recovery" }
            val current = read()
            // An uncertain replacement may already contain the intended preference.
            if (current.draft == value) {
                handle.checkpoint = reconciled(current)
                return@withLock
            }
            check(current.version == before.version) { "History period preference changed" }
            handle.checkpoint = reconciled(current)
            val changed = binding.checkpoint(checkNotNull(handle.checkpoint).version, value)
            if (changed is DraftChange.Written) {
                handle.checkpoint = changed.checkpoint
            } else {
                // A lost private-file acknowledgement must not be mistaken for a failed keep.
                val inspected = read()
                check(inspected.draft == value) { "History period preference was not kept" }
                handle.checkpoint = reconciled(inspected)
            }
        }
    }

    /** Explicitly forget only this navigation preference, including unreadable private data. */
    suspend fun reset(handle: HistoryPeriodHandle) = serial.withLock {
        requireCurrent(handle)
        handle.retirementRevision += 1L
        handle.checkpoint = null
        val loaded = binding.read()
        val cleared = if (loaded is DraftRead.PresentCheckpoint) {
            binding.discard(loaded.checkpoint.version, loaded.checkpoint.currentGeneration)
        } else {
            binding.discardUnreadable()
        }
        check(cleared is DraftChange.Written) { "History period preference could not be reset" }
        handle.checkpoint = cleared.checkpoint
    }

    private fun requireCurrent(handle: HistoryPeriodHandle) {
        check(handle.claim == claim) { "History period preference belongs to a newer visit" }
    }

    private suspend fun read(): DraftCheckpoint<HistoryPeriodSelection, Unit> {
        val loaded = binding.read()
        check(loaded is DraftRead.PresentCheckpoint) { "History period preference could not be read" }
        check(loaded.checkpoint.pending == null) { "Unexpected History period preference command" }
        return loaded.checkpoint
    }

    private suspend fun reconciled(
        current: DraftCheckpoint<HistoryPeriodSelection, Unit>,
    ): DraftCheckpoint<HistoryPeriodSelection, Unit> {
        if (!current.needsReconciliation) return current
        // This owner references no restored business entity; only civil navigation is retained.
        val changed = binding.reconcileOwner(current.version, current.currentGeneration)
        check(changed is DraftChange.Written) { "History period preference could not be reconciled" }
        return changed.checkpoint
    }
}

internal object HistoryPeriodCodec : DraftCodec<HistoryPeriodSelection, Unit> {
    override val format = "history-selected-period"
    override val version = 1

    override fun encodeDraft(value: HistoryPeriodSelection): JsonElement = JsonObject().apply {
        addProperty("horizon", value.horizon.name)
        addProperty("anchorEpochDay", value.anchorEpochDay)
        addProperty("followToday", value.followToday)
    }

    override fun decodeDraft(value: JsonElement): HistoryPeriodSelection {
        check(value.isJsonObject)
        val fields = value.asJsonObject
        check(fields.keySet() == setOf("horizon", "anchorEpochDay", "followToday"))
        val horizon = fields.get("horizon")
        check(horizon.isJsonPrimitive && horizon.asJsonPrimitive.isString)
        val anchor = fields.get("anchorEpochDay")
        check(anchor.isJsonPrimitive && anchor.asJsonPrimitive.isNumber)
        val rawAnchor = anchor.asString
        check(rawAnchor.matches(Regex("-?[0-9]+")))
        val follow = fields.get("followToday")
        check(follow.isJsonPrimitive && follow.asJsonPrimitive.isBoolean)
        return HistoryPeriodSelection(
            horizon = AnalyticsHorizon.valueOf(horizon.asString),
            anchorEpochDay = rawAnchor.toLong(), followToday = follow.asBoolean,
        )
    }

    override fun encodeCommand(value: Unit): JsonElement = JsonNull.INSTANCE
    override fun decodeCommand(value: JsonElement) { check(value.isJsonNull) }
}
