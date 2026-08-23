# Job 6 — Regroup: polish and intuition

Living plan. **Not the law.** If the phone disagrees, write it under
*Floor findings* and take that instead.

Status: **done** · **next** · *later* · **won't**

This packet is leftover paper. No Kotlin. No Gradle. No assets.

---

## Where we stand (23 Aug 2026, `trunk` @ `ddcfa62`)

Temper is a **four-tab strength logger** that can generate a week,
log a session, rest, finish, repair history, and survive a restore.
Jobs 1–5 are **code-done on `trunk`**. Job 6 P0, P1, P3, and P4 are
on `trunk` (`ddcfa62` marked P4 paper-done). P1 is closed: `ci.yml`
lists `trunk`. We still do not use GitHub runners to test. Setup
intuition is code-done; leftover setup defects are packets P1–P8
below. Phone week runs in parallel and can jump the queue.

The gym-floor sentence now:

> The week can be started in one tap. The bar names its plates.
> The rest cue is ours. Large type still logs. Settings nags
> when the backup is old. The first rest-permission ask now has
> a gym why. The phone has not judged any of that yet.
> We test on Cursor and live on Studio. Ignore a GitHub red X.
> `trunk` is the only sitting remote.

That is the product. Job 6 does **not** add a fifth surface. It
makes the existing one simpler to trust.

---

## What is already excellent (do not reopen)

1. **Four tabs + live bar.** Home · Body · Plan · History. Library
   is pushed. Volt is the one filled act. Home never says Resume.
2. **Onboarding writes once.** Real preview. Derived split.
   Build-my-own escape. Re-run from Settings deletes nothing.
3. **The log loop.** Rest dock outside the scroll. Type-in on the
   numeral. Barbell plates as a caption. Finish on any logged set.
4. **Honest empty weeks.** Replay when routines exist. Suggest when
   they do not. Fully pinned week hides Suggest.
5. **Lighter week HOLDs load.** They log fewer sets; we do not invent
   a smaller bar.
6. **Backup discipline.** Live session out of the file. Restore
   blocked while live. 14-day nag is a caption, not a second volt.
7. **Domain tests.** Policy objects (`IncrementTable`, `PlateMath`,
   `BackupPrompt`, `SetLogRules`, `RoutineEditorPolicy`) are the
   product. Do not replace them with screenshots.

---

## The real leftover piles

### A — Process (blocks trust in `trunk`)

| Item | Why it matters | Disposition |
|---|---|---|
| `ci.yml` lists `trunk` | P1 landed (`db787f5`). Hosted runners are not a test lane. | **done.** Do not reopen. |
| Lint `continue-on-error` | A red lint never fails the job | *later* — ignore unless we choose to gate it |
| Instrumented smoke non-gating | Emulator job is written blind | **won't as a merge gate.** We do not use GitHub runners. |
| Phone gates for Jobs 2–5 | Code-done ≠ gym-done | **Owner first.** Highest leverage. |

### B — Intuition (strangers get lost)

| Item | Why it matters | Disposition |
|---|---|---|
| Week verbs stack | Suggest / Replay / Tune / Lighter / Use this week / Dismiss live in one place. Four true ideas, similar words. | **P2** if the phone confirms the cliff |
| Notification permission has no sentence | System dialog appears with no in-app why | **P3 · this packet** |
| Library is off-tab | Correct, but first-week users hunt for the catalog | Copy / Plan header is enough. Do not add a fifth tab. |
| Body vs History | “Did it save?” is History. Body is heat + hole. | A Home tertiary already points at History. Taste. |
| Untitled routine | A nameless program can be saved | *later* — require a name on leave if the phone hates it |
| Tap-to-type caption | Already underlined. Still missable. | Phone first. Do not add a second button. |
| Backup caption is long | Stale nag prepends the survival paragraph | *later* — shorten only if the phone says it is a wall |

### C — Honesty (code vs paper)

