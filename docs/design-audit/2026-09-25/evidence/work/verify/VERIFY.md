# Verifier brief (audit X6, Phase C)

You are an adversarial verifier. Specialist passes have written findings; your
job is to **refute** the P1/P2 claims assigned to you from the code at
`/home/user/PersonalTrainer` (HEAD `1ad3b11`). A claim you cannot refute after
an honest attempt is Confirmed. You did not write these findings; do not
defend them.

## Hard rules

- Read-only on the repository. Never edit, create or delete anything under
  `/home/user/PersonalTrainer`. Write only under
  `/tmp/claude-0/-home-user-PersonalTrainer/cf46fb84-1fb9-5154-8302-bc82a0b34c05/scratchpad/verify/`.
- Never run `./gradlew`, `tools/preflight.sh`, `tools/verify.sh` or anything
  that starts Gradle. The lead runs your probes in one batch. Python checkers
  (`python3 tools/check-*.py`) are fine.
- No Supabase, GitHub-write or network tools.
- For every claim, read the cited `path:lines` yourself, then look for the
  thing that would make the claim false: a guard the pass missed, a caller
  that never reaches the path, a test that already pins the behaviour, an
  ADR that makes it intended. Say what you looked at.

## Inputs

- The pass reports are in `…/scratchpad/passes/B<n>.md`. Your cluster names
  which IDs to verify; each row's Files, Claim and Probe columns are your
  starting point. Read the pass's "Notes on §2" for the counter-evidence it
  already checked.
- Severity rubric (verbatim, 22 Sep record): **P1** = protect data or a
  first-run blocker; **P2** = next correctness or polish work; **P3** = worth
  doing, not urgent. Types: defect / validation gap / architecture debt /
  target gap / opportunity.
- Duplicates: your cluster note lists rows that describe the same defect
  from two passes. Verify once, and say which ID should survive and which
  folds into it.

## Output: `…/scratchpad/verify/V<n>.md`

```
# V<n> — <cluster>

## Verdicts
| ID (pass) | Verdict | How | Corrected claim (verbatim, when Partly) | Severity | Plain-language impact |
(Verdict: Confirmed / Partly / Refuted / Needs runtime.
 How: "read <path:lines>" and/or "probe <TestClass.method> (see below)" and/or "gate output <file>".
 Severity: "agree P2" or "propose P<x> because <one clause>".
 Plain-language impact: one sentence for the owner, no jargon.)

## Refutations (one paragraph each; these are published in the record)

## Duplicates merged
| Surviving ID | Folded IDs | Why one defect |

## Noticed, not verified
(bullets; never enters the record as a finding)

## Probes
(the list of probe methods you wrote, one line each: method name → which claim, expected result on trunk = <passes/fails>, and what a fix would flip)
```

## Probes

Where a claim can be run on the JVM, write a JUnit test into
`…/scratchpad/verify/probes/AuditProbe<Cluster>Test.kt` (one file per
cluster; package `com.sinura.personaltrainer.audit`). Rules:

- Method name `claim_<ID with dashes replaced by underscores>_holds()`, e.g.
  `claim_S_11_B1_holds()` (append the pass when two passes share an ID).
- **The assertion states the defect: a green probe means the claim is
  confirmed.** Add a one-line comment above each method: `// a fix flips
  this by …`.
- Use only APIs you have read: `FakeAppDependencies`
  (`app/src/test/java/com/sinura/personaltrainer/FakeAppDependencies.kt`),
  the fakes under `app/src/test/…/data/sync/FakeSyncRemote.kt`, the
  `testutil` seed helpers, `sharedTest` clocks (`FakeClock`,
  `ControllableElapsedRealtime`), Robolectric with `@RunWith(RobolectricTestRunner::class)`
  when Android classes are involved, `runTest` from kotlinx-coroutines-test.
  Copy import lines from an existing test that does the same kind of thing;
  do not guess names. Pure-domain probes need no Robolectric.
- Keep each probe short (≤ 40 lines). If a claim needs more than that, or a
  device, mark it "read only" / "Needs runtime" and skip the probe.
- The file must compile as a whole: one bad import fails the whole batch.
  When unsure of an API, prefer a read-only verdict over a probe.

Your final message to the lead: the verdict table only (the file has the
rest).
