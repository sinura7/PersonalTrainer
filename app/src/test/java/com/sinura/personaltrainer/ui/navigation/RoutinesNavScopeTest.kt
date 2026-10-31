package com.sinura.personaltrainer.ui.navigation

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.ui.plan.PlanViewModel
import com.sinura.personaltrainer.ui.routines.RoutinesTags
import com.sinura.personaltrainer.ui.routines.RoutinesViewModel
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.workout.settle
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

/**
 * v4 V3 (Critic MAJOR 2): the Routines destination AppNav registers builds a RoutinesViewModel
 * and never a PlanViewModel, so Plan's one-shot flags and pipelines are not run a second time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h840dp-xhdpi")
class RoutinesNavScopeTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var deps: FakeAppDependencies
    private val built = mutableListOf<ViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext())
    }

    @After fun tearDown() {
        try {
            runBlocking { built.forEach { it.clearAndJoinForTest() } }
        } finally {
            deps.close()
            Dispatchers.resetMain()
        }
    }

    private val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app: Application = ApplicationProvider.getApplicationContext()
            val vm: ViewModel = when (modelClass) {
                RoutinesViewModel::class.java -> RoutinesViewModel(app, deps)
                PlanViewModel::class.java -> PlanViewModel(app, deps)
                else -> error("unexpected ${modelClass.name}")
            }
            built += vm
            return vm as T
        }
    }

    @Test
    fun routinesDestinationBuildsOnlyRoutinesViewModel() {
        lateinit var nav: NavHostController
        compose.setContent {
            PersonalTrainerTheme {
                nav = rememberNavController()
                NavHost(navController = nav, startDestination = Route.Routines.path) {
                    composable(Route.Routines.path) { Text("plan") }
                    routineLibraryDestination(nav, factory)
                }
            }
        }
        compose.runOnIdle { nav.navigate(Route.RoutineLibrary.create()) }
        compose.settle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag(RoutinesTags.CONTENT))
                .fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(androidx.compose.ui.test.hasTestTag(RoutinesTags.EMPTY))
                    .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(listOf(RoutinesViewModel::class.java), built.map { it.javaClass })
        assertTrue("no PlanViewModel built", built.none { it is PlanViewModel })
        assertEquals(Route.RoutineLibrary.path, nav.currentDestination?.route)
    }
}
