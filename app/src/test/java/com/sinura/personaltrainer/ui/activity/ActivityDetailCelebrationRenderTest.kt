package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.ActivityDetailCopy
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.StrengthSet
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.util.JvmTime
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class ActivityDetailCelebrationRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: ActivityDetailViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher)
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun celebrationReceiptShowsSavedNextToCompleteKicker() {
        runBlocking {
        val now = JvmTime.captureNow()
        val write = deps.confirmActivity(
            ActivityDraft(
                status = ActivityStatus.COMPLETED,
                origin = ActivityOrigin.BACKDATED,
                title = "Past squat",
                performedStart = now,
                performedEnd = now,
                blocks = listOf(
                    StrengthBlock(
                        id = "blk",
                        sortOrder = 0,
                        exerciseId = "ex",
                        exerciseName = "Squat",
                        loadType = LoadType.EXTERNAL,
                        equipment = EquipmentType.BARBELL,
                        muscles = emptyList(),
                        sets = listOf(
                            StrengthSet(
                                id = "set",
                                setNumber = 1,
                                weightKg = 100.0,
                                reps = 5,
                                rpe = null,
                                isWarmup = false,
                                completedAtMs = now.instantMillis,
                            ),
                        ),
                    ),
                ),
            ),
            now,
        )
        val session = (write as ActivityWrite.Accepted).session
        val vm = ActivityDetailViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("activityId" to session.id)),
            container = deps,
        )
        viewModel = vm
        vm.uiState.awaitFirst { !it.isLoading && it.session != null }
        compose.setContent {
            PersonalTrainerTheme {
                CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG) {
                    ActivityDetailScreen(onBack = {}, celebration = true, viewModel = vm)
                }
            }
        }
        compose.onNodeWithText(ActivityDetailCopy.kicker(celebration = true, session).uppercase()).assertIsDisplayed()
        compose.onNodeWithText(ActivityDetailCopy.SAVED.uppercase()).assertIsDisplayed()
        }
    }

    @Test
    fun historyDetailDoesNotShowSavedKicker() {
        runBlocking {
        val now = JvmTime.captureNow()
        val write = deps.confirmActivity(
            ActivityDraft(
                status = ActivityStatus.COMPLETED,
                origin = ActivityOrigin.BACKDATED,
                title = "History squat",
                performedStart = now,
                performedEnd = now,
                blocks = listOf(
                    StrengthBlock(
                        id = "blk2",
                        sortOrder = 0,
                        exerciseId = "ex2",
                        exerciseName = "Squat",
                        loadType = LoadType.EXTERNAL,
                        equipment = EquipmentType.BARBELL,
                        muscles = emptyList(),
                        sets = listOf(
                            StrengthSet(
                                id = "set2",
                                setNumber = 1,
                                weightKg = 80.0,
                                reps = 5,
                                rpe = null,
                                isWarmup = false,
                                completedAtMs = now.instantMillis,
                            ),
                        ),
                    ),
                ),
            ),
            now,
        )
        val session = (write as ActivityWrite.Accepted).session
        val vm = ActivityDetailViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("activityId" to session.id)),
            container = deps,
        )
        viewModel = vm
        vm.uiState.awaitFirst { !it.isLoading && it.session != null }
        compose.setContent {
            PersonalTrainerTheme {
                CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG) {
                    ActivityDetailScreen(onBack = {}, celebration = false, viewModel = vm)
                }
            }
        }
        compose.onNodeWithText(ActivityDetailCopy.SAVED.uppercase()).assertDoesNotExist()
        }
    }
}
