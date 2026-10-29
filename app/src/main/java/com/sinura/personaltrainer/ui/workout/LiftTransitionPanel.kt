package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.ui.components.GhostButton
import com.sinura.personaltrainer.ui.components.PanelEmphasis
import com.sinura.personaltrainer.ui.components.PrimaryButton
import com.sinura.personaltrainer.ui.components.RestRing
import com.sinura.personaltrainer.ui.components.RingSize
import com.sinura.personaltrainer.ui.components.UpNextCard
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LiftTransitionPanel(
    liftIndex: Int,
    liftCount: Int,
    completedLiftName: String,
    workingSets: Int,
    volumeKgLabel: String,
    prLabel: String?,
    nextLiftIndex: Int,
    nextLiftName: String,
    nextTarget: String,
    nextTargetLoading: Boolean,
    nextFirstTimeCaption: Boolean,
    restRemainingSeconds: Int,
    restTotalSeconds: Int,
    restRunning: Boolean,
    restCompleted: Boolean,
    onStartNext: () -> Unit,
    onChooseOther: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    LaunchedEffect(restCompleted) {
        if (restCompleted) Haptics.holdDone(view)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Metrics.gutter, vertical = Metrics.space4)
            .testTag(WorkoutTestTags.LIFT_TRANSITION),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            Text(
                stringResource(R.string.between_kicker, liftIndex, liftCount),
                style = InstrumentType.kicker,
                color = TextSecondary,
            )
            Text(
                stringResource(R.string.between_title, completedLiftName),
                style = InstrumentType.display,
                color = TextPrimary,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Text(
                stringResource(R.string.between_sets, workingSets),
                style = InstrumentType.bodyStrong,
                color = TextSecondary,
                modifier = Modifier.testTag(WorkoutTestTags.BETWEEN_SETS_CHIP),
            )
            Text(
                stringResource(R.string.between_volume, volumeKgLabel),
                style = InstrumentType.bodyStrong,
                color = TextSecondary,
                modifier = Modifier.testTag(WorkoutTestTags.BETWEEN_VOLUME_CHIP),
            )
            prLabel?.let {
                Text(
                    stringResource(R.string.between_pr, it),
                    style = InstrumentType.bodyStrong,
                    color = Volt,
                    modifier = Modifier.testTag(WorkoutTestTags.BETWEEN_PR_CHIP),
                )
            }
        }
        UpNextCard(
            kicker = stringResource(R.string.between_up_next, nextLiftIndex, liftCount),
            target = nextTarget,
            onEdit = {},
            loading = nextTargetLoading,
            showEdit = false,
            reason = stringResource(R.string.between_first_time).takeIf { nextFirstTimeCaption },
            modifier = Modifier.fillMaxWidth(),
            emphasis = PanelEmphasis.Emphasis,
        )
        if (restRunning || restCompleted) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RestRing(
                    remainingMs = restRemainingSeconds.coerceAtLeast(0) * 1000L,
                    totalMs = restTotalSeconds.coerceAtLeast(1) * 1000L,
                    size = RingSize.Mini,
                    running = restRunning && !restCompleted,
                    completed = restCompleted,
                    modifier = Modifier.testTag(WorkoutTestTags.BETWEEN_MINI_RING),
                )
                Text(
                    RestTimer.formatClock(if (restCompleted) 0 else restRemainingSeconds.coerceAtLeast(0)),
                    style = InstrumentType.caption,
                    color = TextSecondary,
                )
            }
        }
        PrimaryButton(
            label = stringResource(R.string.between_start, nextLiftName),
            onClick = onStartNext,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WorkoutTestTags.BETWEEN_START),
        )
        GhostButton(
            label = stringResource(R.string.between_other),
            onClick = onChooseOther,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WorkoutTestTags.BETWEEN_OTHER),
        )
    }
}
