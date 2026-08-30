# ADR-019 — Move a leftover session to today

- **Status:** Accepted
- **Date:** 29 August 2026
- **Supersedes:** [ADR-012](ADR-012-rest-and-reminders.md) §15–17 only to
  the extent that **one** leftover occurrence may be moved onto today
  by an explicit Home confirm; [ADR-018](ADR-018-home-start-confirm.md)
  §2 only the reading that confirm always starts the occurrence **on
  its original civil day**
- **Related:** [ADR-012](ADR-012-rest-and-reminders.md);
  [ADR-017](ADR-017-home-week-board.md);
  [ADR-018](ADR-018-home-start-confirm.md); owner request 29 August 2026
  (Saturday, start yesterday’s workout today)
- **Amended:** 30 August 2026 — **Still open** includes the previous
  week so Monday still lists Sunday. Relocating onto a day whose
  canonical id is already a `MOVED` row mints a distinct id instead of
  upserting the vacancy away.

## Context

Home’s week strip lets you open Friday on Saturday. Planned rows confirm,
then start. Starting that occurrence leaves it dated Friday, so the
work is completed “back there.” The owner wanted to **do it today**
instead of paging back to the missed day.

Week-level missed-work already offers Move remaining (ADR-012). That is
one answer for the whole week, with Keep-the-dates as the Volt. It is
not a per-session “put this block on Saturday.” Reminder **Move** only
pushes +1 day. Recurrence rules must not change.

## Decision

1. **A leftover session can move to today.** A planned or missed
   occurrence whose civil day is before today may be relocated onto
   today.    The old row becomes `MOVED`. A new `PLANNED` row is minted
   for today (`OccurrenceGenerator.occurrenceId(ruleId, today)` when
   that id is free; a `-from-<day>` suffix when a `MOVED` row already
   holds it), same hour and minute, same rule. Recurrence is untouched.

2. **Home confirm is the act.** When that leftover is what they tapped
   (a past day’s row, or a **Still open** row on today), the dialog is
   `Do {title} today?` with the session summary plus a line that it
   was the earlier weekday. Confirm is `Do it today`. Confirm moves,
   then starts the new row. Cancel does not move. Today’s own planned
   rows keep ADR-018 `Start {title}?` / `Start`.

3. **Today lists leftovers.** When the selected day is today, Home
   shows leftover `PLANNED` and `MISSED` rows from this week and the
   previous week under **Still open**. They are tappable. You do not
   have to select Friday to start Friday’s work, and Monday still lists
   Sunday. Volt still prefers a still-planned block **on today**;
   if today has none, Volt may name the preferred leftover
   (`Do {title} today`).

4. **Same rule already on today.** If a non-moved row for that rule
   already sits on today and is `PLANNED`, confirm starts that row
   (already there). If it is `DONE` or `SKIPPED`, the move is blocked
   with a gym-floor error. `SKIPPED` leftovers are not offered.

5. **Week-level missed-work stays.** The one persisted Keep / Move /
   Adapt / Skip prompt is unchanged. This packet does not silently
   shift the week. Plan still does not Start. No schema bump. No
   catalog seed. No sixth tab.

## Consequences

- Completing a leftover **on the old civil day** is no longer the Home
  start path. Confirm relocates, then starts.
- Feature packets do not revive silent D2 week-shift, dump
  `StartOptionsSheet` onto Home, or treat Stretch as today’s Volt while
  a workout is still planned on today.
- Notification pending-start of a leftover occurrence uses the same
  relocate-then-start path.

## Review questions

- Does starting Friday’s workout on Saturday leave it dated Friday? No.
- Must they open Friday on the week strip to do Friday’s work? No.
- Do recurrence rules change? No.
- Does Keep-the-dates for the week disappear? No.
- Does Plan Start? No.
