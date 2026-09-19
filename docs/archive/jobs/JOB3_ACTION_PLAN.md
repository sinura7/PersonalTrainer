# Job 3 action plan — the week continues

> **Banner (24 Aug 2026).** Code-done historical job. “Room v3 won’t” in
> this file is **superseded** for the one Phase 5 cutover
> ([ADR-010](architecture/ADR-010-schema-reset-migrations.md)). Current
> program: [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md).

Living plan. **Not the law.** If a clearer move shows up, take it, write it
under *Floor findings*, strike the old line. Same method as
[JOB2_ACTION_PLAN.md](../archive/jobs/JOB2_ACTION_PLAN.md) and [UX_PAGE_PASS.md](UX_PAGE_PASS.md).

Status: **done** · **next** · *later* · **won't**

---

## What this is

Job 1 is logging. Job 2 is first-week generation. Job 3 is the week
continuing. All three are **code-done on `trunk`**. Phone gates are still
the owner's — that is expected, not a blockage.

Job 3 is what happens **after** the first accepted week, without asking the six
questions again, without inventing a second program, and without opening the
jobs we have already cut.

The gym-floor sentence:

> I already told you who I am. The week is empty. Put my routines back on the
> days I picked. And if the coach says take an easier week, let me mark this
> one so the bar stops climbing.

That is the whole product. Everything below is how we do it without lying,
without duplicating routines, and without a schema bump.

---

## What is already true (do not rediscover)

These are floor facts. A packet that fights them is the wrong packet.

1. **Pins survive Monday.** `WeekDerivation` only resets satisfaction (signed
   Rule 6). A pinned Tuesday is still Tuesday next week. "Week two" with pins
   is automatic. There is no empty-week hole for a lifter who left the pins
   alone.
2. **The real hole is an empty week with routines still present.** Unpin
   everything, or never pin after a restore, or delete slots and keep the
   programs. Suggest can invent a heat-shaped week from those routines. It
   cannot replay the *layout the lifter accepted in setup* — preferred days,
   Athletic families already sitting in the routines, the same names on the
   same weekdays.
3. **`OnboardingApplier.apply()` always creates routines.** Re-run from
   Settings is additive on purpose (history FKs). Calling `apply()` for "same
   plan" duplicates Push / Pull / Legs beside the ones they already train.
   Replay must **never** create.
4. **`trainingAge` and `preferredDays` are not persisted today.** Goal,
   emphasis, equipment, days-per-week, split, bodyweight, and the setup-done
   flag are. Place is only recoverable lossily from `availableEquipment`
   (empty = full gym = no filter). A restore or a later week cannot reconstruct
   the questionnaire without those three fields.
5. **Suggest is the planner.** `WeeklySchedulePlanner.plan` remaps open days
   from heat and recommendations. It does not regenerate lifts. Athletic
   templates apply on generate. Pins stay owned. That contract stays.
6. **A block is a horizon, not a load prescription.** `startNextBlock` moves
   `block_start_epoch_day`. It does not scale sets. A lighter week is a
   **different** marker. Do not fold them together.
7. **The deload card has no destination.** `deload-volume-flat-strength` lives
   on Body only (D3). Copy is option B: *"Take an easier week: same lifts,
   fewer sets."* ROADMAP option A is the affordance: one preference key, a
   Tune toggle, a strip marker, progression HOLD. No Room v3. No auto-scaled
   set counts.
8. **Four tabs. One volt.** Library is pushed. Volt = the one live/act
   control on the page. Preview's filled button is Use this plan / Use this
   week. Do not put two filled buttons on Plan.
9. **Room stays version 2.** Additive preferences and backup fields only.
   Never `fallbackToDestructiveMigration`. Never invent a `3.json`.
10. **Debug is `com.sinura.personaltrainer.debug`.** Release id is unchanged.
    The next debug install is a new app. `DEVELOPMENT.md` says so at the top.

---

## Binding rules

These stay signed. This plan may not weaken them.

- Four tabs. Library stays pushed. No LLM trainer. No head-level anatomy.
- One Room file, version **2**. Additive DataStore / backup fields only.
- Setup writes **nothing** until Use this plan. Re-run from Settings deletes
  **nothing**. Replay of stored answers also deletes **nothing** and creates
  **no** routines.
