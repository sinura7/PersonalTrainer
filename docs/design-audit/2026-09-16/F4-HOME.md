# F4 — selected-day Home and recorded activity

Status: implementation and native acceptance in progress on `codex/home-day-design`.
Base: `8840af7f`, after F3 and the verified Debug 79 milestone delivery.

## Scope

- D01: historical completion says Training complete; Trained today belongs only
  to the actual current date. A Today shortcut returns from a historical selection.
- D14: shared Home/Plan day cells retain minimum touch bounds and scroll rather
  than compressing. Today has a top mark, selection has a border/fill, and status
  words distinguish planned, missed, skipped, recorded and remaining work.
- Completed scheduled workouts use their explicit linked record; free/repeated
  and cardio records remain visible by captured civil date. Planned exercise
  previews are labeled and never presented as exercises actually performed.
- Missing links stay truthful. Cross-date completion retains the scheduled
  activity relationship and explicitly labels the recorded date.
- Home retains the existing start sheet and confirmation behavior, missed-work
  decisions, skip/Undo, the single-live-session boundary and global resume bar.
  No Home Add/recurrence flow is restored.

## Internal changes

`HomeRecords` derives standalone completed records by captured date and removes
only explicit DONE occurrence links. It never matches by current routine name.
Activity duration in `SessionSummary` is cardio-only, so Home labels it cardio
time and omits zero cardio time for strength-only activities.

`WeekBoard` retains occurrence-resolution calculations while separating completed,
skipped, missed and recorded counts for presentation. Week summaries no longer
call skipped blocks done. Plan proposal semantics remain distinguishable from
accepted planned work.

Home consumes the existing all-time summary stream; no schema, backup, unit,
schedule identity or stored calculation changes are introduced.

## Checks and review

- Initial targeted unit/Android test compilation passed after correcting one
  mixed-argument test call that the static ratchet rejected.
- Early independent review identified cross-date record ambiguity and cardio
  duration labeling; both fixes are implemented, with final review pending.
- Follow-up reviews found false completion for DONE + MISSED days and lost
  completion for focus-only stored slots. The heading now says Training recorded
  for mixed completed/missed days. SuggestedTrainingDay carries the existing
  derived satisfaction ID; Home checks that exact record and captured date.
  Unrelated records cannot satisfy a stored slot. Regression cases cover both.
- The initial API 26 matrix passed 23/23 tests and produced 44 reviewed captures
  (`f4-layout2-api26`, run 20260917-154133859). These are preliminary evidence,
  before populated routine and cross-date/missing-link fixture expansion.
- Full gate 2 passed before the final review fixes. Gate 3 caught a missing test
  import and a WorkoutSession/SessionSummary fixture mismatch; both corrected.
  Latest source acceptance requires a fresh full gate, not the earlier result.
- The new native matrix mounts shipping HomeScreen/ViewModel with private Room,
  private preferences, pinned insight records and shipping navigation/live chrome.
  It covers five viewport profiles, three font scales, empty/live/long/RTL states,
  day reachability, historical copy, detail callbacks and return to Today.
- Full gate 4 passed 2,587 unit tests, static checks, lint, debug and test builds.
- Expanded API 26 matrix passed 27/27 with 56 captures (run
  20260917-155820337); all contact pages and representative full-size records
  were reviewed. The [Home evidence page](home.html) shows six native examples.
- Existing Home journeys plus real MainActivity navigation passed 25/25 (run
  20260917-160528051), capturing all five tabs. An earlier run passed 24/25:
  the remaining test expected the deliberately replaced accessibility label
  Start today's planned session; it now checks Start this planned session.
- Independent and adversarial source review findings are resolved. API 29
  recording passed 27/27, and 56 new Windows renderer references were visually
  reviewed. Fresh comparison plus Home/real-navigation journeys passed 52/52
  (run 20260917-161215431). No existing reference or comparison allowance changed.
- Screenshot assertions retain all state artifacts before failing the test,
  so a missing hosted reference cannot silently pass or conceal later states.
  Independent review confirmed that this remains fail-closed.
- API 36 passed the same 52 checks (run 20260917-161542561), with 61 captures
  including actual app chrome. All Home layout contact pages were reviewed.
  Hosted and integrated verification remain pending.
- F5 follow-up: the API 36 real-app Body observation captured no visible figure.
  Determine whether its capture readiness or rendering needs correction during
  the Body packet; this observation is not treated as Body visual acceptance.

## Milestone boundary

Debug 79 contains F0–F3, not these unmerged Home changes. The next planned phone
milestone follows F6 (Home, Body and unified History). Physical acceptance of
Milestone A remains separate from emulator and hosted verification.
