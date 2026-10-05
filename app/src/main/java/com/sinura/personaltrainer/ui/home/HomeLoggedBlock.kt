package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.HomeLogged
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

/**
 * A finished session that was not tied to a planned row — quiet Done ink,
 * name plus one stats line, tap opens History detail.
 */
@Composable
fun HomeLoggedBlock(
    summary: SessionSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = LocalWeightUnit.current
    val title = HomeLogged.title(summary)
    val stats = HomeLogged.statsLine(summary, unit)
    GymCard(
        modifier = modifier
            .semantics { role = Role.Button }
            .testTag(HomeTags.loggedSession(summary.id)),
        onClick = onClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            Text(
                title,
                style = InstrumentType.title,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stats,
                style = InstrumentType.caption,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
