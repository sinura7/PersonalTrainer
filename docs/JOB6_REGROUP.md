# Job 6 — Regroup: polish and intuition

Living plan. **Not the law.** If the phone disagrees, write it under
*Floor findings* and take that instead.

Status: **done** · **next** · *later* · **won't**

This packet is the plan. No Kotlin. No Gradle. No assets.

---

## Where we stand (23 Aug 2026, `trunk` @ `c025570`)

Temper is a **four-tab strength logger** that can generate a week,
log a session, rest, finish, repair history, and survive a restore.
Jobs 1–5 are **code-done on `trunk`**. Job 6 P0 is on `trunk`.
727 JVM tests, 0 failures (before this packet's copy test).

The gym-floor sentence now:

> The week can be started in one tap. The bar names its plates.
> The rest cue is ours. Large type still logs. Settings nags
> when the backup is old. The first rest-permission ask now has
> a gym why. The phone has not judged any of that yet.
> CI still watches a branch that is gone.
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
| CI `on.push` lists `main`, not `trunk` | A push to the shipping branch may never compile | **P1 · Studio** (token has no workflow scope) |
| Lint `continue-on-error` | A red lint never fails the job | *later* — gate it once the baseline is known |
| Instrumented smoke non-gating | Emulator job is written blind | *later* — do not make it a merge gate |
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
| ROADMAP D1 still says five tabs | The signed decision is four. The opening sentence lies. | **this packet** |
| JOB5 “already true” still describes pre-P2 rest and no plates | Agents will rediscover lies | **this packet** |
| JOB5 P5 still says “this PR” | It is merged | **this packet** |
| ROADMAP “known open” still lists Job 5 leftovers as open | They shipped | **this packet** |
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
sex (no sentence) · catalog seed · auto-scaled lighter-week sets ·
WorkManager silent backup · overlay rest clock · `fallbackToDestructiveMigration`.

Unlocking any row needs a floor finding here, then a packet header,
then code.

---

## Recommendation (the sequence I would take)

The app does not need another feature job. It needs a **phone week**,
then a **small intuition pass**, then we stop.

```
Owner phone (Jobs 2–5 gates)
  → P1 CI trunk (Studio; do not sit idle)
    → P2 week-verb language, only if the phone confirms the cliff
      → P3 one sentence before the notification dialog
```

Deviate if:

- The phone wants a last-five-seconds tick — add it in a rest packet,
  do not open a sound library.
- The phone cannot find type-in — strengthen the numeral, do not add
  a Type button.
- A home-gym week actually collapses — reopen catalog seed here.
- The owner writes the sex sentence — then Job 2 P5, not this job.

Do not deviate into Room v3, an LLM, a package rename, or a fifth tab.

---

## Packets

### P0 — This regroup · **done** (this PR)

**Goal.** A stranger can name what Temper is, what is excellent, and
what Job 6 will and will not touch.

**Work.** This file. ROADMAP Job 6 pointer. JOB5 paper settled.
Owner-loop retarget. D1 opening sentence says four tabs.

**Gate.** Reading this file names A–E and the recommended order.

**Won't.** Kotlin. Assets. Editing `ci.yml` (Studio).

### P1 — CI listens for `trunk` · **Studio**

Same work Job 5 / P1 named. Still the same token limit. Do not
block P2 on it.

### P2 — Week verbs a stranger can hold · **after the phone**

**Goal.** Suggest, Replay, Tune, and Lighter week never share a
sentence. Each word means one thing everywhere it appears.

**Work.** Copy only unless a control still lies. Do not merge the
four ideas. Do not hide Lighter week.

**Won't.** A fifth tab. A wizard. Auto-scaled deload sets.

### P3 — Why we need the notification · **this packet**

**Goal.** One in-app sentence before the system permission dialog.

**Work.** `RestNotificationCopy.SENTENCE` in a confirm before
`POST_NOTIFICATIONS`. Continue launches the system dialog. Not now
leaves the existing denial banner as the recovery. No settings
deep-link in the sentence.

**Won't.** A settings deep-link as a substitute for the sentence
(the banner after denial already exists). A sound library. Overlay.

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
   trust (phone, CI) and language (week verbs), not missing screens.
2. **DESIGN_AUDIT is a museum.** Use it for taste, not as a cut list.
3. **Four tabs is the signed IA.** D1's opening "five tabs" was the
   leftover lie. Struck in this packet.
4. **Simple means fewer words for the same four ideas**, not fewer
   ideas. Replay is not Suggest. Lighter is not Tune.

---

## Verification

- [x] This file written; ROADMAP / JOB5 / owner-loop / D1 retarget
- [ ] P1 `ci.yml` lists `trunk` (Studio)
- [ ] Owner phone week
- [ ] P2 week-verb language (only if the phone confirms)
- [x] P3 notification sentence (this packet)
- [ ] Signed won'ts still won't
