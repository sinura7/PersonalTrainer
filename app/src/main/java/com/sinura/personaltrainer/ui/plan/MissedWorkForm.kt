package com.sinura.personaltrainer.ui.plan

import com.sinura.personaltrainer.data.draft.DraftRecoveryOutcome
import com.sinura.personaltrainer.data.draft.MissedWorkDrafts
import com.sinura.personaltrainer.data.draft.MissedWorkHandle
import com.sinura.personaltrainer.data.repository.MissedWorkProposal
import com.sinura.personaltrainer.domain.MissedWorkChoice
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class MissedWorkFormState(
    val visible: Boolean = false,
    val busy: Boolean = false,
    val retained: Boolean = false,
    val resumed: Boolean = false,
    val proposal: MissedWorkProposal? = null,
    val pending: Boolean = false,
    val outcome: DraftRecoveryOutcome? = null,
    val error: String? = null,
    val receipt: String? = null,
) {
    val canConfirm: Boolean get() = !busy && proposal != null && outcome == DraftRecoveryOutcome.UNCOMMITTED
}

/** UI admission is synchronous; the shared private service arbitrates Home/Plan visits. */
internal class MissedWorkForm(private val drafts: MissedWorkDrafts?, private val scope: CoroutineScope) {
    private val updates = MutableStateFlow(MissedWorkFormState())
    val snapshot = updates.asStateFlow()
    private var handle: MissedWorkHandle? = null

    fun readRetained(): Boolean = start {
        handle = checkNotNull(drafts).open()
        val active = checkNotNull(handle)
        val outcome = if (active.proposal != null) drafts.inspect(active) else null
        reflect(outcome = outcome, resumed = active.proposal != null)
    }

    fun prepare(choice: MissedWorkChoice, weekStart: Long): Boolean = start(visible = true) {
        val service = checkNotNull(drafts)
        val active = service.open().also { handle = it }
        if (active.proposal != null) {
            val outcome = service.inspect(active)
            reflect(visible = outcome != DraftRecoveryOutcome.EXACT_SAVED, outcome = outcome, resumed = true)
        } else {
            service.propose(active, choice, weekStart)
            reflect(visible = true, outcome = DraftRecoveryOutcome.UNCOMMITTED)
        }
    }

    fun showRetainedPreview() { if (!updates.value.busy) updates.value = updates.value.copy(visible = true) }
    fun close() { if (!updates.value.busy) updates.value = updates.value.copy(visible = false) }

    fun readAgain(): Boolean = start(visible = updates.value.visible) {
        val service = checkNotNull(drafts)
        val active = service.open().also { handle = it }
        val outcome = if (active.proposal != null) service.inspect(active) else null
        reflect(visible = updates.value.visible && outcome != DraftRecoveryOutcome.EXACT_SAVED, outcome = outcome, resumed = true)
    }

    fun reviewCurrent(weekStart: Long): Boolean = start(visible = true) {
        checkNotNull(drafts).reviewCurrent(checkNotNull(handle), weekStart)
        reflect(visible = handle?.proposal != null, outcome = if (handle?.proposal == null) DraftRecoveryOutcome.EXACT_SAVED else DraftRecoveryOutcome.UNCOMMITTED)
    }

    fun confirm(): Boolean {
        if (!updates.value.canConfirm) return false
        return start(visible = true) {
            val outcome = checkNotNull(drafts).confirm(checkNotNull(handle))
            reflect(visible = outcome != DraftRecoveryOutcome.EXACT_SAVED, outcome = outcome,
                failure = if (outcome == DraftRecoveryOutcome.UNCOMMITTED) "The update did not save. Your exact proposal is kept for Retry." else null)
        }
    }

    fun discard(): Boolean = start(visible = updates.value.visible) {
        checkNotNull(drafts).discard(checkNotNull(handle))
        updates.value = MissedWorkFormState(receipt = "Unfinished proposal discarded. Saved work is unchanged.")
    }

    private fun reflect(visible: Boolean = false, outcome: DraftRecoveryOutcome? = null, resumed: Boolean = false, failure: String? = null) {
        val active = handle
        val error = failure ?: when {
            active?.readable == false -> "The unfinished proposal could not be read. Read again or discard only the private proposal."
            outcome == DraftRecoveryOutcome.CONFLICT -> "Your schedule or date changed. Review the current week before confirming."
            outcome == DraftRecoveryOutcome.UNAVAILABLE -> "Could not check this update. The proposal is kept; no Retry is allowed until its result is known."
            else -> null
        }
        updates.value = MissedWorkFormState(visible = visible, retained = active?.proposal != null || active?.readable == false,
            resumed = resumed, proposal = active?.proposal, pending = active?.pending == true, outcome = outcome, error = error,
            receipt = if (outcome == DraftRecoveryOutcome.EXACT_SAVED) "This week’s update is confirmed saved." else null)
    }

    private fun start(visible: Boolean = updates.value.visible, block: suspend () -> Unit): Boolean {
        if (drafts == null || updates.value.busy) return false
        updates.value = updates.value.copy(visible = visible, busy = true, error = null, receipt = null)
        scope.launch {
            runCatchingCancellable { block() }.onFailure {
                updates.value = updates.value.copy(busy = false, retained = true, proposal = handle?.proposal,
                    pending = handle?.pending == true, outcome = null,
                    error = "Could not finish this step. Read the proposal again before continuing.")
            }
        }
        return true
    }
}
