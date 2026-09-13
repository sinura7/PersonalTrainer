# ADR-021 — Home start, day add, skip leftover, editor Save

- **Status:** Accepted
- **Date:** 1 September 2026
- **Amended:** 10 September 2026 — §7 says what "Save keeps the program"
  means when a write does not land (UX04 batch A, owner decision D16).
  13 September 2026 — §2–§3: Home's filled Volt opens a start sheet
  (free / Plan routine / cardio / Extra). Home Add is gone. Plan still
  adds.
- **Supersedes:** [ADR-018](ADR-018-home-start-confirm.md) §4 only the
- **Supersedes:** [ADR-018](ADR-018-home-start-confirm.md) §4 only the
  reading that Home's filled Volt **names** the next planned block;
  [ADR-020](ADR-020-warmup-extras.md) §3 only the reading that Home's
  add control is a quiet **Add extra** at the bottom of the board and
  is once-only with no weekly choice; [ADR-006](ADR-006-information-architecture.md)
  T1 only the reading that today's planned start **is** the one filled
  act
- **Related:** [ADR-005](ADR-005-instrument-identity.md);
  [ADR-015](ADR-015-plan-day-blocks.md);
  [ADR-017](ADR-017-home-week-board.md);
  [ADR-019](ADR-019-move-to-today.md); owner request 1 September 2026
  (tap a Home routine to start; noticeable freestyle Start; + under
  Today; skip Still open; collapsed Plan routines; Save on create)

## Context

Home listed today's planned blocks and put a filled Volt
(`Start {title}`) under Still open, then a quiet **Start a free
workout**, then **Add extra** at the bottom of the scroll. Tapping a
planned row already opened the ADR-018 confirm, but the row did not
*look* like a start. The owner asked why clicking the Home routine
does not start the workout: the start was buried, and the loud button
named the plan instead of a freestyle session.

**Add extra** only minted a warm-up / mobility pack, always once, and
sat below the fold. The owner wants a **+** under Today's last block
(or as the only item on an empty day) that can add a workout, cardio,
or extra, then choose **just today** or **every this weekday**.

Still open leftovers could only move-and-start. There was no Skip.

Plan's routines list was always expanded. Creating a routine had no
Save: edits write through, and back discards an empty stub. The owner
asked for a Save when creating a routine.

Per-machine photo portfolios (one exercise, several gym photos, each
with its own targets and history) are a later packet: they need a
schema bump, camera, file storage, and backup of those photos
([ADR-009](ADR-009-backup-privacy-sync.md),
[ADR-010](ADR-010-schema-reset-migrations.md)). This record does not
authorize that work.

## Decision

1. **A planned Home row starts that session.** Tap a `PLANNED` row
   (or a leftover `PLANNED` / `MISSED` Still open row) opens the
   existing ADR-018 / ADR-019 confirm. Confirm starts it (leftover:
   move onto today, then start). Cancel does not. The row carries a
   trailing **Start** (leftover: **Do it today**) in Volt ink so the
   control is the routine, not a second filled button. Done, skipped,
   and moved rows stay readouts. Plan still does not Start.

2. **Home's filled Volt is Start a workout.** That opens a sheet of
   starts, not two Home buttons and not `StartOptionsSheet` (Body /
   History / Plan still host that sheet for log-or-cardio). The sheet
   is: **Start a free workout** (empty live session, same as the old
   immediate Volt); **Select a routine** (named Plan routines, not the
   generator — tap starts that routine); **Cardio** then the live-52
   picture cards (Walk, Run / sprints, Ride, Row, Swim, Hike); **Extra**
   then the warm-up / mobility pictures. None of those mint a Plan row.
   While a session is live, the bar is the way back and the Volt hides.
   While the missed-work prompt's Keep-the-dates is the screen's Volt,
   Start a workout goes quiet (same rule as ADR-018 quiet start). There
   is no Get started sheet.

3. **Home has no Add row.** Plan Add session stays weekly and still asks
   what to put on that weekday. Recurrence of existing rules does not
   change from Home.

4. *(Removed 13 September 2026.)* Home no longer adds a session from this
   screen, so it does not ask just-today vs every this weekday.

5. **Still open can be skipped.** Skip marks that leftover
   `SKIPPED`. Recurrence is unchanged. Next week still gets the
   block. Skip is never Volt. Today's own planned rows are not
   skipped from this control.

6. **Plan routines are collapsed.** The catalog list starts closed.
   Tap **Routines** to expand. Empty still offers Create a routine.
   Long-press delete is unchanged.

7. **Routine editor Save keeps the program.** When the routine has
   lifts, a Save dock writes the name and notes, flushes staged
   targets, and leaves. Back still discards an empty stub created
   this session. Lifts, reorder, and targets still write through as
   they land. No schema bump. No catalog seed. No sixth tab.
   *Amended 10 September 2026 by the UX04 batch (owner decision D16).
   The decision is unchanged; this says what it means when a write
   does not land, which §7 as accepted left open.*
   Save is truthful: it leaves only when every write it is
   responsible for landed. A failed or refused write keeps the
   editor open and says so beside the dock; Back with such a write
   asks — **Try again** or **Leave without saving these** — and
   says the write-through edits are already saved. A quiet caption
   beside the dock explains the split once.

## Consequences

- Feature packets do not restore a filled `Start {planned title}` as
  Home's Volt, dump `StartOptionsSheet` onto Home, or put an Add row
  back under Today. Home Start is the sheet in §2.
- Treating Stretch as today's start while a workout row is still
  planned remains a defect (ADR-018 §5). The row starts Stretch; the
  Volt does not name it.
- Gym-station photo portfolios are not this packet.

## Review questions

- Does tapping a planned Home row start immediately? No. Confirm, then
  start.
- Is Home's filled Volt `Start {planned title}`? No. It is
  **Start a workout**, and that tap opens the §2 sheet.
- Does Add sit under Today? No. Plan still adds.
- Does **Start a free workout** on the sheet start immediately? Yes.
- Can a Still open leftover be skipped? Yes. Recurrence unchanged.
- Are Plan routines expanded by default? No.
- Is there a Save when creating a routine that has lifts? Yes.
