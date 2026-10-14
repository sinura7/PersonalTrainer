package com.sinura.personaltrainer.data.draft

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.util.runCatchingCancellable
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Orientation and a reference to the owning custom draft; never a replayable selection list. */
internal data class PickerContextSnapshot(
    val query: String = "",
    val muscle: CanonicalMuscle? = null,
    val anchor: String? = null,
    val index: Int = 0,
    val offsetDp: Float = 0f,
    val creating: Boolean = false,
    val customDraftId: String? = null,
) {
    override fun toString(): String = "PickerContextSnapshot(redacted)"
}

internal class PickerContextHandle internal constructor(
    val owner: DraftOwner,
    internal val claim: Long,
    internal val binding: DraftBinding<PickerContextSnapshot, Unit>,
    internal var checkpoint: DraftCheckpoint<PickerContextSnapshot, Unit>?,
) {
    val readable: Boolean get() = checkpoint != null
    val remembered: PickerContextSnapshot? get() = checkpoint?.draft
    val needsReconciliation: Boolean get() = checkpoint?.needsReconciliation == true
}

/** Separate claims per owning form. Explicit discard retires the old handle, including delayed work. */
internal class PickerContextDrafts(private val store: DurableDraftStore) {
    private val serial = Mutex()
    private val claims = mutableMapOf<DraftOwner, Long>()

    suspend fun open(owner: DraftOwner): PickerContextHandle = serial.withLock {
        require(owner.form in PICKER_FORMS)
        val claim = Math.addExact(claims[owner] ?: 0L, 1L)
        claims[owner] = claim
        val binding = store.bind(DraftOwner(DraftForm.LIBRARY, pickerContextKey(owner)), PickerContextCodec)
        PickerContextHandle(owner, claim, binding, runCatchingCancellable { read(binding) }.getOrNull())
    }

    suspend fun isCurrent(handle: PickerContextHandle): Boolean = serial.withLock {
        claims[handle.owner] == handle.claim
    }

    suspend fun reload(handle: PickerContextHandle) = serial.withLock {
        requireCurrent(handle)
        handle.checkpoint = null
        handle.checkpoint = read(handle.binding)
    }

    suspend fun keep(handle: PickerContextHandle, value: PickerContextSnapshot) = serial.withLock {
        requireCurrent(handle)
        val before = checkNotNull(handle.checkpoint) { "Picker view needs checking" }
        val current = read(handle.binding)
        check(!current.needsReconciliation) { "Picker owner needs checking" }
        if (current.draft == value) {
            handle.checkpoint = current
            return@withLock
        }
        check(current.version == before.version) { "Picker view belongs to changed data" }
        val changed = handle.binding.checkpoint(current.version, value)
        handle.checkpoint = if (changed is DraftChange.Written) changed.checkpoint else {
            val inspected = read(handle.binding)
            check(!inspected.needsReconciliation && inspected.draft == value) { "Picker view was not kept" }
            inspected
        }
    }

    /** The caller verifies the actual owning form under its existing read/restore coordinator. */
    suspend fun reconcile(handle: PickerContextHandle, stillOwned: suspend () -> Boolean) = serial.withLock {
        requireCurrent(handle)
        val before = checkNotNull(handle.checkpoint)
        val current = read(handle.binding)
        check(current.version == before.version)
        check(stillOwned()) { "Picker owner is missing or changed" }
        val changed = handle.binding.reconcileOwner(current.version, current.currentGeneration)
        check(changed is DraftChange.Written) { "Picker owner could not be checked" }
        handle.checkpoint = changed.checkpoint
    }

    /** Only explicit private discard; accepted routine/session selections remain authoritative. */
    suspend fun discard(handle: PickerContextHandle) = serial.withLock {
        requireCurrent(handle)
        val before = handle.checkpoint
        val current = handle.binding.read()
        val changed = if (current is DraftRead.PresentCheckpoint) {
            check(before != null && current.checkpoint.version == before.version) { "Picker view changed before discard" }
            handle.binding.discard(current.checkpoint.version, current.checkpoint.currentGeneration)
        } else {
            check(before == null) { "Picker view needs checking before discard" }
            handle.binding.discardUnreadableIfStillUnreadable()
        }
        val confirmed = if (changed is DraftChange.Written) changed.checkpoint else {
            val inspected = read(handle.binding)
            check(inspected.draft == null && inspected.pending == null &&
                inspected.version != before?.version) { "Picker discard needs checking" }
            inspected
        }
        handle.checkpoint = confirmed
        // A discarded handle can never be reused to resurrect a queued snapshot.
        claims[handle.owner] = Math.addExact(handle.claim, 1L)
    }

