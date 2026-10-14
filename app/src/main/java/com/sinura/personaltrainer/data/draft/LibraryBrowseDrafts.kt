package com.sinura.personaltrainer.data.draft

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A last-used view, never a saved search, catalogue mutation or portable backup. */
internal data class LibraryBrowseSnapshot(
    val query: String = "",
    val muscle: CanonicalMuscle? = null,
    val equipment: EquipmentType? = null,
    val expandedFamilies: List<String> = emptyList(),
    val anchor: String? = null,
    val index: Int = 0,
    val offsetDp: Float = 0f,
) {
    override fun toString(): String = "LibraryBrowseSnapshot(redacted)"
}

internal class LibraryBrowseHandle internal constructor(
    internal val claim: Long,
    internal var checkpoint: DraftCheckpoint<LibraryBrowseSnapshot, Unit>?,
) {
    internal var retirementRevision = 0L
    val remembered: LibraryBrowseSnapshot? get() = checkpoint?.draft
    val readable: Boolean get() = checkpoint != null
}

internal class LibraryBrowseDrafts(store: DurableDraftStore) {
    private val binding = store.bind(DraftOwner(DraftForm.LIBRARY, "last-view"), LibraryBrowseCodec)
    private val serial = Mutex()
    private var claim = 0L

    suspend fun open(): LibraryBrowseHandle = serial.withLock {
        LibraryBrowseHandle(++claim, runCatchingCancellable { reconciled(read()) }.getOrNull())
    }

    suspend fun isCurrent(handle: LibraryBrowseHandle): Boolean = serial.withLock { handle.claim == claim }

    suspend fun reload(handle: LibraryBrowseHandle) = serial.withLock {
        requireCurrent(handle)
        handle.checkpoint = null
        handle.checkpoint = reconciled(read())
    }

    suspend fun keep(handle: LibraryBrowseHandle, value: LibraryBrowseSnapshot) {
        val admittedRevision = handle.retirementRevision
        serial.withLock {
            requireCurrent(handle)
            check(admittedRevision == handle.retirementRevision) { "Library view was reset" }
            val before = checkNotNull(handle.checkpoint) { "Library view needs recovery" }
            val current = read()
            if (current.draft == value) {
                handle.checkpoint = reconciled(current)
                return@withLock
            }
            check(current.version == before.version) { "Library view belongs to changed data" }
            handle.checkpoint = reconciled(current)
            val changed = binding.checkpoint(checkNotNull(handle.checkpoint).version, value)
            if (changed is DraftChange.Written) handle.checkpoint = changed.checkpoint
            else {
                val inspected = read()
                check(inspected.draft == value) { "Library view was not kept" }
                handle.checkpoint = reconciled(inspected)
            }
        }
    }

    /** Explicitly reset only private orientation. Catalogue, drafts and history are untouched. */
    suspend fun reset(handle: LibraryBrowseHandle) = serial.withLock {
        requireCurrent(handle)
        handle.retirementRevision += 1L
        val current = binding.read()
        val changed = if (current is DraftRead.PresentCheckpoint) {
            val defaults = LibraryBrowseSnapshot()
            if (current.checkpoint.draft == defaults && current.checkpoint.pending == null) {
                handle.checkpoint = current.checkpoint
                return@withLock
            }
            val before = handle.checkpoint
            check(before != null && current.checkpoint.version == before.version) { "Library view changed before reset" }
            val base = reconciled(current.checkpoint)
            val replaced = binding.checkpoint(base.version, defaults)
            if (replaced is DraftChange.Written) replaced else {
                val inspected = read()
                check(inspected.draft == defaults) { "Library view reset needs inspection" }
                handle.checkpoint = reconciled(inspected)
                return@withLock
            }
        } else binding.discardUnreadableIfStillUnreadable()
        check(changed is DraftChange.Written) { "Library view could not be reset" }
        handle.checkpoint = changed.checkpoint
    }

    private fun requireCurrent(handle: LibraryBrowseHandle) {
        check(handle.claim == claim) { "Library view belongs to a newer visit" }
    }

