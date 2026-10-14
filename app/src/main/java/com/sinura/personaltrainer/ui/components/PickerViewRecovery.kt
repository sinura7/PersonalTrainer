package com.sinura.personaltrainer.ui.components

import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.DraftVersion
import com.sinura.personaltrainer.data.draft.PickerContextDrafts
import com.sinura.personaltrainer.data.draft.PickerContextHandle
import com.sinura.personaltrainer.data.draft.PickerContextSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class PickerFrameworkSeed(
    val owner: DraftOwner,
    val version: DraftVersion?,
    val fields: PickerContextSnapshot,
    val unkept: Boolean,
)

internal data class PickerViewStatus(
    val opening: Boolean = true,
    val keeping: Boolean = false,
    val resumed: Boolean = false,
    val changedElsewhere: Boolean = false,
    val error: String? = null,
) {
    val busy: Boolean get() = opening || keeping
}

/** ViewModel-owned live fields; disk acknowledgements update their basis without undoing typing. */
internal class PickerViewRecovery(
    private val scope: CoroutineScope,
    private val drafts: PickerContextDrafts,
    private val owner: suspend () -> DraftOwner,
    private val stillOwned: suspend (DraftOwner) -> Boolean,
    private val initial: (DraftOwner) -> PickerContextSnapshot,
    private val framework: (DraftOwner) -> PickerFrameworkSeed?,
    private val cache: (PickerFrameworkSeed) -> Unit,
    private val restored: (PickerContextSnapshot) -> Unit,
) {
    private val mutableFields = MutableStateFlow(PickerContextSnapshot())
    val fields: StateFlow<PickerContextSnapshot> = mutableFields
    private val mutableStatus = MutableStateFlow(PickerViewStatus())
    val status: StateFlow<PickerViewStatus> = mutableStatus
    private val serial = Mutex()
    private var handle: PickerContextHandle? = null
    private var basis: DraftVersion? = null
    private var foreground = false
    private var epoch = 0L
    private var revision = 0L
    private var unkept = false
    private var pendingLeave: (() -> Unit)? = null
    private var departureUnsettled = false
    private var unreadableSeed: PickerContextSnapshot? = null
    val editable: Boolean get() = !status.value.busy && !status.value.changedElsewhere && !departureUnsettled
    val activeOwner: DraftOwner? get() = handle?.owner

    fun open() {
        if (foreground) return
        foreground = true
        val visit = ++epoch
        val returning = handle != null
        mutableStatus.value = status.value.copy(opening = true, keeping = false)
        scope.launch { serial.withLock {
            if (visit != epoch) return@withLock
            try {
                val requested = owner()
                check(stillOwned(requested))
                if (visit != epoch) return@withLock
                val retained = handle
                if (retained?.owner == requested && drafts.isCurrent(retained) && retained.readable) {
                    val prior = retained.checkpoint?.version
                    drafts.reload(retained)
                    if (visit != epoch) return@withLock
                    if (unkept && prior != retained.checkpoint?.version && fields.value != retained.remembered) {
                        changedElsewhere()
                        return@withLock
                    }
                    if (retained.needsReconciliation) drafts.reconcile(retained) { stillOwned(requested) }
                    if (visit != epoch) return@withLock
                    if (!unkept) mutableFields.value = retained.remembered ?: initial(requested)
                    basis = retained.checkpoint?.version
                    cacheCurrent()
                    restored(fields.value)
                    mutableStatus.value = status.value.copy(opening = false)
                    return@withLock
                }
                val seed = framework(requested)?.takeIf { it.owner == requested }
                val loaded = drafts.open(requested)
                if (visit != epoch) return@withLock
                handle = loaded
                val initialFields = seed?.fields ?: initial(requested)
                basis = seed?.version
                unkept = seed?.unkept == true
                if (!loaded.readable) {
                    mutableFields.value = initialFields
                    unreadableSeed = initialFields
                    restored(initialFields)
                    failed()
                    return@withLock
                }
                if (loaded.needsReconciliation) drafts.reconcile(loaded) { stillOwned(requested) }
                if (visit != epoch) return@withLock
                val wasResumed = loaded.remembered != null || seed != null
                if (!returning && seed?.unkept == true && seed.version != loaded.checkpoint?.version) {
                    mutableFields.value = seed.fields
                    restored(seed.fields)
                    mutableStatus.value = PickerViewStatus(opening = false, changedElsewhere = true,
                        error = "Picker changed elsewhere. Current fields remain here. Use the kept view before continuing.")
                    return@withLock
                }
                val newerFramework = !returning && seed?.unkept == true && seed.version == loaded.checkpoint?.version
                val value = if (newerFramework) checkNotNull(seed).fields else loaded.remembered ?: initialFields
                mutableFields.value = value
                basis = loaded.checkpoint?.version
                unkept = true
                cacheCurrent()
                restored(value)
                drafts.keep(loaded, value)
                if (visit != epoch) return@withLock
                basis = loaded.checkpoint?.version
                unkept = false
                unreadableSeed = null
                cacheCurrent()
                mutableStatus.value = PickerViewStatus(opening = false,
                    resumed = wasResumed)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (visit == epoch) failed() }
        } }
    }

    fun changed(value: PickerContextSnapshot) {
        if (!foreground || !editable || value == fields.value) return
        mutableFields.value = value
        unkept = true
        cacheCurrent()
        val visit = epoch
        val admitted = ++revision
        scope.launch {
            delay(150L)
            serial.withLock {
                if (visit != epoch || admitted != revision) return@withLock
                val active = handle ?: return@withLock
                try {
                    drafts.keep(active, value)
                    if (handle === active) {
                        basis = active.checkpoint?.version
                        if (admitted == revision) unkept = false
                        cacheCurrent()
                    }
                    if (visit == epoch && admitted == revision) {
                        mutableStatus.value = PickerViewStatus(opening = false)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (visit == epoch && admitted == revision) failed() }
            }
        }
    }

    /** Confirmed child handoff may finish fields while a retained departure is unresolved. */
    fun selectionCompleted() {
        if (!foreground || status.value.busy || status.value.changedElsewhere || handle == null) return
        mutableFields.value = fields.value.copy(creating = false, customDraftId = null)
        unkept = true
        revision++
        cacheCurrent()
    }

    fun closeVisit() { foreground = false; epoch += 1L; pendingLeave = null }

    /** Rebind a retired composition's close action without admitting another operation. */
    fun bindDeparture(onKept: () -> Unit) {
        if (departureUnsettled) pendingLeave = onKept
    }

    /** A layout observation cannot replace remembered authored fields after a failed read. */
    fun positionChanged(index: Int, offsetDp: Float, anchor: String?) {
        if (handle?.readable != true || status.value.busy || status.value.error != null || fields.value.creating) return
        changed(fields.value.copy(index = index, offsetDp = offsetDp, anchor = anchor))
    }

    fun leave(onKept: () -> Unit) {
        if (!foreground || status.value.busy || status.value.changedElsewhere) return
        departureUnsettled = true
        pendingLeave = onKept
        keep()
    }

    fun keep() {
        if (!foreground || status.value.busy || status.value.changedElsewhere) return
        val visit = epoch
        val value = fields.value
        revision += 1L
        mutableStatus.value = status.value.copy(keeping = true)
        scope.launch { serial.withLock {
            if (visit != epoch) return@withLock
            try {
                var active = handle
                if (active == null || !drafts.isCurrent(active)) {
                    val requested = owner()
                    check(stillOwned(requested))
                    active = drafts.open(requested)
                    if (visit != epoch) return@withLock
                    handle = active
                    check(active.readable)
                    mutableFields.value = active.remembered ?: initial(requested)
                    restored(fields.value)
                }
                if (!active.readable) {
                    drafts.reload(active)
                    if (value == unreadableSeed && !unkept) {
                        mutableFields.value = active.remembered ?: value
                        restored(fields.value)
                    }
                } else {
                    val prior = active.checkpoint?.version
                    drafts.reload(active)
                    if (visit != epoch) return@withLock
                    if (prior != active.checkpoint?.version && unkept && fields.value != active.remembered) {
                        changedElsewhere()
                        return@withLock
                    }
                }
                val checked = checkNotNull(active)
                if (checked.needsReconciliation) drafts.reconcile(checked) { stillOwned(checked.owner) }
                if (visit != epoch) return@withLock
                drafts.keep(checked, fields.value)
                if (handle === checked) { basis = checked.checkpoint?.version; unkept = false; cacheCurrent() }
                if (visit != epoch) return@withLock
                unreadableSeed = null
                mutableStatus.value = PickerViewStatus(opening = false)
                val action = pendingLeave
                pendingLeave = null
                if (action != null) departureUnsettled = false
                action?.invoke()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (visit == epoch) failed() }
        } }
    }

    fun useKeptView() {
        if (!foreground || status.value.busy || !status.value.changedElsewhere) return
        val visit = epoch
        mutableStatus.value = status.value.copy(keeping = true)
        scope.launch { serial.withLock {
            if (visit != epoch) return@withLock
            try {
                val active = checkNotNull(handle)
                check(stillOwned(active.owner))
                drafts.reload(active)
                if (active.needsReconciliation) drafts.reconcile(active) { stillOwned(active.owner) }
                if (visit != epoch) return@withLock
                mutableFields.value = active.remembered ?: initial(active.owner)
                basis = active.checkpoint?.version
                unkept = false
                cacheCurrent()
                restored(fields.value)
                mutableStatus.value = PickerViewStatus(opening = false, resumed = active.remembered != null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (visit == epoch) failed() }
        } }
    }

    /** Confirmed private reset retires delayed writes; accepted owner additions are untouched. */
    fun reset(onReset: () -> Unit = {}) {
        if (!foreground || status.value.busy) return
        val active = handle ?: return
        val visit = ++epoch
        revision++
        mutableStatus.value = status.value.copy(keeping = true)
        scope.launch { serial.withLock {
            if (visit != epoch) return@withLock
            try {
                drafts.discard(active)
                if (visit != epoch) return@withLock
                mutableFields.value = initial(active.owner)
                basis = active.checkpoint?.version
                unkept = false
                unreadableSeed = null
                cacheCurrent()
                restored(fields.value)
                handle = drafts.open(active.owner)
                check(checkNotNull(handle).readable)
                pendingLeave = null
                departureUnsettled = false
                mutableStatus.value = PickerViewStatus(opening = false)
                onReset()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (visit == epoch) failed() }
        } }
    }

    private fun cacheCurrent() {
        val active = handle ?: return
        cache(PickerFrameworkSeed(owner = active.owner, version = basis, fields = fields.value, unkept = unkept))
    }

    private fun failed() {
        mutableStatus.value = PickerViewStatus(opening = false, changedElsewhere = status.value.changedElsewhere,
            error = "Picker was not kept. Current fields remain here. Retry before leaving.")
    }

    private fun changedElsewhere() {
        mutableStatus.value = PickerViewStatus(opening = false, changedElsewhere = true,
            error = "Picker changed elsewhere. Current fields remain here. Use the kept view before continuing.")
    }
}
