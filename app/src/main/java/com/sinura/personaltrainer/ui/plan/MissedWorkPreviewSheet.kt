package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinura.personaltrainer.data.draft.DraftRecoveryOutcome
import com.sinura.personaltrainer.data.repository.MissedWorkProposal
import com.sinura.personaltrainer.domain.MissedWorkChoice
import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.units.DateCopy
import java.time.LocalDate

internal object MissedWorkPreviewTags {
    const val SHEET = "missed-work-preview"
    const val CONFIRM = "missed-work-confirm"
    const val CLOSE = "missed-work-close"
    const val READ = "missed-work-read-again"
    const val REVIEW = "missed-work-review-current"
    const val DISCARD = "missed-work-discard"
    const val RESUME = "missed-work-resume"
    const val ERROR = "missed-work-preview-error"
}

@Composable
internal fun MissedWorkRetainedCard(form: MissedWorkForm,
    state: MissedWorkFormState = form.snapshot.collectAsStateWithLifecycle().value) {
    if (state.retained || state.error != null) {
        GymCard {
            Kicker("Unfinished week update")
            Text(state.error ?: "Your proposal is kept on this phone. Review it before changing the week.",
                style = InstrumentType.caption, color = TextSecondary)
            TextButton(onClick = form::showRetainedPreview, enabled = !state.busy,
                modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(MissedWorkPreviewTags.RESUME)) {
                Text("Review proposal", style = InstrumentType.bodyStrong)
            }
        }
    } else state.receipt?.let {
        Text(it, style = InstrumentType.caption, color = TextSecondary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MissedWorkPreviewSheet(form: MissedWorkForm, routines: List<Routine>, onReviewCurrent: () -> Unit,
    state: MissedWorkFormState = form.snapshot.collectAsStateWithLifecycle().value) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    if (state.visible) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { value -> value != SheetValue.Hidden || !form.snapshot.value.busy })
        ModalBottomSheet(onDismissRequest = form::close, sheetState = sheet, containerColor = Surface3) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.gutter).padding(bottom = Metrics.space5).testTag(MissedWorkPreviewTags.SHEET),
                verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                Text("Review week", style = InstrumentType.title, color = TextPrimary)
                state.proposal?.let { proposal ->
                    Text(missedChoiceTitle(proposal.choice), style = InstrumentType.bodyStrong, color = TextPrimary)
                    Text("${day(proposal.weekStartEpochDay)} – ${day(proposal.weekStartEpochDay + 6)}",
                        style = InstrumentType.caption, color = TextSecondary)
                    if (state.resumed) Text("Resumed proposal. Reopening does not apply it.", style = InstrumentType.caption, color = TextSecondary)
                    MissedWorkChangeList(proposal = proposal, routines = routines)
                    Text("Completed history and next week’s schedule stay unchanged.",
                        style = InstrumentType.caption, color = TextSecondary)
                    Text("Enabled reminders follow changed dates; delivery remains best effort.",
                        style = InstrumentType.caption, color = TextSecondary)
                }
                state.error?.let { error ->
                    Text(error, style = InstrumentType.bodyStrong, color = Danger,
                        modifier = Modifier.testTag(MissedWorkPreviewTags.ERROR).semantics { liveRegion = LiveRegionMode.Polite })
                }
                PrimaryGymButton(text = when {
                    state.busy -> "Checking update…"
                    state.outcome == DraftRecoveryOutcome.CONFLICT -> "Review current schedule"
                    !state.canConfirm -> "Read update again"
                    state.pending -> "Retry update"
                    else -> "Confirm ${state.proposal?.choice?.let(::missedChoiceTitle)?.lowercase() ?: "update"}"
                }, enabled = !state.busy, onClick = {
                    when {
                        state.outcome == DraftRecoveryOutcome.CONFLICT -> onReviewCurrent()
                        state.canConfirm -> form.confirm()
                        else -> form.readAgain()
                    }
                }, modifier = Modifier.testTag(when {
                    state.outcome == DraftRecoveryOutcome.CONFLICT -> MissedWorkPreviewTags.REVIEW
                    state.canConfirm || state.busy -> MissedWorkPreviewTags.CONFIRM
                    else -> MissedWorkPreviewTags.READ
                }))
                TextButton(onClick = form::close, enabled = !state.busy,
                    modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(MissedWorkPreviewTags.CLOSE)) {
                    Text("Close · keep proposal", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
                TextButton(onClick = { confirmDiscard = true }, enabled = !state.busy,
                    modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(MissedWorkPreviewTags.DISCARD)) {
                    Text("Discard unfinished proposal", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
            }
        }
    }
    if (confirmDiscard) ConfirmActionDialog(title = "Discard unfinished proposal?",
        body = "This removes only the private proposal. Any week changes already saved and all recorded sessions stay saved.",
        confirmLabel = "Discard proposal", destructive = true, confirmEnabled = !state.busy,
        onConfirm = { confirmDiscard = false; form.discard() }, onDismiss = { confirmDiscard = false })
}

/** Each before/after row is literal policy data; no inferred pairing hides removed or added work. */
@Composable
private fun MissedWorkChangeList(proposal: MissedWorkProposal, routines: List<Routine>) {
    val names = routines.associate { it.id to it.name }
    val before = proposal.occurrencesBefore.associateBy { it.id }
    val after = proposal.occurrencesAfter.associateBy { it.id }
    val changedBefore = proposal.occurrencesBefore.filter { !sameScheduledWork(after[it.id], it) }
    val changedAfter = proposal.occurrencesAfter.filter { !sameScheduledWork(before[it.id], it) }
    val groups = (changedBefore + changedAfter).sortedWith(compareBy({ it.localEpochDay }, { it.minutesOfDay }, { it.id }))
        .map { it.ruleId }.distinct()
    GymCard {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            groups.forEachIndexed { index, ruleId ->
                if (index > 0) HairlineDivider()
                val original = changedBefore.filter { it.ruleId == ruleId }
                val intended = changedAfter.filter { it.ruleId == ruleId }
                val representative = (original + intended).first()
                val title = DailyAgenda.forDay(representative.localEpochDay, listOf(representative), proposal.rulesBefore, names).single().title
                val routineId = proposal.rulesBefore.firstOrNull { it.id == ruleId }?.routineId
                val exercise = routines.firstOrNull { it.id == routineId }?.exercises?.firstOrNull()?.exercise
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                    exercise?.let { ExerciseThumb(exercise = it) }
                    Text(title, style = InstrumentType.bodyStrong, color = TextPrimary,
                        modifier = Modifier.weight(1f).semantics { heading() })
                }
                if (original.isNotEmpty()) {
                    Kicker("Before")
                    original.forEach { row -> Text(previewRow(row), style = InstrumentType.body, color = TextPrimary) }
                }
                Kicker("After confirmation")
                intended.forEach { row -> Text(previewRow(row), style = InstrumentType.body, color = TextPrimary) }
                if (intended.isEmpty()) Text("Removed from the plan.", style = InstrumentType.body, color = TextSecondary)
            }
            if (changedAfter.isEmpty()) Text("No sessions added or changed.", style = InstrumentType.body, color = TextSecondary)
            val unchanged = proposal.occurrencesBefore.count { sameScheduledWork(after[it.id], it) }
            if (unchanged > 0) Text("$unchanged other ${if (unchanged == 1) "session stays" else "sessions stay"} unchanged.",
                style = InstrumentType.caption, color = TextSecondary)
        }
    }
}

private fun previewRow(row: ScheduleOccurrence): String {
    val status = when (row.status) {
        OccurrenceStatus.PLANNED -> "Planned"
        OccurrenceStatus.MOVED -> "Moved"
        OccurrenceStatus.MISSED -> "Missed"
        OccurrenceStatus.SKIPPED -> "Skipped"
        OccurrenceStatus.DONE -> "Completed"
    }
    return "${day(row.localEpochDay)} · $status"
}
private fun sameScheduledWork(a: ScheduleOccurrence?, b: ScheduleOccurrence): Boolean =
    a?.copy(createdAtMs = 0, updatedAtMs = 0) == b.copy(createdAtMs = 0, updatedAtMs = 0)
private fun day(epochDay: Long): String = DateCopy.weekdayShort(LocalDate.ofEpochDay(epochDay))
private fun missedChoiceTitle(choice: MissedWorkChoice): String = when (choice) {
    MissedWorkChoice.MOVE_REMAINING -> "Move remaining"
    MissedWorkChoice.ADAPT_WEEK -> "Rebuild week"
    MissedWorkChoice.KEEP_DATES -> "Keep dates"
    MissedWorkChoice.SKIP_MISSED -> "Skip missed"
}
