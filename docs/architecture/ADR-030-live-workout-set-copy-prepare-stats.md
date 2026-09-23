# ADR-030: Live workout clarity — set copy and prepare-phase stats

**Status:** Accepted (2026-09-22)  
**Amended:** 23 September 2026 — Consequences only: the prepare-phase Last cell keeps the full row's height at side-by-side sizes (packet W1c). Decisions 1–6 are unchanged.  
**Scope:** Active strength floor only. Rest dock and idle rest card behavior are unchanged.

## Context

On the gym floor, exercise identity showed two competing set lines (for example `Set 1 of 3` and `0/3 working sets`). The stats row always showed Last, Best, and Volume even before any working set was logged this session, when Last (or last time) is the only number that earns space.

## Decisions

1. **One set-position line** — [SetOrdinalCopy.draftLine] is the identity source: `Working set n of m`, `Warm-up n`, `Extra n`. The ringed current-set chip uses [SetOrdinalCopy.draftChipLabel] (`Set n of m` / `WU n`) so the strip stays within the height budget; receipts and TalkBack use the full identity phrases. The identity row no longer repeats an `x/y working sets` caption; [CurrentLiftCopy.cardSpoken] omits working-set progress when the set-position line follows.

2. **Quieter prepare-phase stats** — Until at least one **working** set of the current lift is logged today, [ExerciseFloorStatsPresentation] shows only the Last / Last time cell on the floor. Best and Volume return after the first working set. Details and history surfaces are unchanged.

3. **Compact Next-set coach on prepare or match** — While no **working** set of the current lift is logged this session, or when the draft already matches the coach suggestion, [NextSetRecommendation] renders as a one-line strip: suggested numbers, **Why?**, and **Apply** (or **Applied**). The tall card (kicker, delta, inline evidence chip) returns after the first working set unless the entry still matches the suggestion. Apply, Why, and evidence (via Why and the evidence dialog when the full card is shown) stay wired to [CoachEngine] (ADR-029).

4. **Quieter Working | Warm-up** — The set-type toggle remains a two-way control under identity; it uses dense [InstrumentChip] styling (caption labels, compact padding) so it does not visually outrank the weight/reps diamond.

5. **Out of scope** — Logging diamond reorder (Packet A), rest dock changes, RPE removal, debugLiveCode bumps, Home packets.

6. **Bodyweight added-weight hero (2026-09-22 polish)** — On [LoadClass.BODYWEIGHT_ADDED] lifts, added load **0** must not render as a numeric `0` beside the unit on the live floor. [SetCopy.weightEntryHero] shows **BW** with caption **No added weight**; added load **> 0** shows **BW + N** with the unit. [SetCopy.weightWellSpoken] uses **Bodyweight** wording for TalkBack. Pure [LoadClass.LOADED] heroes stay numeric. Log-set payload lines still use [SetCopy.setLine] (`8 reps` at zero added).

## Consequences

- Receipt and chip ordinals use the same [SetOrdinalCopy] wording.
- Accessibility matrix notes for active strength reflect the single identity line and prepare-phase stats row.
- The lone prepare-phase Last cell reserves the full row's two-line label at side-by-side sizes (font below 1.6), so saving the first working set of a lift does not move the entry ([FRONTEND_REDESIGN.md](../FRONTEND_REDESIGN.md), "Ordinary logging retains entry position"). Two cases still move it, and both are packet W1d's, on the owner's decisions of 23 September 2026: a Last, Best or Volume value that wraps in a third-width cell (values will shrink to fit one line), and stacked large text (font 1.6 and above), where Best and Volume arrive above the entry and can push it below the fold (the floor will show Last alone at those sizes, with Best and Volume in Details). *Amended 23 September 2026 (packet W1c).*
