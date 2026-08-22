# Job 5 action plan — leftover platform work

Living plan. **Not the law.** If a clearer move shows up, take it, write it
under *Floor findings*, strike the old line. Same method as
[JOB4_ACTION_PLAN.md](JOB4_ACTION_PLAN.md).

Status: **done** · **next** · *later* · **won't**

This packet is the plan. No Kotlin. No Gradle. No assets.

---

## What this is

Jobs 1–4 are **code-done on `trunk`**. The week can be generated, replayed,
and marked lighter. The card tap marks it. Home replay matches Plan.

What was left on the cut list was not one product. It was two piles:

1. **Platform leftovers ROADMAP already named and never owned** — rest
   sound *design*, a plate calculator, font-scale 2.0, prompted backup,
   and CI still listening for `main`.
2. **Signed non-goals** — Room v3, a fifth tab, an LLM, package / Drive
   rename, Job 2 P5 sex, Job 2 P6 catalog seed.

Job 5 owns pile 1. Pile 2 stays signed. Opening a non-goal still needs
the unlocking sentence written here first, not a surprise PR.

The gym-floor sentence for the owned pile:

> The rest cue is Temper. The bar names its plates. Large type still
> logs. Settings nags when the backup is old. CI still watches a
> branch that is gone. Phone gates remain the owner's.

That is the whole product. Everything below is how we do it without a
fifth tab, without Room v3, and without a chat coach.

---

## What is already true (do not rediscover)

1. **Weights are kg in Room.** `SetLogEntity.weightKg`. Display is
   `LocalWeightUnit`. `IncrementTable` already thinks in plates
   (2.5 kg / side, 2.5 lb / side). There is no bar weight, no per-side
   table, no loadable-weight field. Figure "plates" are Temper art.
2. **Rest sound is a Temper cue.** `RestTimerAlerts.playSound` plays
   `res/raw/rest_done.ogg`. Existing Sound toggle. Silent ringer stays
   silent. The done channel is silent on purpose (`rest_timer_done_v2`).
3. **Typography is `InstrumentType` in `sp`.** No `fontScale` clamp.
   `LogLoopScale` stacks the wells from 1.6. Touch floors are `heightIn`.
4. **Room is version 2.** `2.json` is committed. Identity hash
   `3eedd5301f0344b7802f5d0da2f68b3e`. Nothing in Jobs 2–4 needs a
   column. Lighter week, emphasis, age, place, preferred days, bodyweight
   log — all DataStore. Catalog growth is `CATALOG_VERSION` (now **5**,
   **101** built-ins), not a schema bump.
5. **Four tabs.** Home · Body · Plan · History. Library is a pushed
   route. D1 signed; ROADMAP's leftover "five tabs" line is stale.
6. **The coach is `RecommendationEngine`.** Offline. Drive is the only
   network. D4 cut the LLM.
7. **Identity is frozen.** Release id `com.sinura.personaltrainer`. Debug
   is `.debug`. Drive folder `PersonalTrainer Backups`. Backup `APP_ID`
   `personal-trainer`. Renaming any of those orphans Obtainium, the
   folder, or the debug rail.
8. **Sex is not a field.** Seven onboarding answers. Emphasis is the
   lever people mean. Job 2 P5's test sentence is still unwritten.
9. **Job 2 P6 is won't.** Athletic / emphasis families were already in
   the catalog. Reopen only if a real home-gym week collapses.
10. **Backup is a tap.** No WorkManager. Snapshot already drops the live
    session. Pre-migration copy is not a scheduled backup.
11. **CI `on.push` lists `main`, not `trunk`.** Feature `cursor/**`
    branches still build. A push to `trunk` alone does not. The cloud
    token cannot edit `.github/workflows/`. That packet is Studio, or
    a PAT with `workflow` scope.

---

## Binding rules

These stay signed. This plan may not weaken them.

- Four tabs. Library stays pushed. No LLM. No head-level anatomy.
- Room **version 2**. No `3.json`. No `fallbackToDestructiveMigration`.
- Weights in kg. Display via `LocalWeightUnit`. Do not replace
  `IncrementTable` with plate math.
- Volt = live / act only. One filled button per screen.
- Drive folder and package stay as they are.
- Training works offline. Backup is never required to log.
- Do not run `connectedDebugAndroidTest` on the release `applicationId`.

---

## Architecture we already have (do not rebuild)

