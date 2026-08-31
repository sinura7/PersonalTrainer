# ADR-020 — Warm-up extras, untimed day board, same-day extra

- **Status:** Accepted
- **Date:** 31 August 2026
- **Supersedes:** [ADR-015](ADR-015-plan-day-blocks.md) §4 (aux list)
  and §5 (Home shows clocks; hour chips on add / existing blocks);
  [ADR-017](ADR-017-home-week-board.md) §5 (add picker may set an hour;
  Home shows those times) and §7 (five auxiliary packs);
  [ADR-018](ADR-018-home-start-confirm.md) §3 only the reading that the
  confirm body leads with `clock · kind`
- **Related:** [ADR-012](ADR-012-rest-and-reminders.md);
  owner request 31 August 2026 (golf / lower / upper / shoulder
  warm-ups; add on Plan or as a free extra today; rearrange Home;
  drop visible times)

## Context

Plan already mints short extra blocks from catalog ids that exist
(Stretch, Lower back, Hips, Holds, Core). The owner asked for golf,
lower-body, upper-body, and shoulder **warm-ups** (exercises only —
not a new catalog, not a golf round). Those extras should be addable
on a planned weekday **or** as a same-day extra when they were not
planned. Home lists the day's sessions; clocks on those rows were
noise. Order should be something the lifter can change.

Splicing warm-up lifts into the pinned Push/Pull routine would hide
them inside one start. The owner also wants Home rows rearrangable,
so extras stay **their own blocks**.

A sort-order column would be a schema bump (ADR-010). Hours already
order the day.

## Decision

1. **Warm-up packs are auxiliary packs.** Golf, Lower-body, Upper-body,
   and Shoulder warm-ups join Stretch / Lower back / Hips / Holds /
   Core. Each is minted from **existing** `DefaultExercises` ids, stored
   as STRENGTH with `templateId` `aux:{packId}`. Not a catalog seed.
   One of each pack id per civil day. They are their own session —
   not prepended onto the pinned workout.

2. **Plan add is recurring.** Plan day **Add session → Extra** writes an
   enabled weekday rule. Next week gets the block.

3. **Home Add extra is once.** Home's quiet **Add extra** (not the Volt,
   not `StartOptionsSheet`) mints this week's occurrence, then disables
   the rule so the generator skips later weeks. Plan remains the weekly
   author. A later Plan add of the same pack on that weekday creates
   (or reuses) an enabled rule.

4. **Clocks stay in the model and leave the floor.** Defaults remain
   07:00 cardio, 18:00 imported strength, +2h later. Reminders still
   fire from the stored hour. Home rows, Plan's selected-day board,
   Plan day blocks, the add picker, and the Home start confirm do
   **not** show a clock. The confirm body leads with kind, then lifts.

5. **Up / Down rearranges the day.** Home (selected day, not past) and
   Plan day permute the existing hours of that day's blocks. No drag
   handle. No new column. Next week follows the rule hours that move
   with the row.

6. **No schema bump. No sixth tab. No catalog seed.** Start stays on
   Home. Plan still does not Start.

## Consequences

- Feature packets do not restore hour chips on Plan day or Home clocks
  as the way to order a stacked day. They do not splice pack lifts into
  the evening pin.
- GitHub-hosted runners remain not a test lane.
- Same-day extras that were only meant for today must be added from
  Home (or deleted from Plan if they were added as weekly by mistake).

## Review questions

- May we seed golf-specific catalog rows? No.
- Is a golf warm-up a cardio block? No. Exercises only.
- Does adding Stretch from Home make it every Tuesday? No. Home is once.
- Do reminders still use stored hours? Yes. The hours are not shown.
