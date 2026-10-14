package com.sinura.personaltrainer.data.draft

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CustomWeekLift
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class StagedStrength(val exercise: Exercise, val weightKg: Double, val reps: Int)
internal data class StagedCardio(val type: CardioType, val minutes: Int, val distanceKm: Double?, val indoor: Boolean)
internal data class ActivityFormFields(
    val identity: String, val mode: String, val occurrenceId: String?,
    val title: String, val epochDay: Long,
    val strength: List<StagedStrength>, val cardio: List<StagedCardio>,
    val pickedId: String = "", val weightText: String = "0", val repsText: String = "8",
    val weightUnit: WeightUnit? = null,
) { override fun toString(): String = "ActivityFormFields(redacted)" }
internal data class WeekFormFields(
    val identity: String, val selectedDay: Weekday, val days: Map<Weekday, List<CustomWeekLift>>,
    val userPickedDay: Boolean, val answers: OnboardingAnswers?, val weightUnit: WeightUnit?,
    val preferredDays: Set<Weekday>,
) { override fun toString(): String = "WeekFormFields(redacted)" }

/** A private staged form owns its picker. No business save or plan application happens here. */
internal class StagedFormHandle<T> internal constructor(
    val owner: DraftOwner, val binding: DraftBinding<T, Unit>, internal val claim: Long,
    internal var checkpoint: DraftCheckpoint<T, Unit>?,
) {
    val fields: T? get() = checkpoint?.draft
    internal var reconciliation: Pair<DraftVersion, String>? = null
}

internal class StagedPickerForms(private val store: DurableDraftStore, private val maintenance: DbMaintenance) {
    private val serial = Mutex()
    private val claims = mutableMapOf<DraftOwner, Long>()

    suspend fun <T> open(owner: DraftOwner, codec: DraftCodec<T, Unit>): StagedFormHandle<T> = serial.withLock {
        val binding = store.bind(owner, codec)
        val claim = Math.addExact(claims[owner] ?: 0L, 1L)
        claims[owner] = claim
        val current = com.sinura.personaltrainer.util.runCatchingCancellable { read(binding) }.getOrNull()
        StagedFormHandle(owner = owner, binding = binding, claim = claim, checkpoint = current)
    }

    suspend fun <T> current(handle: StagedFormHandle<T>): Boolean = serial.withLock {
        claims[handle.owner] == handle.claim
    }
    suspend fun <T> refresh(handle: StagedFormHandle<T>) = serial.withLock {
        requireCurrent(handle); handle.checkpoint = read(handle.binding)
    }

    /** Unsubmitted fields may follow a generation change only after their actual owners are checked.
     * The restore/catalog coordinator covers both that read and the private acknowledgement. */
    suspend fun <T> reconcile(handle: StagedFormHandle<T>, stillOwned: suspend (T) -> Boolean) =
        maintenance.withMaintenanceLock { serial.withLock {
            requireCurrent(handle)
            val current = read(handle.binding)
            val attempt = handle.reconciliation
            if (attempt != null && current.version != handle.checkpoint?.version) {
                check(!current.needsReconciliation && current.pending == null && current.draft == handle.fields &&
                    current.version == DraftVersion(handle.owner, attempt.second, Math.addExact(attempt.first.revision, 1L)))
                check(current.draft?.let { stillOwned(it) } != false)
                handle.checkpoint = current
                handle.reconciliation = null
                return@withLock
            }
            check(current.version == handle.checkpoint?.version && current.pending == null)
            if (!current.needsReconciliation) return@withLock
            check(current.draft?.let { stillOwned(it) } != false) { "The staged form's owner changed." }
            handle.reconciliation = current.version to current.currentGeneration
            val result = handle.binding.reconcileOwner(current.version, current.currentGeneration)
            handle.checkpoint = if (result is DraftChange.Written) result.checkpoint else {
                read(handle.binding).also { check(!it.needsReconciliation && it.draft == current.draft && it.pending == null) }
            }
            handle.reconciliation = null
        } }

