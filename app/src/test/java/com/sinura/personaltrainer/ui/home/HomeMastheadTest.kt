package com.sinura.personaltrainer.ui.home

import android.app.Application
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.domain.MastheadCopy
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** D01: browsing another day keeps a one-tap route back to today; today itself shows none. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class HomeMastheadTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun anotherDayOffersTheWayBackAndTheTapTakesIt() {
        var backToToday = 0
        compose.setContent {
            PersonalTrainerTheme {
                HomeMasthead(
                    epochDay = TUESDAY,
                    headline = MastheadCopy.TRAINING_COMPLETE,
                    onBackToToday = { backToToday++ },
                )
            }
        }

        compose.onNodeWithText(MastheadCopy.TRAINING_COMPLETE).assertExists()
        compose.onNodeWithTag(HomeTags.BACK_TO_TODAY)
            .assertHeightIsAtLeast(Metrics.touchMin)
            .performClick()
        assertEquals(1, backToToday)
    }

    @Test
    fun todayShowsNoWayBack() {
        compose.setContent {
            PersonalTrainerTheme {
                HomeMasthead(epochDay = TUESDAY, headline = MastheadCopy.TRAINED_TODAY, onBackToToday = null)
            }
        }

        compose.onNodeWithTag(HomeTags.BACK_TO_TODAY).assertDoesNotExist()
    }

    private companion object {
        // Tuesday 15 September 2026.
        const val TUESDAY = 20_711L
    }
}
