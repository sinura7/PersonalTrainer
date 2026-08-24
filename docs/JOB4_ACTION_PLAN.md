# Job 4 action plan — the coach keeps its word

> **Banner (24 Aug 2026).** Code-done historical job. “Room v3 won’t” in
> this file is **superseded** for the one Phase 5 cutover
> ([ADR-010](architecture/ADR-010-schema-reset-migrations.md)). Current
> program: [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md).

Living plan. **Not the law.** If a clearer move shows up, take it, write it
under *Floor findings*, strike the old line. Same method as
[JOB3_ACTION_PLAN.md](JOB3_ACTION_PLAN.md).

Status: **done** · **next** · *later* · **won't**

---

## What this is

Jobs 1–4 are **code-done on `trunk`**. The week can be generated, replayed,
and marked lighter. The card tap marks it. Home replay matches Plan.
Phone gates are still the owner's.

Job 4 is the honesty pass those packets left open. The coach can already
see overreaching. The Tune chip can already mark the week. Home can already
arm Plan. The remaining lies are *where the tap is*:

> The card says schedule a lighter week. The tap should mark it. Home says
> the week is empty. If my routines are still here, the tap should put
> them back — I should not have to discover Plan first.

That is the whole product. Everything below is how we do it without a
second volt, without a fifth tab, and without Room v3.

---

## What is already true (do not rediscover)

1. **The Tune chip already writes the marker.** `PlanViewModel.setLighterWeek`
   → `lighter_week_start_epoch_day`. Progression HOLD is Job 3 / P2. Do not
   rebuild the marker. Wire the card to the same key.
2. **The deload card is Body-only (D3).** Home folds recommendations into
   `nextSessionReason`. It does not dispatch taps. Do not put a second
   "Schedule a lighter week" button on Home.
3. **`hasDestination` is why the card is flat.** `deload-volume-flat-strength`
   has `action == null`. The old Volt "Show on the map →" cleared the
   selection. Restoring a map arrow is the wrong fix. The destination is
   *the marker*, not the silhouette.
4. **Replay already exists.** `PlanViewModel.replayStoredAnswers` +
   `ExistingLayoutMatcher` + `WeekTwoCopy`. Home's empty hero still says
   **Suggest a week** even when routines exist. That was Job 3's split
   (Home = one door). The floor disagrees: they land on Home.
5. **`pendingWeekSuggestion` is the deep-link idiom.** App-scoped
   `MutableStateFlow<Boolean>`, written on Home, consumed once on Plan
   *before* the work runs. Replay uses the same shape. Do not invent a
   nav argument.
6. **Progress observes insights with `includeWeekPlan = false`.** Do not
   turn the plan on to mark a week. `LighterWeek.weekStartEpochDay(today,
   prefs.weekStart)` is enough.
7. **Restore-while-live, "+5.5 lbs", editor empty CTA, per-lift
   `landingCopy`, Repeat vs Resume — all already true in code.** ROADMAP
   still lists "+5.5 lbs" as open. That is a doc lie. Strike it in P0.
8. **Four tabs. One volt. Room v2. Debug is `.debug`.**

---

## Binding rules

These stay signed.

- Four tabs. Library stays pushed. No LLM. No head-level anatomy.
- Room **version 2**. Additive DataStore / backup fields only. No `3.json`.
- Volt = live / act only. One filled button per screen.
- Suggest stays the heat/planner path. Replay never calls `apply()`.
- Lighter week is a marker, not a mesocycle. Do not auto-scale sets.
- D3 surface map: Body holds the full cards. Home does not grow a
  recommendation list.
- Do not run `connectedDebugAndroidTest` on the release `applicationId`.
- Drive folder and package stay as they are.

---

## Architecture we already have (do not rebuild)

| Piece | Where | Job 4 use |
|---|---|---|
| Marker | `PreferencesRepository.lighterWeekStartEpochDay` | Card tap writes this. |
| Week math | `LighterWeek.weekStartEpochDay` | Progress has no `weekPlan`. Use today + prefs. |
| Card dest | `TrainingRecommendation.hasDestination` | New action `MARK_LIGHTER_WEEK` is always a dest. |
| Dispatch | `dispatchRecommendation` | New callback `onMarkLighterWeek`. Label from the same `when`. |
| Replay | `PlanViewModel.replayStoredAnswers` | Home arms it. Plan consumes it. |
| Deep link | `pendingWeekSuggestion` | Twin: `pendingAnswerReplay`. |
| Copy | `WeekTwoCopy` | Home uses the same volt + caption. |
| Fake graph | `FakeAppDependencies` | Tests get the new flag. |

