package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.sinura.personaltrainer.domain.coach.CoachEvidenceCopy
import com.sinura.personaltrainer.domain.coach.CoachSuggestion
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary

@Composable
internal fun EvidenceCitationChip(
    suggestion: CoachSuggestion,
    onShowDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val line = CoachEvidenceCopy.basedOnLine(suggestion)
        ?: CoachEvidenceCopy.heuristicOnlyLine(suggestion)
        ?: return
    val spoken = evidenceSpoken(suggestion)
    // A full 48 dp target with a button's role: it opens the evidence, and a two-line
    // caption was a thin strip to aim a thumb at and did not say it could be pressed.
    Box(
        modifier = modifier
            .heightIn(min = Metrics.touchMin)
            .clickable(role = Role.Button, onClickLabel = CoachEvidenceCopy.OPEN_EVIDENCE, onClick = onShowDetail)
            .testTag(WorkoutTestTags.COACH_EVIDENCE_CHIP)
            .clearAndSetSemantics { contentDescription = spoken },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = line,
            style = InstrumentType.caption,
            color = TextSecondary,
            maxLines = 2,
        )
    }
}

private fun evidenceSpoken(suggestion: CoachSuggestion): String {
    val primary = suggestion.primaryEvidence()
    return if (primary != null) {
        "Evidence, ${CoachEvidenceCopy.chipLabel(primary)}, ${primary.claim}"
    } else {
        "Evidence, Temper heuristics"
    }
}
