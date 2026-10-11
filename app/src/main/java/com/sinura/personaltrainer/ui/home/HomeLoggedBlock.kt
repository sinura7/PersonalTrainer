package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.HomeLogged
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.ui.components.HairlineDivider
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
    Column(
        modifier = modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(HomeTags.loggedSession(summary.id))
            .padding(vertical = Metrics.space3),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            Text(
                title,
                style = InstrumentType.title,
                color = TextSecondary,
            )
            Text(
                stats,
                style = InstrumentType.caption,
                color = TextTertiary,
            )
        }
        HairlineDivider(startIndent = 0.dp)
    }
}
