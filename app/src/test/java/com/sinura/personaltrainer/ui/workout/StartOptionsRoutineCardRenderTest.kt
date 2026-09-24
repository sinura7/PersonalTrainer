package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.RoutineCardCopy
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A routine on the start sheet, from a seeded four-lift routine: one card, tapped as one, that
 * names the routine and its planned sets, says the kit it uses, lists its lifts numbered in
 * order, pictures only the first three of them, and starts that routine when tapped.
 *
 * These were lines of StartOptionsSheet.kt read as text (`RoutineCardCopy.mix`,
 * `RoutineCardCopy.STILL_LIMIT`, `ExerciseThumb(`, `GymCard(`,
 * `SessionOrderCopy.numberedPreview`, and `GymCard(` and `ExerciseThumb(` inside `private fun
 * RoutineRow`). The card is found as the lifter finds it, by the routine's name; its pictures
 * by what a still is (FloorTestKit.stillsUnder).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class StartOptionsRoutineCardRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<StartOptionsViewModel>()
    private val started = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        viewModels.clear()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun aRoutineCardNamesTheWorkItsKitAndItsLiftsInOrder() {
        showSheet()
        val texts = card().mergedTexts()
        assertTrue("was $texts", ROUTINE in texts)
        assertTrue("four lifts of three sets, was $texts", "12" in texts)
        assertTrue("was $texts", RoutineCardCopy.mix(LIFTS.map { it.third }) in texts)
        assertTrue("was $texts", SessionOrderCopy.numberedPreview(LIFTS.map { it.second }) in texts)
    }

    @Test
    fun aRoutineCardPicturesOnlyItsFirstThreeLifts() {
        showSheet()
        assertEquals(RoutineCardCopy.STILL_LIMIT, compose.stillsUnder(CARD_WITHIN, ThumbSize.row).size)
        assertTrue("the routine has more lifts than it pictures", LIFTS.size > RoutineCardCopy.STILL_LIMIT)
    }

    @Test
    fun aTapOnARoutineCardStartsThatRoutine() {
        showSheet()
        card().performClick()
        compose.awaitThat(what = "the card started a routine", now = ::started) { started.isNotEmpty() }
        val session = checkNotNull(runBlocking { deps.workoutRepository.getSession(started.single()) })
        assertEquals(ROUTINE, session.routineName)
        assertEquals(LIFTS.map { it.second }, session.exercises.map { it.exercise.name })
    }

    private fun card() = compose.onNode(CARD).performScrollTo().assertIsDisplayed()

    private fun showSheet() {
        seedRoutine()
        val vm = StartOptionsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps).also(viewModels::add)
        val onStarted: (String) -> Unit = { started += it }
        var visible by mutableStateOf(true)
        compose.setContent {
            PersonalTrainerTheme {
                if (visible) {
                    StartOptionsSheet(onDismiss = { visible = false }, onWorkoutStarted = onStarted, viewModel = vm)
                }
            }
        }
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { compose.isDisplayed(hasText(ROUTINE)) }
        compose.waitForIdle()
    }

    /** A four-lift push day, each 3 × 8 with 90 s rest, on four kinds of kit. */
    private fun seedRoutine() = runBlocking {
        deps.database.exerciseDao().insertAll(
            LIFTS.map { (id, name, equipment) ->
                ExerciseEntity(
                    id = id,
                    name = name,
                    muscleGroup = "Chest",
                    notes = "",
                    isCustom = false,
                    equipment = equipment.name,
                    nameKey = name.lowercase(),
                )
            },
        )
        val routine = deps.routineRepository.create(name = ROUTINE)
        LIFTS.forEach { (id, _, _) ->
            val exercise = checkNotNull(deps.exerciseRepository.getById(id))
            deps.routineRepository.addExercise(routine.id, exercise, targetSets = 3, targetReps = 8, targetWeightKg = null, restSeconds = 90)
        }
    }

    private companion object {
        const val ROUTINE = "Push A"
        val LIFTS = listOf(
            Triple("test-bench", "Bench Press", EquipmentType.BARBELL),
            Triple("test-incline", "Incline Press", EquipmentType.DUMBBELL),
            Triple("test-fly", "Cable Fly", EquipmentType.CABLE),
            Triple("test-dip", "Machine Dip", EquipmentType.MACHINE),
        )

        /** The routine's card: the one tap target that carries its name. */
        val CARD: SemanticsMatcher = hasClickAction() and hasText(ROUTINE)

        /** The same card in the unmerged tree, where its name is a node under it. */
        val CARD_WITHIN: SemanticsMatcher = hasClickAction() and hasAnyDescendant(hasText(ROUTINE))
    }
}
