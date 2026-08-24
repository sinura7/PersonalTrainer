# ADR-013 — Finding dispositions and superseded doctrine

- **Status:** Accepted
- **Date:** 24 August 2026
- **Related:** [risk-register.md](../foundation-audit/risk-register.md),
  [FOUNDATION_PROGRAM.md](../FOUNDATION_PROGRAM.md)

## Context

The 23 August foundation audit ranked findings FND-001 through FND-048.
FND-037 was never issued. Historical Jobs 3–6 signed “won’t” rows that
would block the agreed product if still treated as law.

## Decision

### Numbering

1. **FND-037 is a numbering gap. No finding was issued.** Packets must not
   invent work for it. The gap stays visible so later writers do not
   “discover” an undocumented FND-037.

### Disposition map

Every issued finding has exactly one planned disposition. “Later” means a
named packet, not a wish.

| ID | Disposition | Packet |
|---|---|---|
| FND-001 | Fix | P2.1–P2.3 |
| FND-002 | Build | P5.2–P6.7 |
| FND-003 | Prove | P1.3 |
| FND-004 | Fix | P1.1 |
| FND-005 | Fix | P2.4 |
| FND-006 | Fix | P2.5 |
| FND-007 | Build | P7.1–P7.5 |
| FND-008 | Build | P6.2, P6.6 |
| FND-009 | Build | P8.2, P8.3 |
| FND-010 | Build | P8.1, P8.5 |
| FND-011 | Fix + policy | P3.1, P3.5, P3.6 |
| FND-012 | Optional build | P11.1–P11.6 after start gate |
| FND-013 | Fix | P2.6 |
| FND-014 | Build | P12.1 |
| FND-014A | Fix | P3.3 |
| FND-014B | Fix | P3.2 |
| FND-014C | Fix | P3.4 |
| FND-015 | Fix | P2.3 |
| FND-016 | Fix | P2.5 |
| FND-017 | Fix | P7.3 |
| FND-018 | Decide + prove | [ADR-007](ADR-007-activity-model.md); P5.2, P7.5 |
| FND-019 | Build | P5.3, P6.1 |
| FND-020 | Seam, optional KMP | P5.1; P10.1 after start gate |
| FND-021 | Prove | P1.2, feature packets, P9.7 |
| FND-022 | Prove | every UI packet, P9.7 |
| FND-023 | Fix | P9.6 Body, P9.7 |
| FND-024 | Fix | P9.2, P9.7 |
| FND-025 | Prove | P1.3–P1.6 and feature tests |
| FND-026 | Fix | P4.6 |
| FND-027 | Upgrade | P4.1–P4.6 |
| FND-028 | Fix | P4.5 |
| FND-029 | Harden | P12.3 |
| FND-030 | Publish | P12.2 |
| FND-031 | Evidence | P0.3, P9.1, P9.6 Plan |
| FND-032 | Evidence | P0.3, P9.1, P9.6 Library |
| FND-033 | Evidence | P9.1, P9.6 Body |
| FND-034 | Consolidate | P9.3, P9.6 |
| FND-035 | Fix | P9.4 |
| FND-036 | Fix | P9.5 |
| FND-037 | No finding issued | — |
| FND-038 | Measure, then maybe stream | P3.7, P8.5 |
| FND-039 | Build | P5.1, P6.2, P7.1, P8.1 |
| FND-040 | Prove | P1.6 |
| FND-041 | Fix | P0.2 |
| FND-042 | Fix | P0.2 |
| FND-043 | Build | P1.2, P9.6, P9.7 |
| FND-044 | Evidence-only skin | P9.3, P9.6 |
| FND-045 | Permanent decision | [ADR-005](ADR-005-instrument-identity.md) |
| FND-046 | Permanent default + gate | [ADR-006](ADR-006-information-architecture.md) |
| FND-047 | Permanent decision | [ADR-008](ADR-008-deterministic-rules.md) |
| FND-048 | Permanent decision | [ADR-004](ADR-004-offline-core-and-entitlements.md) |

### Historical constraints explicitly superseded

2. The following signed-won’t or “do not build” lines are **superseded**.
   They remain in historical files; they are not current law.

   | Historical constraint | Now |
   |---|---|
   | Room v3 won’t | Superseded by [ADR-010](ADR-010-schema-reset-migrations.md) for the one Phase 5 cutover |
   | No backdated session creation | Superseded by [ADR-007](ADR-007-activity-model.md) |
   | Day/year heat windows cut | Current Body windows stay This week + Last 30 days until Phase 8 adds comparable year/all-time *analytics*, which are not heat-window chips |
   | D2 silent missed-day shift as product policy | Superseded by [ADR-012](ADR-012-rest-and-reminders.md) |
   | ROADMAP / AUDIT as source of what is being built | Superseded by [ADR-001](ADR-001-documentation-authority.md) |
   | Job 6 as the current program | Superseded by [FOUNDATION_PROGRAM.md](../FOUNDATION_PROGRAM.md) |

3. The following historical won’ts **remain in force**:

   | Constraint | Authority |
   |---|---|
   | `fallbackToDestructiveMigration` | ADR-010 |
   | Fifth tab without a new ADR | ADR-006 |
   | LLM / chat coach as author | ADR-008 |
   | Package or Drive-folder rename | install identity |
   | Job 2 P5 sex question | no sentence exists |
   | Catalog seed expansion | no floor finding |
   | GitHub-hosted runners as the test lane | ADR-002 |
   | Auto-scaled lighter-week sets | HOLD remains the deload |
   | WorkManager silent Drive upload | ADR-009 |
   | Overlay rest clock | ADR-012 |
   | Per-day gym-versus-home schedule | still unrequested |
   | Forcing `MAX_DAYS` back to 6 | leftover P7 closed the opposite way |

## Consequences

- A packet that cannot name its FND IDs is incomplete.
- Rediscovering “we must not do Room v3” in a Phase 5 review is wrong.
- Rediscovering “we must do Room v3 next week” in a Phase 2 review is also
  wrong.

## Review questions

- Is there an FND-037 to implement? No.
- Which historical won’ts still bind an executor today? The table in (3).
- Which file lists every finding’s packet? This ADR. The program repeats
  the map as the schedule.
