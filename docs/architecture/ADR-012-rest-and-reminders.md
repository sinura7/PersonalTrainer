# ADR-012 — Exact rest, reminders, and missed-work policy

- **Status:** Accepted
- **Date:** 24 August 2026
- **Supersedes:** D2 rule 3 as the *user-visible* missed-day policy (silent
  forward shift). D2 remains the description of historical v2 slot
  derivation and the leftover ThisWeekCard week strip. P7.3 is the
  product rule. Documentation that the rest alarm is “exempt” or
  unconditionally reliable is superseded.
- **Amended:** 29 August 2026 — [ADR-019](ADR-019-move-to-today.md)
  lets one leftover occurrence move onto today from Home; week-level
  missed-work is unchanged; 23 September 2026 — owner decision: the rest a
  logged set starts is the coach's suggested length (decision 18); the same
  day, W2b-1 made decision 1 hold across threads (see Consequences);
  24 September 2026 — W2b-1b: a stop names its rest, and a skipped rest
  does not say "Rest done" (owner decisions; see Consequences); the same
  day, W2b-1c: no call cancels another's disk job, and a SYNC is owed until
  one is sent; W2b-1d: the notification's Skip names its rest (see
  Consequences); W2b-2: decision 18 says what the dock and the rest
  page show, which the code already did; W2b-3: the lock glance's and
  the rest page's Skip name their rest too; and W2b-4: the rest page's
  Next line is the Log's (see Consequences); 26 September 2026 — the
  rest-alarm packet: every re-arm writes the row first, and a job arms
  only the rest it wrote (audit RT-4, RT-5; see Consequences)
- **Related:** FND-001, FND-007, FND-017; P2.1–P2.3, P7.3–P7.5

## Context

Rest must ring with the screen off. Workout reminders must keep a person on
a schedule without guilt-spamming them. Those are different problems and
must not share one alarm path.

Current D2 derivation silently shifts a missed anchored day to the next open
day. The agreed product asks once, then adapts only if the user says so.

## Decision

### Rest completion

1. Rest uses a **unique timer generation/ID**. Completion is claimed only
   when the ID matches *and* elapsed realtime has reached the current
   deadline. Stale IDs are ignored. A current-but-early delivery is
   rescheduled. An old receiver cannot clear a replacement timer.
2. Exact scheduling uses **`SCHEDULE_EXACT_ALARM`**. Do **not** declare or
   use `USE_EXACT_ALARM` unless a later Play-eligibility decision signs a
   new ADR.
3. The scheduler checks `canScheduleExactAlarms()` and returns a typed
   outcome: `Exact`, `BestEffort`, or `Failed`. Exact APIs are not called
   when the check is false. Failures are not swallowed.
4. Special-access is requested only after rest is used or configured, never
   during onboarding. Recheck on resume. Reschedule a live timer after grant.
5. Notification permission and exact-alarm access are separate capabilities.
   Denial of one does not pretend to grant the other.
6. When exact access is unavailable, use an honest inexact fallback. The UI
   never displays “reliable” for that state. The Active Workout denial
   recovery is a compact persistent row, not a viewport-dominating banner
   (FND-015).
7. **Reboot semantics:** a rest shorter than a gym set is meaningless after
   a different boot. Clear persisted short rests across reboot rather than
   fire a late alert. Do not reschedule a rest onto the next day.
8. Persistence of timer state uses a synchronous commit whose necessity is
   documented at the call site.

### Workout reminders

9. Reminders are **best-effort WorkManager**, not exact alarms. They are a
   different capability from rest.
10. Database delivery records are authoritative. Workers reread state.
    Stale work is a no-op.
11. Actions: **Start**, **Snooze**, **Move**, **Skip / Rest**. Quiet hours
    defer. Opt-out exists. One missed-session check-in, never an endless
    guilt repeat.
12. Rebuild safely after reboot and time-zone change without catch-up spam.
13. Reminder permission is not requested during onboarding.

### Missed scheduled work

14. **Recurrence rules do not change** when a day is missed.
15. The user sees **one persisted decision** for the missed work:
    - Move remaining (push unfinished occurrences forward in this week);
    - Adapt week (regenerate untouched future planned occurrences);
    - Keep dates (leave them missed);
    - Skip missed.
16. The prompt is deduped. Read paths do not mutate the week. Week rollover
    still generates the next week from the unchanged rule
    ([ADR-007](ADR-007-activity-model.md) occurrences).
17. Silent D2-style shifting is removed as user-visible policy. Occurrences
    stay dated until one persisted missed-work decision **or** an explicit
    Home **Do it today** on a single leftover
    ([ADR-019](ADR-019-move-to-today.md)). The leftover
    slot-week strip may still derive a shifted day; that is not the
    product rule and must not grow.

### Rest length after a logged set

