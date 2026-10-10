package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.coach.CoachEvidenceCopy
import com.sinura.personaltrainer.domain.coach.EvidenceCatalog
import com.sinura.personaltrainer.domain.coach.EvidenceEntry
import com.sinura.personaltrainer.domain.coach.TempoWhySheetCopy
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.SectionEdge
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TempoWhySheet(
    model: TempoWhySheetCopy.SheetModel,
    canApply: Boolean,
    applied: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface3,
    ) {
        CompositionLocalProvider(LocalDensity provides density, LocalLayoutDirection provides direction) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Metrics.gutter)
                    .padding(bottom = Metrics.space6)
                    .testTag(WorkoutTestTags.TEMPO_WHY_SHEET),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Metrics.space3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
                ) {
                    Image(
                        painter = painterResource(R.drawable.tempo_coach_avatar),
                        contentDescription = null,
                        modifier = Modifier.size(Metrics.touchMin),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        model.title,
                        modifier = Modifier.weight(1f).testTag(WorkoutTestTags.TEMPO_WHY_TITLE),
                        style = InstrumentType.title,
                        color = TextPrimary,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Reserve room for the fixed actions; a long explanation must
                        // scroll instead of measuring Keep/Use at zero height.
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = Metrics.space4),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                ) {
                    SuggestionCallout(model.callout)
                    Text(
                        model.summary,
                        modifier = Modifier.testTag(WorkoutTestTags.TEMPO_WHY_SUMMARY),
                        style = InstrumentType.body,
                        color = TextSecondary,
                    )
                    DecisionBlock(model.decisionRows)
                    EvidenceBlock(
                        evidenceIds = model.evidenceIds,
                        onOpenDoi = { doi -> openCoachDoi(context, doi) },
                    )
                }
                HairlineDivider()
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                    if (canApply && !applied) {
                        PrimaryGymButton(
                            text = SetMicroRecCopy.USE_SUGGESTION,
                            onClick = {
                                Haptics.tick(view)
                                onApply()
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(WorkoutTestTags.TEMPO_WHY_USE),
                            height = Metrics.logFloorCommit,
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Metrics.touchMin)
                            .testTag(WorkoutTestTags.TEMPO_WHY_KEEP),
                    ) {
                        Text(
                            SetMicroRecCopy.KEEP_MY_NUMBERS,
                            style = InstrumentType.bodyStrong,
                            color = if (canApply && !applied) TextSecondary else Volt,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCallout(callout: TempoWhySheetCopy.Callout) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WorkoutTestTags.TEMPO_WHY_CALLOUT),
        shape = RoundedCornerShape(Radius.md),
        color = Surface3,
        border = BorderStroke(Metrics.hairline, SectionEdge),
    ) {
        Column(
            modifier = Modifier.padding(Metrics.space4),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Text(
                callout.verb,
                style = InstrumentType.caption,
                color = Volt,
            )
            Text(
                callout.numbers,
                style = InstrumentType.numeralMd,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            callout.restLabel?.let { rest ->
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = Surface3,
                    border = BorderStroke(Metrics.hairline, SectionEdge),
                ) {
                    Text(
                        rest,
                        modifier = Modifier.padding(horizontal = Metrics.space3, vertical = Metrics.space1),
                        style = InstrumentType.caption,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DecisionBlock(rows: List<TempoWhySheetCopy.DecisionRow>) {
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        Text(
            TempoWhySheetCopy.SECTION_DECISION,
            style = InstrumentType.bodyStrong,
            color = TextPrimary,
        )
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
            ) {
                Text(
                    row.label,
                    modifier = Modifier.weight(0.42f),
                    style = InstrumentType.caption,
                    color = TextTertiary,
                )
                Column(modifier = Modifier.weight(0.58f)) {
                    Text(
                        row.value,
                        style = InstrumentType.bodyStrong,
                        color = TextPrimary,
                    )
                    row.hint?.let { hint ->
                        Text(
                            hint,
                            style = InstrumentType.caption,
                            color = TextTertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EvidenceBlock(
    evidenceIds: List<String>,
    onOpenDoi: (String) -> Unit,
) {
    val entries = EvidenceCatalog.resolve(evidenceIds)
    if (entries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        Text(
            TempoWhySheetCopy.SECTION_EVIDENCE,
            style = InstrumentType.bodyStrong,
            color = TextPrimary,
        )
        entries.forEach { entry ->
            EvidenceRow(entry = entry, onOpenDoi = onOpenDoi)
        }
    }
}

@Composable
private fun EvidenceRow(
    entry: EvidenceEntry,
    onOpenDoi: (String) -> Unit,
) {
    val tag = WorkoutTestTags.tempoWhyEvidence(entry.id)
    val openable = !entry.heuristic && entry.doi != null
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(Metrics.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                    Text(
                        CoachEvidenceCopy.chipLabel(entry),
                        style = InstrumentType.bodyStrong,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.heuristic) {
                        Text(
                            TempoWhySheetCopy.HEURISTIC_BADGE,
                            style = InstrumentType.caption,
                            color = TextTertiary,
                        )
                    }
                }
                Text(
                    entry.claim,
                    style = InstrumentType.caption,
                    color = TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!entry.heuristic && entry.title.isNotBlank()) {
                    Text(
                        entry.title,
                        style = InstrumentType.caption,
                        color = TextTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (openable) {
                Icon(
                    TemperIcons.Chevron,
                    contentDescription = null,
                    tint = Volt,
                    modifier = Modifier.size(Metrics.icon),
                )
            }
        }
    }
    if (openable) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    role = Role.Button
                    contentDescription = "${CoachEvidenceCopy.chipLabel(entry)}. ${entry.claim}. ${TempoWhySheetCopy.OPENS_STUDY}"
                }
                .testTag(tag),
            onClick = { onOpenDoi(checkNotNull(entry.doi)) },
            shape = RoundedCornerShape(Radius.md),
            color = Surface3,
            border = BorderStroke(Metrics.hairline, SectionEdge),
            content = rowContent,
        )
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth().testTag(tag),
            shape = RoundedCornerShape(Radius.md),
            color = Surface3,
            border = BorderStroke(Metrics.hairline, SectionEdge),
            content = rowContent,
        )
    }
}
