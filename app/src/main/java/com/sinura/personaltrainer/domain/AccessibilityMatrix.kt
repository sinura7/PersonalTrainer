package com.sinura.personaltrainer.domain

/**
 * P9.6 / P9.7 inventory. Automated page passes can mark [automatedEvidence].
 * Physical TalkBack stays false until a phone session signs the page.
 * [publicCandidateReady] is therefore false until that session exists.
 */
data class PagePass(
    val id: String,
    val title: String,
    val states: List<String>,
    val voltAction: String,
    val talkBackNotes: String,
    val automatedEvidence: Boolean,
    val physicalTalkBack: Boolean = false,
)

object AccessibilityMatrix {
    val requiredStates: List<String> = listOf(
        "loading",
        "empty",
        "error",
        "populated",
        "permission",
        "process",
    )

    val widthsDp: List<Int> = listOf(360, 412, 600)
    val fontScales: List<Double> = listOf(1.0, 1.6, 2.0)

    val pages: List<PagePass> = listOf(
        PagePass(
            id = "home",
            title = "Home",
            states = requiredStates,
            voltAction = "Start a workout (freestyle). Planned rows confirm, then start.",
            talkBackNotes = "Settings is a tab. Home week strip picks the day. Selected day is filled, not faint type. Planned rows open a start confirm, and a row skipped today still opens one. A day block is one button: title, order and count, then its state or Start on the foot; Skip and Up / Down sit inside it. The filled Volt is Start a workout. There is no Get started sheet. Day blocks have no clocks; Up / Down rearranges them. Add sits under Today and asks just-today vs every this weekday. This week, Library, Goals and the ready-to-progress list stay off this screen.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "body",
            title = "Body",
            states = requiredStates,
            voltAction = "None. Body is a readout; Start lives on Home.",
            talkBackNotes = "Map is an illustration. Muscle rows are the 48 dp target. Before any sets, Body names catalog lifts and a muscle opens the lifts that train it. Log or start cardio opens the start sheet.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "plan",
            title = "Plan",
            states = requiredStates,
            voltAction = "Add session",
            talkBackNotes = "Library, Add session, and Log or start cardio are named. Planned Start is Home. The sheet is not a second Volt. Routines start collapsed behind Show routines. Generator lives on Settings. Settings is a tab, not a header gear. Reminders live on Settings.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "plan-day",
            title = "Plan day",
            states = requiredStates,
            voltAction = "Add session",
            talkBackNotes = "Back and Add session are named. Session rows open the editor. Up / Down rearranges the day's blocks. Remove deletes. No clocks, Start, Swap, or Unpin.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "history",
            title = "History",
            states = requiredStates,
            voltAction = "None. History is a readout; Start lives on Home.",
            talkBackNotes = "Session rows speak title, date, sets, work, and minutes. Log or start cardio opens the start sheet.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "library",
            title = "Library",
            states = requiredStates,
            voltAction = "Create lift",
            talkBackNotes = "Back, search, and create are named. Not a tab.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "settings",
            title = "Settings",
            states = requiredStates,
            voltAction = "Export to file",
            talkBackNotes = "Settings is a tab. Back is gone. Weight and hours sit at the top. Reminders are per-day workout alarms. The week generator lives here. Export is the page's Volt. Restore is never the gym act. Share diagnostics is quiet.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "active-strength",
            title = "Active strength",
            states = requiredStates,
            voltAction = "Log set",
            talkBackNotes = "Rest is not announced every second. Idle rest says it is not running. First rest names unrestricted battery. Rest Start/Skip and Log set sit in the lower dock. Process restore keeps the set.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "active-cardio",
            title = "Active cardio",
            states = requiredStates,
            voltAction = "Finish",
            talkBackNotes = "Elapsed time is a readout, not a live announcement stream.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "active-mixed",
            title = "Active mixed",
            states = requiredStates,
            voltAction = "Log set or update cardio — one live envelope",
            talkBackNotes = "Blocks stay independently named.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "routine-editor",
            title = "Routine editor",
            states = requiredStates,
            voltAction = "Add lifts when empty; Save when the routine has lifts",
            talkBackNotes = "Cards are numbered in session order. Tap a card to set work. Save keeps the program. Back leaves and discards an empty stub.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "custom-week",
            title = "Custom week",
            states = requiredStates,
            voltAction = "Use this week · N training",
            talkBackNotes = "Confirm writes the week. Cards are numbered in session order. Back leaves the draft.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "summary",
            title = "Summary",
            states = requiredStates,
            voltAction = "Done",
            talkBackNotes = "Strength work and cardio minutes are never one score.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "session-detail",
            title = "Session detail",
            states = requiredStates,
            voltAction = "Edit set or delete session, confirmed",
            talkBackNotes = "Destructive dialogs name the session. Overflow holds Repeat and Delete.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "exercise-detail",
            title = "Exercise detail",
            states = requiredStates,
            voltAction = "Add to a routine",
            talkBackNotes = "Identity stays before metrics at 360 dp / font 2.0. Charts have a textual summary.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "activity-detail",
            title = "Activity detail",
            states = requiredStates,
            voltAction = "Done",
            talkBackNotes = "Strength sets and cardio minutes stay separate scores.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "activity-composer",
            title = "Activity composer",
            states = requiredStates,
            voltAction = "Save",
            talkBackNotes = "Nothing writes until Save. Future dates are refused.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "onboarding",
            title = "Onboarding",
            states = requiredStates,
            voltAction = "Use this plan",
            talkBackNotes = "Nothing writes until Use this plan. No notification prompt. Opened from Home, not as a launch gate.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "annual-analytics",
            title = "Annual analytics",
            states = requiredStates,
            voltAction = "History day / week / month / year / all chips",
            talkBackNotes = "Charts have a textual latest/best/direction summary.",
            automatedEvidence = true,
        ),
    )

    fun page(id: String): PagePass =
        pages.first { it.id == id }

    fun publicCandidateReady(): Boolean =
        pages.isNotEmpty() && pages.all { it.physicalTalkBack && it.automatedEvidence }

    fun missingRequiredState(page: PagePass): List<String> =
        requiredStates.filterNot { it in page.states }
}
