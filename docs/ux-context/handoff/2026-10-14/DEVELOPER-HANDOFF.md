# Temper developer continuation

## Source recovery

Repository: https://github.com/sinura7/PersonalTrainer

The development work is integrated in `trunk` through `eea668ce5105a5f4c4058b2d4aad35991416a174`. Release-preparation branch `codex/ux-core-debug-126` is pushed at `158f492b552e839c7f5ee1cd27e1ef464980d7ff`; draft PR 505 remains open. The separate six dependency PRs are not part of this delivery.

Online continuation:

```powershell
git clone https://github.com/sinura7/PersonalTrainer.git Temper
Set-Location Temper
git switch trunk
git log -1 --format="%H %s"
```

Optional recovery using the separate offline package supplied by the owner (the bundle is not included in this GitHub directory):

```powershell
git clone -b trunk ./temper-source.bundle Temper
Set-Location Temper
git remote set-url origin https://github.com/sinura7/PersonalTrainer.git
git fetch origin
```

That offline bundle contains the release branch as well as trunk. Check its listed heads before restoring another ref. Do not reset or clean the existing owner checkout: its local changes are unrelated to this handoff and have not been staged, stashed or removed.

## Required reading and evidence

Use existing records rather than a second backlog:

- In this handoff, read `PLAN-RECONCILIATION.md` and `ACCEPTANCE-CHECKLIST.md` first. They separate the original requirement, integrated implementation, bounded evidence and remaining acceptance. `SOURCE-INDEX.md` and the JSON checklist preserve access to the exact existing records.
- `AGENTS.md`, `docs/FOUNDATION_PROGRAM.md` and applicable accepted decisions in `docs/architecture/`.
- `docs/DEVELOPMENT.md` for actual verification, including Windows database-test limitations and ADR 024's hosted deterministic gate.
- `SETUP.md` for signing/distribution.
- ADR 026 for frontend contracts, ADR 032 for evidence lanes and ADR 033 for private durable drafts.
- `docs/ux-context/README.md` and `docs/ux-program/UX-Backlog.json` for current coverage.
- `docs/ux-context/studies/q01/p01-baseline-2026-10-12.json` through `p10-library-context-2026-10-14.json` for problems, implementation, original failures, reviews, native screenshots and receipts.

Tracked concepts/screenshots are available through the source records and offline bundle. Older raw run logs name the original managed checkout; those archives remain in the separate offline handoff package and can be extracted elsewhere. They are not uploaded with this documentation PR. The original managed checkout retains additional ignored evidence; do not delete it before transfer acceptance.

## Last verified integrated packet

P10R source candidate: `dccd7f4eae96149f57967beb95e56ea4cf609e92`.
Receipt-only candidate: `0178df9c77c5e1d3ce9da3853ea1ca7f943db9db`.
PR 504 squash merge: `eea668ce5105a5f4c4058b2d4aad35991416a174`.
Candidate and merge tree: `0645a75ad2122fd8f05c1c2ce05cb58af5201987`.

- `picker-recovery/final64`: complete fresh gate passed **4,700 app tests, 631 fresh XML files and 1,715 standalone tests**, zero failures/errors/skips; static checks, lint and required builds passed. Source and refs stayed unchanged across 1,678 hashed execution inputs.
- Final independent/adversarial source reviews: zero unresolved Critical/High/Medium findings. Review reports and hash seals are archived.
- `candidate69-receipts` and `integrated71-gate`: complete invocations passed using verified identical execution inputs and intact archived outputs. **Zero new JVM/standalone tests**; 4,700/1,715 results explicitly reused. Do not report these as fresh executions.
- `merged72-swap`: one fresh actual MainActivity/AppNav normal Swap passed at the exact merge with unchanged input/APK proofs.
- Both P10R hosted deterministic jobs and both supplemental emulator jobs passed before merge.
- `native65-swap` remains the failed combined run described in the implementation handoff. Later passes do not erase it.

The separate offline package contains the P10R evidence archive (3,700 files), integration archive (1,313 files), P07–P09 and P10 core archives, including original failed/aborted attempts. Their receipts contain hashes and per-file verification. These binary archives are not uploaded with this documentation PR. No new app tests were run during handoff.

