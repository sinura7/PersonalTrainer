package com.sinura.personaltrainer.domain

/**
 * Turns “I care about upper / lower” into a different week, without asking for a
 * different split.
 *
 * The split stays derived. This only rearranges the session kinds inside it: one extra
 * press/pull day instead of a second hard leg day (or the inverse). Rest days are not
 * here — they are already decided by the answers — and a full-body week is left alone
 * so [RoutineGenerator] can swap a slot instead of stealing a whole day.
 */
object EmphasisLayout {
    fun apply(
        kinds: List<SessionFocusKind>,
        emphasis: TrainingEmphasis,
    ): List<SessionFocusKind> {
        if (emphasis == TrainingEmphasis.BALANCED) return kinds
        if (kinds.none { it.isUpperFamily || it.isLowerFamily }) return kinds
        val favorUpper = emphasis == TrainingEmphasis.UPPER
        val result = kinds.toMutableList()
        val needed = result.size / 2 + 1
        fun favored(kind: SessionFocusKind) =
            if (favorUpper) kind.isUpperFamily else kind.isLowerFamily
        fun other(kind: SessionFocusKind) =
            if (favorUpper) kind.isLowerFamily else kind.isUpperFamily
        while (result.count { favored(it) } < needed && result.count { other(it) } > 1) {
            val index = result.indices.last { other(result[it]) }
            result[index] = if (favorUpper) promoteUpper(result) else promoteLower(result)
        }
        return result
    }

    private fun promoteUpper(kinds: List<SessionFocusKind>): SessionFocusKind {
        val usesPpl = kinds.any {
            it == SessionFocusKind.PUSH || it == SessionFocusKind.PULL
        }
        if (!usesPpl) return SessionFocusKind.UPPER
        val push = kinds.count { it == SessionFocusKind.PUSH }
        val pull = kinds.count { it == SessionFocusKind.PULL }
        return if (pull < push) SessionFocusKind.PULL else SessionFocusKind.PUSH
    }

    private fun promoteLower(kinds: List<SessionFocusKind>): SessionFocusKind =
        if (kinds.any { it == SessionFocusKind.LEGS }) {
            SessionFocusKind.LEGS
        } else {
            SessionFocusKind.LOWER
        }
}
