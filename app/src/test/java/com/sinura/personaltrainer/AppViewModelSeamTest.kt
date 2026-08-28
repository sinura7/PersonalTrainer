package com.sinura.personaltrainer

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.sinura.personaltrainer.ui.home.HomeViewModel
import com.sinura.personaltrainer.ui.workout.ActiveWorkoutViewModel
import com.sinura.personaltrainer.ui.workout.RestTimerViewModel
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the A1 seam: every screen ViewModel is constructible with [AppDependencies], and the
 * default `viewModel()` factory still finds the Android constructors it already used.
 */
class AppViewModelSeamTest {

    @Test
    fun applicationOnlyConstructorStillExistsForDefaultFactory() {
        val constructors = HomeViewModel::class.java.constructors
        assertTrue(
            constructors.any { it.parameterTypes.contentEquals(arrayOf(Application::class.java)) },
        )
    }

    @Test
    fun dependenciesConstructorIsCallableFromTests() {
        val constructors = HomeViewModel::class.java.constructors
        assertTrue(
            constructors.any {
                it.parameterTypes.contentEquals(
                    arrayOf(Application::class.java, AppDependencies::class.java),
                )
            },
        )
    }

    @Test
    fun savedStateViewModelsKeepTheFactoryPairAndAcceptDependencies() {
        val constructors = ActiveWorkoutViewModel::class.java.constructors
        assertTrue(
            constructors.any {
                it.parameterTypes.contentEquals(
                    arrayOf(Application::class.java, SavedStateHandle::class.java),
                )
            },
        )
        assertTrue(
            constructors.any {
                it.parameterTypes.contentEquals(
                    arrayOf(
                        Application::class.java,
                        SavedStateHandle::class.java,
                        AppDependencies::class.java,
                    ),
                )
            },
        )
        val restConstructors = RestTimerViewModel::class.java.constructors
        assertTrue(
            restConstructors.any {
                it.parameterTypes.contentEquals(
                    arrayOf(Application::class.java, SavedStateHandle::class.java),
                )
            },
        )
        assertTrue(
            restConstructors.any {
                it.parameterTypes.contentEquals(
                    arrayOf(
                        Application::class.java,
                        SavedStateHandle::class.java,
                        AppDependencies::class.java,
                    ),
                )
            },
        )
    }
}
