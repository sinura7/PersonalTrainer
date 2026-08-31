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
            voltAction = "Start today's planned session when a week is pinned. Generate a schedule on a first visit. Free workout stays quiet.",
            talkBackNotes = "Last session and days-since tiles merge into one name each. Settings is a tab. Home week strip picks the day. Planned rows and the Volt open a start confirm — the empty-agenda leftover Volt included. Leftovers confirm as Do it today. Day blocks have no clocks; Up / Down rearranges them. Add extra mints a same-day warm-up or mobility pack. This week, Library, and Goals stay off this screen.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "body",
            title = "Body",
            states = requiredStates,
            voltAction = "None. Body is a readout; Start lives on Home.",
            talkBackNotes = "Map is an illustration. Muscle rows are the 48 dp target.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "plan",
            title = "Plan",
            states = requiredStates,
            voltAction = "Add session — recovery stays quiet unless confirming a proposed week",
            talkBackNotes = "Library and Add session are named. Start is Home, not Plan. Lighter is a quiet chip. Settings is a tab, not a header gear. Reminders live on Settings.",
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
            talkBackNotes = "Session rows speak title, date, sets, work, and minutes.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "library",
            title = "Library",
            states = requiredStates,
            voltAction = "Create exercise",
            talkBackNotes = "Back, search, and create are named. Not a tab.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "settings",
            title = "Settings",
            states = requiredStates,
            voltAction = "Export to file — backup is never the page's Volt gym act",
            talkBackNotes = "Settings is a tab. Back is gone. Weight and hours sit at the top. Reminders (opt-out and quiet hours) live here. Export stays reachable. Share diagnostics is quiet.",
            automatedEvidence = true,
        ),
        PagePass(
            id = "active-strength",
            title = "Active strength",
            states = requiredStates,
            voltAction = "Log set",
            talkBackNotes = "Rest is not announced every second. Process restore keeps the set.",
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
            voltAction = "Add lifts — edits write through",
            talkBackNotes = "Cards are numbered in session order. Tap a card to set work. Back leaves. There is no Save: every edit writes itself.",
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