    /** Only a repository outcome guaranteeing no business write may release this fence. */
    suspend fun <T> rejected(handle: StagedFormHandle<T>) = serial.withLock {
        requireCurrent(handle)
        var current = read(handle.binding)
        val before = checkNotNull(handle.checkpoint)
        check(!current.needsReconciliation && current.draft == before.draft)
        if (current.version != before.version) {
            check(current.version == before.version.copy(revision = Math.addExact(before.version.revision, 1L)))
            val intended = checkNotNull(before.pending)
            check(when (intended.phase) {
                DraftCommandPhase.PENDING -> current.pending?.identity == intended.identity && current.pending.phase == DraftCommandPhase.RETRYABLE
                DraftCommandPhase.RETRYABLE -> current.pending == null
                DraftCommandPhase.ACCEPTED -> false
            })
            handle.checkpoint = current
        }
        val pending = current.pending ?: return@withLock
        check(pending.phase != DraftCommandPhase.ACCEPTED)
        if (pending.phase != DraftCommandPhase.RETRYABLE) {
            val inspected = handle.binding.inspect(expected = current.version, commandIdentity = pending.identity,
                outcome = DraftRecoveryOutcome.UNCOMMITTED)
            current = if (inspected is DraftChange.Written) inspected.checkpoint else {
                read(handle.binding).also { check(!it.needsReconciliation && it.pending?.identity == pending.identity && it.pending.phase == DraftCommandPhase.RETRYABLE) }
            }
            handle.checkpoint = current
        }
        val resumed = handle.binding.resumeEditing(current.version, pending.identity)
        handle.checkpoint = if (resumed is DraftChange.Written) resumed.checkpoint else {
            read(handle.binding).also { check(!it.needsReconciliation && it.pending == null && it.draft == current.draft) }
        }
    }

    suspend fun <T> keep(handle: StagedFormHandle<T>, fields: T) = serial.withLock {
        requireCurrent(handle)
        val current = read(handle.binding)
        check(!current.needsReconciliation && current.version == handle.checkpoint?.version && current.pending == null)
        if (current.draft == fields) { handle.checkpoint = current; return@withLock }
        val result = handle.binding.checkpoint(current.version, fields)
        handle.checkpoint = if (result is DraftChange.Written) result.checkpoint else {
            read(handle.binding).also { check(!it.needsReconciliation && it.draft == fields) }
        }
    }

    suspend fun <T> clear(handle: StagedFormHandle<T>) = serial.withLock {
        requireCurrent(handle)
        val current = read(handle.binding)
        check(current.version == handle.checkpoint?.version)
        val result = handle.binding.discard(current.version, current.currentGeneration)
        val cleared = if (result is DraftChange.Written) result.checkpoint else {
            read(handle.binding).also { check(it.draft == null && it.version != current.version) }
        }
        handle.checkpoint = cleared
        claims[current.version.owner] = Math.addExact(handle.claim, 1L)
    }

    /** A submission fence prevents an accepted/uncertain form from reopening as new work.
     * Repository-level exact replay/inspection belongs to the activity/plan acceptance packets. */
    suspend fun <T> submit(handle: StagedFormHandle<T>, fields: T) = serial.withLock {
        requireCurrent(handle)
        val current = read(handle.binding)
        check(!current.needsReconciliation && current.version == handle.checkpoint?.version && current.pending == null)
        val identity = UUID.randomUUID().toString()
        val result = handle.binding.beginSave(expected = current.version, draft = fields,
            commandIdentity = identity, command = Unit)
        handle.checkpoint = if (result is DraftChange.Written) result.checkpoint else {
            read(handle.binding).also { check(!it.needsReconciliation && it.draft == fields && it.pending?.identity == identity) }
        }
    }

    suspend fun <T> accept(handle: StagedFormHandle<T>): Boolean = serial.withLock {
        requireCurrent(handle)
        val before = checkNotNull(handle.checkpoint)
        val current = read(handle.binding)
        check(current.version == before.version && !current.needsReconciliation)
        val pending = checkNotNull(current.pending)
        if (pending.phase != DraftCommandPhase.ACCEPTED) {
            val accepted = handle.binding.inspect(expected = current.version, commandIdentity = pending.identity,
                outcome = DraftRecoveryOutcome.EXACT_SAVED)
            handle.checkpoint = if (accepted is DraftChange.Written) accepted.checkpoint else {
                read(handle.binding).also { check(it.pending?.identity == pending.identity && it.pending.phase == DraftCommandPhase.ACCEPTED) }
            }
        }
        val accepted = checkNotNull(handle.checkpoint)
        val cleared = handle.binding.clear(accepted.version)
        if (cleared is DraftChange.Written) {
            handle.checkpoint = cleared.checkpoint
            claims[handle.owner] = Math.addExact(handle.claim, 1L)
            true
        } else {
            handle.checkpoint = read(handle.binding)
            handle.checkpoint?.let { it.draft == null && it.pending == null } == true
        }
    }