- The split is still **derived**, never asked.
- Weights in kg. Display via `LocalWeightUnit`.
- Suggest stays the heat/planner path. Replay is generate → match → proposals
  → the existing **Use this week**. Same confirm. Same `acceptFills`.
- Do not rewrite `WeekDerivation`. Rule 6 stays.
- Do not call `apply()` for "same plan."
- Volt = live / act only. One filled button.
- Drive folder and package stay as they are.
- Do not run `connectedDebugAndroidTest` on the owner's real `applicationId`.

---

## Architecture we already have (do not rebuild)

| Piece | Where | Job 3 use |
|---|---|---|
| Answers | `domain/OnboardingAnswers.kt` | Add `fromStored` + `inferPlace`. Do not add a question. |
| Generator | `domain/RoutineGenerator.generate` | Replay calls this. Pure. Already tested. |
| Write (create) | `data/repository/OnboardingApplier.apply` | First-run / Settings rebuild only. |
| Write (pin) | `ScheduleRepository.pin` / `acceptFills` | Replay lands here after confirm. |
| Days later | `WeeklySchedulePlanner` + Plan Suggest | Unchanged. Quiet when replay is the volt. |
| Prefs | `PreferencesRepository` DataStore | Persist age, preferred days, place. Lighter-week key. |
| Backup | `BackupDocument` / `BackupJson` | Additive fields, field-by-field defaults. |
| Restore | `setRestoredPreferences` | Every new field is required and written. |
| Progression | `WorkoutRepository.progressionFor` + `RpeModifier` | Lighter week is the same shape: force HOLD. |
| Coach | `RecommendationEngine.deloadSignal` | Copy reverts to "Schedule a lighter week." when A ships. |
| Week math | `WeekDerivation.derive` | Read `weekStartEpochDay`. Do not change placement. |
| Insights | `TrainingInsightsSource` | ViewModel tests fake this, they do not re-test it. |
| DI | `AppDependencies` | A1 already landed. Tests construct with a fake graph. |

If a packet can be a new DataStore key plus a matcher plus a volt label, that
is the whole packet. Do not add a service.

---

## Better idea than the first instinct (take this)

The first instinct is "week two must generate a new block." That is Settings
→ Rebuild my plan, and it **adds** routines.

**The first goal is: an empty week with routines already on the phone can
become the same week they accepted, without a second copy of anything.**

Replay stored answers. Match existing routines by name, then by unique
`focusKind`. Fill `proposals`. The confirm they already know — **Use this
week** — is the only write.

Suggest stays. It is the offer when they want the planner's heat-shaped week
instead of the setup layout. Two paths, one confirm, one volt.

Deload is option A from ROADMAP, not a new program: a marker, not a scaled
mesocycle. ViewModel tests lock the A1 seam so the next UI packet cannot
regress Home / Plan / setup by accident. The debug `applicationId` split is
the last packet because it is a product event (next debug install is a new
app), not because it is hard.

---

## Packets

### P0 — Job 2 settled on paper · **done** (merged)

**Goal.** The living docs tell the truth about what is on `trunk` and what
Job 3 is, so the next packet does not reopen Athletic or invent a week-two
questionnaire.

**Work**

- [JOB2_ACTION_PLAN.md](../archive/jobs/JOB2_ACTION_PLAN.md): P0–P4 **done** (code) / phone
  pending. P5 later. P6 won't. Floor finding: pins survive Monday; empty week
  is the hole.
- This file.
- [ROADMAP.md](ROADMAP.md) pointer: Job 2 code-done; Job 3 is next.
- [UX_PAGE_PASS.md](UX_PAGE_PASS.md): deload decision UI is no longer a
  "do not open" — it is Job 3 / P2 (ROADMAP option A). Plan empty-week volt
  will change in P1 when routines exist.

**Gate.** A stranger reading ROADMAP + this file can name the four remaining
packets and what they must not do.

**Won't.** Code. Gradle. A fifth Job 2 question.

---

