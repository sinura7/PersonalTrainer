package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.ui.preview.TemperAccessibilityPreviews
import com.sinura.personaltrainer.ui.preview.TemperReducedMotionPreview
import com.sinura.personaltrainer.ui.preview.TemperWidthPreviews
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun PlanReplayPreview() {
    PersonalTrainerTheme {
        PlanPreviewColumn {
            PlanHeader(onOpenLibrary = {})
            PlanRecoveryCommands(
                hasPins = false,
                hasRoutines = true,
                hasOpenDay = true,
                hasProposals = false,
                onReplay = {},
                onSuggest = {},
                onAccept = {},
                onDismiss = {},
            )
        }
    }
}

@TemperWidthPreviews
@Composable
private fun PlanProposalPreview() {
    PersonalTrainerTheme {
        PlanPreviewColumn {
            PlanRecoveryCommands(
                hasPins = false,
                hasRoutines = true,
                hasOpenDay = true,
                hasProposals = true,
                onReplay = {},
                onSuggest = {},
                onAccept = {},
                onDismiss = {},
            )
        }
    }
}

@TemperReducedMotionPreview
@Composable
private fun PlanReducedMotionPreview() {
    PersonalTrainerTheme(reduceMotion = true) {
        PlanPreviewColumn {
            PlanHeader(onOpenLibrary = {})
        }
    }
}

@Composable
private fun PlanPreviewColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pit)
            .padding(Metrics.gutter),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        content()
    }
}
