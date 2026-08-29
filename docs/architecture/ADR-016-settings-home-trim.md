# ADR-016 — Settings / Home trim

- **Status:** Accepted (superseded in part by
  [ADR-017](ADR-017-home-week-board.md): Home week strip as a day
  picker; reminder opt-out / quiet hours return to Settings)
- **Date:** 29 August 2026
- **Supersedes:** [ADR-006](ADR-006-information-architecture.md) §1 Goals-as-pushed-route
  and §7 (Goals snapshot on Home) only; [ADR-014](ADR-014-settings-tab.md) §3
  (Goals remain a pushed route) only; [ADR-015](ADR-015-plan-day-blocks.md) §5
  clocks-hidden-on-Plan only to the extent that **existing** blocks may set an
  hour. Adding a block still has no time picker.
- **Related:** [ADR-012](ADR-012-rest-and-reminders.md); owner request 29 August 2026

## Context

Settings opened on a two-row weight radio and a schedule card. Hours were
always 24-hour. The questionnaire and Settings wrote the same DataStore keys
but did not seed each other. Equipment chips were a flat dump and generated
weeks filtered by place, not by the kit the user actually toggled. Workout
reminders lived on Settings while session hours were invisible on Plan, so
Home's agenda times could not be edited where the schedule is. Home repeated
This week, Goals, Library, and a training calendar that already live on the
tab bar. Goals as a product idea were unused.

## Decision

1. **Display sits at the top of Settings**, compact: weight (lbs / kg) and
   hours (Regular 12-hour / Military 24-hour). Stored weights stay kilograms.
   Stored hours stay 0–23. This only changes how they are shown and entered.
2. **Schedule and coaching are one store with the questionnaire.** Re-running
   setup seeds from `storedOnboardingAnswers()`. Settings (and Plan Tune) day
   count trims `preferredDays` the same way the questionnaire does. A place
   change in the questionnaire clears an explicit equipment set so the kit
   re-derives from place.
3. **Weekly bodyweight check-in.** Default weekday is the first training day
   of the user's week (`preferredDays` in week-start order, else the first
   spaced training index). Settings may override (Auto + a weekday). Home
   asks to log weight on that day when this week has no weigh-in. No second
   exact-alarm path. Session reminders stay ADR-012 best-effort.
4. **Equipment is grouped** (free weights / gym / other). Opt-out remains:
   empty set is gym floor except Hyper Pro. Generated weeks and coach recs
   honour `CoachPreferences.allows()`. Catalog seed is unchanged.
5. **Session reminders and quiet hours lived on Plan** (Tune), not Settings.
   Existing Plan-day blocks can set an hour. Adding a block still uses
   defaults and has no clock picker. Home agenda times follow the stored
   hour and the clock format.
   **Superseded by [ADR-017](ADR-017-home-week-board.md):** opt-out and
   quiet hours are Settings prefs. Adding a block may set an hour. Tune
   is gone.
6. **Goals UI is removed.** Room goal tables, `GoalRepository`, and backup
   `measurableGoals` stay (schema freeze). There is no Goals route, no Home
   snapshot, no Plan link. Goals are not a tab. Promoting them onto the bar
   still needs a new ADR.
7. **Home is Start**, not a second tab bar. This week, Library, Goals, and
   the training-calendar *link* are gone. Library stays pushed from Plan,
   Body, and in-workout. Five tabs unchanged.
   **Superseded in part by [ADR-017](ADR-017-home-week-board.md):** Home
   shows a week strip as a day picker bound to occurrences. That is not
   a second tab bar.

   Shipping IA: Home · Body · Plan · History · Settings.

## Consequences

- Feature packets do not restore Goals chrome or Home tab-bar duplicates.
- Plan Tune is allowed a reminders disclosure. The Plan Volt remains
  recovery (Suggest / Replay / Use this week).
  **Superseded by [ADR-017](ADR-017-home-week-board.md):** Tune is gone;
  reminder prefs are Settings; Add session is the Plan Volt.
- New display and check-in prefs travel in DataStore and in backup
  preferences. No Room schema bump.

## Review questions

- Are Goals a pushed route? No. The UI is gone. Tables stay.
- May Home show This week / Library / a calendar strip? This week,
  Library, and Goals: no. A week strip as a day picker: yes
  ([ADR-017](ADR-017-home-week-board.md)).
- Does adding a Plan block require a clock? No. It may set one
  ([ADR-017](ADR-017-home-week-board.md)).
- Do Settings equipment toggles change generated weeks? Yes, via `allows()`.
