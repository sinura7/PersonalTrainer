# ADR-015 — Plan is a day-block schedule

- **Status:** Accepted (superseded in part by
  [ADR-017](ADR-017-home-week-board.md): adding a block may set an hour;
  Add session is the Plan Volt; Tune / New are gone)
- **Date:** 29 August 2026
- **Supersedes:** [ADR-006](ADR-006-information-architecture.md) T3 landing
  (Plan day *sheet*); Plan as a start surface
- **Related:** [ADR-007](ADR-007-activity-model.md), [ADR-014](ADR-014-settings-tab.md);
  owner request 29 August 2026

## Context

The Plan tab opened a weekday as a bottom sheet that mixed schedule
editing with Start Cardio, Swap, Unpin, and a second copy of the date.
That is a start surface wearing a planner's clothes. Start already lives
on Home. The owner asked for daily activity blocks — a workout, optional
cardio, and short auxiliary / longevity work — built on a dedicated day
page, without clock chrome.

## Decision

1. **Plan is a schedule workshop.** Start, Resume, and free workout are
   Home (and the live bar). Plan does not offer Start Cardio or Start a
   free workout.
2. **Tapping a weekday opens a pushed day page**, not a sheet. One title
   (the weekday). The date is not repeated as a second heading. The tab
   bar hides on this pushed route, matching Goals and Library.
3. **Add session is a Plan-tab control** (today's day, add-picker open)
   **and** the Volt on the day page. The day page is where the user adds
   and deletes blocks for that weekday.
4. **A day holds up to three kinds of block:**
   - **Workout** — the pinned strength routine, or a later extra.
   - **Cardio** — Walk, Run / sprints, Ride, Row, Swim, or Hike. One
     cardio block per weekday. Type is stored on the existing
     `ScheduleRule.templateId` as `cardio:{TYPE}` (no schema bump).
   - **Auxiliary** — short 5–10 minute packs (Stretch, Lower back, Hips,
     Holds, Core) minted from **existing** catalog ids. Not a catalog seed.
     Stored as STRENGTH with `templateId` `aux:{packId}`.
5. **Clocks stay in the model as defaults** (07:00 cardio, 18:00 imported
   strength, +2h later). Adding a block has no *required* time picker.
   **Existing** blocks may set an hour on the Plan day page
   ([ADR-016](ADR-016-settings-home-trim.md)).
   **[ADR-017](ADR-017-home-week-board.md):** the add picker may set an
   hour too. Home shows those times. Reminders fire from the stored hour.
6. **No Swap. No Unpin chrome.** Delete the session. Deleting the imported
   evening pin unpins that weekday. Logged work stays.
7. **Edit lifts** by tapping the workout (or auxiliary) row. That opens
   the routine editor.
8. Library stays pushed. Goals UI is gone ([ADR-016](ADR-016-settings-home-trim.md)).
   Instrument stays. A sixth tab is still
   [ADR-014](ADR-014-settings-tab.md).

   | ID | Task | Primary expected landing |
   |---|---|---|
   | T3 | Schedule cardio and a workout on one day | Plan day page |

## Consequences

- Canonical T3 in ADR-006 lands on the Plan day page.
- Catalog seed expansion remains a won’t. Auxiliary packs only reference
  ids already in `DefaultExercises`.
- Reminders (ADR-012) still fire from stored hours. Setting an hour on
  an existing Plan-day block updates Home and the reminder. Adding a
  block may set an hour ([ADR-017](ADR-017-home-week-board.md)); otherwise
  defaults stay.

## Review questions

- Does Plan start a session? No.
- May a day hold cardio without a clock picker? Yes. Defaults stay.
  The add picker may still set an hour.
- May we seed new stretch rows for this packet? No.
