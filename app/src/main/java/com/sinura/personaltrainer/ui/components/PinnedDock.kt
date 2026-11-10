package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * G3: one pinned bottom dock. Volt, optional secondary, optional tertiary.
 *
 * Six screens hand-rolled this Column (hairline or not, nav inset or not).
 * Landscape collapse and Library's floating button land here later.
 */
@Composable
fun PinnedDock(
    volt: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    secondary: (@Composable () -> Unit)? = null,
    tertiary: (@Composable () -> Unit)? = null,
    prelude: (@Composable ColumnScope.() -> Unit)? = null,
    nextStep: AnnotatedString? = null,
    hairline: Boolean = true,
    verticalPadding: Dp = Metrics.space3,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Pit)
            .navigationBarsPadding()
            .padding(horizontal = Metrics.gutter, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        if (hairline) HairlineDivider(startIndent = 0.dp)
        if (nextStep != null) {
            Text(
                nextStep,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ActionDockTags.NEXT),
                style = InstrumentType.caption,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        prelude?.invoke(this)
        volt()
        secondary?.invoke()
        tertiary?.invoke()
    }
}
