package com.sinura.personaltrainer.ui.workout

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job

/**
 * After [arm], holds every Main dispatch for [armedRoot] and its descendant jobs until [release].
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MutationJobResumeGate(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    private data class Pending(val context: CoroutineContext, val block: Runnable)
    private val lock = Any()
    private var armedRoot: Job? = null
    private val held = ArrayDeque<Pending>()

    val isHeld: Boolean get() = synchronized(lock) { held.isNotEmpty() }

    fun arm(root: Job) = synchronized(lock) {
        check(armedRoot == null && held.isEmpty())
        armedRoot = root
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = delegate.isDispatchNeeded(context)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val queue = synchronized(lock) {
            val root = armedRoot ?: return@synchronized null
            val job = context[Job] ?: return@synchronized null
            if (!job.isDescendantOf(root)) return@synchronized null
            held.addLast(Pending(context, block))
            held
        }
        if (queue == null) delegate.dispatch(context, block)
    }

    fun release() {
        val toRun = synchronized(lock) {
            armedRoot = null
            held.toList().also { held.clear() }
        }
        toRun.forEach { pending -> delegate.dispatch(pending.context, pending.block) }
    }

    private fun Job.isDescendantOf(ancestor: Job): Boolean {
        var current: Job? = this
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent
        }
        return false
    }
}
