package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
        if (startRank != FreeStartRank.HIDDEN) {
            HairlineDivider(startIndent = 0.dp)
            Box(Modifier.fillMaxWidth().padding(horizontal = Metrics.gutter, vertical = Metrics.space2)) {
                HomeStartAction(rank = startRank, onStartFree = onStartFree)
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
