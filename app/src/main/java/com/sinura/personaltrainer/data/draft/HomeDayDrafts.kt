package com.sinura.personaltrainer.data.draft

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.sinura.personaltrainer.util.runCatchingCancellable
import java.time.LocalDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A civil-day navigation preference; never a training date or business command. */
internal data class RememberedHomeDay(val selectedEpochDay: Long, val todayEpochDay: Long) {
    init {
        LocalDate.ofEpochDay(selectedEpochDay)
        LocalDate.ofEpochDay(todayEpochDay)
    }
}

internal class HomeDayHandle internal constructor(
    internal val claim: Long,
    internal var checkpoint: DraftCheckpoint<RememberedHomeDay, Unit>?,
) {
    internal var retirementRevision = 0L
    val remembered: RememberedHomeDay? get() = checkpoint?.draft
    val readable: Boolean get() = checkpoint != null
}

/** Shared ownership and CAS prevent a retired Home from replacing a newer selection. */
internal class HomeDayDrafts(store: DurableDraftStore) {
    private val binding = store.bind(DraftOwner(DraftForm.HOME, "selected-day"), HomeDayCodec)
    private val serial = Mutex()
    private var claim = 0L

    suspend fun open(): HomeDayHandle = serial.withLock {
        val admitted = ++claim
        // Even a failed reconstruction owns a lease for explicit private-only Reset.
        HomeDayHandle(admitted, runCatchingCancellable { reconciled(read()) }.getOrNull())
    }

    suspend fun reload(handle: HomeDayHandle) = serial.withLock {
        requireCurrent(handle)
        handle.checkpoint = null
        handle.checkpoint = reconciled(read())
    }

    suspend fun keep(handle: HomeDayHandle, value: RememberedHomeDay) {
        val admittedRevision = handle.retirementRevision
        serial.withLock {
            requireCurrent(handle)
            check(admittedRevision == handle.retirementRevision) { "Home preference was reset" }
            val before = checkNotNull(handle.checkpoint) { "Home preference needs recovery" }
            val current = read()
            // An uncertain replacement may already contain the intended preference.
            if (current.draft == value) {
                handle.checkpoint = reconciled(current)
                return@withLock
            }
            check(current.version == before.version) { "Home preference changed" }
            handle.checkpoint = reconciled(current)
            val changed = binding.checkpoint(checkNotNull(handle.checkpoint).version, value)
            if (changed is DraftChange.Written) {
                handle.checkpoint = changed.checkpoint
            } else {
                // A lost private-file acknowledgement must not be mistaken for a failed keep.
                val inspected = read()
                check(inspected.draft == value) { "Home preference was not kept" }
                handle.checkpoint = reconciled(inspected)
            }
        }
    }

    /** Explicitly forget only this navigation preference, including unreadable private data. */
    suspend fun reset(handle: HomeDayHandle) = serial.withLock {
        requireCurrent(handle)
        handle.retirementRevision += 1L
        handle.checkpoint = null
        val loaded = binding.read()
        val cleared = if (loaded is DraftRead.PresentCheckpoint) {
            binding.discard(loaded.checkpoint.version, loaded.checkpoint.currentGeneration)
        } else {
            binding.discardUnreadable()
        }
        check(cleared is DraftChange.Written) { "Home preference could not be reset" }
        handle.checkpoint = cleared.checkpoint
    }

    private fun requireCurrent(handle: HomeDayHandle) {
        check(handle.claim == claim) { "Home preference belongs to a newer visit" }
    }

    private suspend fun read(): DraftCheckpoint<RememberedHomeDay, Unit> {
        val loaded = binding.read()
        check(loaded is DraftRead.PresentCheckpoint) { "Home preference could not be read" }
        check(loaded.checkpoint.pending == null) { "Unexpected Home preference command" }
        return loaded.checkpoint
    }

    private suspend fun reconciled(
        current: DraftCheckpoint<RememberedHomeDay, Unit>,
    ): DraftCheckpoint<RememberedHomeDay, Unit> {
        if (!current.needsReconciliation) return current
        // This owner references no restored business entity; only civil navigation is retained.
        val changed = binding.reconcileOwner(current.version, current.currentGeneration)
        check(changed is DraftChange.Written) { "Home preference could not be reconciled" }
        return changed.checkpoint
    }
}

internal object HomeDayCodec : DraftCodec<RememberedHomeDay, Unit> {
    override val format = "home-selected-day"
    override val version = 1

    override fun encodeDraft(value: RememberedHomeDay): JsonElement = JsonObject().apply {
        addProperty("selectedEpochDay", value.selectedEpochDay)
        addProperty("todayEpochDay", value.todayEpochDay)
    }

    override fun decodeDraft(value: JsonElement): RememberedHomeDay {
        check(value.isJsonObject)
        val fields = value.asJsonObject
        check(fields.keySet() == setOf("selectedEpochDay", "todayEpochDay"))
        fun day(name: String): Long {
            val field = fields.get(name)
            check(field.isJsonPrimitive && field.asJsonPrimitive.isNumber)
            val raw = field.asString
            check(raw.matches(Regex("-?[0-9]+")))
            return raw.toLong().also { LocalDate.ofEpochDay(it) }
        }
        return RememberedHomeDay(day("selectedEpochDay"), day("todayEpochDay"))
    }

    override fun encodeCommand(value: Unit): JsonElement = JsonNull.INSTANCE
    override fun decodeCommand(value: JsonElement) { check(value.isJsonNull) }
}
