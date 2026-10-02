# Pre-merge research — P1 / P2a / P2b (29 September plan)

**Date:** 2 October 2026  
**Author:** Cursor cloud agent (research only — no merges, no Obtainium drop)  
**Plan:** `docs/owner-eight-plan-2026-09-29.md` ( lands with **#444**; not on `trunk` until P1 merges)  
**Open PRs:** [#444 P1](https://github.com/sinura7/PersonalTrainer/pull/444), [#445 P2a](https://github.com/sinura7/PersonalTrainer/pull/445), [#446 P2b](https://github.com/sinura7/PersonalTrainer/pull/446)  
**`trunk` since PR bases were cut:** P3 menu icons **#453** merged (`debugLiveCode` **107** already on `trunk`).

---

## Executive summary

| Packet | Intent | JVM / CI (as reported) | Rebase vs `trunk` | Merge readiness |
|--------|--------|------------------------|-------------------|-----------------|
| **P1 #444** | Section panels on the workout floor + global `SectionEdge` on cards/lists | GitHub CI green; local full gate: 1 unrelated failure (`CompletedTrainingParityTest` timezone/host) | **Required** — `mergeable_state: dirty`; trial rebase hit **`docs/ROADMAP.md`** conflict; `GymSurfaces.kt` auto-merged with P3 | **Fix-first:** rebase onto current `trunk`, resolve ROADMAP, re-run gate; do **not** bump `debugLiveCode` again (107 taken by P3) |
| **P2a #445** | Working sets require effort (RPE); warm-ups & holds exempt | GitHub CI green | **Required**; overlaps P1 on `RpeSelector.kt`, `FloorTestKit.kt`, docs | **Fix-first:** merge **after P1** (or rebase onto post-P1 `trunk`); expect manual merge on RpeSelector/docs |
| **P2b #446** | 5s GET READY before hold clock; 3/5/10 in Rest timer settings | GitHub CI green; stacked on P2a | **Required**; merge **445** first, then rebase 446 | **Fix-first:** same rebase stack as P2a; P2b-only diff is ~31 files / ~530 lines once P2a is out of the way |

**Recommended merge sequence:** **P1 → P2a → P2b**, each rebased to green gate on current `trunk`, one Temper Debug drop per owner checklist (likely **108+** after P3 consumed 107).

---

## 1. Live workout workflow today (`trunk`)

**Prepare → log → rest → next set → timed hold** (plain path):

1. **Prepare:** User opens an active workout. Header shows lift hero (image, name, Working/Warm-up). Stats row (Last / Best / Volume), weight-reps entry (or hold seconds), optional warm-up ramp, **Effort · optional** chips, coach **Next set** card, set history strip. Blocks separated by **hairline dividers** on `Pit` (no filled frames).
2. **Log working set:** Tap **Log** → validation (`SetLogRules`) → save → coach micro-recompute → **auto rest** if not warm-up and not last planned set (`RestTimer.shouldStartAfterLog`, ADR-012 d.18).
3. **Rest:** Dock shows `RestTimerCard` (idle kicker visible when not running — Allen’s idle rest). Skip / edit / open rest page. Landscape may hide idle rest via `LandscapeChrome.hideIdleRest`.
4. **Next set:** Entry may follow suggestion (W2e: effort pick does **not** count as “typed” on `trunk` — P2a changes that).
5. **Timed hold (plank):** User sets seconds, taps **Log** → hold starts **immediately** (`FloorWorkClocks.startHold`), dock shows hold bar; **no RPE required** on `trunk`. No GET READY phase.

**What P3 on `trunk` already changed (orthogonal):** Leading icons on all `InstrumentMenu` rows (#453). Touches `GymSurfaces.kt` — the same file P1 edits for `FloorSection` and `SectionEdge`.

---

## 2. Per-packet diffs (vs PR bases’ `trunk` at `af4e3fcc`)

### P1 #444 — Section borders (`ccr-1cb8377d-ekl6ii`)

**17 files, +1249 / −54**

| Area | Files / surface |
|------|-----------------|
| **Tokens** | `Color.kt` — `SectionEdge` (white @ 22%); `TokenIdentityTest` |
| **Global chrome** | `GymSurfaces.kt` — `GymCard`, `GroupedList` borders → `SectionEdge`; new **`FloorSection`** composable (Surface1 fill + edge) |
| **Workout floor UX** | `ActiveWorkoutScreen.kt` — stats, entry, effort, Next set, history each wrapped in `FloorSection`; block gap 12→8 dp; test tags `SECTION_*` |
| **Layout fixes (audit)** | `ExerciseStatsRow.kt`, `RpeSelector.kt` (chip row slack 8→4 dp), `NextSetRecommendation.kt` (compact card stacks at 2.0 font), `RestTimerCard.kt`, `LiftCard.kt`, `SetEntryPanel.kt` |
| **Tests** | New `FloorSectionEdgesRenderTest` (stroke/fill @ 1.0/1.6/2.0, 360 dp); `WorkoutFloorRenderTest` loop budget **868→852 dp**; `FloorTestKit.twoWorkingSetsLogged()` |
| **Docs** | **`owner-eight-plan-2026-09-29.md` (776 lines)**, FRONTEND_REDESIGN 12h, ROADMAP, HANDOFF-NEXT |

**Strengths**

- Earn-the-table rationale is explicit: hairlines vanished in daylight; **fill + edge** reads as panels, not form groups.
- Height budget re-measured; audit regressions (wrapped stats, ramp captions, 3+2 RPE chips, clipped Next-set numbers) addressed with render tests.
- `SectionEdge` applied app-wide so Home/History/Settings match the floor (G9).

**Weaknesses / clarity risks**

- **Visual density:** Five framed blocks plus dock competes with Log/Rest; mitigated by tighter 8 dp gaps but **phone proof under bright light** is mandatory (plan drop 107 checklist).
- **Hero unframed** (by design): header + Working/Warm-up stay outside panels — correct for hierarchy; confirm with owner (plan Q4).
- **Still owed (plan §8):** Home `DayBlock` render @ `SectionEdge`; 600 dp width pass.
- **PR body stale:** Says post-merge `debugLiveCode` bump for drop **107** — **`trunk` already at 107** after P3; next ship needs **`debug-drop-plan.py`** (likely **108**).
- **Dark/light:** `SectionEdge` is decorative white alpha on dark `Pit`; render tests cover large font, not a dedicated light-theme floor pass (rely on token ADR-023 “not colour alone”).

**Merge readiness:** **Fix-first** — rebase onto `trunk` (ROADMAP conflict confirmed in trial rebase); re-run `./gradlew testDebugUnitTest assembleDebug lintDebug`.

---

### P2a #445 — Effort required (`cursor/p2a-effort-required-b87f`)

**36 files, +355 / −104** (does **not** include P1 UI)

| Area | Files / surface |
|------|-----------------|
| **Domain rule** | `SetLogRules.requiresEffort` / `validateEffort` — working sets only |
| **Write paths** | `ActiveWorkoutViewModel.logSetWithDuration` refuses missing effort; `SessionDetailViewModel` update/add same rule |
| **Copy / a11y** | `RpeCopy` — `Effort` (not “· optional”); `AccessibilityMatrix`; ADR-026 d.3, ADR-029 W2e note amended |
| **W2e behaviour** | `setRpe` no longer calls `markDraftDirty()` — effort must not block suggestion follow after log |
| **Tests** | `SetLogRulesTest`; VM + History refusal; **51 call sites** pick effort via `FloorTestKit.pickEffortIfNeeded()` / `logWorkingSet()` |
| **Instrumented** | Journey tests updated to pick effort before log |

**Strengths**

- Single gate on **`logSetWithDuration`** — floor, holds, warm-ups, History align with domain rule (G4).
- Exemptions match coach evidence model (ADR-008): warm-ups/holds don’t feed effort-based rules.
- Reversal of W1b is documented in ADR-026 with owner date.

**Weaknesses / Allen-bar gaps**

- **Plan vs shipped UX:** Plan §P2a step 1 describes **disabled-reason on commit + effort track pulse**; shipped code **keeps Log enabled** (`canLog` unchanged) and **refuses on tap** via `error.fail` + reject haptic — no pulse found in P2a diff. Phone check still works but **feedback is easier to miss** than a disabled Log with reason.
- **Copy mismatch:** `SetLogRules.EFFORT_MISSING` vs plan’s shorter `LogCommitCopy.EFFORT_MISSING` — verify what the user actually reads on reject.
- **Product consequence (named in plan):** “Another in you” / shorter in-tank rests fire **more often** — owner should expect snappier rests after honest RPE 8–9 sets.
- **Open Q1:** No “Easy = 5” scale step — required effort without an easy-out for light sets.

**Merge readiness:** **Fix-first** — rebase after P1; resolve **`RpeSelector.kt`** (P1 chip layout + P2a copy) and **`FloorTestKit.kt`**.

---

### P2b #446 — GET READY lead-in (`cursor/p2b-lead-in-b87f`)

**60 files vs old `trunk` (+886 / −131)** — includes **all of P2a**; **31 files** P2b-only vs P2a tip.

| Area | Files / surface |
|------|-----------------|
| **Model** | `HoldWork.kt` — lead-in deadlines; `FloorTimedMode.HOLD_LEAD_IN` outranks hold/rest |
| **Clocks** | `FloorWorkClocks.startHold(..., leadInSeconds)`; cues `LeadInTick` / `LeadInDone` |
| **Dock** | `WorkoutDock` → `SetWorkDock` with GET READY kicker; **Cancel** in stop slot; primary **`GET_READY`** disabled |
| **Safety** | `FloorTimerSurface.durationToLog` null in lead-in — cannot log 1s plank mid-countdown |
| **Persistence** | `SavedStateFloorTimer` — `timer.hold.leadInStartMs`; reboot guard updated |
| **Settings** | `RestTimerPrefsSection` — 3/5/10 chips; pref in `RestPrefsStore` |
| **Haptics** | `ActiveWorkoutScreen` — light tick each second, warn on hand-over (Q9 still open in plan) |
| **Tests** | `HoldWorkTest`, `FloorTimedModeTest`, clock characterisation, `SetWorkDockRenderTest`, saved state, backup round-trip |

**Strengths**

- Clear phase separation: lead-in **replaces** rest card in dock (`HOLD_LEAD_IN` → `SetWorkDock`, not `RestTimerCard`) — avoids rest/hold competition.
- Cancel is one obvious control; lock-screen behaviour restores phase (ticks foreground-only — honest).
- Hold logs still **RPE-exempt** (pairs correctly with P2a).

**Weaknesses**

- **Extra seconds every hold** — earns its place for planks/hangs; verify it does not annoy on repeated short holds.
- **Plan doc inconsistency:** Plan P2b step 7 says backup carries lead-in; PR body says **device-local** — code/tests (`BackupV2RoundTripTest`) assert lead-in **stays on phone** after restore (not overwritten by backup) — align docs to **device-local**.
- **Stacked PR noise:** Diff vs `trunk` is inflated until #445 merges.

**Merge readiness:** **Fix-first** — merge P2a first; rebase P2b; full gate.

---

## 3. Cross-PR conflicts and merge order

```
trunk (today: P3, debugLiveCode 107)
  └─ P1 #444  ──►  P2a #445  ──►  P2b #446
```

| Pair | Overlapping paths | merge-tree / trial |
|------|-------------------|-------------------|
| P1 ∩ P2a | `RpeSelector.kt`, `FloorTestKit.kt`, FRONTEND_REDESIGN, HANDOFF-NEXT, ROADMAP | Both sides changed; auto-merge may work for Kotlin if P1 merges first |
| P1 ∩ P2b-only | `ActiveWorkoutScreen.kt` (P1 layout vs P2b haptics/wiring), docs | Sequential merge after P1 |
| P1 ∩ `trunk` | `GymSurfaces.kt`, `ROADMAP.md` | Trial rebase: **ROADMAP conflict** |
| P2a ∩ P2b | P2b branch contains P2a | Merge **445** before **446** |

**Rebase debt:** All three PRs show **`mergeable_state: dirty`** (base `af4e3fcc`); **`trunk` is +2 commits** (`P3` + debug 107 bump).

---

## 4. CI / JVM gate

| PR | GitHub `Tests, lint, debug build` | Instrumented smoke |
|----|-----------------------------------|--------------------|
| #444 | Success (run 36559903021) | Non-blocking success |
| #445 | Success (duplicate runs green) | Non-blocking success |
| #446 | Success | Non-blocking success |

**Cursor VM re-run (2 Oct 2026):** Full `testDebugUnitTest` on P1 and P2b branches: **3529 tests, 1 failure** — `CompletedTrainingParityTest.chronologyAppearsOnceOnTheLocalDate` (same on partial `trunk` run). Treat as **host/timezone**, not packet regression, but **re-run gate after rebase** before merge.

**Test gaps vs plan phone checklists**

| Criterion | Automated | Phone-only |
|-----------|-----------|------------|
| G9 floor panels @ 1.6/2.0 | `FloorSectionEdgesRenderTest` | Bright light, half brightness, Home/Settings edges |
| G4 effort gate | VM refusal tests | Tap Log without effort; History correction |
| G5 GET READY | Clock + render tests | Lock mid-countdown; 3s pref; Cancel |
| G12 auto rest after working set | Existing VM tests | Confirm still true **after P1** density change |

---

## 5. Product / ADR fit

| ADR / rule | P1 | P2a | P2b |
|------------|----|-----|-----|
| **ADR-012** rest | No change to auto-rest | No change | Lead-in hides rest card — OK |
| **ADR-008** coach evidence | — | **Aligns** — working sets carry RPE | Holds exempt |
| **ADR-026** frontend | Row 12h panels | **Amends d.3** effort required | Lead-in copy row 12j |
| **ADR-029** W2e | — | Effort not “typed” for follow | — |
| **W1b #393** | — | **Supersedes** optional effort | — |
| **ADR-023** reduced motion | — | Plan pulse if reject — **not implemented** | Haptics each second (Q9 open) |

---

## 6. Regression watchlist (Allen)

| Risk | Assessment |
|------|------------|
| **Bodyweight hero / 0 kg working sets** | Unchanged in these PRs; `SetLogRules` bodyweight logic untouched |
| **InstrumentMenu icons (#453 on `trunk`)** | Merge P1 after P3 — verify `GymSurfaces` menus still show icons + TalkBack once |
| **Coach Next card** | P1 changes compact layout at 2.0 font — re-check Apply/Why? |
| **Idle rest visible** | P2b only swaps dock to hold bar during GET READY/hold; **REST_IDLE unchanged** on `trunk` path |
| **Skip/swap overflow** | Not touched |
| **Log/Rest competition** | P1 adds frames — watch dock clearance (`LOOP_BUDGET_DP` 852) |

---

## 7. Earn-the-table verdict (Allen rule)

| Change | Verdict |
|--------|---------|
| **Section panels (P1)** | **Conditional yes** — solves real legibility failure (hairlines invisible); cost is vertical density — **phone under gym lighting** decides |
| **Forced effort (P2a)** | **Yes for coach product** — owner explicitly traded taps for history; improve **on-tap feedback** (disabled reason or pulse) to meet plan clarity |
| **GET READY (P2b)** | **Yes for holds** — prevents accidental 1s logs; Cancel + disabled Log during countdown is clean; confirm haptics with owner (Q9) |

**Violations / near-misses**

- P2a: **Log stays enabled** without effort — user discovers rule by failure, not prevention (plan promised clearer commit state).
- P1 PR: **drop 107** language conflicts with **P3 already on `trunk`**.
- P2b: **doc drift** on backup vs device-local lead-in pref.

---

## 8. Recommended actions before merge

1. **Rebase #444** onto current `trunk`; fix `ROADMAP.md`; run full JVM gate.
2. **Phone-check P1 alone** (checklist §P1) on Temper Debug **after** P1 merge — panels, 2.0 font chips, auto-rest (G12), global card edges.
3. **Rebase #445** onto post-P1 `trunk`; merge; phone-check P2a checklist.
4. **Rebase #446** onto post-P2a `trunk`; merge; phone-check P2b checklist.
5. **One debug drop per merge** using `python3 tools/debug-drop-plan.py` (do not reuse 107).
6. **Optional fix in P2a before merge:** wire effort into `canLog` or `LogCommitCopy.disabledReason` + pulse (plan step 1) — owner call if reject-only is acceptable.

---

## 9. Obtainium phone scripts (after each merge)

### After P1 merge

1. Update Temper Debug from new pre-release.
2. Settings → display size default and **largest**; start any workout with 3+ working sets planned.
3. **Bright light / ~50% brightness:** each floor block reads as its own panel; stats and warm-up captions **one line** at 360 dp width.
4. **Largest text:** five effort chips **one row**; Next-set compact card numbers not clipped.
5. Home + Settings: cards/lists show same outline as floor.
6. Log a **non-final working set** → rest starts automatically (G12).
7. TalkBack: section headings still **single label** (no double “Effort” + border noise).

### After P2a merge

1. Working set: tap **Log** without effort → **refused**, message visible, reject feel.
2. Pick effort → Log → saved; History shows RPE.
3. **Warm-up** logs without effort.
4. **Plank/hold** logs without effort (on `trunk` hold path; full GET READY after P2b).
5. History: edit working set, clear effort, save → **refused**.
6. Log RPE 8–9 set → note if rest feels **shorter** (“in tank”) vs before.

### After P2b merge

1. Plank: tap Log → **GET READY 5…1** (ticks), then hold clock.
2. **Cancel** during GET READY → back to entry, nothing logged.
3. Tap primary during GET READY → **nothing logged**.
4. **Lock phone** mid-countdown → unlock → correct remaining phase.
5. Rest timer settings: **3 s** and **10 s** lead-in change countdown.
6. Working set before plank still requires effort (P2a + hold exempt).

---

## 10. Hold recommendation

| Packet | Merge now? |
|--------|------------|
| P1 | **No — fix-first** (rebase + ROADMAP + phone P1 checklist) |
| P2a | **No — fix-first** (after P1; consider UX parity with plan) |
| P2b | **No — fix-first** (after P2a) |

**Do not hold indefinitely** — work is CI-green on original bases and plan-aligned; **blockers are rebase hygiene, drop numbering, and phone proof**, not architectural disagreement.

---

*End of report.*
