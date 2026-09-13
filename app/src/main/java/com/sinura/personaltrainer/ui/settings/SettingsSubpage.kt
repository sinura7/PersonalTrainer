package com.sinura.personaltrainer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics

@Composable
internal fun SettingsSubpage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = title,
            onBack = onBack,
            titleStyle = InstrumentType.display,
            contentPadding = PaddingValues(
                start = Metrics.space2,
                end = Metrics.gutter,
                bottom = Metrics.space2,
            ),
        )
        val bodyModifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .then(
                if (scroll) {
                    Modifier.verticalScroll(rememberScrollState())
                } else {
                    Modifier
                },
            )
            .padding(
                start = Metrics.gutter,
                end = Metrics.gutter,
                top = Metrics.space2,
                bottom = Metrics.space8,
            )
        Column(
            modifier = bodyModifier,
            verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
            content = content,
        )
    }
}
