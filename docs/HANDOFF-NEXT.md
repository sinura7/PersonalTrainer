# Start here

The first thing a new session on this repository should read. Written
2026-09-07 at the end of the session that landed the nineteen-finding
engineering handoff.

## Where the code stands

`trunk` carries pull requests #168 and #169: every item R01 through R19 from
the 2026-09-06 engineering handoff, verified against the source, implemented,
and recorded. [`HANDOFF-2026-09-06.md`](HANDOFF-2026-09-06.md) is the full
account, batch by batch, including what was fixed, what was deferred and why,
and every command that was run with its result. `debugLiveCode` is 22.

Nothing about the app's data was changed. Room stays frozen at v4, the backup
document and envelope formats are untouched, and no identifier is ever
rewritten. Those three constraints hold for future work too.

## What has and has not been verified

Two lanes ran and passed on every commit:

```bash
PT_STATIC_ONLY=1 PT_JARS=build/test-jars sh tools/preflight.sh
#  -> preflight: OK (static only)
#     ratchets: required_args_mixed 180, required_args_lambda 46,
#     when_exhaustive 47, state_members 2, all at baseline

PT_JARS=build/test-jars sh tools/run-domain-tests.sh build/test-jars
#  -> Running 170 test classes... / OK (1175 tests)
```

Nothing else ran. No Gradle build, no Robolectric, no lint, no emulator, no
device. Roughly four hundred tests on this work have never executed once,
including every Room and ViewModel test written for R06 through R12 and the
production-screen instrumented tests written for R16. Three compile errors
were found by reading the code rather than compiling it; a fourth class of
mistake could still be sitting in the unexecuted Android sources.

If this session has the Android SDK, that gap is the first thing to close.
See [`CLOUD-ENVIRONMENT.md`](CLOUD-ENVIRONMENT.md) for how the environment is
configured and what it can and cannot do.

## First actions

1. Confirm what the environment actually has:

   ```bash
   cat /opt/android-sdk-setup.log 2>/dev/null || echo "setup script did not run"
   echo "ANDROID_HOME=${ANDROID_HOME:-unset}"
   ```

2. Run the two lanes above. They are the known-good baseline; if either
   regresses, that is this session's first problem, not a new feature.

3. If and only if the SDK is present, run the lanes that have never run, and
   report honestly which pass:

   ```bash
   ./gradlew compileDebugKotlin
   ./gradlew testDebugUnitTest
   ./gradlew lintDebug
   ./gradlew assembleDebug
   ```

   Expect friction on the first run: `gradle/verification-metadata.xml` pins
   a checksum for every dependency and CI has already failed on missing
   entries. Extend the ledger; never disable verification.

4. Report what actually happened. A failure here is a genuine finding about
   code that was written blind, not a setback. Fix what fails, keep the
   ratchets at baseline, and say plainly what still has not run.

## What is outstanding

**R05, blocked on the owner.** Temper Debug drops can be signed with one
stable key, but only once four `DEBUG_KEYSTORE_*` secrets and the
`DEBUG_CERT_SHA256` variable exist. `SETUP.md` section 6 has the procedure.
Until then every drop is marked THROWAWAY SIGNER and installs beside the
app on the phone rather than over it.

**The live test 22 drop never built.** `debug-live/2026-09-07` exists at the
merge commit, but the workflow run ended in seconds with no runner assigned,
as has every run on this account since 2026-09-05. Obtainium still offers the
2026-09-03 pre-release. The repository is private, so Actions minutes are
metered; the owner's own rule in `.cursor/rules/owner-loop.mdc` says not to
chase that. Building on a machine with the SDK and attaching the APK to a
pre-release for tag `debug-live-2026-09-07` is the documented alternative.

**R16 residue.** Production-screen tests exist for History, the activity
composer, live cardio, the activity receipt and Home. Settings, onboarding,
the routine editor, custom week, summary, session detail's delete dialog,
exercise detail and Library still have only isolated-control coverage, and
`AccessibilityMatrix` claims automated evidence for all of them, which
overstates it.

**R18 step one.** `HistoryViewModel.pastBlockReviews`, its `horizonProgress`,
and `PlanViewModel.completedBlockSessions` still read the strength store
alone, so a backdated strength activity counts toward totals and Records but
never toward the readout's PRs. The plan and the capability matrix are in
[`architecture/completed-training-convergence.md`](architecture/completed-training-convergence.md).

**R17 measurement.** The History catalog is shared and the revision keys are
in place, but the full-history read behind the horizon readout was left alone
deliberately: the finding asks for a measured before and after, and there was
nothing to measure on. Section 5 of the convergence record is the plan.

**A leftover branch.** `claude/read-zip-files-suok0y` is merged but still on
GitHub. The session credential cannot delete a remote branch or push a tag,
both refused with 403; that is a limit to design around, not a bug to retry.

## Rules that bind this work

`.cursor/rules/owner-loop.mdc` is the process authority and
`docs/architecture/` is the decision set. In short: `trunk` is the only
standing line, work happens on a throwaway branch, it lands by squash merge,
and the branch is deleted. The JVM gate merges a packet; the phone gates a
gym-floor release. Do not treat hosted runners as the test lane.

Every commit ends with the co-author and session trailers the session's own
instructions specify. Ratchets live in `tools/checker-baselines.toml` and may
fall but never rise.