### P1 — Replay stored answers (empty week) · **done** (merged · phone pending)

**Goal.** Empty week + routines exist → one tap rebuilds the setup layout on
the routines they already have. Nothing is created. Nothing is deleted.
Nothing is pinned until **Use this week**.

**Why first.** Highest gym-floor value. Settings rebuild is the nuclear
option (adds a block and new routines). Suggest is the heat path. The missing
path is "same person, same programs, same days."

**Store (additive)**

Persist on `OnboardingApplier.apply` — the moment they accept a plan:

| Key | Type | Default if absent |
|---|---|---|
| `training_age` | string enum name | `TrainingAge.NEW` |
| `preferred_days` | string set of `DayOfWeek.name` | empty = space them out |
| `training_place` | string enum name | `inferPlace(availableEquipment)` |

`setRestoredPreferences` writes all three unconditionally. Backup fields
default so a v1/v2 file without them decodes. Old installs without the keys
still reconstruct: age NEW, days empty, place inferred.

**`OnboardingAnswers.fromStored`**

```
trainingAge, daysPerWeek, preferredDays, place, goal, emphasis, bodyweightKg
```

Days, goal, emphasis, bodyweight already live in prefs. Place: stored key, or
`inferPlace`:

- empty equipment → `FULL_GYM` (no filter, same as today)
- every type ⊆ bodyweight+other → `BODYWEIGHT_ONLY`
- every type ⊆ home-dumbbell set → `HOME_DUMBBELLS`
- otherwise → `FULL_GYM`

**Match, never create** — `ExistingLayoutMatcher` (pure domain)

Input: `PlanBlueprint` from `RoutineGenerator.generate(fromStored, catalog,
weekStart)` plus the routines already on the phone.

For each training day in the blueprint, claim one unused existing routine:

1. Exact name, case-insensitive, trimmed. Setup names are the focus labels
   (`Upper`, `Pull`, `Full Body A`). That is the happy path.
2. If no name hit: unique unused routine whose
   `WeeklySchedulePlanner.classifyRoutine` equals the blueprint
   `focusKind`. Unique means one candidate. Two "Push"-shaped unnamed
   customs → do not guess.
3. Never create. Never delete. Never `beginBlock`. Never rewrite prefs.

Output: `List<SuggestedTrainingDay>` with `slotId == null`, `routineId` set,
`epochDay` on **this** week (`weekStart` + weekday). Reason:
`"Pinned from your answers."` Confidence `HIGH`.

If **nothing** matches: fail honestly. Copy:
`"Couldn’t match your routines. Rebuild from Settings."`

Partial match (4-day blueprint, 2 names found) is success — pin what matched.
An empty list is the only failure. They can still Suggest.

**UI — Plan, empty week, routines exist**

- Volt: **Use my answers again**.
- Quiet: **Suggest a week** (unchanged planner).
- Caption under the volt (one line):
  `"Pins the routines you already have to the same days. Does not add routines or delete history."`
- Confirm path is the existing proposals row: **Use this week** / Dismiss.
- Empty week + **no** routines: today's volt Suggest + empty-state Create.
  Replay is hidden — there is nothing to match.
- Week with pins: quiet Suggest only. Replay is an empty-week recovery, not a
  rewrite of a decided week.
- Home's empty hero stays **Suggest a week** (arms Plan via
  `pendingWeekSuggestion`). One door from Home. Plan is where the two paths
  are visible.

**Copy object.** `domain/WeekTwoCopy.kt` — volt, caption, match-failed.
Pin with a test the way `PlanSetupCopy` is pinned.

**ViewModel.** `PlanViewModel.replayStoredAnswers()`:

1. Read stored answers + catalog + this week's `weekStartEpochDay` from the
   current `weekPlan` (already on insights).
2. `RoutineGenerator.generate`.
3. `ExistingLayoutMatcher.match`.
4. On empty: `actionError` = match-failed copy.
5. On hit: `proposals.value = matched` — same field Suggest writes.

Do **not** pin inside the replay call. `acceptFills` is the write.

