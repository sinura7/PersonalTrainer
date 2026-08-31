# ADR-017 — Home week board and Plan day fill

- **Status:** Accepted
- **Date:** 29 August 2026
- **Amended:** 29 August 2026 — [ADR-018](ADR-018-home-start-confirm.md)
  replaces one-tap Home Start with confirm-then-start; the start tag
  prefers a workout over an auxiliary pack. 31 August 2026 —
  [ADR-020](ADR-020-warmup-extras.md) hides clocks, adds warm-up packs,
  Home Add extra (once), and Up / Down order.
- **Supersedes:** [ADR-016](ADR-016-settings-home-trim.md) §5 (reminders
  live on Plan Tune) and §7 (Home must not show a week strip);
  [ADR-015](ADR-015-plan-day-blocks.md) §5 only to the extent that
  **adding** a block had no clock; [ADR-006](ADR-006-information-architecture.md)
  §8 Tune as a fourth Plan command
- **Related:** [ADR-012](ADR-012-rest-and-reminders.md); owner request
  29 August 2026 (Saturday showing Friday)

## Context

Home after ADR-016 was Start without a week: masthead date, two stat
tiles, and one today-card. The leftover slot-week card named the pinned
routine (`Friday`) even when the civil day was Saturday, because D2
`placeUnsatisfied` can park a Friday-anchored pin on Saturday’s epoch
day while occurrences stay on the rule weekday (ADR-012). Plan’s week
strip used the same leftover caption. The quiet “Add session” text sat
under “1 pinned · 0 logged this week”, redundant with header **New**.
**Tune** duplicated Settings (days / split / week start) and held
reminder opt-out. Session hours existed on existing blocks but not when
adding one.

The owner asked for Home as the day’s summary: a selectable week, every
planned block (workout, cardio, auxiliary) for the selected day, Start
on a planned row or a quiet free workout, and completion colour on the
days. Plan is fill-the-day: Friday shows Friday’s blocks, Saturday
shows Saturday’s. Add session is the button. Reminders follow the hour
set on the day.

## Decision

1. **Home is the day’s board.** A shared week strip sits under the
   masthead. Default selected day is today. The masthead date follows
   the selected day. The board lists that day’s occurrences
   (`DailyAgenda.forDay`) — workout, cardio, and auxiliary as separate
   rows. Volt names the next undone planned block (first strength
   preferred). **[ADR-018](ADR-018-home-start-confirm.md):** Volt and
   planned rows open a confirm; they do not start until confirm. Start
   tag prefers a non-aux workout over Stretch. Free workout stays quiet.
   One live activity still blocks a second start. This week / Library /
   Goals / a training-calendar *link* stay gone. The strip is a day
   picker, not a second tab bar.

2. **Occurrence law owns the cell.** Week captions and Home’s board
   come from dated occurrences, not leftover slot-week `routineName`.
   A leftover ThisWeekCard may appear only when that leftover **belongs**
   on the selected civil day (Suggest / Replay before occurrences exist).
   A weekday-named routine (`Friday`) does not belong on a different
   weekday. D2 shifting a pin onto Saturday must not label Saturday
   “Friday”.

3. **Day fill is rest / none / some / all**, counted on **planned
   blocks** (routines), not on “workouts” as a session tally.
   - Empty → rest, muted
   - Planned, none resolved (DONE or SKIPPED) → Danger
   - Planned, some resolved → Warn
   - Planned, all resolved → check, not a Volt wash

   Volt remains the act (Start on Home, Add session on Plan). Seven
   green cells are not a second brand.

4. **Plan is fill-the-day.** Tapping a strip cell selects that civil
   day and shows that day’s blocks. Add session is the Plan Volt (opens
   the selected day’s page with the add picker). Header **Tune** and
   **New** are gone. **Library** stays. Days / split / week-start live
   on Settings only. **Lighter this week** stays a quiet chip on Plan
   (it is this week’s decision). Suggest / Replay / Use this week remain
   recovery; they are quiet when Add session is the Volt, except Use
   this week while a proposal is up. The routines list is the program
   catalog, not the selected day’s board.

5. **Adding a block does not require a clock.** Defaults stay 07:00
   cardio, 18:00 imported strength, +2h later. **[ADR-020](ADR-020-warmup-extras.md):**
   the add picker and existing blocks do not offer hour chips. Stored
   hours still drive ADR-012 best-effort reminders and day order.

6. **Reminder opt-out and quiet hours live on Settings**, not Plan Tune.
   Session hours stay in the model as a sort key. No second
   exact-alarm path.

7. **Auxiliary packs** are warm-ups (Golf, Lower-body, Upper-body,
   Shoulder) plus mobility (Stretch, Lower back, Hips, Holds, Core),
   minted from existing catalog ids. Not a catalog seed. No Room schema
   bump. **[ADR-020](ADR-020-warmup-extras.md).**

   Shipping IA: Home · Body · Plan · History · Settings.

## Consequences

- Feature packets do not restore Tune, header New, or Home tab-bar
  duplicates. They do not paint completion in Volt.
- Silent contradiction of leftover captions vs occurrence days is a
  defect.
- Reminders remain WorkManager best-effort.

## Review questions

- May Home show a week strip? Yes — as a day picker bound to
  occurrences. Not as This week / Library / Goals.
- May Saturday show a routine named Friday? No.
- Is Add session the Plan act? Yes, except while confirming a proposed
  week or answering missed work.
- Does adding a block require a clock? No.
- Do reminder prefs live on Plan? No. Settings. Hours stay in the model.
