package com.sinura.personaltrainer.diagnostics

/**
 * Capped in-process diagnostic ring. Default is no automatic telemetry —
 * [AppLog] failures and uncaught exceptions may be recorded locally, and
 * the user shares a redacted bundle from Settings.
 */
class DiagnosticStore(val capacity: Int = DiagnosticRing.CAPACITY) {
    private val lock = Any()
    private val events = ArrayDeque<DiagnosticEvent>(capacity)

    fun record(event: DiagnosticEvent) {
        synchronized(lock) {
            while (events.size >= capacity) {
                events.removeFirst()
            }
            events.addLast(event)
        }
    }

    fun snapshot(): List<DiagnosticEvent> = synchronized(lock) { events.toList() }

    fun clear() {
        synchronized(lock) { events.clear() }
    }
}

object DiagnosticRing {
    const val CAPACITY = 64
    val shared = DiagnosticStore(CAPACITY)
}

data class DiagnosticEvent(
    val id: String,
    val atMs: Long,
    val kind: String,
    val exceptionClass: String?,
    val tag: String?,
    val frames: List<String>,
)