**Files.** `OnboardingAnswers.kt`, `WeekTwoCopy.kt`, `ExistingLayoutMatcher.kt`,
`PreferencesRepository.kt`, `BackupDocument.kt`, `BackupJson.kt`,
`LocalBackupRepository.kt`, `OnboardingApplier.kt` (persist on apply only),
`PlanViewModel.kt`, `PlanScreen.kt`, tests:
`OnboardingAnswersRestoreTest`, `ExistingLayoutMatcherTest`,
`OnboardingApplierTest` (answers land), `WeekTwoCopyTest`, backup round-trip
+ decode of a document missing the new fields.

**Gate.**

1. Apply a 3-day Tue/Thu/Sun plan. Unpin all slots. Routines remain.
   Replay → proposals on Tue/Thu/Sun pointing at the **same routine ids**.
   Use this week → four tabs, Home can start Tuesday.
2. Replay does not increase `routineRepository.count()`.
3. Rename every routine → unique focusKind still matches; two customs of the
   same kind → those days omitted, others match.
4. No routines → volt is still Suggest; replay is not shown.
5. Old backup without the new fields restores; replay still runs via
   inferPlace + defaults.
6. `./gradlew testDebugUnitTest` 0 failures; `assembleDebug` green.

**Won't.**

- Calling `apply()` or `beginBlock` from replay.
- Rewriting `WeekDerivation`.
- Asking the six questions again on Plan.
- Home growing a second volt.
- Two filled buttons (replay + Suggest) on Plan.
- Auto-pin without **Use this week**.
- Matching on lift lists (fragile, slow, wrong when they swapped a lift).

---

### P2 — Lighter week (ROADMAP option A) · **done** (merged · phone pending)

**Goal.** The coach's overreach card becomes an instruction the app can help
follow. Same lifts. The bar does not climb this week.

**Stack after P1.** Overlaps `PreferencesRepository`, `PlanViewModel`,
`PlanScreen`, backup. Do not cut from `trunk` while P1 is open.

**Store**

`lighter_week_start_epoch_day`: nullable `Long`. The marked week's
`weekStartEpochDay` (same instant `WeekDerivation` already computes). Null =
no lighter week. A past value is inert — readers compare to **this** week's
start. No cleanup job.

Backup field `lighterWeekStartEpochDay: Long? = null`.
`setRestoredPreferences` writes it.

**Rules (pure, tested first)**

`LighterWeekModifier.apply(hint, lighter: Boolean)` — same shape as
`RpeModifier`:

- `lighter == false` → hint unchanged.
- `DECREASE` stays `DECREASE` (they already missed; do not "hold" a drop).
- `HOLD` stays `HOLD`.
- `INCREASE` becomes `HOLD`, `suggestedWeightKg = lastWeightKg`,
  `lighterHold = true` (new flag, default false, like `rpeHold`).

`ProgressionCopy.stripReason`: if `lighterHold` →
`"Lighter week. Keep ${weight}."`

`WorkoutRepository.progressionFor` applies this after `RpeModifier`, given
whether **today's** week is the marked week. Do not put DataStore inside the
calculator. The repository (or its caller) reads the key and the current
week start and passes a boolean.

Do **not** auto-scale set counts. The card already said "fewer sets" as
advice they log. The app's job is to stop recommending more weight.

**UI**

- Plan → Tune: chip **Lighter week**. Selected iff stored == this week's
  start. Tap on → write this start. Tap off → clear if it was this week.
- Under the week strip, when marked: caption **"Lighter week"** (one line,
  not a sixth thing in every cell). Home's strip can read the same caption
  from `HomeUiState` if the key is already in the combine — only if it
  costs a few lines. Do not restyle `WeekStrip` cells.
- Body deload card copy reverts to **"Schedule a lighter week."** The card
  still has no destination (D3). Tune is the affordance.

**Files.** `PreferencesRepository.kt`, `BackupDocument.kt`, `BackupJson.kt`,
`LocalBackupRepository.kt`, `Models.kt` (`lighterHold`),
`LighterWeekModifier.kt`, `ProgressionCopy.kt`, `WorkoutRepository.kt`,
`RecommendationEngine.kt`, `PlanViewModel.kt`, `PlanScreen.kt`,
`PreferenceBlock.kt`, optional Home caption, tests:
`LighterWeekModifierTest`, `ProgressionCopy` branch, backup round-trip,
prefs restore.

