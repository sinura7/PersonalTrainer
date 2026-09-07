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

Claude Code's no-SDK session ran the static gate and the domain JVM lane
on every commit of #168:

```bash
PT_STATIC_ONLY=1 PT_JARS=build/test-jars sh tools/preflight.sh
#  -> preflight: OK (static only)
#     ratchets: required_args_mixed 180, required_args_lambda 46,
#     when_exhaustive 47, state_members 2, all at baseline

PT_JARS=build/test-jars sh tools/run-domain-tests.sh build/test-jars
#  -> Running 170 test classes... / OK (1175 tests)
```

Cursor then ran the Gradle lanes that session could not, on `28f485f`
(the #168 squash; #169/#170 are docs and a Claude setup script):

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
#  -> 1818 tests, 0 failures; lintDebug green;
#     PersonalTrainer-1.0.0-debug.apk versionCode 22
```

The instrumented production-screen tests (R16) and every device /
emulator lane still have not run. See
[`CLOUD-ENVIRONMENT.md`](CLOUD-ENVIRONMENT.md) for Claude Code's
environment; Cursor already has an SDK.

## First actions

1. Confirm what the environment actually has:

   ```bash
   cat /opt/android-sdk-setup.log 2>/dev/null || echo "setup script did not run"
   echo "ANDROID_HOME=${ANDROID_HOME:-unset}"
   ```

2. Run the two lanes above. They are the known-good baseline; if either
   regresses, that is this session's first problem, not a new feature.

3. If the session changes Kotlin, re-run the Cursor JVM gate
   (`testDebugUnitTest` + `lintDebug` + `assembleDebug`). Do not treat
   hosted runners as the test lane. Do not weaken
   `gradle/verification-metadata.xml`.

4. The live-22 APK is already built. Do not bump `debugLiveCode` again
   until the next drop. Sideload Temper Debug; gym-floor Temper stays
   on the signed APK.

## What is outstanding

**R05, blocked on the owner.** Temper Debug drops can be signed with one
stable key, but only once four `DEBUG_KEYSTORE_*` secrets and the
`DEBUG_CERT_SHA256` variable exist. `SETUP.md` section 6 has the procedure.
Until then every drop is marked THROWAWAY SIGNER and installs beside the
app on the phone rather than over it.

**The live test 22 Obtainium drop never published.** Hosted
`debug-live.yml` died in seconds with no runner. Obtainium still offers
`debug-live-2026-09-03` (live **20** — live 21 also never published).
The Cursor APK at versionCode 22 is the phone install. Do not bump 22.
Do not `gh release create`. Leave `#125` / `#126` unmerged.

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

**Leftover heads.** `claude/read-zip-files-suok0y`,
`debug-live/2026-09-07`, and `claude/cloud-android-env` were deleted
after merge. Do not delete `claude/app-audit-optimization-xnqf5e`.
`claude/file-visibility-check-jraqc2` is an unmerged Claude vehicle
(UX + stub compiler); do not start a second edit of those paths from
`trunk`.

## Rules that bind this work

`.cursor/rules/owner-loop.mdc` is the process authority and
`docs/architecture/` is the decision set. In short: `trunk` is the only
standing line, work happens on a throwaway branch, it lands by squash merge,
and the branch is deleted. The JVM gate merges a packet; the phone gates a
gym-floor release. Do not treat hosted runners as the test lane.

Every commit ends with the co-author and session trailers the session's own
instructions specify. Ratchets live in `tools/checker-baselines.toml` and may
fall but never rise.
