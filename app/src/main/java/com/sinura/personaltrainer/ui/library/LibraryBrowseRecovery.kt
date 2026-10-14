package com.sinura.personaltrainer.ui.library

import com.sinura.personaltrainer.data.draft.LibraryBrowseDrafts
import com.sinura.personaltrainer.data.draft.LibraryBrowseHandle
import com.sinura.personaltrainer.data.draft.LibraryBrowseSnapshot
import com.sinura.personaltrainer.data.draft.DraftVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class LibraryBrowseStatus(
    val opening: Boolean = true,
    val keeping: Boolean = false,
    val error: String? = null,
    val resumed: Boolean = false,
) {
    val busy: Boolean get() = opening || keeping
}

internal data class LibraryFrameworkBasis(val version: DraftVersion?, val unkept: Boolean)

/** Live controls own their values. Private acknowledgements never replace current typing. */
internal class LibraryBrowseRecovery(
    private val scope: CoroutineScope,
    private val drafts: LibraryBrowseDrafts,
    private val current: () -> LibraryBrowseSnapshot,
    private val restore: (LibraryBrowseSnapshot) -> Unit,
    private val framework: () -> LibraryFrameworkBasis,
    private val onBasis: (DraftVersion) -> Unit,
    private val onKept: (DraftVersion) -> Unit,
) {
    private val mutableStatus = MutableStateFlow(LibraryBrowseStatus())
    val status: StateFlow<LibraryBrowseStatus> = mutableStatus
    private val serial = Mutex()
    private var handle: LibraryBrowseHandle? = null
    private var opened = false
    private var foreground = false
    private var epoch = 0L
    private var revision = 0L
    private var pendingLeave: (() -> Unit)? = null
    private var pendingReset: LibraryBrowseSnapshot? = null
    private var unreadableBaseline: LibraryBrowseSnapshot? = null
    private var explicitReadRecovery = false
    val editable: Boolean get() = !status.value.busy && pendingReset == null

    fun open(explicitRoute: Boolean) {
        if (foreground) return
        foreground = true
        val returning = opened
        opened = true
        val owner = ++epoch
        val initial = current()
        if (!returning) explicitReadRecovery = explicitRoute
        val frameworkSeed = framework()
        mutableStatus.value = status.value.copy(opening = true, keeping = false)
        scope.launch { serial.withLock {
            if (owner != epoch) return@withLock
            try {
                val retained = handle
                if (returning && retained != null && drafts.isCurrent(retained)) {
                    if (owner == epoch) {
                        val accepted = retained.checkpoint
                        if (pendingReset != null && accepted?.draft == LibraryBrowseSnapshot()) {
                            restore(checkNotNull(pendingReset))
                            pendingReset = null
                        }
                        if (accepted != null) {
                            onBasis(accepted.version)
                            if (accepted.draft == current()) onKept(accepted.version)
                        }
                        mutableStatus.value = status.value.copy(opening = false)
                        if (pendingReset != null) failed()
                    }
                    return@withLock
                }
                val loaded = drafts.open()
                if (owner != epoch) return@withLock
                handle = loaded
                explicitReadRecovery = explicitRoute
                unreadableBaseline = if (loaded.readable) null else initial
                check(loaded.readable)
                pendingReset = null
                val wasResumed = !explicitRoute && loaded.remembered != null && loaded.remembered != LibraryBrowseSnapshot()
                val version = checkNotNull(loaded.checkpoint).version
                val newerFramework = !returning && frameworkSeed.unkept && frameworkSeed.version == version
                onBasis(version)
                // An explicit new Body→Library request keeps its requested muscle scope.
                val value = if (explicitRoute || newerFramework) initial else loaded.remembered ?: initial
                restore(value)
                drafts.keep(loaded, current())
                if (owner != epoch) return@withLock
                onKept(checkNotNull(loaded.checkpoint).version)
                explicitReadRecovery = false
                mutableStatus.value = LibraryBrowseStatus(opening = false,
                    resumed = wasResumed)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (owner == epoch) failed() }
        } }
    }

    /** Retire queued navigation when its screen leaves composition. Completed IO remains durable. */
    fun closeVisit() {
        foreground = false
        epoch += 1L
        pendingLeave = null
    }

    fun changed() {
        if (!foreground || status.value.busy) return
        val owner = epoch
        val admitted = ++revision
        val value = current()
        scope.launch {
            delay(BROWSE_CHECKPOINT_DELAY_MS)
            serial.withLock {
            if (owner != epoch || admitted != revision) return@withLock
            val active = handle ?: return@withLock
            try {
                drafts.keep(active, value)
                if (handle === active) onBasis(checkNotNull(active.checkpoint).version)
                if (owner == epoch) {
                    val version = checkNotNull(active.checkpoint).version
                    if (admitted == revision) {
                        onKept(version)
                        mutableStatus.value = status.value.copy(error = null, resumed = false)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (owner == epoch && admitted == revision) failed() }
        } }
    }

    /** Reserve departure synchronously and flush the exact latest view before navigation. */
    fun leave(onKept: () -> Unit) {
        if (!foreground || status.value.busy) return
        pendingLeave = onKept
        keep()
    }

    fun keep() {
        if (!foreground || status.value.busy) return
        pendingReset?.let { reset(it); return }
        val value = current()
        val owner = epoch
        revision += 1L
        mutableStatus.value = status.value.copy(keeping = true)
        scope.launch { serial.withLock {
            if (owner != epoch) return@withLock
            try {
                var active = handle
                var accepted = value
                if (active == null || !drafts.isCurrent(active)) {
                    active = drafts.open()
                    if (owner != epoch) return@withLock
                    handle = active
                    check(active.readable)
                    // A newer visit owns the accepted context; never overwrite it with an old screen.
                    restore(active.remembered ?: LibraryBrowseSnapshot())
                    accepted = current()
                }
                if (!active.readable) {
                    drafts.reload(active)
                    val baseline = unreadableBaseline
                    val frameworkSeed = framework()
                    val sameAuthoredView = baseline != null && value.copy(anchor = null, index = 0, offsetDp = 0f) ==
                        baseline.copy(anchor = null, index = 0, offsetDp = 0f)
                    val newerFramework = frameworkSeed.unkept && frameworkSeed.version == active.checkpoint?.version
                    if (sameAuthoredView && !newerFramework && !explicitReadRecovery) {
                        restore(active.remembered ?: value)
                        accepted = current()
                    }
                }
                drafts.keep(active, accepted)
                unreadableBaseline = null
                explicitReadRecovery = false
                if (handle === active) onBasis(checkNotNull(active.checkpoint).version)
                if (owner != epoch) return@withLock
                onKept(checkNotNull(active.checkpoint).version)
                mutableStatus.value = LibraryBrowseStatus(opening = false)
                val action = pendingLeave
                pendingLeave = null
                action?.invoke()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (owner == epoch) failed() }
        } }
    }

    /** Confirmation belongs to the screen. Reset never abandons an exercise-editor draft. */
    fun reset(defaults: LibraryBrowseSnapshot) {
        if (!foreground || status.value.busy) return
        val owner = ++epoch
        revision += 1L
        pendingLeave = null
        pendingReset = defaults
        mutableStatus.value = status.value.copy(keeping = true)
        scope.launch { serial.withLock {
            if (owner != epoch) return@withLock
            try {
                val active = handle ?: drafts.open().also { handle = it }
                if (!drafts.isCurrent(active)) {
                    val latest = drafts.open()
                    if (owner != epoch) return@withLock
                    handle = latest
                    check(latest.readable)
                    resumeInspectedReset(latest)
                    return@withLock
                }
                drafts.reset(active)
                if (owner != epoch) return@withLock
                restore(defaults)
                drafts.keep(active, current())
                if (owner != epoch) return@withLock
                onKept(checkNotNull(active.checkpoint).version)
                pendingReset = null
                mutableStatus.value = LibraryBrowseStatus(opening = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (owner == epoch) {
                    // An opaque reset may have met a newly readable or changed healthy view.
                    // Inspect it, resume it, and require confirmation again rather than blind Retry.
                    try {
                        val active = checkNotNull(handle)
                        drafts.reload(active)
                        if (owner == epoch) resumeInspectedReset(active)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { if (owner == epoch) failed() }
                }
            }
        } }
    }

    private fun resumeInspectedReset(active: LibraryBrowseHandle) {
        restore(active.remembered ?: LibraryBrowseSnapshot())
        unreadableBaseline = null
        explicitReadRecovery = false
        onKept(checkNotNull(active.checkpoint).version)
        pendingReset = null
        mutableStatus.value = LibraryBrowseStatus(opening = false, resumed = true,
            error = "The remembered view was checked and resumed. Confirm Reset again to clear it.")
    }

    private fun failed() {
        mutableStatus.value = LibraryBrowseStatus(opening = false, error = if (pendingReset != null)
            "View reset needs checking. Retry before changing search. Exercises and history stay saved."
        else "Search was not kept. Current values remain here. Retry, or reset only the remembered view.")
    }
}

private const val BROWSE_CHECKPOINT_DELAY_MS = 150L
