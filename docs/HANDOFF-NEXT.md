# Start here

The first thing a new session on this repository should read. Written
2026-09-07 at the end of the session that landed the nineteen-finding
engineering handoff.

## Where the code stands

`trunk` carries pull requests #168, #169, and #170: every item R01
through R19 from the 2026-09-06 engineering handoff, plus a Claude
Code Android setup script. [`HANDOFF-2026-09-06.md`](HANDOFF-2026-09-06.md)
is the full account. `debugLiveCode` is 28.

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

### Reproduced in a cloud session, 2026-09-08

The first Claude Code cloud session with the Android SDK actually present
(`/opt/android-sdk-setup.log` dated 2026-09-07) re-ran every lane on
`4a90551` from a cold container. All green, nothing fixed, no source
changed:

```bash
PT_STATIC_ONLY=1 PT_JARS=build/test-jars sh tools/preflight.sh
#  -> preflight: OK; all 19 ratchets at baseline
PT_JARS=build/test-jars sh tools/run-domain-tests.sh build/test-jars
#  -> 170 test classes, OK (1175 tests)
./gradlew compileDebugKotlin   #  -> BUILD SUCCESSFUL, 0 errors
./gradlew testDebugUnitTest    #  -> 1818 tests, 0 failures, 279 classes
./gradlew lintDebug            #  -> BUILD SUCCESSFUL
./gradlew assembleDebug        #  -> PersonalTrainer-1.0.0-debug.apk, code 22
```

This confirms Cursor's numbers on independent hardware. Two things the
run surfaced that are worth carrying forward:

1. **`compileDebugAndroidTestKotlin` is covered by no lane.** It was run
   here for the first time and passes. The androidTest sources are where
   the three 2026-09-07 self-test defects lived (`onAllNodes` imported as
   a top-level function; the uninferable `sidecarFromHealth` generic), and
   nothing in the merge gate compiles them. It costs about ten seconds on
   a warm cache. Consider adding it beside `testDebugUnitTest`.
2. **A cold container silently downgrades the static gate.** With an empty
   Gradle cache `tools/preflight.sh` cannot find `kotlin-compiler-embeddable`,
   so the syntax check prints `WARNING — syntax check skipped` and the JVM
   lane falls back to `./gradlew testDebugUnitTest`, yet the script still
   exits `preflight: OK`. Run any Gradle task first, or read the log rather
   than the exit code.

The APK built here is signed by AGP's throwaway debug key (R05 is still
open), so it installs beside rather than over an existing Temper Debug.
No emulator is possible in that environment: no `/dev/kvm`, no `vmx`/`svm`.

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

4. Live test 28 is the current drop (`debugLiveCode` 28). Do not bump
   it again until the next drop. Obtainium, not Studio; gym-floor
   Temper stays on the signed APK.

## What is outstanding

**The Obtainium lane is automatic again.** Hosted `debug-live.yml` had
died in seconds with no runner since 2026-09-05, which stranded live 21
and live 22. That was never a billing problem worth paying to solve:
Actions is free and unmetered on a public repository, and the account had
simply exhausted its private-repo minutes. The repository was made public
on 2026-09-08 after a scan of all 72 commits found no keystore, private
key, API key or token in any of them — the only matches were `printf`
lines reading GitHub secrets and a placeholder in `SETUP.md`.

`debugLiveCode` is 28 (`#195` / `#197`). The Obtainium drop is
`debug-live/2026-09-09-7` (Body facts line plus the cancellation
checker). Live 27 remains `debug-live/2026-09-09-6`. Do not bump 28
on `#181`. A Temper Debug from a throwaway-signed drop must still be
backed up, uninstalled, and reinstalled once onto 25+ (stable signer).
Gym-floor Temper stays on the signed APK.

