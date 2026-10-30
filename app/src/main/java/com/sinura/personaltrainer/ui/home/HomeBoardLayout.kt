package com.sinura.personaltrainer.ui.home

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import com.sinura.personaltrainer.ui.components.PrimaryButton
import com.sinura.personaltrainer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.FreeStartRank
import com.sinura.personaltrainer.domain.HomeToday
import com.sinura.personaltrainer.domain.OneFilledVolt
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary

/** The day can scroll while the one generic Start remains next to navigation. */
@Composable
internal fun HomeBoardLayout(
    startRank: FreeStartRank,
    onStartFree: () -> Unit,
    modifier: Modifier = Modifier,
    todayStart: TodayDockStart? = null,
    onResume: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag(HomeTags.BOARD),
            contentPadding = PaddingValues(
                start = Metrics.gutter, end = Metrics.gutter,
                top = Metrics.space4, bottom = Metrics.space4,
            ),
            verticalArrangement = Arrangement.spacedBy(Metrics.space5),
            content = content,
        )
        val primary = onResume != null || todayStart != null
        if (startRank != FreeStartRank.HIDDEN || primary) {
            HairlineDivider(startIndent = 0.dp)
            val padded = Modifier.fillMaxWidth().padding(horizontal = Metrics.gutter, vertical = Metrics.space2)
            // With Today's own Start in front, the generic start sheet steps back to text.
            val rank = if (primary && startRank != FreeStartRank.HIDDEN) FreeStartRank.TEXT else startRank
            // A short window (landscape, large fonts) cannot spare two stacked rows of dock.
            if (primary && LocalConfiguration.current.screenHeightDp < COMPACT_DOCK_HEIGHT_DP) {
                Row(modifier = padded, horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) { TodayPrimaryAction(todayStart = todayStart, onResume = onResume) }
                    if (rank != FreeStartRank.HIDDEN) {
                        Box(modifier = Modifier.weight(1f)) { HomeStartAction(rank = rank, onStartFree = onStartFree) }
                    }
                }
            } else {
                Column(modifier = padded, verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                    TodayPrimaryAction(todayStart = todayStart, onResume = onResume)
                    HomeStartAction(rank = rank, onStartFree = onStartFree)
                }
            }
        }
    }
}

/** Retains each surface's existing action priority, including missed-work recovery. */
internal fun homeStartRank(
    surface: HomeToday.Surface,
    sessionLive: Boolean,
    quietStart: Boolean,
    trainingToday: Boolean,
    hasPlan: Boolean,
    loggedToday: Boolean,
    setupComplete: Boolean,
): FreeStartRank = when {
    sessionLive -> FreeStartRank.HIDDEN
    surface == HomeToday.Surface.AGENDA || (trainingToday && !loggedToday) ->
        if (quietStart) FreeStartRank.SECONDARY else FreeStartRank.PRIMARY
    !hasPlan -> OneFilledVolt.leftoverFreeStart(
        sessionLive = sessionLive, setupComplete = setupComplete,
        hasRecoveryVolt = false, quietStart = quietStart,
    )
    else -> FreeStartRank.TEXT
}

@Composable
internal fun HomeStartAction(rank: FreeStartRank, onStartFree: () -> Unit) {
    val modifier = Modifier.testTag(HomeTags.START)
        .semantics { contentDescription = SessionOrderCopy.FREE_WORKOUT }
    when (rank) {
        FreeStartRank.HIDDEN -> Unit
        FreeStartRank.PRIMARY -> PrimaryGymButton(
            text = SessionOrderCopy.FREE_WORKOUT, onClick = onStartFree,
            modifier = modifier, height = Metrics.control,
        )
        FreeStartRank.SECONDARY -> SecondaryGymButton(
            text = SessionOrderCopy.FREE_WORKOUT, onClick = onStartFree,
            modifier = modifier, height = Metrics.control,
        )
        FreeStartRank.TEXT -> TextButton(
            onClick = onStartFree,
            modifier = modifier.fillMaxWidth().heightIn(min = Metrics.control),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(text = SessionOrderCopy.FREE_WORKOUT, style = InstrumentType.bodyStrong,
                color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private const val COMPACT_DOCK_HEIGHT_DP = 480

/** Today's planned strength start, the dock's primary (screen 01, A20). */
internal data class TodayDockStart(
    val dayName: String,
    val liftCount: Int,
    val onStart: () -> Unit,
)

@Composable
internal fun TodayPrimaryAction(todayStart: TodayDockStart?, onResume: (() -> Unit)?) {
    when {
        onResume != null -> PrimaryButton(
            label = stringResource(R.string.today_resume_cta),
            subLabel = stringResource(R.string.today_in_progress),
            onClick = onResume,
            modifier = Modifier.fillMaxWidth().testTag(HomeTags.TODAY_RESUME),
        )
        todayStart != null -> PrimaryButton(
            label = stringResource(R.string.today_start_cta),
            subLabel = stringResource(R.string.today_start_sub, todayStart.dayName, todayStart.liftCount),
            onClick = todayStart.onStart,
            modifier = Modifier.fillMaxWidth().testTag(HomeTags.TODAY_START),
        )
    }
}
