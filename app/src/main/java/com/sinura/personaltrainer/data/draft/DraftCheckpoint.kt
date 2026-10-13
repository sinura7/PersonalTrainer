package com.sinura.personaltrainer.data.draft

import com.google.gson.JsonElement

/** Private recovery identities, never catalogue/session records or backup content. */
internal enum class DraftForm {
    CUSTOM_EXERCISE, WORKOUT, ROUTINE, LIBRARY, CARDIO, ACTIVITY, ONBOARDING, HOME, HISTORY,
}

internal data class DraftOwner(val form: DraftForm, val identity: String) {
    init { require(identity.isNotBlank() && identity.length <= 256 && wellFormedDraftText(identity)) }
}

/** Reject malformed UTF16 rather than replacing an authored character or aliasing an owner. */
internal fun wellFormedDraftText(value: String): Boolean {
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (Character.isHighSurrogate(character)) {
            if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) return false
            index += 2
        } else {
            if (Character.isLowSurrogate(character)) return false
            index++
        }
    }
    return true
}

internal data class DraftVersion(val owner: DraftOwner, val generation: String, val revision: Long)

/** Adopters use immutable value types and validate every authored field when decoding. */
internal interface DraftCodec<D, C> {
    val format: String
    val version: Int
    fun encodeDraft(value: D): JsonElement
    fun decodeDraft(value: JsonElement): D
    fun encodeCommand(value: C): JsonElement
    fun decodeCommand(value: JsonElement): C
}

internal enum class DraftCommandPhase { PENDING, RETRYABLE, ACCEPTED }

internal data class PendingDraftCommand<C>(
    val identity: String,
    val value: C,
    val phase: DraftCommandPhase,
) {
    override fun toString(): String = "PendingDraftCommand(redacted, phase=$phase)"
}

internal data class DraftCheckpoint<D, C>(
    val version: DraftVersion,
    val currentGeneration: String,
    val draft: D?,
    val pending: PendingDraftCommand<C>?,
) {
    val needsReconciliation: Boolean get() = version.generation != currentGeneration
    override fun toString(): String = "DraftCheckpoint(redacted, revision=${version.revision})"
}

internal enum class DraftReadProblem { STORAGE, CORRUPT, UNSUPPORTED, TOO_LARGE }
internal enum class DraftRejection {
    WRONG_OWNER, STALE_VERSION, RECONCILIATION_REQUIRED, PENDING_COMMAND,
    NO_COMMAND, WRONG_COMMAND, INSPECTION_REQUIRED, INVALID_DATA,
}

internal sealed interface DraftRead<out D, out C> {
    data class PresentCheckpoint<D, C>(val checkpoint: DraftCheckpoint<D, C>) : DraftRead<D, C>
    data class UnreadableCheckpoint(val problem: DraftReadProblem) : DraftRead<Nothing, Nothing>
}

internal sealed interface DraftChange<out D, out C> {
    data class Written<D, C>(val checkpoint: DraftCheckpoint<D, C>) : DraftChange<D, C>
    data class Rejected(val reason: DraftRejection) : DraftChange<Nothing, Nothing>
    data class Unavailable(val problem: DraftReadProblem) : DraftChange<Nothing, Nothing>
}

/** Only the owning repository can establish these outcomes by inspecting stable IDs/values. */
internal enum class DraftRecoveryOutcome { EXACT_SAVED, UNCOMMITTED, CONFLICT, UNAVAILABLE }

internal sealed interface DraftGenerationChange {
    data class Changed(val generation: String) : DraftGenerationChange
    data object Conflict : DraftGenerationChange
    data class Unavailable(val problem: DraftReadProblem) : DraftGenerationChange
}