If a packet can be an enum value plus a pref write, that is the whole
packet. Do not add a service.

---

## Better idea than the first instinct (take this)

The first instinct is "navigate to Plan and highlight Tune." That is a
treasure hunt. They tapped **Schedule a lighter week**. The tap should
schedule it. Stay on Body. The strip on Plan/Home will say so the next
time they look.

The first instinct for Home is "keep Suggest as the volt, add a quiet
replay." Job 3 did that on Plan because replay is the recovery when
routines exist. Home should **match Plan**, not invert it. One volt:
**Use my answers again**. Quiet Suggest stays the heat path. Empty + no
routines stays Suggest — there is nothing to replay.

---

## Packets

### P0 — Plan + stale ROADMAP · **done** (merged)

**Goal.** A stranger can name the four remaining packets. ROADMAP stops
claiming "+5.5 lbs" is open.

**Work**

- This file.
- [ROADMAP.md](ROADMAP.md): Job 4 pointer. Strike the increment row
  (`IncrementTable.STEP_LBS` is 5 lbs; `IncrementAndDefaultsTest` already
  locks it). Phase 7 bodyweight leftover is closed (Settings caption +
  `SetWork` split).
- [UX_PAGE_PASS.md](UX_PAGE_PASS.md): deload card tap is Job 4 / P1, not
  "do not open." Home empty-week volt will change in P2 when routines
  exist.

**Gate.** Reading ROADMAP + this file names P1–P4 and what they must not do.

**Won't.** Kotlin. Gradle.

---

### P1 — Deload card marks this week · **done** (merged · phone pending)

**Goal.** Body card `deload-volume-flat-strength` is tappable. The tap
writes the same key Tune writes. Sets are not scaled.

**Work**

```
RecommendationAction.MARK_LIGHTER_WEEK
hasDestination = true          // always — the dest is the marker
actionLabel    = "Mark this week lighter"
```

`deloadSignal` sets `action = MARK_LIGHTER_WEEK`.
`ProgressViewModel.markLighterWeek()` reads `schedulePreferences.weekStart`,
computes `LighterWeek.weekStartEpochDay(LocalDate.now(), weekStart)`,
writes `setLighterWeekStartEpochDay`. Idempotent if already marked.
Does **not** turn `includeWeekPlan` on.

`dispatchRecommendation` gains `onMarkLighterWeek`. Body wires it.
Every `when (RecommendationAction)` gets the new branch
(`tools/check-when-exhaustive.py` will fail if one is missed).

**Tests.** `RecommendationEngineTest`: the deload card `hasDestination`,
action is `MARK_LIGHTER_WEEK`. `hasDestination` for the new action with
no muscle. Rest-all-productive stays destination-less.

**Gate.** JVM + `assembleDebug`. Tapping the card in a unit test (or
calling `markLighterWeek`) leaves `lighterWeekStartEpochDay` equal to
this week's start.

**Won't.** Navigate to Plan. A confirm dialog. Unmark-on-second-tap
(Tune still toggles). Auto-scaled sets. A Home recommendation list.

---

### P2 — Home replay when routines exist · **done** (merged · phone pending)

**Goal.** Empty week + routines on the phone → Home's volt is
**Use my answers again**. Quiet Suggest. Same confirm on Plan
(**Use this week**). Nothing created. Nothing pinned until confirm.

**Work**

- `AppDependencies.pendingAnswerReplay: MutableStateFlow<Boolean>`
  (same idiom as `pendingWeekSuggestion`).
- `HomeViewModel.requestAnswerReplay()` arms it and Home navigates to Plan.
- `PlanViewModel.init` consumes it once, then `replayStoredAnswers()`.
- `ThisWeekCard`: `hasRoutines` + `onReplayAnswers`. When `!hasPlan &&
  hasRoutines` → volt `WeekTwoCopy.VOLT`, caption `WeekTwoCopy.CAPTION`,
  quiet "Suggest a week". When `!hasPlan && !hasRoutines` → today's Suggest.
- `FakeAppDependencies` grows the flag.

**Deviation from Job 3.** Job 3 left Home as Suggest-only. The floor
finding: they open Home, not Plan. Record it here. One volt still.

**Tests.** `HomeViewModelTest` or a focused card-state test is optional;
the VM method only writes a flag. `PlanViewModelTest` already locks
replay. Add: arming `pendingAnswerReplay` from a constructed
`PlanViewModel` produces proposals without creating routines — if the
init collect is the new path.

**Gate.** JVM + assemble. Empty + routines → Home volt is WeekTwoCopy.
Empty + no routines → Suggest. Confirm still `acceptFills`.