    private fun requireCurrent(handle: PickerContextHandle) {
        check(claims[handle.owner] == handle.claim) { "Picker view belongs to another visit" }
    }

    private suspend fun read(binding: DraftBinding<PickerContextSnapshot, Unit>): DraftCheckpoint<PickerContextSnapshot, Unit> {
        val current = binding.read()
        check(current is DraftRead.PresentCheckpoint && current.checkpoint.pending == null) { "Picker view could not be read" }
        return current.checkpoint
    }

    private companion object {
        val PICKER_FORMS = setOf(DraftForm.ROUTINE, DraftForm.WORKOUT, DraftForm.ACTIVITY, DraftForm.ONBOARDING)
    }
}

private fun pickerContextKey(owner: DraftOwner): String = "picker-view:" + java.security.MessageDigest.getInstance("SHA-256")
    .digest((owner.form.name + "\u0000" + owner.identity).toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

internal object PickerContextCodec : DraftCodec<PickerContextSnapshot, Unit> {
    override val format = "owned-picker-view"
    override val version = 1
    override fun encodeDraft(value: PickerContextSnapshot): JsonElement = JsonObject().apply {
        addProperty("query", value.query)
        add("muscle", value.muscle?.name?.let { com.google.gson.JsonPrimitive(it) } ?: JsonNull.INSTANCE)
        add("anchor", value.anchor?.let { com.google.gson.JsonPrimitive(it) } ?: JsonNull.INSTANCE)
        addProperty("index", value.index)
        addProperty("offsetDp", value.offsetDp)
        addProperty("creating", value.creating)
        add("customDraftId", value.customDraftId?.let { com.google.gson.JsonPrimitive(it) } ?: JsonNull.INSTANCE)
    }

    override fun decodeDraft(value: JsonElement): PickerContextSnapshot {
        check(value.isJsonObject)
        val fields = value.asJsonObject
        check(fields.keySet() == setOf("query", "muscle", "anchor", "index", "offsetDp", "creating", "customDraftId"))
        fun text(key: String, maximum: Int, nullable: Boolean = false): String? {
            val part = fields.get(key)
            if (nullable && part.isJsonNull) return null
            check(part.isJsonPrimitive && part.asJsonPrimitive.isString)
            return part.asString.also { check(it.length <= maximum && wellFormedDraftText(it)) }
        }
        val query = checkNotNull(text("query", 65_536))
        val muscle = text("muscle", 80, true)?.let(CanonicalMuscle::valueOf)
        val anchor = text("anchor", 512, true)
        val index = fields.get("index")
        check(index.isJsonPrimitive && index.asJsonPrimitive.isNumber && index.asString.matches(Regex("[0-9]+")))
        val position = index.asString.toInt().also { check(it >= 0) }
        val offset = fields.get("offsetDp")
        check(offset.isJsonPrimitive && offset.asJsonPrimitive.isNumber)
        val offsetDp = offset.asFloat.also { check(it.isFinite() && it >= 0f && it <= 100_000f) }
        val creating = fields.get("creating")
        check(creating.isJsonPrimitive && creating.asJsonPrimitive.isBoolean)
        val customId = text("customDraftId", 80, true)?.also {
            check(it.startsWith("ex-custom-"))
            val identity = it.removePrefix("ex-custom-")
            check(UUID.fromString(identity).toString() == identity)
        }
        check(!creating.asBoolean || customId != null)
        return PickerContextSnapshot(query = query, muscle = muscle, anchor = anchor, index = position,
            offsetDp = offsetDp, creating = creating.asBoolean, customDraftId = customId)
    }

    override fun encodeCommand(value: Unit): JsonElement = JsonNull.INSTANCE
    override fun decodeCommand(value: JsonElement) { check(value.isJsonNull) }
}