| Piece | Where | Job 5 use |
|---|---|---|
| Step table | `IncrementTable` | Plate math consumes the same steps. It does not own them. |
| Weight entry | `SetEntryPanel` / `WeightStepper` | Read-only plate line under the numeral when `LoadClass.LOADED`. |
| Rest prefs | `RestTimerPreferences` | Existing Sound toggle gates the new cue. |
| Rest funnel | `RestTimerCompletion` → `RestTimerAlerts` | Swap the ringtone for `res/raw`. Channel stays silent. |
| Type | `InstrumentType` | Audit at 2.0. Do not invent a second type scale. |
| Backup | `BackupRepository` + Settings | Prompt if stale. Do not upload at launch. |
| Fake graph | `FakeAppDependencies` + private DataStore | New prefs keys go here. Isolated factory, not the singleton. |

If a packet can be a pure object plus a caption, that is the whole packet.
Do not add a service.

---

## Better idea than the first instinct (take this)

**Rest.** First instinct is a picker of eight sounds. The toggle already
means "make a noise when rest ends." One bundled cue. Keep the switch.
A last-five-seconds tick is a later finding, not the first packet.

**Plates.** First instinct is a new screen or a fifth-tab calculator.
The numeral is already on the log. A read-only line under `WeightStepper`
when the lift is loaded. Bodyweight and stack lifts stay quiet. Bar
weight is a DataStore key, not a Room column.

**Type.** First instinct is `fontScale = min(system, 1.3f)` in the theme.
That hides a broken layout by fighting accessibility. Audit the log
loop first. Clamp one screen only if it cannot wrap.

**Backup.** First instinct is weekly WorkManager → Drive. ROADMAP already
said "manual + prompted." A Settings sentence when the last backup is
stale is the product. Silent cloud upload implies the file exists when
they never signed in.

**Sex / catalog / v3 / fifth tab / LLM / rename.** First instinct is
"the owner said tackle the list, so open them." The list was a mix.
The ones without an unlocking sentence stay won't. Write the sentence
here before any of them grows a packet.

---

## Packets

### P0 — Plan + unbind the leftover list · **done** (merged)

**Goal.** A stranger can name which cut-list items Job 5 owns and which
stay signed. The next packet is allowed to touch rest / plates / type /
backup without fighting owner-loop.

**Work**

- This file.
- [ROADMAP.md](ROADMAP.md): Job 5 pointer. The three Phase-5 leftovers
  (rest sound, plates, font 2.0) plus prompted backup are owned here.
- [UX_PAGE_PASS.md](UX_PAGE_PASS.md): those four leave "Do not open."
  LLM, fifth tab, Room v3, package / Drive rename stay.
- [.cursor/rules/owner-loop.mdc](../.cursor/rules/owner-loop.mdc): same
  split. Waiting PR is still not permission to open a *different* job.

**Gate.** Reading ROADMAP + this file names P1–P5 and the six won'ts.

**Won't.** Kotlin. Assets. Editing `ci.yml` in this packet (token has
no workflow scope).

---

### P1 — CI listens for `trunk` · **after P0** (Studio or workflow-scoped PAT)

**Goal.** A push to `trunk` runs the same verify job a `cursor/**` push
already runs.

**Work**

`.github/workflows/ci.yml`:

- `on.push.branches` includes `trunk`. Drop `main` (the branch is gone;
  do not recreate it).
- Concurrency comments that say "main" say `trunk`.

**Gate.** A commit on `trunk` shows the verify workflow. PRs still run.

**Won't.** Enabling `instrumented-smoke` as a merge gate. Recreating
`main`. Touching `release.yml` unless it has the same lie.

**Note.** Cloud agent tokens cannot update workflow files. Owner does
this in Studio, or the next agent with `workflow` scope. Do not sit
idle on P2 waiting for it.

---

### P2 — Rest done is a Temper cue · **done** (merged · phone pending)

**Goal.** Sound-on plays a bundled cue. Sound-off is still silence.
Silent ringer is still silence. The notification channel stays silent.

**Work**

- One `res/raw/rest_done.ogg` (short, instrument, not a recording of a
  gym).
- `RestTimerAlerts.playSound` plays that asset (`SoundPool` or
  `MediaPlayer`). Fallback to today's ringtone if the asset is missing.
- No new preference. The existing switch is the switch.

