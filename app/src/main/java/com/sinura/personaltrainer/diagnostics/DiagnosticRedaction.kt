package com.sinura.personaltrainer.diagnostics

/**
 * P12.1 / FND-014. A shared bundle names app, schema, and device metadata,
 * event IDs, exception classes, and Temper-owned frames. It never copies
 * log messages, workout names, weights, notes, bodyweight, emails, tokens,
 * database contents, or raw filesystem paths.
 */
object DiagnosticRedaction {
    const val APP_PREFIX = "com.sinura.personaltrainer"
    const val SCHEMA = "temper-diag-1"
    const val MAX_FRAMES = 16

    fun exceptionClass(error: Throwable): String = error::class.java.name

    fun appFrames(error: Throwable): List<String> =
        error.stackTrace
            .asSequence()
            .filter { it.className.startsWith(APP_PREFIX) }
            .map { frame ->
                val simple = frame.className.substringAfterLast('.')
                "$simple.${frame.methodName}:${frame.lineNumber}"
            }
            .take(MAX_FRAMES)
            .toList()

    fun fromThrowable(
        error: Throwable,
        tag: String?,
        nowMs: Long,
        id: String,
    ): DiagnosticEvent = DiagnosticEvent(
        id = id,
        atMs = nowMs,
        kind = "exception",
        exceptionClass = exceptionClass(error),
        tag = tag?.takeIf { it.startsWith("PT/") },
        frames = appFrames(error),
    )

    fun renderBundle(
        events: List<DiagnosticEvent>,
        metadata: Map<String, String>,
    ): String = buildString {
        appendLine("TEMPER DIAGNOSTICS")
        appendLine("schema=$SCHEMA")
        metadata.toSortedMap().forEach { (key, value) ->
            appendLine("$key=$value")
        }
        appendLine("eventCount=${events.size}")
        events.forEach { event ->
            appendLine()
            append("event id=${event.id} atMs=${event.atMs} kind=${event.kind}")
            event.exceptionClass?.let { append(" class=$it") }
            event.tag?.let { append(" tag=$it") }
            appendLine()
            event.frames.forEach { frame ->
                appendLine("  frame $frame")
            }
        }
    }

    fun containsForbidden(text: String, canaries: Collection<String>): Boolean =
        canaries.any { canary -> canary.isNotEmpty() && canary in text }
}

object DiagnosticCanaries {
    const val WORKOUT_NAME = "CANARY_PUSH_DAY"
    const val NOTE = "CANARY_SESSION_NOTE"
    const val WEIGHT = "187.5kg-CANARY"
    const val BODYWEIGHT = "BW-CANARY-82.4"
    const val EMAIL = "canary.user@example.com"
    const val TOKEN = "ya29.CANARY_TOKEN_VALUE"
    const val PATH = "files/databases/temper.db-CANARY"
    const val MESSAGE = "failed to save CANARY_PUSH_DAY"

    val ALL: List<String> = listOf(
        WORKOUT_NAME,
        NOTE,
        WEIGHT,
        BODYWEIGHT,
        EMAIL,
        TOKEN,
        PATH,
        MESSAGE,
    )
}
