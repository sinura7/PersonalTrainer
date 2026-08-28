# ADR-012 — Exact rest, reminders, and missed-work policy

- **Status:** Accepted
- **Date:** 24 August 2026
- **Supersedes:** D2 rule 3 as the *user-visible* missed-day policy (silent
  forward shift). D2 remains the description of historical v2 slot
  derivation and the leftover ThisWeekCard week strip. P7.3 is the
  product rule. Documentation that the rest alarm is “exempt” or
  unconditionally reliable is superseded.
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
    stay dated until one persisted missed-work decision. The leftover
    slot-week strip may still derive a shifted day; that is not the
    product rule and must not grow.

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

## Review questions

- Why not `USE_EXACT_ALARM`? Play eligibility is unsettled and rest is a
  short in-gym timer, not a calendar alarm clock. `SCHEDULE_EXACT_ALARM`
  plus an honest fallback is the signed strategy.
- Does a missed Monday automatically become Tuesday? Not after P7.3, and
  not as the product rule.
- Do reminders have to be exact to the minute? No. Rest does. Reminders do
  not.