**Gate.**

1. Tune on this week → strip caption shows; `progressionFor` on an INCREASE
   lift returns HOLD + `lighterHold`.
2. Next calendar week (or a test clock) → marker inert; progression climbs
   again.
3. RPE hold and lighter hold can both be true; strip prefers the lighter
   sentence (this week is the reason they opened the session).
4. Coach card ends "Schedule a lighter week."
5. Restore of an old backup leaves the key null.
6. JVM + `assembleDebug`.

**Won't.**

- Room v3. A `deload_weeks` table. Auto-reduced sets.
- A fifth tab or a Body tap target (D3 stays).
- Folding lighter week into `TrainingBlock`.
- Changing Rule 6 or unpinning days because the week is lighter.

---

### P3 — ViewModel JVM tests · **done** (merged)

**Goal.** The A1 seam earns its keep. Home, Plan, and setup cannot regress
their one job without a red test.

**Need first.** `FakeAppDependencies` in `app/src/test`. Real Room +
DataStore where the test is about a write; a fake
`TrainingInsightsPublisher` where the test is about a projection.

Do not duplicate domain tests (`MastheadCopy`, `hasOpenTrainingSlot`,
`WeeklySchedulePlanner`, `RoutineGenerator`). Those are already green.

**If `TrainingInsightsSource` cannot be faked** without opening every
repository, extract a small `TrainingInsightsPublisher` interface
(`observeShared`) that the source already implements. That is A1
completion, not a new architecture.

**The three tests**

1. `HomeViewModelTest` — when `insights.hints` is non-empty, the
   `progression-ready` card is **absent** from `uiState.recommendations`.
   When hints are empty, the card (if the engine emitted it) remains.
2. `PlanViewModelTest` — `suggestFills` writes proposals only for unpinned
   training days (`slotId == null`, `!isRest`). A fully pinned week yields
   an empty proposals list. After P1: `replayStoredAnswers` does not
   increase routine count and fills proposals whose `routineId`s already
   existed.
3. `OnboardingViewModelTest` — `preview == null` until the catalog is
   non-empty; then `preview` tracks `answers` (change days → training day
   count changes). Catalog-empty error path still surfaces
   `CATALOG_MISSING_MESSAGE` on retry-fail.

Robolectric + `runBlocking`. `PlanViewModel.uiState` is `flowOn(Dispatchers.Default)`,
so `runTest` + virtual `withTimeout` fires immediately. House style for these
three: `Dispatchers.setMain(UnconfinedTestDispatcher())` and a **real**
`withTimeout(5_000)` on `uiState.first`. Do not close the in-memory database
to force `retryCatalog` to fail — Room hangs on `observeAll().first()`.

Application-only constructors stay for the default factory
(`AppViewModelSeamTest` already locks that).

**Gate.** The three classes exist, 0 failures, `assembleDebug`. No new
production behaviour except the publisher interface if required.

**Won't.** Screen instrumented tests. `connectedDebugAndroidTest` on the
real id. Restyling. A fake that reimplements `WeekDerivation`.

---

### P4 — Debug `applicationIdSuffix` · **done** (merged · phone: two icons)

**Goal.** Debug and release are separate apps. Experimenting cannot open the
real history.

```
debug { applicationIdSuffix = ".debug" }
```

`DEVELOPMENT.md` already says they share an id. Update that paragraph:
**the next debug install is a new app.** The release install on the phone
is untouched. The owner will see two icons until they uninstall the old
debug (which *was* the release id). Say that in the PR body and in
DEVELOPMENT. Do not bury it.

Do **not** run `connectedDebugAndroidTest` on the real `applicationId`
after this — the connected suite targets debug, which will be the suffix
id. That is the point.

**Files.** `app/build.gradle.kts`, `app/src/debug/res/values/strings.xml`
(launcher name **Temper Debug** so the two icons are not the same word),
`docs/DEVELOPMENT.md`. Living-plan status lines only besides those.

