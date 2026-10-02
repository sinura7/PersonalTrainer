package com.sinura.personaltrainer.ui.components

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.TextPrimary
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P3 menu icons: [InstrumentMenuItem] shows a leading mark beside its label, and TalkBack
 * still hears the label once (W1a — decorative icon, no second contentDescription).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class InstrumentMenuRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun menuRowShowsLeadingIconBesideLabel() {
        val label = "Switch exercise"
        compose.setContent {
            PersonalTrainerTheme {
                InstrumentMenu(expanded = true, onDismissRequest = {}) {
                    InstrumentMenuItem(
                        spokenLabel = label,
                        leadingIcon = TemperIcons.ChevronDown,
                        onClick = {},
                    )
                }
            }
        }
        compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithTag(InstrumentMenuTags.leadingIcon(label), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun leadingIconIsDecorativeAndRowSpeaksTheLabelOnce() {
        val label = "Session notes"
        compose.setContent {
            PersonalTrainerTheme {
                InstrumentMenu(expanded = true, onDismissRequest = {}) {
                    InstrumentMenuItem(
                        spokenLabel = label,
                        leadingIcon = TemperIcons.Log,
                        onClick = {},
                        text = {
                            Text(label, style = InstrumentType.bodyStrong, color = TextPrimary)
                        },
                    )
                }
            }
        }
        val iconNode = compose.onNodeWithTag(
            InstrumentMenuTags.leadingIcon(label),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        assertNull(iconNode.config.getOrNull(SemanticsProperties.ContentDescription))
        // The label is visible text on the row; the icon stays decorative (no second name).
        compose.onNodeWithText(label).assertIsDisplayed()
    }
}