    /** Explicit discard is still fenced to this visit, even if its original read failed. */
    suspend fun <T> reset(handle: StagedFormHandle<T>) = serial.withLock {
        requireCurrent(handle)
        val binding = handle.binding
        val before = binding.read()
        val result = if (before is DraftRead.PresentCheckpoint) {
            check(handle.checkpoint == null || handle.checkpoint?.version == before.checkpoint.version)
            binding.discard(before.checkpoint.version, before.checkpoint.currentGeneration)
        } else binding.discardUnreadableIfStillUnreadable()
        if (result !is DraftChange.Written) {
            val after = read(binding)
            check(after.draft == null && after.pending == null &&
                (before !is DraftRead.PresentCheckpoint || before.checkpoint.version != after.version))
        }
        claims[handle.owner] = Math.addExact(handle.claim, 1L)
    }

    private fun <T> requireCurrent(handle: StagedFormHandle<T>) {
        check(claims[handle.owner] == handle.claim) { "This draft belongs to another visit." }
    }
    private suspend fun <T> read(binding: DraftBinding<T, Unit>): DraftCheckpoint<T, Unit> {
        val result = binding.read()
        check(result is DraftRead.PresentCheckpoint) { "Could not read this draft." }
        return result.checkpoint
    }
}

internal abstract class StagedFormCodec<T>(private val type: Class<T>) : DraftCodec<T, Unit> {
    private val gson = Gson()
    override val version = 1
    abstract fun validate(fields: T)
    override fun encodeDraft(value: T): JsonElement { validate(value); return gson.toJsonTree(value) }
    override fun decodeDraft(value: JsonElement): T {
        check(value.isJsonObject)
        val decoded = gson.fromJson(value, type)
        validate(decoded)
        // Unknown fields and malformed shapes must not become an apparently valid empty form.
        check(gson.toJsonTree(decoded) == value)
        return decoded
    }
    override fun encodeCommand(value: Unit): JsonElement = JsonNull.INSTANCE
    override fun decodeCommand(value: JsonElement) { check(value.isJsonNull) }
    protected fun identity(value: String) { check(UUID.fromString(value).toString() == value) }
    protected fun text(value: String) { check(value.length <= 65_536 && wellFormedDraftText(value)) }
    protected fun exercise(value: Exercise) {
        check(value.id.isNotBlank() && value.id.length <= 256)
        text(value.id); text(value.name); text(value.notes); text(value.muscleGroup)
        check(value.loadType in com.sinura.personaltrainer.domain.LoadType.entries)
        check(value.equipment in com.sinura.personaltrainer.domain.EquipmentType.entries)
        check(value.muscles.all { it.muscleKey.isNotBlank() && it.weight.isFinite() && it.weight >= 0.0 })
    }
}

internal object ActivityFormCodec : StagedFormCodec<ActivityFormFields>(ActivityFormFields::class.java) {
    override val format = "activity-picker-parent"
    override fun validate(fields: ActivityFormFields) {
        identity(fields.identity); check(fields.mode in setOf("STRENGTH", "CARDIO", "MIXED"))
        fields.occurrenceId?.let { check(it.isNotBlank() && it.length <= 256); text(it) }
        text(fields.title); text(fields.pickedId); text(fields.weightText); text(fields.repsText)
        check(fields.epochDay in -365_243_219_162L..365_241_780_471L)
        check(fields.strength.size <= 1_000 && fields.cardio.size <= 1_000)
        fields.strength.forEach { exercise(it.exercise); check(it.weightKg.isFinite() && it.reps > 0) }
        fields.cardio.forEach { check(it.minutes > 0 && (it.distanceKm == null || it.distanceKm.isFinite())) }
    }
}

internal object WeekFormCodec : StagedFormCodec<WeekFormFields>(WeekFormFields::class.java) {
    override val format = "custom-week-picker-parent"
    override fun validate(fields: WeekFormFields) {
        identity(fields.identity)
        check(fields.selectedDay in Weekday.entries)
        val rows = fields.days.values.flatten()
        check(rows.size <= 1_000 && rows.map { it.id }.distinct().size == rows.size)
        rows.forEach {
            check(it.id.isNotBlank()); text(it.id); exercise(it.exercise)
            check(it.targetSets > 0 && it.targetReps >= 0 && it.restSeconds >= 0)
            check(it.targetWeightKg == null || it.targetWeightKg.isFinite())
            check(it.targetSeconds == null || it.targetSeconds > 0)
            check(it.targetSecondsMax == null || it.targetSecondsMax > 0)
        }
        fields.answers?.let { check(it.bodyweightKg == null || it.bodyweightKg.isFinite()); check(it.availableEquipment.all(::wellFormedDraftText)) }
        check(fields.preferredDays.size <= 7)
    }
}
