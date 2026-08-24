# ADR-002 — One-packet execution protocol

- **Status:** Accepted
- **Date:** 24 August 2026
- **Supersedes:** The Job 6 “one leftover packet at a time” rule as the
  *current* program. The one-packet discipline itself is kept.
- **Related:** [FOUNDATION_PROGRAM.md](../FOUNDATION_PROGRAM.md),
  [owner-loop](../../.cursor/rules/owner-loop.mdc)

## Context

The owner develops from Cursor when Studio is away. Phone checks happen later.
The strength-logger jobs already proved that two open packets editing the same
Kotlin file collide, and that GitHub-hosted runners are not a test lane.

The foundation program is larger than those jobs. It still has one developer.

## Decision

1. Execution is optimized for **one developer or agent**. Only one
   implementation packet is open at a time.
2. Every packet is one throwaway branch off current `trunk`, one logical
   commit series, and one PR into `trunk`. The packet merges before the next
   packet begins, except:
   - Phase 0’s documentation packets may share one PR because they are one
     audit gate and touch no production Kotlin;
   - Phase 5 database-cutover packets form one uninterrupted train with no
     unrelated changes between them.
3. Branch names stay `cursor/<short-slug>-b87f`. Delete the remote branch
   after merge. Leftover `cursor/*` heads are a process defect.
4. The sitting line is `trunk`. `main` is gone. Do not recreate it.
5. Local evidence required before review is defined by the foundation
   program’s universal packet protocol. The standing push gate remains
   `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`, plus
   `tools/preflight.sh`.
6. **GitHub-hosted runners are not the project test lane.** A red X on a
   hosted workflow is noise. Do not open a CI-billing packet. Do not ask the
   owner to grant Actions scopes so an agent can read workflow logs.
7. Phone evidence may remain a milestone blocker after the branch is cleaned
   up. Do not leave “code complete, branch pending phone” work open.
8. Independent review, adversarial audit, and post-merge trunk verification
   are mandatory for every packet, as specified in the program. Critical and
   high findings block merge.
9. `docs/ROADMAP.md` may receive a few pointer lines from any packet. That
   exception is not a license to restack Kotlin in the same PR.

## Consequences

- Parallel feature work is rejected even when it looks independent.
- Hosted CI may keep existing YAML. It is not a gate and not a reason to stop.
- Phase 10 (KMP) and Phase 11 (sync) do not start because they would be
  interesting. They start only when their ADRs’ start gates are true.

## Review questions

- May two packets be in flight if they touch different modules? No.
- Is a GitHub Actions failure a merge blocker? No.
- May Phase 5 cutover packets be interleaved with unrelated UI work? No.
