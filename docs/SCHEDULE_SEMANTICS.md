# Schedule semantics — signed spec

> **Banner (24 Aug 2026).** This file is the **current v2 derivation**. D2
> remains historically signed. The *target* missed-work policy no longer
> silently shifts a missed day: recurrence stays unchanged and the user is
> asked once ([ADR-012](architecture/ADR-012-rest-and-reminders.md), packet
> P7.3). Until P7.3 ships, the code may still shift; that is a known gap,
> not permission to add more silent rewrites. Current program:
> [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md).

**Status:** decision D2 (implemented). Phase 3 derived the `schedule_slots` DDL from this spec; Phase 4
implemented the derivation.

## The model

The week is an **ordered cycle of slots**, persisted in `schedule_slots(id, position,
routineId? FK→routines ON DELETE CASCADE, focusKind?, anchorDay? 0–6, createdAt,
updatedAt)`. A slot is a **routine slot** (routineId set), a **focus-only slot**
(focusKind set, routineId null — the planner may propose a routine for it), or, by
convention, absent — days with no slot are rest. `anchorDay`: 0=Monday … 6=Sunday (ISO
order, independent of the week-start display preference).

## Derivation rules

The effective week is a **pure function** of (stored slots, completion history, today).
It never writes; stored slots are input-only.

1. A slot is **satisfied** this week when a finished session traces to it (started via
   its pin) or matches its routine within the current week. "Next up" is the first
   unsatisfied slot in cycle order.
2. A satisfied slot renders on the day its session finished.
3. An anchored, unsatisfied slot renders on its anchor day when that day is still
   reachable (≥ today, not taken by an earlier slot). A **missed anchored day shifts
   forward** to the next open day — never skipped.
4. An unanchored, unsatisfied slot renders on the earliest open day ≥ today that
   preserves cycle order.
5. Cycle order beats anchors: if an earlier slot must occupy a later slot's anchor day,
   the anchored slot shifts forward.
6. Satisfaction resets at the week boundary: the cycle restarts each week; unfinished
   slots do **not** carry over as debt. *(If you want carry-over instead, strike this
   rule and initial the margin — the DDL is unaffected; only the derivation changes.)*
7. Planner regeneration proposes fills for **empty (focus-only) slots only**; an accepted
   fill persists into that slot's routineId. User-created slots are never touched by
   regeneration.
8. Deleting a routine cascades its slots away; the derived week heals (Phase 4's
   reconciliation test proves it). A pin whose routine still exists but has zero
   exercises keeps its slot, and starting it surfaces an explicit error — never a silent
   free workout.

## Worked examples

Week of **Mon 24 – Sun 30 Aug 2026**. Stored slots: **slot 1** Push (anchor Mon),
**slot 2** Pull (no anchor), **slot 3** Legs (anchor Fri). Baseline derived week on Monday
morning, nothing trained: **Push Mon · Pull Tue · Legs Fri**, rest otherwise.

**1 — Missed anchored day.** Monday passes with no session. Tuesday's derived week:
**Push Tue** (shifted, rule 3) **· Pull Wed · Legs Fri** (anchor still reachable). Stored
slots unchanged, byte for byte.

**2 — Missed unanchored day.** Push finished Mon. Nothing Tue or Wed. Thursday: next up is
still Pull → **Pull Thu · Legs Fri**. If nothing happens until Friday: **Pull Fri** (cycle
order takes Legs' anchor day, rule 5) **· Legs Sat** (shifted).

**3 — Week rollover.** The week ends with Push (done Tue 25) and Pull (done Thu 27)
finished; Legs never happened. Monday 31 Aug: satisfaction resets (rule 6) — the derived
week is the baseline again: **Push Mon 31 · Pull Tue 1 · Legs Fri 4**. Last week's
unfinished Legs is not owed.

**4 — Regeneration.** Stored: slot 1 Push (anchor Mon), slot 2 focus-only "PULL"
(routineId null), slot 3 Legs (anchor Fri). Derived week shows **Push Mon · [Pull-focus,
proposed: Pull] Tue · Legs Fri**. Tapping regenerate re-proposes for slot 2 only;
accepting writes Pull into slot 2's routineId (persisted — the week stops reshuffling).
Slots 1 and 3 are untouched, including their `updatedAt`.

**5 — Routine deletion.** The Pull routine is deleted. CASCADE removes slot 2. Stored:
slots 1 and 3. Derived week: **Push Mon · Legs Fri**; next up after Push is Legs. No
orphan, no error. (Contrast: emptying Pull to zero exercises without deleting it keeps
slot 2, and starting it shows the explicit error state — rule 8.)

## What to check before signing

For each worked example, ask: *is this what I'd expect my week to do?* The one
deliberately open question is **rule 6** — a day you miss does not carry into next week.
Strike it and initial the margin if you would rather owe the session; the table shape is
unaffected and only Phase 4's derivation changes.

Sign D2 in `docs/ROADMAP.md` § Decisions.