    private suspend fun read(): DraftCheckpoint<LibraryBrowseSnapshot, Unit> {
        val current = binding.read()
        check(current is DraftRead.PresentCheckpoint) { "Library view could not be read" }
        check(current.checkpoint.pending == null) { "Unexpected Library view command" }
        return current.checkpoint
    }

    private suspend fun reconciled(current: DraftCheckpoint<LibraryBrowseSnapshot, Unit>): DraftCheckpoint<LibraryBrowseSnapshot, Unit> {
        if (!current.needsReconciliation) return current
        // No business owner is referenced: row keys are only a recoverable scroll hint.
        val changed = binding.reconcileOwner(current.version, current.currentGeneration)
        check(changed is DraftChange.Written) { "Library view could not be reconciled" }
        return changed.checkpoint
    }
}

internal object LibraryBrowseCodec : DraftCodec<LibraryBrowseSnapshot, Unit> {
    override val format = "library-last-view"
    override val version = 1

    override fun encodeDraft(value: LibraryBrowseSnapshot): JsonElement = JsonObject().apply {
        addProperty("query", value.query)
        add("muscle", value.muscle?.name?.let { com.google.gson.JsonPrimitive(it) } ?: JsonNull.INSTANCE)
        add("equipment", value.equipment?.name?.let { com.google.gson.JsonPrimitive(it) } ?: JsonNull.INSTANCE)
        add("families", com.google.gson.JsonArray().apply { value.expandedFamilies.forEach { add(it) } })
        add("anchor", value.anchor?.let { com.google.gson.JsonPrimitive(it) } ?: JsonNull.INSTANCE)
        addProperty("index", value.index)
        addProperty("offsetDp", value.offsetDp)
    }

    override fun decodeDraft(value: JsonElement): LibraryBrowseSnapshot {
        check(value.isJsonObject)
        val fields = value.asJsonObject
        check(fields.keySet() == setOf("query", "muscle", "equipment", "families", "anchor", "index", "offsetDp"))
        fun text(key: String, maximum: Int, nullable: Boolean = false): String? {
            val part = fields.get(key)
            if (nullable && part.isJsonNull) return null
            check(part.isJsonPrimitive && part.asJsonPrimitive.isString)
            return part.asString.also { check(it.length <= maximum && wellFormedDraftText(it)) }
        }
        val query = checkNotNull(text("query", 65_536))
        val muscle = text("muscle", 80, true)?.let(CanonicalMuscle::valueOf)
        val equipment = text("equipment", 80, true)?.let(EquipmentType::valueOf)
        val families = fields.get("families")
        check(families.isJsonArray && families.asJsonArray.size() <= 2_048)
        val expanded = families.asJsonArray.map {
            check(it.isJsonPrimitive && it.asJsonPrimitive.isString)
            it.asString.also { name -> check(name.isNotBlank() && name.length <= 512 && wellFormedDraftText(name)) }
        }
        check(expanded.distinct().size == expanded.size)
        val anchor = text("anchor", 512, true)
        val index = fields.get("index")
        check(index.isJsonPrimitive && index.asJsonPrimitive.isNumber && index.asString.matches(Regex("[0-9]+")))
        val offset = fields.get("offsetDp")
        check(offset.isJsonPrimitive && offset.asJsonPrimitive.isNumber)
        val offsetDp = offset.asFloat
        check(offsetDp.isFinite() && offsetDp >= 0f && offsetDp <= 100_000f)
        val anchorIndex = index.asString.toInt()
        check(anchorIndex >= 0)
        return LibraryBrowseSnapshot(query = query, muscle = muscle, equipment = equipment,
            expandedFamilies = expanded, anchor = anchor, index = anchorIndex, offsetDp = offsetDp)
    }

    override fun encodeCommand(value: Unit): JsonElement = JsonNull.INSTANCE
    override fun decodeCommand(value: JsonElement) { check(value.isJsonNull) }
}
