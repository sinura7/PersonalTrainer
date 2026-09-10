# ADR-024 — The deterministic hosted job may gate `trunk`; the emulator may not

- **Status:** Accepted
- **Date:** 10 September 2026
- **Amends:** [ADR-002](ADR-002-execution-protocol.md) decision 6 and its
  consequence "Hosted CI ... is not a gate", for one named job only.
  ADR-002 decisions 1–5 and 7–10 are untouched, and the phone lane
  (decision 10) is unchanged.
- **Does not supersede:** ADR-002. The local gate — `tools/preflight.sh`
  plus `./gradlew testDebugUnitTest assembleDebug` — remains the gate a
  packet must pass before review. This record adds a second lock on the
  door, it does not move the first one.
- **Related:** owner decision, 10 September 2026

## Context

ADR-002 decision 6 reads: "**GitHub-hosted runners are not the project
test lane.** A red X on a hosted workflow is noise. Do not open a
CI-billing packet. Do not ask the owner to grant Actions scopes so an
agent can read workflow logs."

Every clause of that describes a situation that no longer exists. It was
written while the account had no working runner: runs died in seconds,
before checkout, and `DEVELOPMENT.md` carried a paragraph blaming
billing that turned out to be wrong and cost nine sessions. A red mark
genuinely was noise, because nothing had run.

Today a runner is assigned. `ci.yml`'s *Tests, lint, debug build* job
checks out, installs JDK 17 and the SDK, and runs the same three things
the local gate runs — `tools/preflight.sh`, `./gradlew testDebugUnitTest`,
`./gradlew assembleDebug lintDebug` — plus the instrumented-sources
compile. On 10 September it ran on nine packets in a row and was green on
every one, and its two reds that day were both real: a lint rule about a
`Modifier` default, and a unit test. Agents read those logs routinely
through permissions the owner has already granted.

The second hosted job is a different animal. `instrumented-smoke` boots an
emulator with no GPU. On the same 10 September its golden passed and
failed on the same commit, seventeen pixels apart, which is exactly the
"a red X is noise" that decision 6 was written about.

The owner has asked for *Tests, lint, debug build* to be a required check
on `trunk`, with branches required to be up to date. Under ADR-002 as
written that could not be done, because a required check is a gate.

## Decision

1. **`Tests, lint, debug build` may be a required status check on
   `trunk`,** together with "require branches to be up to date before
   merging". A squash then cannot land on a base whose CI never built it.
2. **`Instrumented smoke` may never be a required check** and keeps
   `continue-on-error: true`. It is read, not gated on. Removing that flag
   needs its own signed decision; ten green runs are not one.
3. **The local gate is unchanged and still comes first.** Hosted green is
   not evidence a packet is ready for review, and a packet is not "done
   because CI is green". `tools/preflight.sh` plus the Gradle pair on a
   real machine remains the standing push gate (ADR-002 decision 5).
4. **A red on the required job is never routed around.** Not by disabling
   the rule, not by an administrator override, not by skipping a test.
   Fix it or revert.

## Consequences

- ADR-002 decision 6's blanket "hosted runners are not the project test
  lane" now has exactly one exception, named here. The refusal list in
  [README.md](README.md) points at this record.
- Branch protection is a repository setting the owner holds; an agent
  cannot set it and must not ask for the scope to.
- If the hosted JVM job becomes unreliable — flaky reds not caused by the
  diff — this decision is revisited rather than worked around.
- The emulator lane stays where it is: useful, read on every pull request,
  and unable to block anything.

## Review questions

- May *Tests, lint, debug build* block a merge? Yes, once the owner
  enables it.
- May *Instrumented smoke* block a merge? No. Never, without a new ADR.
- Does hosted green replace the local gate? No. The local gate is first
  and unchanged.
- Ten green emulator runs — may the flag come off then? No. That is a new
  decision, not a counter.