## Interrupted Debug 126 release packet

The fresh planner had found code 125 already shipped and recommended 126; the suffix `2026-10-14` was free at that moment. This does not reserve the suffix or make it safe to publish later without replanning.

The version branch changes four files: `app/build.gradle.kts` plus three existing UX documentation files. Both clean source reviews passed with zero findings. Review seals bind to source `158f492b`.

The local `drop-a-version01` gate began at 2026-10-14 21:36 UTC. Preflight and **1,715 standalone tests passed**. Android compilation/static checks progressed into `testDebugUnitTest`; the owner then requested immediate stop. The wrapper was terminated. **No complete local gate, final app test count, final lint pass or final APK acceptance is claimed.** Existing XML from earlier gates must not be mistaken for fresh results. `STOP-STATE.json` records the cancellation; the offline package retains the partial logs.

GitHub runs `38641525755` and `38641515310` were cancelled during cleanup. The PR run had passed static checks, its unit-test step and supplemental emulator smoke before cancellation; lint was cancelled and later build steps skipped. The whole hosted gate did not pass. Raw job status is retained in the separate offline package; the cancelled run IDs also resolve through GitHub Actions.

PR 505 is draft and must remain unmerged until its remaining required checks are satisfied by the next developer. No automatic publishing action remains running.

## First actions for the next developer

1. Verify the pinned GitHub source and read the stop-state records. If using the separate offline package, verify its bundle/manifest too. Decide whether to resume the existing Debug 126 packet or revise the release plan with the owner.
2. If resuming unchanged release preparation, use a unique evidence directory and execute a fresh complete gate; do not reuse the interrupted attempt as a pass. On Windows the supported wrapper is:

   ```powershell
   ./tools/dev-windows.ps1 testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest
   ```

   Also execute `tools/preflight.sh` through the configured Git shell. The ignored archived `run-picker-recovery.ps1` helper can record input hashes/results, but read it first; its defaults name the old packet branch. The complete successful P10R run took about 38 minutes on this host. Avoid repeated broad runs after unchanged inputs have already passed.

3. Resolve actual failures, retain their original evidence, and update the PR with the real outcome. Verify exact merge inputs after integration.
4. For an authorized Drop A, run `tools/debug-drop-plan.py` freshly. Merge the verified version packet, run the planner again, then publish the **exact merge SHA** through the established `debug-live/<free suffix>` push workflow. `workflow_dispatch` is artifact-only and does not publish.
5. Independently download the release APK and verify package/version, source tag, hash and stable signing certificate. Record successful delivery before calling Drop A complete.
6. Continue P11–P19 sequentially and P20 afterward. Retain working existing behavior. Do not restart an app-wide audit or rebuild completed P01–P10 work.

## Delivery identity

Latest verified release: `debug-live-2026-10-12`, source `587e9577ee30f1962fa0a6db1a6acd2aad0b642c`.

- Package: `com.sinura.personaltrainer.debug`.
- Version: 125 / `1.0.0+debug.125`.
- APK SHA-256: `57444B1078AC393143614B60E1CD5FF9EB383B90193CAAE3C571838C260752CE`.
- Stable certificate SHA-256: `B26EA64C9E3E2CC44063DEE3D1892E8557CEA2D25E71DE05ABCD754DE912C336`.

The owner last reported using Debug 124; do not infer that 125 was installed. Local Debug builds can use another signing identity. Never install one over the Obtainium distribution without checking the signer. Signing secrets are not in the transfer.

The prepared verifier `verify-temper-debug.ps1` remains in the offline stopped-release evidence, not this documentation PR. It checks a published prerelease's exact source tag, package/version, SHA-256 asset digest and stable signer. Its prepared existence is not proof Debug 126 was published or verified.

## Host cleanup and recovery limits

Task-owned gate processes, preview servers and the named disposable emulator were stopped. Shared Gradle daemons and unrelated apps were not forcibly terminated. Completed review agents require no running work. No destructive cleanup was applied to owner data or unrelated local changes.

The managed source checkout remains clean and recoverable. Build caches and extra ignored evidence remain in place intentionally; deleting them before transfer acceptance would risk losing useful receipts. This is a handoff, not programme completion.
