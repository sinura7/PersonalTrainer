package com.sinura.personaltrainer.ui.workout

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job

/** Hold the next Main dispatch for [armedRoot] or any coroutine job descended from it. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MutationJobResumeGate(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    private data class Pending(val context: CoroutineContext, val block: Runnable)
    private val lock = Any()
    private var armedRoot: Job? = null
    private var held: Pending? = null

    val isHeld: Boolean get() = synchronized(lock) { held != null }

    fun arm(root: Job) = synchronized(lock) {
        check(armedRoot == null && held == null)
        armedRoot = root
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = delegate.isDispatchNeeded(context)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val hold = synchronized(lock) {
            val root = armedRoot ?: return@synchronized false
            val job = context[Job] ?: return@synchronized false
            if (!job.isDescendantOf(root)) return@synchronized false
            check(held == null)
            held = Pending(context, block)
            armedRoot = null
            true
        }
        if (!hold) delegate.dispatch(context, block)
    }

    fun release() {
        val pending = synchronized(lock) {
            armedRoot = null
            held.also { held = null }
        }
        if (pending != null) delegate.dispatch(pending.context, pending.block)
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
