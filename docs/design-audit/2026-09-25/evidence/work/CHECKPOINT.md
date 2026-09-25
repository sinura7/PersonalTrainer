# Audit X6 checkpoint — written 25 Sep 2026 ~02:25 UTC after a session-limit cut

Context: the six verifiers (V1–V6) and the harness author (H) were terminated by
"session limit · resets 3:50am (UTC)". Nothing they had written survived except
what is listed below. The lead session may also be cut. Resume at/after 03:55 UTC.

## State

- Plan: /root/.claude/plans/snug-toasting-charm.md (approved). Owner decisions:
  Supabase project reachable from the session ("Taskbot") is NOT Temper's → no
  server call ever; commit ~40 curated frames; push branch, offer PR, do not open.
- Phase A baseline: DONE. Numbers in baseline/SUMMARY.md and already written to
  docs/design-audit/2026-09-25/baseline.md (render-matrix section still to fill).
  Key: 3,149 tests / 0 failures; lint 0/0; all 7 coverage floors hold;
  full preflight fails on 4 files; assembleRelease FAILS in R8 (slf4j).
- Phase B: DONE. 19 reports in passes/B*.md; merged tables in triage/merged.md
  (60 P1/P2 rows, 194 P3 rows, 207 prior-status rows). No pass rated a P1.
- Phase A2 harness: run 1 produced 54 frames (6 screens) then the hang-watchdog
  killed the worker (quiet waitUntil). H diagnosed: `compose.waitUntil` under
  Robolectric does not drain the main looper; fix = a wait loop that idles the
  looper on every poll (e.g. `Shadows.shadowOf(Looper.getMainLooper()).idle()` /
  `compose.waitForIdle()` inside the loop), cap wait 8 s, never fail the method,
  write never-ready.txt and manifest.csv. H did NOT get to write the fix.
  Harness source: harness/AuditRenderTest.kt (1,217 lines, compiles). The copy
  under app/src/test/java/com/sinura/personaltrainer/ui/AuditRenderTest.kt is
  UNTRACKED and must be deleted before the final commit. Run 2 must go WITHOUT
  tools/hang-watchdog.sh: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew
  -PskipStaticChecks testDebugUnitTest --tests 'com.sinura.personaltrainer.ui.Audit*'`
  (use setsid/nohup; may take 20–40 min).
- Phase C verification: NOT STARTED effectively (all six died within ~10 min).
  Briefs: verify/VERIFY.md; cluster assignments are in the lead transcript, and
  restated below. Relaunch V1–V6 with the same prompts (see verify/CLUSTERS.md).
- Draft: draft/since-22-sep.md (the record's "what changed" section, complete).
- Owner decisions log: briefs/owner-decisions.md.

## Next steps, in order

1. Relaunch V1–V6 (verify/CLUSTERS.md has each cluster's claim list). One probe
   run after they finish: copy probes/*.kt to app/src/test/java/com/sinura/personaltrainer/audit/,
   run `--tests '*AuditProbe*'` (no watchdog), record, delete.
2. Fix + rerun the harness (see above); curate ≤ 40 frames into evidence/;
   write manifest into baseline.md; send frame dirs to B12 (§7) and the
   frontend passes for a short review round (optional if time is short).
3. Phase D: write AUDIT.md (owner order), backend.md, frontend.md,
   architecture-and-quality.md, verification.md; move this work/ folder OUT of
   the tree (it is a checkpoint only; evidence/ is excluded from the link check
   but the folder must not ship).
4. Phase E: pointer edits (docs/AUDIT.md banner, HANDOFF-NEXT 3-6 & 39-45,
   FRONTEND_REDESIGN pointer paragraph, README line 43); check-doc-authority 0;
   PT_STATIC_ONLY preflight OK.
5. Phase F: delete app/src/test/.../AuditRenderTest.kt and audit/ probes; merge
   origin/trunk; commit "(audit X6)" with Gate: line + trailers; push.