18. **The coach deals with the timing** (owner decision, 23 September 2026).
    The rest a logged working set starts runs the coach's suggested length
    for that lift. A length picked on the dock or the rest page is the next
    *manual* rest and the fallback when the coach has none; it never
    overrides the coach's suggestion after a set. The order is: the coach's
    suggestion, then the routine's rest for the lift, then the length
    showing on the dock (`ActiveWorkoutViewModel.startRestAfterSet` through
    `RestTimer.secondsToStart`).
    `ActiveWorkoutViewModelTest.afterALoggedSetRestRunsTheCoachsLengthNotOnePickedOnTheDock`
    holds it. What the screens show follows from it (24 September 2026,
    packet W2b-2, no behaviour change): after a logged set that starts a
    rest, the dock shows the coach's length; a length picked on the dock
    holds until a logged set starts a rest or the lift changes (a warm-up
    starts none, and leaves the pick); and the rest page, when it opens,
    seeds its own length (the coach's, else the routine's, else the last
    length picked, else the default) without counting that seed as a pick.
    Held by
    `ActiveWorkoutViewModelTest.aPickHoldsUntilTheNextLoggedSetThenTheDockShowsTheCoachsLength`,
    `ActiveWorkoutViewModelTest.aWarmUpStartsNoRestSoAPickOnTheDockHoldsThroughIt`,
    `ActiveWorkoutViewModelTest.aRestPickedOnOneLiftDoesNotStopTheNextLiftSeedingItsOwn`
    and `RestTimerViewModelTest.theStoredLastPresetIsNotANewPickWhenThePageOpens`.

## Consequences

- Rest lint must not report an unhandled exact-alarm path after P2.2.
- A reminder cannot be implemented by `setAlarmClock`.
- Schedule packets after P7.3 that “just slide the day” without a prompt
  are regressions.
- Running rest uses a HIGH public channel with a countdown chronometer so
  the lock screen shows remaining time when the user turns the phone on.
  `RestLockActivity` is `showWhenLocked`. That is not overlay rest on the
  live log. Full-screen intent is rest-done only, never onboarding.
  Completion still uses `SCHEDULE_EXACT_ALARM`.
- Decision 1 holds across threads, not only in order (W2b-1, 23 September
  2026). The notification's ±15 and the screens write the rest on Main; the
  alarm's completion clears it on a background thread. `RestTimerStore`'s
  `adjust` and `clearIfCurrent` are compare-and-set, and the controller
  chooses what to do from what they return, not from a second read of the
  store: a late ±15 cannot revive a finished rest, and a completion for the
  old id cannot clear the timer a ±15 just replaced it with (it takes its
  "rest done" back instead). A ±15 that finds the rest already finished does
  nothing, so it cannot turn "rest done" into a skip.
- A stop names its rest, and a skipped rest is not "done" (W2b-1b, owner
  decisions of 24 September 2026). The service's STOP carries the id of the
  rest it ends, so a STOP read after a new rest started (the next set's
  auto-rest) leaves the new rest running. The store remembers the last 16
  rests it held: an empty store that held a rest was emptied by a Skip, a
  stop or a −15 to zero, or by a finish that already announced it. So a
  completion already on its way for that rest (an alarm that fired as the
  Skip landed, reading the disk row before its clear) announces nothing, and
  a recovery in the same process (after the exact-alarm permission changes,
  say) neither brings it back nor announces it; it clears the row again. A
  process started after death has held nothing, so a rest that ran out
  while it was dead still says "Rest done". The notification's Skip names
  the rest its card shows and ends only that one, in the same
  compare-and-set as a named STOP (W2b-1d, owner decision of 24 September
  2026): a finish that lands first keeps its "rest done" (some phones keep
  the card a beat after the rest ends), and a newer rest the card has not
  caught up with (the next set's) keeps running while the card moves to
  it. A ±15 is not a newer rest: it replaces the rest under a new id, so an
  alarm for the old deadline cannot end it, and the store remembers which
  rest each id began as, so a Skip tapped as a ±15 lands still ends it. A
  card built before Skip named its rest ends the rest running when the
  service reads it. Until W2b-3 the lock glance's Skip and the app's own
  Skip ended whatever ran. *Amended 24 September 2026 (W2b-3, owner decision):*
  the lock glance's and the rest page's Skip now name their rest, as the
  notification's does. Each hands over the id of the rest it drew, read as
  it was drawn, not the rest running when the tap is handled, and the same
  named skip ends it or its ±15. A rest that ran out as Skip was tapped
  keeps its "rest done": the glance stays and shows "Back to the bar", and
  the page shows "Rest complete". A newer rest keeps running, and the
  glance or the page moves to it. The skip ends a rest only if it cleared
  one that still ran: a finish that empties the store between its read and
  its clear leaves it nothing to end, so it reports that and the glance
  stays on "Back to the bar"
  (`RestTimerControllerTest.aFinishLandingInsideAnInAppSkipKeepsTheRestDone`). The dock's
  Skip still ends whatever runs (owner decision pending). Left as
  they are: a skipped rest whose row clear fails twice, followed by the
  process dying before any recovery, can still announce after the next
  start, as before.
- No call cancels another's disk job, and a SYNC is owed until one is sent
  (W2b-1c, owner decision of 24 September 2026). Every change to the rest
  queues one job that writes the row, then arms the wakeup; a later call
  takes a newer number and an older job then does nothing. Each call used
  to cancel the job before it, and calls come from two threads: a finish
  off the main thread could cancel the job the next set's rest had just
  queued, leaving that rest counting down with no row and no wakeup, so
  nothing would end it. Now nothing is cancelled. When the newest job is a
  finish's but a new rest runs by the time the finish reads the store (the
  owner logged a set as the last rest ended), it writes that rest; the SYNC
  the start asked for is owed until a job that writes a running rest sends
  it, so the new rest still reaches the shade. The number is taken in one
  step with a test seam just before and just after it, and tests hold
  each rule deterministically: a finish overtaken after its number by the
  next rest's start (the old cancel lost that rest's row); a finish
  overtaken just before its number (the store must be read after it); a
  start overtaken by a later call (the SYNC must be owed before the
  number); a save overtaken by a +15 inside it (the older job arms
  nothing); and jobs run newest first (the older job writes nothing). A
  race on real threads stays as a smoke test.
