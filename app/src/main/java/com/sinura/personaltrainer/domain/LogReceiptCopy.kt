package com.sinura.personaltrainer.domain

/**
 * The line that appears after a set is actually saved (Packet F).
 *
 * Button payload, captured payload, this receipt, and the persisted row
 * are the same numbers. Rest is not a live region; this line is, once.
 */
data class LogReceipt(
    val setId: String,
    val line: String,
    val weightKg: Double,
    val reps: Int,
    val rpe: Int?,
    val isWarmup: Boolean,
)

object LogReceiptCopy {
    fun line(ordinal: String, payload: String): String = "$ordinal logged · $payload"

    fun payload(
        weightKg: Double,
        reps: Int,
        loadClass: LoadClass,
        unit: WeightUnit,
        rpe: Int?,
        durationSeconds: Int? = null,
    ): String = buildString {
        append(SetCopy.setLine(weightKg, reps, loadClass, unit, durationSeconds, true))
        rpe?.let { append(" · RPE $it") }
    }

    fun ordinal(
        isWarmup: Boolean,
        warmupAfter: Int,
        workingAfter: Int,
        targetSets: Int,
    ): String = if (isWarmup) {
        SetOrdinalCopy.identityWarmup(warmupAfter)
    } else if (targetSets > 0 && workingAfter > targetSets) {
        SetOrdinalCopy.extra(workingAfter - targetSets)
    } else {
        SetOrdinalCopy.identityWorking(workingAfter, targetSets)
    }
}
