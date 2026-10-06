package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.coach.CoachEvidenceCopy
import com.sinura.personaltrainer.domain.coach.CoachSuggestion
import com.sinura.personaltrainer.domain.coach.TempoCoachTip
import com.sinura.personaltrainer.domain.coach.TempoWhySheetCopy
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.QuietButton
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.SectionEdge
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

@Composable
internal fun TempoCoachCard(
    tip: TempoCoachTip,
    loadClass: LoadClass,
    unit: WeightUnit,
    applied: Boolean,
    enabled: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    compactLandscape: Boolean = false,
    compactStrip: Boolean = false,
) {
    val view = LocalView.current
    var showWhy by rememberSaveable(tip.tipShort) { mutableStateOf(false) }
    var showEvidence by rememberSaveable(tip.tipShort) { mutableStateOf(false) }
    val (rec, suggestion, canUse) = when (tip) {
        is TempoCoachTip.NextSet -> Triple(
            tip.rec,
            tip.suggestion,
            tip.rec.showApply && !tip.rec.previewOnly,
        )
        is TempoCoachTip.AddASet -> Triple(
            tip.seedRec,
            CoachSuggestion(
                weightKg = tip.seedRec.nextWeightKg,
                reps = tip.seedRec.nextReps,
                rpe = tip.seedRec.nextRpe,
                restSeconds = tip.seedRec.restSeconds,
                reasonCode = "ADD_A_SET",
                explanationShort = tip.tipShort,
                evidenceIds = tip.evidenceIds,
                trace = tip.trace,
                previewOnly = false,
                showApply = true,
                anotherSetAdvised = false,
                warmupSets = emptyList(),
            ),
            true,
        )
    }
    val numbers = SetMicroRecCopy.numbers(rec, loadClass, unit)
    val detailLine = when (tip) {
        is TempoCoachTip.AddASet -> "Add 1 working set · $numbers"
        is TempoCoachTip.NextSet -> numbers
    }
    val stackWells = LogLoopScale.stackEntryWells(LocalDensity.current.fontScale)
    val oneLineCoach = compactLandscape || compactStrip
    val showAvatar = true
    val avatarSize = if (oneLineCoach) Metrics.space6 else Metrics.touchMin
    val pad = if (oneLineCoach) Metrics.space1 else Metrics.space2
    val touchMin = if (oneLineCoach) Metrics.space6 else Metrics.touchMin
    val tempoActions: @Composable RowScope.() -> Unit = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .heightIn(min = touchMin)
                .testTag(WorkoutTestTags.TEMPO_COACH_DISMISS),
        ) {
            Text("×", style = InstrumentType.bodyStrong, color = TextSecondary)
        }
        TextButton(
            onClick = { showWhy = true },
            modifier = Modifier
                .heightIn(min = touchMin)
                .testTag(WorkoutTestTags.MICRO_REC_WHY),
        ) {
            Text("Why?", style = InstrumentType.bodyStrong, color = TextSecondary)
        }
        if (canUse) {
            QuietButton(
                text = if (applied) "Applied" else "Apply",
                onClick = onApply,
                modifier = Modifier.testTag(WorkoutTestTags.MICRO_REC_APPLY),
                enabled = enabled && !applied,
                leading = if (applied) TemperIcons.Check else null,
                accent = applied,
                spoken = if (applied) "Suggestion applied, $numbers" else "Apply suggestion, $numbers",
            )
        }
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WorkoutTestTags.TEMPO_COACH_CARD),
        shape = RoundedCornerShape(Radius.md),
        color = Surface3,
        border = BorderStroke(Metrics.hairline, SectionEdge),
    ) {
        when {
            oneLineCoach && !(compactStrip && stackWells && !compactLandscape) -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(pad)
                        .semantics { contentDescription = "Tempo, ${tip.tipShort}, $detailLine" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
                ) {
                    if (showAvatar) {
                        Image(
                            painter = painterResource(R.drawable.tempo_coach_avatar),
                            contentDescription = null,
                            modifier = Modifier.size(avatarSize),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Text(
                        detailLine,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(WorkoutTestTags.MICRO_REC),
                        style = InstrumentType.bodyStrong,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    tempoActions()
                }
            }
            stackWells -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(pad),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.tempo_coach_avatar),
                            contentDescription = "Tempo coach",
                            modifier = Modifier.size(avatarSize),
                            contentScale = ContentScale.Crop,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Tempo",
                                style = InstrumentType.caption,
                                color = TextSecondary,
                                maxLines = 1,
                            )
                            Text(
                                tip.tipShort,
                                style = InstrumentType.bodyStrong,
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        detailLine,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(WorkoutTestTags.MICRO_REC),
                        style = InstrumentType.caption,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
                        verticalAlignment = Alignment.CenterVertically,
                        content = tempoActions,
                    )
                }
            }
            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(pad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    Image(
                        painter = painterResource(R.drawable.tempo_coach_avatar),
                        contentDescription = "Tempo coach",
                        modifier = Modifier.size(avatarSize),
                        contentScale = ContentScale.Crop,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                    ) {
                        Text(
                            "Tempo",
                            style = InstrumentType.caption,
                            color = TextSecondary,
                            maxLines = 1,
                        )
                        Text(
                            tip.tipShort,
                            style = InstrumentType.bodyStrong,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            detailLine,
                            style = InstrumentType.caption,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag(WorkoutTestTags.MICRO_REC),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
                        verticalAlignment = Alignment.CenterVertically,
                        content = tempoActions,
                    )
                }
            }
        }
    }
    if (showEvidence) {
        ConfirmActionDialog(
            title = "Evidence",
            body = CoachEvidenceCopy.detailLines(suggestion).joinToString("\n\n"),
            confirmLabel = "Close",
            dismissLabel = null,
            onConfirm = { showEvidence = false },
            onDismiss = { showEvidence = false },
        )
    }
    if (showWhy) {
        val whyModel = when (tip) {
            is TempoCoachTip.NextSet ->
                TempoWhySheetCopy.forNextSet(tip.rec, tip.suggestion, loadClass, unit)
            is TempoCoachTip.AddASet ->
                TempoWhySheetCopy.forAddASet(tip, tip.seedRec, loadClass, unit)
        }
        TempoWhySheet(
            model = whyModel,
            canApply = canUse,
            applied = applied,
            onApply = onApply,
            onDismiss = { showWhy = false },
        )
    }
}
