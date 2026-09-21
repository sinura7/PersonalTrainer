package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.domain.coach.CoachEvidenceCopy
import com.sinura.personaltrainer.domain.coach.CoachSuggestion
import com.sinura.personaltrainer.ui.theme.InstrumentType
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
    Text(
        text = line,
        modifier = modifier
            .clickable(onClick = onShowDetail)
            .testTag(WorkoutTestTags.COACH_EVIDENCE_CHIP)
            .semantics { contentDescription = spoken },
        style = InstrumentType.caption,
        color = TextSecondary,
        maxLines = 2,
    )
}

private fun evidenceSpoken(suggestion: CoachSuggestion): String {
    val primary = suggestion.primaryEvidence()
    return if (primary != null) {
        "Evidence, ${CoachEvidenceCopy.chipLabel(primary)}, ${primary.claim}"
    } else {
        "Evidence, Temper heuristics"
    }
}
