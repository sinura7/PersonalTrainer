# ADR-029 — In-workout CoachEngine and evidence-backed suggestions

- **Status:** Accepted
- **Date:** 21 September 2026
- **Related:** [ADR-004](ADR-004-offline-core-and-entitlements.md),
  [ADR-008](ADR-008-deterministic-rules.md), [ADR-020](ADR-020-warmup-extras.md),
  [ADR-025](ADR-025-goal-thresholds.md), [ADR-027](ADR-027-workout-logging-redesign.md)

## Context

Temper already ships a local in-set rule ladder ([`Coach` / `SetMicroRecCalculator`](../../app/src/main/java/com/sinura/personaltrainer/domain/Coach.kt))
with a structured [`RuleTrace`](../../app/src/main/java/com/sinura/personaltrainer/domain/RuleTrace.kt) and a **Next set** card on the active
strength workout ([ADR-027](ADR-027-workout-logging-redesign.md)). The product
direction is a constant gym-floor coach: set-to-set load and rep guidance,
session-open warm-up ladders, and explanations grounded in peer-reviewed
literature — not an LLM author and not a network prerequisite ([ADR-008](ADR-008-deterministic-rules.md)).

Recording, history, and live logging stay free and offline ([ADR-004](ADR-004-offline-core-and-entitlements.md)).
Commercial upgrade may later gate cloud sync or narrative API over an existing
trace; it must not gate Home, Plan, History, or the logger.

## Decision

1. **`CoachEngine` is the named policy façade** for in-workout strength coaching
   v1. It delegates load/rep/rest math to the existing [`Coach.decide`](../../app/src/main/java/com/sinura/personaltrainer/domain/Coach.kt)
   ladder (no parallel progression engine). It returns a **`CoachSuggestion`**
   value: target weight (kg), reps, optional RPE, short explanation, literature
   evidence ids, and the same [`RuleTrace`](../../app/src/main/java/com/sinura/personaltrainer/domain/RuleTrace.kt) as today.

2. **Evidence is curated on-device.** A versioned seed catalog
   ([`EvidenceCatalog`](../../app/src/main/java/com/sinura/personaltrainer/domain/coach/EvidenceCatalog.kt),
   human mirror [`docs/coach/evidence-seed.json`](../coach/evidence-seed.json))
   holds real peer-reviewed entries with DOI where one exists, year, short claim,
   and policy rule ids that may cite them. Rules that cannot be tied to a real
   source are marked **`heuristic: true`** with **no DOI**. The app must never
   invent papers or fake DOIs.

3. **Within-session policy stays RPE-reactive** on the existing reason codes
   (`IN_TANK`, `RPE_HOLD`, `FAILED_DROP`, …). Session-open warm-up ladders
   continue to use [`WarmupRamp`](../../app/src/main/java/com/sinura/personaltrainer/domain/WarmupRamp.kt)
   off the last completed working load. [`CoachPreferences`](../../app/src/main/java/com/sinura/personaltrainer/domain/CoachPreferences.kt)
   (goal, emphasis, equipment) remain inputs to generators and weekly rules;
   v1 next-set math is unchanged, with evidence copy that can reflect goal where
   already wired.

4. **UI:** The active strength workout keeps a persistent **Next set** card.
   It shows numbers, a one-line why, **Apply**, **Why?**, and a **Based on …**
   citation chip when literature ids are present. Tapping the chip or Why opens
   detail including DOI or an honest heuristic label. Apply copies into the draft
   only; it never logs silently ([ADR-008](ADR-008-deterministic-rules.md)).

5. **Offline-first:** Catalog lookup and policy run in `domain/` with no network.
   No LLM and no remote feature flag for gym math.

6. **Entitlements (future):** Optional paid surfaces may include multi-device sync,
   crowd learning, or an API that narrates an existing `RuleTrace`. Coach
   suggestions on the personal/debug build are visible without login. A product
   flag may hide suggestions in a store build later; core logging stays free.

## Consequences

- New literature is added by extending the seed catalog and rule→evidence map,
  not by editing locked micro-rec numbers in the same packet unless a separate
  ADR amends thresholds.
- v2 follow-ups (explicitly out of this ADR): ingestion UI for new papers,
  cardio coach, session-level macro pacing, commercial gate, multi-user learning.
- Privacy: the evidence library is static assets; no new telemetry is required
  ([`docs/PRIVACY.md`](../PRIVACY.md)).

## Review questions

- May v1 call an LLM for “why”? No. Narration may only wrap `RuleTrace` + cited
  evidence after opt-in ([ADR-008](ADR-008-deterministic-rules.md)).
- May coach suggestions require sign-in? No ([ADR-004](ADR-004-offline-core-and-entitlements.md)).
- What if a rule has no paper? Mark heuristic; do not attach a DOI.