**Tests.** `RestTimerAlerts` still no-ops when `soundEnabled == false`
and when ringer is silent. Do not assert audio hardware.

**Gate.** JVM + assemble. Phone: Sound on, rest ends, the cue is ours.
Sound off, rest ends, no tone. Channel banner still has no sound of
its own.

**Won't.** A sound picker. A last-five-seconds tick (record as a finding
if the phone wants it). Changing vibration. A new notification channel.

---

### P3 — Plates, type-in, pounds default · **done** (merged · 719 JVM · phone pending)

**Goal.** A barbell's weight stepper can say how the bar is made, the
number can be typed, and a first run thinks in pounds.

**Work**

```
PlateMath.load(targetKg, unit) → plates per side
```

- Default bars: 20 kg / 45 lb. Unit-default only — no `bar_weight_kg`
  key, no Settings bar row, no backup field.
- Standard plates: kg `25,20,15,10,5,2.5,1.25`; lbs `45,35,25,10,5,2.5`.
- Caption under `WeightStepper` when the lift is a barbell *and*
  `WeightMeaning.LIFTED`. Hidden for dumbbells, stacks, bodyweight,
  assist. `LoadClass.LOADED` includes STACK — do not use it alone.
- Same attach on live log and `SetEditSheet`. One composable.
- Type-in was already the numeral tap (`NumberEntryDialog`). This
  packet makes it obvious: underline + "Tap the number to type".
  No second button.
- `WeightUnit.fromStorage(null)` is **LBS**. Settings lists pounds
  first; kilograms stays the other radio. Storage is still kg.
  Existing `"kg"` prefs and backups stay kg.

**Tests.** Pure `PlateMath` — kg and lb, leftover that cannot be plated,
bar-only, below the bar. `WeightConverterTest.defaultUnitIsPounds`.
Missing backup `weightUnit` decodes as `"lbs"`.
`IncrementAndDefaultsTest` stays the owner of steps.

**Gate.** JVM + assemble. Phone: 225 lb squat names two 45s a side;
switch to kg; a push-up stays quiet. Tap the numeral, type a weight.

**Won't.** A new screen. A fifth-tab calculator. Changing `weightKg`
storage. Replacing `IncrementTable`. A DataStore bar-weight key.
Collar weight. Bumper vs iron as a second catalog.

---

### P4 — Font scale 2.0 on the log loop · **done** (merged · 722 JVM · phone pending)

**Goal.** The session can still be logged at the largest system font.
We do not "fix" it by clamping the whole app to 1.3.

**Work**

- `LogLoopScale.stackEntryWells` from 1.6: weight and reps stack instead
  of sitting half-width. The numeral they are about to log is never
  ellipsised (`maxLines = 2`).
- Touch floors become `heightIn`: stepper plates, rest controls, chips,
  log button, tab bar.
- Workout header metrics share the row. Finish helper already wraps.
- Rest clock keeps the ring and yields the remaining width.
- Four tabs stay four. The bar grows; labels stay kickers.
- Plan header actions drop to a second row at the same scale. Home
  masthead gains a third headline line. Week strip already `heightIn`.

**Tests.** `LogLoopScaleTest` — 1.3 stays side-by-side, 2.0 stacks.
No golden screenshots. No new Compose test dependency.

**Gate.** assemble + JVM. Phone: Settings → largest font. Log a set.
Tabs still labelled. Finish helper still readable.

**Won't.** A global `fontScale` clamp in `PersonalTrainerTheme`.
Restyling the stack. A second type scale.

---

### P5 — Prompted backup, not a silent clock · **done** (merged · 727 JVM · phone pending)

**Goal.** Settings says when the last backup is old, *before* they
need the file. Restore and live-session rules do not change.

**Work**

- `BackupPrompt.isStale`: no `lastBackupAt`, or 14 days or older.
- Caption on the Backup group leads with the nag when stale. Export
  and Drive stay the taps. Not a filled button.
- Last-backup stamp wears Warn when stale (already did for Never).
- The file still excludes the live session (Job 4 already says so).

**Gate.** JVM: `BackupPromptTest` plus Settings ViewModel — a stamp
older than 14 days sets `backupStale`; a fresh stamp clears it.
assemble. Phone: wipe the stamp — Settings nags. After Export, the
nag is gone.

**Won't.** `WorkManager` periodic Drive upload. Backup at process start.
Requiring a Google account. Putting the live session in the file.
Renaming the Drive folder to make the nag prettier.

