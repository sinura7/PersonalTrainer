package com.sinura.personaltrainer.testonly

import com.sinura.personaltrainer.BuildConfig
import com.sinura.personaltrainer.domain.OnboardingAnswers

/**
 * Debug-only bridge for instrumented UX walkthroughs. Release builds never call [installForcePreview];
 * [consumeForcePreview] is always null on gym-floor APKs.
 */
object WalkthroughOnboardingNavHook {
    @Volatile
    private var forcePreview: OnboardingAnswers? = null

    fun installForcePreview(answers: OnboardingAnswers) {
        if (!BuildConfig.DEBUG) return
        forcePreview = answers.sanitized()
    }

    internal fun consumeForcePreview(): OnboardingAnswers? {
        if (!BuildConfig.DEBUG) return null
        return forcePreview.also { forcePreview = null }
    }
}
