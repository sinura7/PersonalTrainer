package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A lift's still wears its equipment badge in its bottom-end corner unless the caller drops it
 * (the floor's identity does, and puts its Details mark there instead), and the badge is capped
 * at the equipment glyph's 24 dp however large the still, so an 88 dp hero does not grow a 40 dp
 * badge. The picture itself is fitted whole into its box, never cropped, stretched or drawn at
 * its own size: a long-limbed lunge keeps its feet and its proportions.
 *
 * These were lines of ExerciseThumb.kt read as text (`showBadge: Boolean = true`,
 * `Metrics.equipmentGlyph` and `ContentScale.Fit`). The badge is decorative, so it is found by
 * its size and its place under the still (FloorTestKit.stillsUnder and badgesOf).
 *
 * In a square thumb a fit and a crop draw the same pixels, since every catalog still is square.
 * The fit is seen in boxes that are not square: the thumb puts its caller's modifier ahead of its
 * own size, so a caller's 88 × 44 dp box is the box it draws in. Fitted, the figure there is
 * drawn exactly as in a 44 dp square thumb; a crop, a stretch or the still's own size draws it
 * larger or out of shape, a fill to the width shows in the wide box and a fill to the height in
 * the tall one. The kept ban on any scale but Fit (FloorImageLedHeroTest) is the other half.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class ExerciseThumbRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aStillWearsItsKitBadgeInTheCornerCappedAtTheGlyphSizeUnlessDropped() {
        val lift = floorLift(targetSets = 3).exercise
        compose.showFloor {
            Column {
                Box(modifier = Modifier.testTag(WITH_BADGE)) { ExerciseThumb(exercise = lift, size = Metrics.exerciseHeroImage) }
                Box(modifier = Modifier.testTag(WITHOUT_BADGE)) { ExerciseThumb(exercise = lift, size = Metrics.exerciseHeroImage, showBadge = false) }
            }
        }
        val still = compose.silentSquaresUnder(hasTestTag(WITH_BADGE), Metrics.exerciseHeroImage).single()
        val badges = compose.badgesOf(still, Metrics.exerciseHeroImage)
        assertEquals("one badge, ${Metrics.equipmentGlyph} square, in the bottom-end corner", 1, badges.size)
        assertEquals("capped at the glyph's size", Metrics.equipmentGlyph, badgeSide(Metrics.exerciseHeroImage))
        assertTrue(
            "capped: an ${Metrics.exerciseHeroImage} still's share would be larger",
            Metrics.exerciseHeroImage.value * STILL_BADGE_SHARE > Metrics.equipmentGlyph.value,
        )
        val dropped = compose.silentSquaresUnder(hasTestTag(WITHOUT_BADGE), Metrics.exerciseHeroImage).single()
        assertEquals("a caller that drops the badge gets none", 0, compose.badgesOf(dropped, Metrics.exerciseHeroImage).size)
    }

    @Test
    fun aStillIsFittedWholeIntoItsBoxNeverCroppedStretchedOrDrawnAtItsOwnSize() {
        val lunge = catalogLift(LUNGE)
        compose.showFloor {
            Row(modifier = Modifier.background(Pit)) {
                Box(modifier = Modifier.testTag(SQUARE)) {
                    ExerciseThumb(exercise = lunge, size = SHORT_SIDE, showBadge = false)
                }
                Box(modifier = Modifier.testTag(WIDE)) {
                    ExerciseThumb(exercise = lunge, modifier = Modifier.size(LONG_SIDE, SHORT_SIDE), size = LONG_SIDE, showBadge = false)
                }
                Box(modifier = Modifier.testTag(TALL)) {
                    ExerciseThumb(exercise = lunge, modifier = Modifier.size(SHORT_SIDE, LONG_SIDE), size = LONG_SIDE, showBadge = false)
                }
            }
        }
        val square = compose.onNodeWithTag(SQUARE).windowBounds()
        val figure = pictureIn(square)
        assertTrue("the lunge is drawn tall in its square, was $figure", figure.height >= square.height / 2)
        listOf(WIDE, TALL).forEach { tag ->
            val thumb = compose.onNodeWithTag(tag).windowBounds()
            val drawn = pictureIn(thumb)
            // Fitted, the still is the same square as in a thumb of the box's short side, in the
            // middle; any other scale draws the figure larger, or stretched, or cut differently.
            assertEquals("$tag: as wide as in the square, was $drawn against $figure", figure.width, drawn.width, SLACK_PX)
            assertEquals("$tag: as tall as in the square, was $drawn against $figure", figure.height, drawn.height, SLACK_PX)
            assertEquals("$tag: placed across as in the square", figure.center.x - square.center.x, drawn.center.x - thumb.center.x, SLACK_PX)
            assertEquals("$tag: placed down as in the square", figure.center.y - square.center.y, drawn.center.y - thumb.center.y, SLACK_PX)
        }
    }

    /**
     * Where the still's figure is drawn in [thumb], over the thumb's Pit ground: inside the art's
     * padding, which keeps the hairline edge and its rounded corners out. The still loads off the
     * main thread, so this waits (bounded) until it is drawn.
     */
    private fun pictureIn(thumb: Rect): Rect {
        val inset = Metrics.space1.value * compose.density.density
        val art = box(thumb.left + inset, thumb.top + inset, thumb.right - inset, thumb.bottom - inset)
        var drawn: Rect? = null
        val lastDrawn: () -> Rect? = { drawn }
        compose.awaitThat(what = "the still is drawn in $thumb", now = lastDrawn) {
            drawn = compose.drawWindow().inkBox(art, Pit)
            drawn != null
        }
        return checkNotNull(drawn)
    }

    private fun catalogLift(name: String): Exercise {
        val seed = DefaultExercises.catalog().first { it.name == name }
        assertTrue("$name has its own still", seed.imageKey.isNotBlank())
        return Exercise(
            id = seed.id,
            name = seed.name,
            muscleGroup = seed.muscleGroup,
            notes = "",
            isCustom = false,
            equipment = seed.equipment,
            loadType = seed.loadType,
            movementKey = seed.movementKey,
            imageKey = seed.imageKey,
            muscles = seed.credits,
        )
    }

    private companion object {
        const val WITH_BADGE = "with-badge"
        const val WITHOUT_BADGE = "without-badge"
        const val SQUARE = "square-box"
        const val WIDE = "wide-box"
        const val TALL = "tall-box"

        /** A long-limbed still: a crop or a stretch would cut or squeeze its stride. */
        const val LUNGE = "Walking Lunge"

        /** The hero's 88 dp, and half of it. */
        val LONG_SIDE = 88.dp
        val SHORT_SIDE = 44.dp

        /** A picture's anti-aliased rim is worth a pixel or two either way. */
        const val SLACK_PX = 3f
    }
}