**Gate.** `assembleDebug` produces `applicationId`
`com.sinura.personaltrainer.debug`. Release id unchanged. JVM suite green
(Robolectric uses the debug variant).

**Won't.** Package rename. Signing-config change. Touching release
`applicationId`. A fifth tab because "we are in Gradle anyway."

---

## Explicitly not this plan

| Item | Why |
|---|---|
| Job 2 P5 sex | No sentence that names a lift. Still later/never. |
| Job 2 P6 catalog | Families already exist. Reopen only if a real home-gym week collapses. |
| Room v3 / `fallbackToDestructiveMigration` | Signed. |
| Plate calculator, rest-sound design, font 2.0 | Different jobs. Easy to make the gym worse. |
| Scheduled auto-backup | Survival, not the week. |
| Package / Drive rename | Breaks identity. |
| Fifth tab / LLM | Signed non-goals. |
| Auto-scale sets on lighter week | They log fewer sets. We hold the load. |
| Calling `apply()` to "refresh" week two | Duplicates their program. |
| Rewriting a pinned week because emphasis changed | Pins are owned. Job 2 / P4. |

---

## Suggested order (and when to deviate)

```
P0 docs (Job 2 settled, this file)
  → P1 replay stored answers
    → P2 lighter-week marker
      → P3 ViewModel JVM tests
        → P4 debug applicationIdSuffix
```

Deviate if:

- P1 matching is wrong on the owner's real renamed routines — tighten the
  matcher, do not fall back to `apply()`.
- P2 on the phone makes the strip look busy — keep the Tune chip, drop the
  Home caption, keep HOLD. The progression sentence is the product.
- P3 cannot fake insights without a 400-line lie — extract the publisher
  interface, do not skip the tests.
- The owner says merge-to-trunk is paused — still push green PRs; do not
  sit idle. Phone remains the taste check.

Do not deviate into sex, catalog seed, Room v3, or plate calculator because
Gradle is open.

---

## Phone gates (owner)

After each packet that ships UI:

- P1: Unpin the week (routines stay). Plan volt is **Use my answers again**.
  Confirm. Home names the first training day. Settings → Rebuild still adds
  (does not replace).
- P2: Tune → Lighter week. Strip says so. Start a lift that would have
  increased — strip says hold. Next week, it climbs again.
- P3: none (tests).
- P4: next debug Run ▶ installs **beside** the release app, not over it.
  Confirm two icons. Confirm the release history is untouched.

---

## Floor findings

1. **Week two with pins is already done.** Rule 6 resets satisfaction, not
   slots. Do not build a "generate week two" feature for a pinned week.
2. **Empty week + routines is the hole.** Replay fills it. Suggest remains
   the heat path.
3. **`apply()` is the wrong tool for same-plan.** It creates. Replay matches.
4. **Age, preferred days, and place were the only setup answers that died
   at accept.** Goal, emphasis, kit, days-per-week already travel.
5. **Deload A is a marker, not a mesocycle.** HOLD this week. They log
   fewer sets if they want fewer sets.
6. **UX page pass forbade opening deload UI during that pass.** Job 3 / P2
   is the named exception (ROADMAP option A, owner-accepted).
7. **P4 is a product event.** The next debug install is a new app. Say it
   in DEVELOPMENT and in the PR, then do it.
8. **ViewModel tests cannot use `runTest` + virtual `withTimeout`.**
   `PlanViewModel.uiState` is `flowOn(Dispatchers.Default)`. Virtual time
   expires before Default runs. `runBlocking` + real 5s timeout.
   Closing the in-memory Room to force a catalog retry-fail hangs. Boot
   them on `@Config(application = Application::class)` so they do not
   start `PersonalTrainerApp` (live Room + catalog seed) beside the
   snapshot suite.

---

## Verification

- [x] This file written; Job 2 statuses settled; ROADMAP pointer
- [x] P1 JVM + assemble (phone: replay empty week)
- [x] P2 JVM + assemble (phone: lighter week HOLD)
- [x] P3 three ViewModel test classes, 0 failures (700 JVM)
- [x] P4 debug suffix; DEVELOPMENT tells the truth (phone: two icons)
- [ ] Phone gates still the owner's
