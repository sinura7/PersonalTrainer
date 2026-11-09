# ADR-034 — Gym profiles (data and scoped history)

- **Status:** Accepted
- **Date:** 9 November 2026 — owner locked decisions D1–D8 and B1–B8
- **Related:** Packet G1 (schema 8, backup v6); G2/G3 UI packets follow

## Context

Strength work will belong to a gym. Machine-family lifts keep separate numbers per
gym; free weights share history everywhere. Existing rows stay Unassigned until the
owner assigns them.

## Decision

1. **Schema:** `TemperDatabase` v7 → v8 adds `gyms` (five seed rows) and nullable
   `gymId` on `workout_sessions` and `activity_sessions`. No foreign keys. No
   destructive migration fallback.
2. **Scope rule:** `domain/GymScope.kt` is the only place that classifies gym-specific
   equipment (`MACHINE`, `CABLE`, `SMITH`, `HYPER_PRO`). History reads use
   `HistoryScope` and `ScopedRows`; SQL aggregates use `gymId IS`, never `=`.
3. **Backup:** Format v6 carries gyms and per-session `gymId`. v5 and older restore
   with phone gyms kept and every restored session Unassigned.
4. **Sync:** Gyms and session `gymId` are local and backup only for this train;
   activity pull preserves local `gymId`.
5. **Authorisation:** This train authorises exactly one Room version bump (7→8) and
   one backup format bump (5→6). Allen approved both for Temper Debug 133.

## Consequences

- G2 must pass non-null `gymId` on every strength start.
- Machine PRs and progression hints are per gym; free-weight PRs remain global.
- Unassigned machine history never feeds any gym's prefill, coach, or PRs.
