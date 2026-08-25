# P10 KMP — gate hold

- **Evidence date:** 25 August 2026
- **Decision:** [ADR-003](../../architecture/ADR-003-shipping-platform.md)

Phase 10 does not start until the local Android milestone is accepted
and an iOS client is an actual signed product decision.

Neither condition is true:

- Phase 9 Public Candidate is not claimed (physical TalkBack open).
- There is no signed iOS product decision.

No `commonMain` module, shared Gradle KMP plugin, or Apple-target
source set was added. FND-020 remains closed by the Phase 5 seams
alone. This is the lawful completion of the Phase 10 todo.