---

## Explicitly not this plan

| Item | Why it stays signed |
|---|---|
| Room v3 / `fallbackToDestructiveMigration` | No column exists. Catalog is `CATALOG_VERSION`. Lighter week is a pref. Inventing `3.json` is the wipe we refused. |
| Fifth tab | D1. Library is pushed. ROADMAP "five tabs" is stale prose. |
| LLM / chat coach | D4. Offline promise. The coach is `RecommendationEngine`. |
| Package / Drive-folder rename | Breaks identity, Obtainium, the debug rail, and the existing folder. |
| Job 2 P5 sex | No sentence that names a slot → family that emphasis / athletic / typed load cannot say. |
| Job 2 P6 catalog seed | 101 lifts, version 5. Families already exist. Reopen only if a real home-gym week collapses. |
| Head-level anatomy | Cannot be derived from set logs. |
| Auto-scaled lighter-week sets | Job 3/4: they log fewer sets; we HOLD the load. |

Unlocking any row above is a floor finding in this file, then a new
packet header, then code. Not the other way around.

---

## Suggested order (and when to deviate)

```
P0 docs (this file, ROADMAP, UX, owner-loop)
  → P1 CI trunk (Studio; do not block P2 on it)
    → P2 rest cue
      → P3 plates under the numeral
        → P4 font-scale 2.0 on the log loop
          → P5 prompted backup
```

Deviate if:

- P2 on the phone wants a last-five-seconds tick — add it in P2, do not
  open a sound library.
- P3 leftover weight cannot be plated — say "and 1.25 leftover", do not
  invent micro-plates we did not stock in `IncrementTable`.
- P4 largest-font still logs but the tab bar wraps — keep four tabs,
  shrink the label, do not add a fifth.
- P5 nag feels like a second volt — keep it a caption, not a filled
  button.
- The owner writes the sex sentence, or a home-gym week actually
  collapses — reopen that row here. Do not guess.

Do not deviate into Room v3, an LLM, a package rename, or a fifth tab.

---

## Phone gates (owner)

Skipped this turn by request. Still the merge flavour when a UI packet
ships:

- P2: Sound on / off / silent ringer, one finished rest each.
- P3: 100 kg and 225 lb on a loaded lift; a push-up stays quiet.
- P4: largest font, log a working set, read the Finish helper.
- P5: stale stamp nags; after Export it does not.

P1 is a GitHub Actions page, not a phone.

---

## Floor findings

1. **The cut list was two piles.** Platform leftovers vs signed
   non-goals. "Tackle the list" is not permission to unsign D1/D4.
2. **`IncrementTable` already speaks plates.** The calculator is a
   readout of a decision we made, not a new system of steps.
3. **Rest "design" is the asset, not the preference.** The toggle and
   the silent done-channel are already honest.
4. **Clamping fontScale is a lie.** The log has to work at 2.0.
5. **Scheduled Drive upload is the wrong backup packet.** Prompted +
   manual matches ROADMAP and Job 4's caption.
6. **CI `main` is a docs-and-Studio packet.** The agent token cannot
   land it. P2 does not wait.
7. **No Settings bar weight.** 20 kg / 45 lb is the gym we already
   claimed. A DataStore key would force `setRestoredPreferences` and a
   backup field for a number nobody has asked to change yet.
8. **`LoadClass.LOADED` is the wrong plate gate.** It includes STACK.
   Caption only on `EquipmentType.BARBELL` + lifted weight.
9. **Type-in already existed.** The defect was discoverability, not a
   missing keyboard. Underline + caption, not a second control.
10. **Pounds is the default.** Missing / unknown storage is LBS.
    Kilograms is the Settings option. Files that already say `"kg"`
    stay kg.
11. **2.0 is layout, not a second type scale.** Stack the wells from
    1.6. Grow `heightIn`. Never clamp `fontScale`.

---

## Verification

- [x] This file written; ROADMAP / UX / owner-loop retarget
- [ ] P1 `ci.yml` lists `trunk`
- [x] P2 one cue, existing toggle (713 JVM)
- [x] P3 plates + type-in hint + pounds default (719 JVM)
- [x] P4 log loop at font 2.0 (722 JVM)
- [x] P5 stale-backup prompt (727 JVM)
- [ ] Phone gates still the owner's
- [ ] Six won'ts still won't
