package com.sinura.personaltrainer.ui.start

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.StartOptionsCopy
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * Quiet header control on Body, History, and Plan. Opens the same
 * [StartChooser] that Today's Start opens, so every tab starts one way.
 */
@Composable
fun StartSheetOpener(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onOpen,
        modifier = modifier
            .heightIn(min = Metrics.touchMin)
            .semantics { contentDescription = StartOptionsCopy.OPEN_SPOKEN },
        contentPadding = PaddingValues(horizontal = Metrics.space2, vertical = 0.dp),
    ) {
        Text(
            StartOptionsCopy.OPEN,
            style = InstrumentType.bodyStrong,
            color = TextSecondary,
        )
    }
}
