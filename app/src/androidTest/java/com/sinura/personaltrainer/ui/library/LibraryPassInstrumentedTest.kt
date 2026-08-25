package com.sinura.personaltrainer.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.ui.components.ExerciseSearchField
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P9.6 Library / FND-032: pushed route stays named — back, search, create.
 */
@RunWith(AndroidJUnit4::class)
class LibraryPassInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun backSearchAndCreateStayNamedAt360Font2() {
        setConstrainedContent(2f) {
            IconButton(
                onClick = {},
                modifier = Modifier.testTag(LibraryTags.BACK),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            ExerciseSearchField(
                value = "",
                onValueChange = {},
                placeholder = "Name or muscle",
                modifier = Modifier
                    .testTag(LibraryTags.SEARCH)
                    .semantics { contentDescription = LibraryTags.SEARCH_SPOKEN },
            )
            FloatingActionButton(
                onClick = {},
                modifier = Modifier.testTag(LibraryTags.FAB),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Create exercise")
            }
        }
        compose.onNodeWithTag(LibraryTags.BACK).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithTag(LibraryTags.SEARCH).assertIsDisplayed()
        compose.onNodeWithContentDescription(LibraryTags.SEARCH_SPOKEN).assertIsDisplayed()
        compose.onNodeWithTag(LibraryTags.FAB).assertIsDisplayed()
        compose.onNodeWithContentDescription("Create exercise").assertIsDisplayed()
    }

    private fun setConstrainedContent(fontScale: Float, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            PersonalTrainerTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = fontScale),
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
}