| Item | Why it matters | Disposition |
|---|---|---|
| ROADMAP D1 still says five tabs | The signed decision is four. The opening sentence lies. | **done** |
| JOB5 “already true” still describes pre-P2 rest and no plates | Agents will rediscover lies | **done** |
| JOB5 P5 still says “this PR” | It is merged | **done** |
| ROADMAP “known open” still lists Job 5 leftovers as open | They shipped | **done** |
| DESIGN_AUDIT.md reads as a prototype | Many P0s are fixed. The file is a museum. | *later* — do not rewrite it as a job |
| Pounds default on reinstall without backup | Missing `weightUnit` → lbs. Existing `"kg"` stays kg. | Document. Do not guess a migration. |

### D — Taste / platform (*later*)

- Audible last-five-seconds rest tick (Job 5 P2 won't unless the phone asks).
- Configurable bar weight (Job 5 P3 won't).
- Smith / dumbbell plate captions (barbell-only is the truth).
- Built-in lift coaching notes.
- Release minify / toolchain bump / `versionCode` still `1`.
- ViewModel/screen instrumented tests (JVM already covers the contracts).
- Session detail as a “program sheet” instead of text rows.

### E — Signed won't (do not surprise)

Room v3 · fifth tab · LLM · package / Drive-folder rename · Job 2 P5
sex (no sentence) · catalog seed · GitHub-hosted runners as a test
lane · auto-scaled lighter-week sets · WorkManager silent backup ·
overlay rest clock · `fallbackToDestructiveMigration` · per-day gym
vs home schedule · forcing `MAX_DAYS` back to 6.

Unlocking any row needs a floor finding here, then a packet header,
then code.

---

## Recommendation (the sequence I would take)

The app does not need another feature job. Phone week runs **in
parallel**. Setup leftovers P1–P8 do **not** wait for it. Week-verb
language (historical P2) waits until the phone confirms the cliff.

```
Owner phone (Jobs 2–5 gates)  ──parallel──►  leftover P1 → P8
                                              (one packet, one PR)
  → historical P2 week verbs, only if the phone confirms
```

A floor finding jumps the leftover queue. Do not sit idle on GitHub
Actions. Do not open two leftover packets in the same PR.

Deviate if:

- The phone wants a last-five-seconds tick — add it in a rest packet,
  do not open a sound library.
- The phone cannot find type-in — strengthen the numeral, do not add
  a Type button.
- A home-gym week actually collapses — reopen catalog seed here.
- The owner writes the sex sentence — then Job 2 P5, not this job.

Do not deviate into Room v3, an LLM, a package rename, a fifth tab,
GitHub-hosted runners as a test lane, per-day gym vs home, or forcing
`MAX_DAYS` back to 6.

---

## Packets

### P0 — This regroup · **done** (this PR)

**Goal.** A stranger can name what Temper is, what is excellent, and
what Job 6 will and will not touch.

**Work.** This file. ROADMAP Job 6 pointer. JOB5 paper settled.
Owner-loop retarget. D1 opening sentence says four tabs.

**Gate.** Reading this file names A–E and the recommended order.

**Won't.** Kotlin. Assets. Editing `ci.yml`.

### P1 — CI listens for `trunk` · **done** (`db787f5`)

`on.push.branches` is `[trunk, 'claude/**', 'cursor/**']`. Closed.
GitHub-hosted runners are not how we test. Do not lift billing.
Do not ask anyone to open the red X.

### P2 — Week verbs a stranger can hold · **after the phone**

**Goal.** Suggest, Replay, Tune, and Lighter week never share a
sentence. Each word means one thing everywhere it appears.

**Work.** Copy only unless a control still lies. Do not merge the
four ideas. Do not hide Lighter week.

**Won't.** A fifth tab. A wizard. Auto-scaled deload sets.

### P3 — Why we need the notification · **done**

**Goal.** One in-app sentence before the system permission dialog.

**Work.** `RestNotificationCopy.SENTENCE` in a confirm before
`POST_NOTIFICATIONS`. Continue launches the system dialog. Not now
leaves the existing denial banner as the recovery. No settings
deep-link in the sentence.

**Won't.** A settings deep-link as a substitute for the sentence
(the banner after denial already exists). A sound library. Overlay.

### P4 — Setup a stranger can finish · **done** (merged · phone pending)

**Goal.** Both forks of "Let's get you training" feel like Temper:
guided questions that look like a readout, and a week you build by
hand that you can confirm and then keep editing.

**Work.** Experience as numbered cards. Days 1–7. Mixed places.
Bodyweight as a live wheel with lbs/kg. Build-your-own is a Mon–Sun
strip, multi-add lifts, compact rows, then "Use this week". Plan
day sheet has Edit lifts. The applied week stays editable.

**Won't.** A fifth tab. An LLM. Catalog seed. Room v3.

---

## Leftover packets (after P4)

One packet, one `cursor/<slug>-0ecb` branch, one squash-merge into
`trunk`, delete the branch, then the next. JVM gate on every Kotlin
throw. `docs/ROADMAP.md` may move a few lines; do not restack Kotlin
files. Do not open P1–P8 in the same PR as each other.

### Leftover P1 — Setup you can leave · **done** (this PR)

**Why.** Settings → Add a new block set `onboardingComplete=false`.
Back on the fork called `onFinished()`, which AppNav wired as `{}`.
The gate stayed SETUP. Closed: fork back with an existing program
restores complete. The row no longer says rebuild.

**Work.** Fork back with `existingProgram`: restore complete and
finish. First install stays on the fork. Custom-week back still
returns to the fork. Rename the Settings row so it does not sound
like a wipe. Delete unused `OnboardingViewModel.skip()`.

**Won't.** Deleting history on re-run. A third setup escape.

**Gate.** Re-run from Settings, back on fork, app is back. First
install still cannot skip past the fork with an empty week.

### Leftover P2 — Bodyweight is opt-in · **done** (this PR)

**Why.** The wheel wrote ~75 kg on first settle, so Skip became
secondary. `setWeightUnit` wrote DataStore while setup still said
nothing is stored until Use this plan. Closed: kg commits on a flick;
unit writes on apply.

**Work.** Do not commit kg until the user flicks or taps Continue.
Hold unit locally; write it only from apply, or revert on abandon.
Keep the live hero numeral.

**Won't.** Removing Skip. Storing lbs in the database.

**Gate.** Open bodyweight, do not touch the wheel, Skip, no weigh-in
stored. Flick once, Continue, kg stored. Toggle lbs then abandon,
stored unit unchanged.

### Leftover P3 — Custom week keeps the questionnaire

**Why.** Preview → I'll build my own drops age, places, kit, goal,
emphasis, bodyweight. `applyCustom` writes days/split/preferredDays
/block/complete only. Replay then guesses NEW / gym / general.
`seedPreferredDays` only moves `selectedDay`.

**Work.** Pass optional `OnboardingAnswers` into `applyCustom`.
Fork-only custom week still omits them. Hold answers when leaving
preview. Mark preferred days as picked with zero lifts; still
require a lift to confirm. Initialize `selectedDay` from `weekStart`.

**Won't.** Inventing questionnaire answers for a fork-only custom
week. Per-day gym vs home scheduling.

**Gate.** Guided answers → own week → confirm → preferences still
hold those answers. Fork-only custom week still does not invent a
goal.

### Leftover P4 — Places tell the truth

**Why.** Mix UI is real for home+bodyweight (union). Gym in the set
still means full kit via `TrainingPlace.equipmentOf`. Copy says gym
days and home days both count. Generation does not split by place.

**Work.** Keep gym-swallow. Blurb and mix caption: gym covers every
lift; home and bodyweight mix only when there is no gym. Optional:
selecting gym clears or covers the other two.

**Won't.** Per-day place. Catalog seed. Changing `equipmentOf` so
gym+home filters out machines.

**Gate.** Home+bodyweight still unions kit, no barbell. Gym+home
generates a gym program. Screen no longer implies two schedules.

### Leftover P5 — Controls fit a phone

**Why.** Seven `weight(1f)` chips for 1–7 and seven single-letter
days will crush in the gutter.

**Work.** Days-per-week: hero numeral; chips in two rows (1–4 /
5–7) or a horizontal scroll. Do not go back to 2–6. Which-days and
custom week strip: two-letter labels via `shortLabel()`, min height
`Metrics.touchMin`.

**Won't.** A calendar widget. Changing 1–7 range.

**Gate.** All seven day-count chips and all seven weekdays remain
tappable at default font; two-letter labels readable.

### Leftover P6 — Compact rows can set load

**Why.** Compact expanded is sets/reps/rest only. Routine editor
still stages `targetWeightKg`; custom week always writes `null`.

**Work.** Optional target weight on the expanded row. Thread
`targetWeightKg` through custom week, `applyCustom`, and the
editor's existing `stageTargets`. Collapsed line stays
`sets × reps`.

**Won't.** Drag-and-drop reorder. A second card design.

**Gate.** Custom week: set 80 kg on a lift, confirm, routine row
has 80 kg. Editor compact row can change weight the same way the
old card did.

### Leftover P7 — Confirm honesty

**Why.** Seven training days with zero rest is allowed with no
warning. Custom confirm treats blank days as rest with no caption.

**Work.** Domain rest-day count / full week. Confirm CTA names
training days and rest. Full week: confirm dialog before write on
custom confirm and guided Use this plan when `daysPerWeek == 7`.
Do **not** cap at 6.

**Won't.** Forcing a rest day. Changing MAX_DAYS.

**Gate.** Six filled days: button names rest. Seven filled: dialog,
then write. Guided 7-day preview: same warning before apply.

### Leftover P8 — Small polish plus dead code

**Why.** Leftover nits that are not worth their own throw if P1
already deleted `skip()`.

**Work.** Shrinking days-per-week trims extra preferred weekdays
(keep first N in week order), do not empty the set. Picker selected
state: drop caption "On"; volt border + "Selected". Delete unused
`setPlace()` if still unused. Fix OnboardingHeader comment. Compact
row KDoc: three fields plus optional weight.

**Won't.** Drag reorder. Untitled-routine name requirement.
Shortening the backup caption.

**Gate.** Pick four days, drop to three: three remain. Tick a lift
in the picker: selected state is obvious.

### After the phone — historical P2 week verbs

Only if the phone confirms the cliff. Copy only. Surfaces:
ThisWeekCard, PlanScreen, PreferenceBlock, WeekTwoCopy, LighterWeek.

Do not merge Suggest / Replay / Tune / Lighter. Do not hide Lighter
week.

### Later (no packet until the phone asks)

- Untitled routine must be named on leave
- Shorten backup stale caption
- Drag-handle reorder on compact rows
- `versionCode` / toolchain bump
- Audible last-five-seconds rest tick

---

## Phone gates (owner)

This is the real next work. Code will sit.

- Job 2: generate → Start this session; Suggest honours emphasis.
- Job 3: unpin + replay; Tune + HOLD; debug icon is the second app.
- Job 4: deload card marks this week; Home replay when routines exist.
- Job 5: rest cue on/off/silent; 225 lb plates; largest font logs;
  stale backup nags, Export clears it.

If any gate fails, write the finding here. That packet jumps the queue.

---

## Floor findings

1. **Jobs 1–5 shipped a product, not a pile.** The leftover is
   trust (phone) and language (week verbs), not missing screens
   and not a GitHub runner.
2. **DESIGN_AUDIT is a museum.** Use it for taste, not as a cut list.
3. **Four tabs is the signed IA.** D1's opening "five tabs" was the
   leftover lie. Struck in this packet.
4. **Simple means fewer words for the same four ideas**, not fewer
   ideas. Replay is not Suggest. Lighter is not Tune.
5. **Setup intuition shipped a leftover list.** Rebuild traps you,
   bodyweight writes on settle, custom week drops answers, place
   copy implies two schedules, 1–7 chips crush, compact rows cannot
   set load, seven hard days have no warning. Packets leftover
   P1–P8. Not a fifth tab.

---

## Verification

- [x] This file written; ROADMAP / JOB5 / owner-loop / D1 retarget
- [x] P1 `ci.yml` lists `trunk` (`db787f5`). Not a test lane.
- [ ] Owner phone week
- [ ] Historical P2 week-verb language (only if the phone confirms)
- [x] P3 notification sentence
- [x] P4 setup intuition (questionnaire + build-your-own week)
- [x] Leftover P1 — setup you can leave
- [x] Leftover P2 — bodyweight is opt-in
- [ ] Leftover P3 — custom week keeps the questionnaire
- [ ] Leftover P4 — places tell the truth
- [ ] Leftover P5 — controls fit a phone
- [ ] Leftover P6 — compact rows can set load
- [ ] Leftover P7 — confirm honesty
- [ ] Leftover P8 — small polish plus dead code
- [ ] Signed won'ts still won't
