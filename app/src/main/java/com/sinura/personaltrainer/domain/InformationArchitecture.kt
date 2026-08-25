package com.sinura.personaltrainer.domain

/**
 * ADR-006 landings for P9.1. Four tabs stay unless the reconsideration
 * gate fails with measured user evidence. This object is the first-click
 * map those sessions judge against — not a fifth tab.
 */
enum class CanonicalTask(val id: String, val label: String) {
    T1("T1", "Start today’s planned strength activity"),
    T2("T2", "Record a cardio activity, live or manual"),
    T3("T3", "Schedule morning cardio and evening strength on one day"),
    T4("T4", "Find the latest completed activity"),
    T5("T5", "Find a lift by the muscle it trains"),
    T6("T6", "Inspect annual progress"),
    T7("T7", "Recover an empty week when routines already exist"),
}

enum class LandingSurface(val label: String, val isTab: Boolean) {
    HOME("Home", isTab = true),
    BODY("Body", isTab = true),
    PLAN("Plan", isTab = true),
    HISTORY("History", isTab = true),
    LIBRARY("Library", isTab = false),
    GOALS("Goals", isTab = false),
    COMPOSER("Activity composer", isTab = false),
}

data class TaskLanding(
    val task: CanonicalTask,
    val firstClick: LandingSurface,
    val primary: LandingSurface,
    val also: List<LandingSurface> = emptyList(),
)

object InformationArchitecture {
    val tabs: List<LandingSurface> = listOf(
        LandingSurface.HOME,
        LandingSurface.BODY,
        LandingSurface.PLAN,
        LandingSurface.HISTORY,
    )

    val pushedNeverTabs: List<LandingSurface> = listOf(
        LandingSurface.LIBRARY,
        LandingSurface.GOALS,
        LandingSurface.COMPOSER,
    )

    fun landing(task: CanonicalTask): TaskLanding = when (task) {
        CanonicalTask.T1 -> TaskLanding(
            task = task,
            firstClick = LandingSurface.HOME,
            primary = LandingSurface.HOME,
        )
        CanonicalTask.T2 -> TaskLanding(
            task = task,
            firstClick = LandingSurface.HOME,
            primary = LandingSurface.COMPOSER,
            also = listOf(LandingSurface.HOME),
        )
        CanonicalTask.T3 -> TaskLanding(
            task = task,
            firstClick = LandingSurface.PLAN,
            primary = LandingSurface.PLAN,
        )
        CanonicalTask.T4 -> TaskLanding(
            task = task,
            firstClick = LandingSurface.HISTORY,
            primary = LandingSurface.HISTORY,
        )
        CanonicalTask.T5 -> TaskLanding(
            task = task,
            firstClick = LandingSurface.BODY,
            primary = LandingSurface.LIBRARY,
            also = listOf(LandingSurface.BODY, LandingSurface.PLAN),
        )
        CanonicalTask.T6 -> TaskLanding(
            task = task,
            firstClick = LandingSurface.HISTORY,
            primary = LandingSurface.HISTORY,
            also = listOf(LandingSurface.HOME),
        )
        CanonicalTask.T7 -> TaskLanding(
            task = task,
            firstClick = LandingSurface.HOME,
            primary = LandingSurface.PLAN,
            also = listOf(LandingSurface.HOME),
        )
    }

    /**
     * Four tabs stay unless *all* ADR-006 §5 conditions are true.
     * A lab session without representative users cannot fire the gate.
     */
    fun reconsiderationGateOpen(
        unassistedCompletionBelow80: Boolean,
        majorityFirstClickFailure: Boolean,
        comparativePrototypeTested: Boolean,
        alternativeHoldsOtherTasks: Boolean,
    ): Boolean =
        (unassistedCompletionBelow80 || majorityFirstClickFailure) &&
            comparativePrototypeTested &&
            alternativeHoldsOtherTasks
}
