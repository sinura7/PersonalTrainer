# ADR-030: Live workout clarity — set copy and prepare-phase stats

**Status:** Accepted (2026-09-22)  
**Amended:** 23 September 2026 — Consequences only: the prepare-phase Last cell keeps the full row's height at side-by-side sizes (packet W1c). Decisions 1–6 are unchanged.  
**Amended:** 23 September 2026 — decision 2 (a dated note) and Consequences, on the owner's decisions of 23 September 2026 (packet W1d): at font 1.6 and above the floor shows Last alone before and after the first working set, and the lift's Details shows Best and Volume once a working set is logged; a stats value too wide for its cell shrinks to fit one line where it can. Decisions 1 and 3–6 are unchanged.  
**Scope:** Active strength floor only. Rest dock and idle rest card behavior are unchanged.

## Context

On the gym floor, exercise identity showed two competing set lines (for example `Set 1 of 3` and `0/3 working sets`). The stats row always showed Last, Best, and Volume even before any working set was logged this session, when Last (or last time) is the only number that earns space.

## Decisions

1. **One set-position line** — [SetOrdinalCopy.draftLine] is the identity source: `Working set n of m`, `Warm-up n`, `Extra n`. The ringed current-set chip uses [SetOrdinalCopy.draftChipLabel] (`Set n of m` / `WU n`) so the strip stays within the height budget; receipts and TalkBack use the full identity phrases. The identity row no longer repeats an `x/y working sets` caption; [CurrentLiftCopy.cardSpoken] omits working-set progress when the set-position line follows.
   *Amended 24 September 2026 (packet W2a):* `CurrentLiftCopy.cardSpoken` is removed; nothing read it. TalkBack reads the identity row from its own words: the equipment and name as one stop, then the set-position line, then the switch ([ExerciseHeader]). Working-set progress is still never said twice.

2. **Quieter prepare-phase stats** — Until at least one **working** set of the current lift is logged today, [ExerciseFloorStatsPresentation] shows only the Last / Last time cell on the floor. Best and Volume return after the first working set. Details and history surfaces are unchanged.
   *Amended 23 September 2026 (packet W1d, owner decision):* at font 1.6 and above Best and Volume do not return on the floor; the lift's Details shows them, by the same rule, once a working set of the lift is logged in the workout in progress. Below 1.6 this decision stands as written. See Consequences.

3. **Compact Next-set coach on prepare or match** — While no **working** set of the current lift is logged this session, or when the draft already matches the coach suggestion, [NextSetRecommendation] renders as a one-line strip: suggested numbers, **Why?**, and **Apply** (or **Applied**). The tall card (kicker, delta, inline evidence chip) returns after the first working set unless the entry still matches the suggestion. Apply, Why, and evidence (via Why and the evidence dialog when the full card is shown) stay wired to [CoachEngine] (ADR-029).

4. **Quieter Working | Warm-up** — The set-type toggle remains a two-way control under identity; it uses dense [InstrumentChip] styling (caption labels, compact padding) so it does not visually outrank the weight/reps diamond.

5. **Out of scope** — Logging diamond reorder (Packet A), rest dock changes, RPE removal, debugLiveCode bumps, Home packets.

6. **Bodyweight added-weight hero (2026-09-22 polish)** — On [LoadClass.BODYWEIGHT_ADDED] lifts, added load **0** must not render as a numeric `0` beside the unit on the live floor. [SetCopy.weightEntryHero] shows **BW** with caption **No added weight**; added load **> 0** shows **BW + N** with the unit. [SetCopy.weightWellSpoken] uses **Bodyweight** wording for TalkBack. Pure [LoadClass.LOADED] heroes stay numeric. Log-set payload lines still use [SetCopy.setLine] (`8 reps` at zero added).

## Consequences

- Receipt and chip ordinals use the same [SetOrdinalCopy] wording.
- Accessibility matrix notes for active strength reflect the single identity line and prepare-phase stats row.
- The lone prepare-phase Last cell reserves the full row's two-line label at side-by-side sizes (font below 1.6), so saving the first working set of a lift does not move the entry ([FRONTEND_REDESIGN.md](../FRONTEND_REDESIGN.md), "Ordinary logging retains entry position"). The two cases that still moved it are closed by packet W1d, in the next bullet. *Amended 23 September 2026 (packets W1c and W1d).*
- On the owner's decisions of 23 September 2026 (packet W1d), the first working set of a lift moves the entry only in the cases below. At stacked large text (font 1.6 and above, `LogLoopScale.stackEntryWells`) the stats row shows Last alone before and after that set, so it never moves the entry there. Best and Volume used to arrive above the entry and push it down, by 166 dp at font 1.6 on a 360 dp phone. The lift's Details, one tap from its picture, shows them under "Session in progress" once a working set of the lift is logged in the workout in progress. They use the floor's words and the same calculator, without the Last cell the floor keeps. Best set there is the floor's: the lift's standing best with this session's sets in the running, marked `Today` only when one of them beat it. Side by side (below 1.6) decision 2 stands. A Last, Best or Volume value too wide for its third of the row, such as `102.5 × 10` at font 1.3, is drawn at the largest smaller size that fits one line instead of wrapping. It never goes below the size of its own label, is never ellipsised, and sits on the row's own line and baseline, so the row keeps its height. The two-line label the lone Last cell reserves is measured at the current font scale, so a font raised while the app is open does not leave it a line short. **The entry still moves** when a value is too long even at its label's size, because it then wraps: on a 360 dp phone at font 1.5, `1102.5 × 10` or `12 reps +45`; on a 320 dp screen (a 411 dp phone at its largest display size) at font 1.5, also `102.5 × 10` and a five-digit volume such as `22,500 lb`. On that screen the Volume cell also wraps later in the session, when the total first reaches five digits, and the entry moves at that set. The owner's rule (no ellipsis on a number, nothing smaller than its label) comes first in those cases. TalkBack reads each cell as before. `FirstWorkingSetRenderTest` holds the rest across ADR-032's matrix. *Amended 23 September 2026 (packet W1d).*