**Won't.** A second filled button. Calling `apply()`. Changing Rule 6.

---

### P3 — ViewModel contracts that users already rely on · **done** (merged)

**Goal.** Settings / History / Progress cannot regress the live-session
and lighter-week contracts without a red test.

**The three tests**

1. `SettingsViewModelTest` — in-progress session ⇒ `sessionLive == true`
   (the screen disables restore from this).
2. `HistoryViewModelTest` — `repeatSession` while live surfaces the
   blocked state (not a silent resume).
3. `ProgressViewModelTest` — `markLighterWeek()` writes this week's
   start day. Already on `trunk` from P1; this packet adds the other two.

House style from Job 3 / P3: `runBlocking` +
`UnconfinedTestDispatcher` + `@Config(application = Application::class)`
+ `clearForTest()`. Do not re-test `IncrementTable` or `MastheadCopy`.

**Gate.** The three classes exist, 0 failures, `assembleDebug`.

**Won't.** Screen instrumented tests. `connectedDebugAndroidTest` on
release. A fake that reimplements `WeekDerivation`.

---

### P4 — Two sentences that stop a dead control · **done** (merged · phone pending)

**Goal.** The file and the Finish button tell the truth before the tap.

1. Settings Backup caption adds: an in-progress workout is left out of
   the file. (The snapshot already excludes it; the post-export note
   already says so. Say it *before* Export.)
2. Active workout: when Finish is disabled because there are no sets,
   a caption — **"Log a set to finish."** — not a second button.

**Files.** `SettingsScreen.kt`, `ActiveWorkoutScreen.kt`. Copy only.

**Gate.** assemble + JVM. No new behaviour.

**Won't.** Enabling Finish on zero sets. Putting today's live session
in the backup.

---

## Explicitly not this plan

| Item | Why |
|---|---|
| Plate calculator, rest-sound, font 2.0 | Signed later. Easy to make the gym worse. |
| Room v3 / `fallbackToDestructiveMigration` | Signed. |
| Fifth tab / LLM / package / Drive rename | Signed. |
| Job 2 P5 sex | No sentence that names a lift. |
| Scheduled auto-backup | Survival, not honesty. |
| Auto-scale sets on lighter week | They log fewer sets. We hold the load. |
| Home recommendation list | D3. Reason line only. |
| Navigating Body → Plan to "find Tune" | Treasure hunt. The tap is the mark. |

---

## Suggested order (and when to deviate)

```
P0 docs (this file, ROADMAP strike)
  → P1 deload card marks the week
    → P2 Home replay when routines exist
      → P3 Settings / History / Progress JVM tests
        → P4 backup caption + Finish helper
```

Deviate if:

- P1 on the phone feels like a silent write — keep the mark, add a one-line
  notice on Body ("This week is marked lighter."). Do not add a dialog.
- P2 makes Home look busy — keep the volt, drop the quiet Suggest, keep
  Plan's quiet Suggest. The replay sentence is the product.
- The owner says merge-to-trunk is paused — still push green PRs.

Do not deviate into sex, catalog seed, Room v3, or plate calculator.

---

## Phone gates (owner)

- P1: Force the deload card (or a week that qualifies). Tap **Mark this
  week lighter**. Plan Tune is on. Start a lift that would have climbed
  — strip says hold.
- P2: Unpin the week (routines stay). Home volt is **Use my answers
  again**. Confirm. Home names the first training day.
- P3: none (tests).
- P4: open Backup while a session is live — the caption says the file
  will not include it. Open a fresh session — Finish is grey and the
  line says why.

---

## Floor findings

1. **Tune already does the write.** The card was the missing tap, not a
   missing feature.
2. **"Show on the map" was a lie.** `hasDestination` exists so we do not
   bring that arrow back.
3. **Job 3 left Home as Suggest-only.** They open Home. P2 matches Plan.
4. **"+5.5 lbs" is dead in code.** `IncrementTable.STEP_LBS == 5.0`.
   ROADMAP was stale.
5. **Progress has no weekPlan.** Mark from today + `weekStart`. Do not
   turn the planner on for Body.
6. **`preferencesDataStore` is a process singleton.** A unique `filesDir`
   is not enough. Fake graphs need their own `PreferenceDataStoreFactory`.

---

## Verification

- [x] This file written; ROADMAP strike; UX pointer
- [x] P1 JVM + assemble (phone: card tap HOLDs)
- [x] P2 JVM + assemble (phone: Home replay)
- [x] P3 three ViewModel test classes, 0 failures (706 JVM)
- [x] P4 two captions (phone: Backup + Finish helper)
- [ ] Phone gates still the owner's
