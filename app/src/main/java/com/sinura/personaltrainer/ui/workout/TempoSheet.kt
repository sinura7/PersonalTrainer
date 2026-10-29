package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.coach.TempoCoachTip
import com.sinura.personaltrainer.ui.components.PrimaryButton
import com.sinura.personaltrainer.ui.components.SecondaryButton
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Warn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TempoSheet(
    tip: TempoCoachTip,
    currentTarget: String,
    suggestedTarget: String,
    applying: Boolean,
    showIntro: Boolean,
    applyFailed: Boolean = false,
    onUse: () -> Unit,
    onKeep: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (tip !is TempoCoachTip.NextSet) return
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onKeep,
        sheetState = sheetState,
        containerColor = Surface2,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space6)
                .testTag(WorkoutTestTags.TEMPO_SHEET),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.tempo_coach_avatar),
                    contentDescription = null,
                    modifier = Modifier.size(Metrics.space8),
                    contentScale = ContentScale.Crop,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.tempo_sheet_kicker),
                        style = InstrumentType.kicker,
                        color = TextSecondary,
                    )
                    Text(
                        tip.tipShort,
                        style = InstrumentType.heroTitle,
                        color = TextPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showIntro) {
                Text(
                    stringResource(R.string.tempo_intro),
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                Text(
                    currentTarget,
                    style = InstrumentType.body,
                    color = TextTertiary,
                    textDecoration = TextDecoration.LineThrough,
                )
                Text(
                    suggestedTarget,
                    style = InstrumentType.numeralLg,
                    color = TextPrimary,
                )
            }
            Text(
                tip.suggestion.explanationShort,
                style = InstrumentType.body,
                color = TextSecondary,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            if (applyFailed) {
                Text(
                    stringResource(R.string.tempo_apply_failed),
                    style = InstrumentType.caption,
                    color = Warn,
                )
            }
            SecondaryButton(
                label = stringResource(R.string.tempo_keep, currentTarget),
                onClick = onKeep,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                label = stringResource(R.string.tempo_use, suggestedTarget),
                onClick = {
                    Haptics.tick(view)
                    onUse()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WorkoutTestTags.TEMPO_SHEET_USE),
                state = if (applying) {
                    com.sinura.personaltrainer.ui.components.ActionState.Loading
                } else {
                    com.sinura.personaltrainer.ui.components.ActionState.Enabled
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Metrics.touchMin),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(R.string.tempo_dismiss),
                        style = InstrumentType.bodyStrong,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}
