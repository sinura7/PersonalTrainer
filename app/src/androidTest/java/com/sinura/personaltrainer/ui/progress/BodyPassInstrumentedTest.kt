package com.sinura.personaltrainer.ui.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.MuscleLoadSummary
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.BodyView
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P9.6 Body / FND-023: the map is an illustration; muscle rows stay named
 * at 360 dp through font 2.0.
 */
@RunWith(AndroidJUnit4::class)
class BodyPassInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun mapIsIllustrationAndRowsStayNamedAt360Font1() = assertBodyAboveFold(1f)

    @Test
    fun mapIsIllustrationAndRowsStayNamedAt360Font2() = assertBodyAboveFold(2f)

    private fun assertBodyAboveFold(fontScale: Float) {
        setConstrainedContent(fontScale) {
            BodyMapCard(
                snapshot = SNAPSHOT,
                view = BodyView.FRONT,
                onViewChange = {},
                selected = CanonicalMuscle.CHEST,
                onSelect = {},
            )
            MuscleHeatRow(
                load = CHEST,
                selected = true,
                onClick = {},
                unit = WeightUnit.KG,
            )
        }
        compose.onNodeWithTag(BodyTags.MAP).assertIsDisplayed()
        compose.onNodeWithContentDescription(BodyTags.MAP_SPOKEN).assertIsDisplayed()
        compose.onNodeWithTag(BodyTags.muscle(CanonicalMuscle.CHEST)).assertIsDisplayed()
        compose.onNodeWithTag(BodyTags.muscle(CanonicalMuscle.CHEST))
            .assertContentDescriptionEquals("Chest, 2 days ago, Low load, 8 sets, 3200 kg")
        compose.onNodeWithTag(BodyTags.VIEW_FRONT).assertIsDisplayed()
        compose.onNodeWithTag(BodyTags.WINDOW_WEEK).assertDoesNotExist()
    }

    private fun setConstrainedContent(fontScale: Float, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            PersonalTrainerTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = fontScale),
                    LocalWeightUnit provides WeightUnit.KG,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.size(360.dp, 800.dp)) {
                            Column { content() }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        val CHEST = MuscleLoadSummary(
            muscle = CanonicalMuscle.CHEST,
            volumeKg = 3_200.0,
            workingSets = 8,
            sessionCount = 2,
            lastTrainedAtMs = 1_700_000_000_000L,
            daysSinceLastTrained = 2,
            weeklySets = 8.0,
            heat = 0.5,
            exercises = emptyList(),
        )
        val SNAPSHOT = BodyHeatSnapshot(
            window = HeatWindow.CURRENT_WEEK,
            windowStartMs = 1L,
            generatedAtMs = 2L,
            loads = listOf(CHEST),
            hasAnyWorkingSets = true,
            hasWindowWorkingSets = true,
        )
    }
}
