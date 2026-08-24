# ADR-008 — Deterministic rules and explanation API

- **Status:** Accepted
- **Date:** 24 August 2026
- **Supersedes:** Nothing. Reaffirms the historical LLM cut and DESIGN_AUDIT §15.
- **Related:** FND-047; P8.4; Phase 11 / 12

## Context

Temper’s coach is a local rule engine. An LLM chat coach was cut because it
breaches offline-first and cannot be the authority for load or schedule.
A later lightweight API is useful only if it cannot silently change records.

## Decision

1. **Recommendations, progression, schedule recovery, and goal progress
   remain local and deterministic.** They work in airplane mode.
2. Every recommendation, progression decision, and schedule-recovery action
   emits a structured **`RuleTrace`**:
   - rule ID and version;
   - action;
   - reason codes;
   - evidence window;
   - typed facts and thresholds;
   - alternatives considered;
   - generated time.
3. The UI can render “Why” from that trace offline. Action and trace are
   produced together. A screen that shows advice without a trace is defective
   after P8.4.
4. A future API may turn an existing `RuleTrace` into natural language after
   explicit opt-in. It may **not**:
   - invent or silently apply new loads, plans, goals, or records;
   - replace the local decision;
   - make medical claims;
   - run as a prerequisite for recording or scheduling.
5. There is no in-app chat coach, no remote feature flag that changes gym
   math, and no “ask the model what to lift.”
6. Users can always override a recommendation. Override is a first-class
   act, not a buried discard.

## Consequences

- Phase 8 ships traces before any network explanation client.
- Commercial copy may not call the product an AI trainer.
- FND-047 is enforced by a concrete contract, not a slogan.

## Review questions

- May we add a chat surface “just for explanations”? No. Explanations render
  from `RuleTrace`. A later API is optional text over that object.
- May the API return a new suggested weight that the client applies if the
  user does not notice? No.
