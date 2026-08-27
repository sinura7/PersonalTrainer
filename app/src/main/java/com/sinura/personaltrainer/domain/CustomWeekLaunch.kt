package com.sinura.personaltrainer.domain

/**
 * One-shot seed for the custom-week editor, handed across a navigation.
 *
 * Held on [com.sinura.personaltrainer.AppDependencies.pendingCustomWeek] rather than
 * stuffed into a route: answers are too large for a URL, and an Activity
 * recreation between the tap and arrival would drop a captured lambda.
 */
data class CustomWeekLaunch(
    val answers: OnboardingAnswers? = null,
    val unit: WeightUnit? = null,
)