**R05 is closed as of live test 25.** The four `DEBUG_KEYSTORE_*` secrets
and the `DEBUG_CERT_SHA256` variable were set on 2026-09-09, so
`debug-live.yml` restores one stable keystore and every drop from 25 on
updates a Temper Debug in place instead of installing beside it. The
certificate is `B2:6E:A6:4C:...:E9:12:C3:36`; the workflow fails the drop
if a build is signed by anything else. The one-time cost of the switch is
on the phone, not in the repository: a Temper Debug installed from an
earlier throwaway-signed drop must be backed up, uninstalled and
reinstalled once, per SETUP.md section 6. Dependabot
refuses now on the ignore list, all
closed unmerged: `#125` (AGP 9.3.2), `#126` (play-services-auth 22.0.0),
`#174` (coroutines 1.11.0 — `kotlinx-coroutines-android` was unnamed, so
the kotlin group bundled it with core/test), and `#176` (android-all
17- jar; J3 stays on `15-robolectric-13954326-i7`). `#178` named those
holes, and also ignores `org.robolectric:robolectric` major/minor so
API-36-and-up Robolectric does not sneak in on Java 17. Leave `#173` (setup-gradle 6.3.0; Actions is not the test lane),
`#175` (Robolectric 4.16.1 patch), and `#180` (AGP **8.9.2 → 8.9.3**,
a patch of the signed compileSdk-36 pair — not the 9.x refuse). None
of those merge as a drive-by: each needs a JVM-gated packet and a
ledger update, and AGP 8.9.3 also moves the `aapt2-8.9.2-*` pins in
`tools/check-supply-chain.py`.

**R16 residue.** Production-screen tests exist for History, the activity
composer, live cardio, the activity receipt and Home. Settings, onboarding,
the routine editor, custom week, summary, session detail's delete dialog,
exercise detail and Library still have only isolated-control coverage, and
`AccessibilityMatrix` claims automated evidence for all of them, which
overstates it.

**R18 step one.** Horizon readout and past-block reviews (and Plan's
completed-block review) read `CompletedTraining` from both stores, so a
backdated strength activity counts toward PRs and movers, not only
totals and Records. Exercise detail, the log-time PR badge, and activity
edits are still later steps
([`architecture/completed-training-convergence.md`](architecture/completed-training-convergence.md)).
Open `#181` is rebased on `trunk` after `#197` (cancellation checker;
live 28 drop `debug-live/2026-09-09-7`). History's three error sites
use `ErrorSlot` on this packet. `HistoryViewModelTest` stays at 0
unbounded waits. `check-cancellation.py` is 0 on this packet. Do not
bump `debugLiveCode` (still 28). The four unbounded waits in
`SettingsViewModelTest` remain (baseline 4).

**R17 measurement.** The History catalog is shared and the revision keys are
in place, but the full-history read behind the horizon readout was left alone
deliberately: the finding asks for a measured before and after, and there was
nothing to measure on. Section 5 of the convergence record is the plan.

**Leftover heads.** `claude/read-zip-files-suok0y`,
`debug-live/2026-09-07`, and `claude/cloud-android-env` were deleted
after merge. Do not delete `claude/app-audit-optimization-xnqf5e`.
`claude/file-visibility-check-jraqc2` is an unmerged Claude vehicle
(UX + stub compiler); do not start a second edit of those paths from
`trunk`. `#197` is on `trunk`; `claude/android-verify-my59sw` is leftover
and Claude reuses it — do not start a second edit of those paths from
`trunk` if a new PR appears on that head. `#196`'s vehicle
`claude/google-signin-integration-xijk5e` was deleted after merge.
`debugLiveCode` on trunk is 28; do not bump it on `#181`.

## Rules that bind this work

`.cursor/rules/owner-loop.mdc` is the process authority and
`docs/architecture/` is the decision set. In short: `trunk` is the only
standing line, work happens on a throwaway branch, it lands by squash merge,
and the branch is deleted. The JVM gate merges a packet; the phone gates a
gym-floor release. Do not treat hosted runners as the test lane.

Every commit ends with the co-author and session trailers the session's own
instructions specify. Ratchets live in `tools/checker-baselines.toml` and may
fall but never rise.
