# ADR-018 — Home start confirm

- **Status:** Accepted
- **Date:** 29 August 2026
- **Amended:** 29 August 2026 — [ADR-019](ADR-019-move-to-today.md)
  leftover Home starts move the occurrence onto today; and the
  empty-agenda leftover card's Volt (`Start this session`) opens the
  same summary confirm instead of starting immediately — the last Home
  start that jumped straight into the log. A slot day is untimed, so
  its summary has no clock line and never relocates
- **Supersedes:** [ADR-017](ADR-017-home-week-board.md) §1 only the
  reading that Volt (or a planned row) **starts** the next undone block
  immediately
- **Related:** [ADR-017](ADR-017-home-week-board.md);
  [ADR-005](ADR-005-instrument-identity.md); owner request 29 August 2026
  (Home routines must confirm before start)

## Context

Home listed the day’s planned blocks and tagged one Volt
(`Start {title}`). The tagged row was dead. Untagged planned rows, and
the Volt, called `startOccurrence` immediately — a jump into the log
with no chance to see what the session was. On a stacked day the tag
also treated an auxiliary pack (Stretch, Core, …) as the same class of
strength as a workout, so Volt could read `Start Stretch` while a
named workout still sat undone.

The owner asked for clickable routines that open a confirm: do they
want to start **this** workout, and what does it entail.

## Decision

1. **Planned Home rows are tappable** when the occurrence is `PLANNED`
   and nothing is live — including the Volt-tagged row. Done, skipped,
   and moved rows stay readouts. A leftover `PLANNED` or `MISSED` row
   (civil day before today) is tappable
   ([ADR-019](ADR-019-move-to-today.md)). Live still hides Start; the
   bar is the way back.

2. **Tap does not start.** A row tap or the Home Volt opens a confirm
   dialog for **that** occurrence. Confirm starts it. Cancel, back, or
   tap-outside dismisses. Free workout stays quiet and does not gain
   this dialog. A notification / pending occurrence id still starts.
   **[ADR-019](ADR-019-move-to-today.md):** a leftover (civil day before
   today) confirms as **Do it today**, which relocates then starts.

3. **The dialog is a summary of that session.** Title `Start {title}?`
   (leftover: `Do {title} today?`). Body: clock · kind (Workout / Ride /
   Stretch / …), then the numbered lift order (or `Ready` for cardio,
   `No lifts yet` for empty strength). A leftover adds a line that it
   was the earlier weekday. Strength / aux may add a set-count and
   about-minutes line; an auxiliary pack may lead with its caption.
   Confirm label is `Start` (leftover: `Do it today`). Volt ink, not a
   second filled button. One filled Volt remains on the Home floor.

4. **Volt still names the next preferred planned block** and opens the
   **same** confirm. It is not a second start path and not
   `StartOptionsSheet`. Body / History / Plan still host that sheet.

5. **Start tag prefers a workout over an auxiliary pack.** Non-aux
   planned strength or mixed first, then any planned strength
   (including aux), then the first planned row (cardio).
   `ScheduleKind.isAux` is the aux test. Stretch remains startable from
   its row.

6. **One live activity still blocks a second start.** Confirm, then
   `ResumeOrDiscardDialog` if something is already running. Plan still
   does not Start. No schema bump. No catalog seed. No sixth tab.

## Consequences

- Feature packets do not restore one-tap Home start, dump
  `StartOptionsSheet` onto Home, or paint a second filled Start in the
  confirm.
- Treating Stretch as the day’s Volt while a non-aux workout is still
  planned is a defect.
- Notification deep-links that already identified the occurrence may
  skip the dialog.

## Review questions

- Does tapping a planned Home row start the session immediately? No.
- Does the tagged row stay dead? No. It opens the same confirm.
- Does Volt open `StartOptionsSheet`? No.
- May Stretch own the Home Volt while a workout is still planned? No.
- Does Plan Start? No.
