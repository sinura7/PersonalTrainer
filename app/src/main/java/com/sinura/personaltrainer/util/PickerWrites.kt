package com.sinura.personaltrainer.util

import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExercisePickerProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI feedback and exact retry intents; callers retain ownership of their transactions. */
internal class PickerWrites {
    private data class Pending(val key: String, val creating: Boolean)
    private data class Failure(val message: String, val retry: (() -> Unit)?, val existing: Exercise?)
    private val pending = linkedMapOf<Long, Pending>()
    private val failures = linkedMapOf<String, Failure>()
    private val newest = mutableMapOf<String, Long>()
    private var next = 0L
    private var generation = 0
    private val mutable = MutableStateFlow(ExercisePickerProgress())
    val state = mutable.asStateFlow()
    val busy: Boolean get() = pending.isNotEmpty()
    val creating: Boolean get() = pending.values.any { it.creating }

    fun begin(key: String, creating: Boolean = false): Long {
        failures.remove(key)
        if (!creating) failures.remove("create")
        val ticket = ++next
        pending[ticket] = Pending(key, creating)
        newest[key] = ticket
        publish()
        return ticket
    }

    fun succeed(ticket: Long, created: Boolean = false) {
        val write = pending.remove(ticket)
        if (write != null && newest[write.key] == ticket) failures.remove(write.key)
        if (created) generation++
        publish()
    }

    fun fail(ticket: Long, message: String, existing: Exercise? = null, retry: (() -> Unit)? = null) {
        val write = pending.remove(ticket) ?: return
        if (newest[write.key] == ticket) failures[write.key] = Failure(message, retry, existing)
        publish()
    }

    fun cancel(ticket: Long) {
        pending.remove(ticket)
        publish()
    }

    fun retry() {
        if (busy) return
        val key = failures.keys.firstOrNull() ?: return
        val action = failures[key]?.retry ?: return
        failures.remove(key)
        publish()
        action()
    }

    fun dismissError() {
        failures.keys.firstOrNull()?.let { failures.remove(it) }
        publish()
    }

    /** A newly opened owner reconstructs exact recovery from its durable command. */
    fun clearFailures() { failures.clear(); publish() }

    private fun publish() {
        val failure = failures.values.firstOrNull()
        mutable.value = ExercisePickerProgress(
            pendingIds = pending.values.filterNot { it.creating }.map { it.key }.toSet(),
            creating = creating,
            error = failure?.message,
            canRetry = failure?.retry != null,
            existing = failure?.existing,
            createdGeneration = generation,
        )
    }
}