- Every re-arm writes the row first, and a job arms only the rest it wrote
  (the rest-alarm packet, 26 September 2026, audit RT-4 and RT-5). A
  resume, an exact-alarm grant and an early delivery used to arm straight
  from the store: before the row of a start still queued, or with no row
  at all after a failed write. A job armed whatever ran when it finished,
  so a rest published after its number got a wakeup over the older rest's
  row. Now all three re-arms queue a job like every other change, and a
  job arms the rest whose row it wrote, only while that rest still runs;
  the newer rest's own job arms it. A wakeup therefore always matches a
  row on disk. Since every re-arm now rewrites the row, a rewrite that
  fails while that rest's earlier row stands (a refused commit leaves it
  as it was; a recovery that read it back counts too) still arms, and the
  rest is not reported unsaved; a rest with no row of its own is never
  armed. Tests hold a refresh and an early delivery with a start's
  job still queued, an early delivery with the row missing, an older job
  and a newer rest, and a job whose rest a Skip ended first. Left as it
  was: a Skip landing between that check and the arm can still leave a
  wakeup for the skipped rest. The Skip's own job then clears the row, so
  the wakeup finds nothing, unless the process dies before that clear.
- The rest page's Next line is the Log's (*amended 24 September 2026, W2b-4,
  owner decision*). The page asks the coach the Log's question
  (`NextSetInputs`): the Log's entry for the lift the page shows, Another
  set, a set open for correction, and that lift's hint and last session,
  re-read when the Log has moved to another lift. It shows the answer only
  where the Log shows its Next card: after the lift's planned sets only with
  Another set, and not on a warm-up entry, while a set is being corrected,
  or while the Log holds a save (one in progress, or a failed one waiting
  for Retry). Its planned length still ignores
  Another set, so after Another set it plans the dock's length, and last
  session is read only after that length is set. Moving both the page and
  the dock to the extra set's length is an owner decision still owed. Held
  by `RestTimerViewModelTest`: `afterAnotherSetTheRestPagesNextLineIsTheLogs`,
  `onALiftsFirstSetTheRestPageKeepsLastSessionsRpeAsTheLogDoes`,
  `afterTheLiftsLastPlannedSetTheRestPageShowsNoNextLineAsTheLogShowsNone`,
  `whileASetIsOpenForCorrectionTheRestPageShowsNoNextLineAsTheLogShowsNone`,
  `onAWarmUpEntryTheRestPageShowsNoNextLineAsTheLogShowsNone`,
  `whileAFailedSaveWaitsForRetryTheRestPageShowsNoNextLineAsTheLogShowsNone`,
  `aPageOpenedAsTheLogMovesToAnotherLiftShowsThatLiftsCall`,
  `anotherSetOnOneLiftDoesNotReachTheRestPageForAnother`,
  `afterAnotherSetTheRestPageStillPlansTheLengthTheDockShows`,
  `whileLastSessionIsStillBeingReadTheRestPagePlansTheCoachsLength` and
  `whenLastSessionCannotBeReadTheRestPageStillPlansTheCoachsLength`,
  `aTimedHoldWithAnRpeGetsTheLogsCallOnTheRestPage` and
  `withAStrengthGoalAndALighterWeekTheRestPagesNextLineIsTheLogs`; by
  `RestPageNextLineTest.afterTheLiftsLastPlannedSetThePageDrawsNoNextLine`;
  by `NextSetInputsTest.theSharedQuestionHasNoDefaults` (a page cannot
  leave an input out, including the coach's goal, which changes only
  wording the page does not show); and, for the Log's own rule, by
  `FloorRestAndCoachWiringRenderTest.whileASaveIsUnderwayTheCoachsCardStandsDown`.

## Review questions

- Why not `USE_EXACT_ALARM`? Play eligibility is unsettled and rest is a
  short in-gym timer, not a calendar alarm clock. `SCHEDULE_EXACT_ALARM`
  plus an honest fallback is the signed strategy.
- Does a missed Monday automatically become Tuesday? Not after P7.3, and
  not as the product rule.
- Do reminders have to be exact to the minute? No. Rest does. Reminders do
  not.
