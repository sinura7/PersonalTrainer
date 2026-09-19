# Job 2 action plan — the app builds the week

> **Banner (24 Aug 2026).** Code-done historical job. Current program:
> [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md).

Living plan. **Not the law.** If a clearer move shows up, take it, write it
under *Floor findings*, strike the old line. Same method as
[UX_PAGE_PASS.md](UX_PAGE_PASS.md).

Status: **done** · **next** · *later* · **won't**

---

## What this is

Temper has two jobs.

1. **You already know today’s session.** Log sets and reps. Rest on a timer that
   survives the lock screen. Keep a history the body map and progression can trust.
2. **You do not have a workout in mind.** Answer a short path. The app picks the
   days, the rest days, the shape of the week, and the actual lifts. Then job 1
   takes over.

Job 1 is the mature product. This file is how we make job 2 strong enough that
someone with no program still opens Temper — without building a second app, a
server, or a second database.

Room already *is* the database: catalog, routines, pinned slots, every set,
preferences. We extend it. We do not start over.

---

## The product we are aiming at

A first-time (or “I want a new block”) lifter can:

1. Say how experienced they are.
2. Say how many days they train, and **which days are rest days**.
3. Say what kit they have.
4. Say what they are chasing (strength / muscle / athletic / general).
5. Say where they want the work (balanced / upper / lower).
6. See a **preview of the real week with the real lifts**.
7. Tap **Use this plan**. Home can start Monday.

Every new question must change that preview. A question whose answer does not
change a day or a lift is a tax. Height and body type were already cut for that
reason. **Sex is not in this plan** until emphasis is real and we can still
point at a lift or a day that would be different without it. Starting loads are
already typed by the lifter; inventing “men’s vs women’s programs” is a gimmick.

---

## Binding rules

These stay signed. This plan may not weaken them.

- Four tabs. Library stays pushed. No LLM trainer. No head-level anatomy.
- One Room file, version **2**. Additive only. Never `fallbackToDestructiveMigration`.
- Never invent a schema v3 identityHash by hand. If a column is required, it is
  a real migration with a generated schema — and that is its own gate, not
  folded into a UI packet.
- Weights in kg. Display via `LocalWeightUnit`.
- Setup writes **nothing** until Use this plan. Re-run from Settings deletes
  **nothing** (existing `OnboardingApplier` rule).
- The split is still **derived**, never asked. Frequency first, then recovery.
- Same `ExercisePickerSheet`. Same `WeekStrip`. Same `AddDefaults`.
- Volt = live / act only. Preview’s one filled button is Use this plan.
- Drive folder and package stay as they are.

---

## How to execute

One packet at a time. A packet is done when its **gate** is true, not when the
diff looks finished.

After every packet:

1. `./gradlew testDebugUnitTest` — 0 failures.
2. `./gradlew assembleDebug`.
3. Commit, push, update the PR.
4. Update this file (status + any floor finding).
5. Phone check for that gate. Do not run `connectedDebugAndroidTest` on the
   owner’s real `applicationId`.

Do not restyle the stack in one pass. Do not open plate calculator, rest-sound
design, font-scale 2.0, scheduled backup, or a fifth tab inside these packets.

---

## Architecture we already have (do not rebuild)

| Piece | Where | Job |
|---|---|---|
| Answers | `domain/OnboardingAnswers.kt` | Six fields. Each one already changes the plan. |
| Split | `domain/SplitDerivation.kt` | Days + experience + place + goal → PPL / UL / full body. |
| Templates | `domain/RoutineGenerator.kt` | Families per `SessionFocusKind`. Catalog answers with a lift they can actually do. |
| Preview | `ui/onboarding/OnboardingScreen.kt` | Real week, real lifts, then Use this plan. |
| Write | `data/repository/OnboardingApplier.kt` | Prefs → routines → pins → block. No deletes. |
| Days later | `domain/WeeklySchedulePlanner.kt` + Plan tab | Suggest fills for open days. Does not invent a ghost week. |
| Coach order | `domain/RecommendationEngine.kt` + `TrainingGoal` | Strength / muscle / general only ranks advice. Rules stay the same. |
| Catalog | Room `exercises` + `exercise_muscles` | ~98 built-ins, equipment, `movementKey`. |
| Kit filter | `CoachPreferences.availableEquipment` | Empty = no filter. Place maps to a set. |

If a packet can be a new field on `OnboardingAnswers` plus a template change,
that is the whole packet. Do not add a service.

---

## Better idea than the first instinct (take this)

The first instinct is “build a database that processes athletic / upper-bias /
male-female.” That is the wrong first goal.

**The first goal is: the preview tells the truth, and two new answers change it.**

Emphasis and Athletic are those answers. They reuse `RoutineGenerator` and
`SplitDerivation`. They do not need Room v3 if we store them as preferences
strings the way `TrainingGoal` already is (DataStore, already backed up).

Sex waits. A second catalog waits. A server waits.

If, while building, Athletic wants different *days* (more full-body, fewer
isolation days) rather than different *lifts*, change the split derivation —
that is a better floor move than stuffing jumps into a Push template.

---

## Packets

### P0 — Imagery: one body, both sides · **done** (code on `trunk` · phone pending)

**Goal.** Front, back, thumbs, empty mark, and the launcher language read as
the same figure. High quality still means **plates**, not a photograph and not
head-level anatomy.

**Why first.** Body and the catalog are how the lifter *sees* the program we
are about to generate. A better generator on a crude silhouette still looks
unfinished. This packet does not change training science.

**Work**

- One shared structure (head, neck, forearms, feet) used by both views so the
  flip cannot drift.
- More plates per canonical muscle (traps + lats as `BACK`, upper/mid/lower
  `CORE`, inner/outer quad, glute shelf). Same ten muscles. No new enum cases.
- Outer silhouette of delts, arms, calves matches front ↔ back.
- Thumbs keep lighting primary at Heat3, secondaries dimmer, identity not live
  heat.
- Equipment glyphs stay filled plates. Refine only if they fight the new figure.
- Tests: every working muscle still has a plate on its thumb view; no plate
  leaves the box; Temper accent remains the viewer-right pec; structure plates
  are identical objects on front and back.

**Gate.** Flip Front/Back on Body: same person. Open Library: a bench thumb is
a front chest, a row thumb is a back. Empty History still shows the same mark.
Launcher icon is unchanged (approved PNG) unless we later redraw it from the
new plates on purpose — not in this packet.

**Won't.** Commissioned bitmaps. Per-exercise photos. Head-level delts.
Changing the launcher PNG in the same commit as the Compose plates (two
reviews).

---

### P1 — Emphasis (upper / lower / balanced) · **done** (code in #12 / #13 · phone pending)

**Goal.** “I care about the upper body” is a real lever. It changes the week
you see.

**Store.** Add `Emphasis` (`BALANCED`, `UPPER`, `LOWER`) to `OnboardingAnswers`
and `CoachPreferences` (DataStore key, backup field). Default `BALANCED` so
existing installs do not change.

**Rules (write these as pure Kotlin tests first)**

- `BALANCED` — today’s templates and today’s split. No behavior change.
- `UPPER` — prefer an extra upper or push/pull slot over a second hard leg day
  when days ≥ 4. On a 3-day full-body week, swap one lower-priority lower slot
  for an upper family (press or row), not a whole stolen leg day.
- `LOWER` — the inverse. Never drop the squat/hinge family from a lower or
  full-body session.
- Rest days the user picked stay rest days. Emphasis does not invent a sixth
  training day.

**UI**

- One new setup screen after Goal: “Where do you want the work?” Three
  choices, each with a one-line blurb that names the consequence (“More
  pressing and pulling. Legs stay in the week.”).
- Preview kicker states the emphasis so the week is explained.
- Settings → Coaching gets the same three chips. Changing it does **not**
  rewrite pinned days. It changes the next Suggest / the next setup run.

**Files.** `OnboardingAnswers.kt`, `CoachPreferences.kt`, `RoutineGenerator.kt`,
`SplitDerivation.kt` (only if days+emphasis should change the split),
`OnboardingScreen.kt`, `OnboardingViewModel.kt`, `OnboardingApplier.kt`,
`PreferencesRepository.kt`, backup codec + validator tests, `PlanReviewRenderer`
artifact if the review matrix includes the new axis.

**Gate.** Setup with 4 days + UPPER produces a week whose training days are
majority upper/push/pull, still has at least one lower or full-body day, and
Home can start it. 4 days + LOWER is the mirror. BALANCED matches today’s
fixture for the same other answers.

**Won't.** Asking sex instead of this. A second catalog.

---

### P2 — Athletic as a fourth goal · **done** (code in #13 · phone pending)

**Goal.** “I train for sport” is not “I train for size.” The preview’s lifts
change.

**Store.** Add `TrainingGoal.ATHLETIC` (“Athletic”, “Power, unilateral work,
less isolation”). Backup-tolerant: unknown goal still falls back to `GENERAL`.

**Rules**

- Athletic does **not** get a different rule set in the coach. It gets a
  different template bias: more hinge, carry, lunge, step-up, landmine /
  kettlebell families; fewer isolation-only slots (curl / fly / lateral as last
  resort).
- Split: prefer upper/lower or full-body over six-way PPL (recovery). Encode
  that in `SplitDerivation`, with a test.
- `AddDefaults` stay per load-type. Do not invent a second increment table.
- Catalog already has the families. Do not add a sport-science library in this
  packet.

**UI.** Goal step gains the fourth chip. Preview names “Athletic” in the
summary line. Settings coaching chips include it.

**Gate.** Same days + kit + NEW, Athletic vs Muscle: at least two slot
families differ on the first training day. Split is not PPL at 5 days for a
new Athletic lifter.

**Won't.** Conditioning clock. Running. CrossFit WODs. An LLM that “coaches
the sport.”

---

### P3 — Preview that explains itself · **done** (code in #13 · phone pending)

**Goal.** The lifter can see *why* Tuesday is Upper before they accept.

**Work**

- Preview header: “4 days · Upper emphasis · Athletic · Dumbbells.”
- Each day row: rest vs routine name + first three lifts (already partly
  there — make it the same lift voice as Home).
- One line under the week: “Rest days stay rest days. You can swap a lift
  after you accept.”
- Own-path (“I’ll build my own”) stays one tap, still lands on editor `new`.

**Gate.** A stranger can screenshot the preview and know what they are
accepting without opening a routine.

---

### P4 — Job 2 after week one · **done** (code in #13 · phone pending)

**Goal.** The builder is not only a first-run screen.

**Work**

- Plan tab empty week already has volt Suggest. Keep it.
- Settings re-run setup already exists. Add a line: “This adds a new block. It
  does not delete history.”
- When Suggest runs, pass current emphasis into the planner so a later fill
  matches the person, not a default ghost. Athletic / kit change the **next
  generated** week (setup re-run), not a Suggest rewrite of existing routines.
- Do not auto-reshape a pinned week because they changed emphasis in
  Settings. Pins are owned. Suggest is the offer.

**Gate.** Change emphasis in Settings. Unpin two open days. Suggest. Those
days match the new emphasis. Existing pins are untouched.

---

### P5 — Sex, only if still needed · *later* (maybe never)

**Goal.** Only if P1–P2 shipped and we can still name a lift or a default that
must differ.

**Test for adding it.** Write the sentence: “If they pick Female, *this* slot
becomes *that* family, because _____.” If the sentence is “women should hip
thrust more,” that is **LOWER or glute bias**, which is P1, not sex. If the
sentence is “starting squat is 40 kg vs 60 kg,” that is a **typed first load**,
which the log already handles on session one.

**Won't until that sentence exists.** A gendered catalog. Pink vs blue. A
hormone lecture.

---

### P6 — Catalog depth for the new templates · **won't** (for now)

**Goal.** Athletic and emphasis do not resolve to the same five lifts for
every home-gym user.

**Work.** Audited during P2. The families were already in the catalog. Do not
add a seed bump for a hole that is not there. If a later Athletic week
collapses to five lifts for a real home-gym user, reopen this packet.

**Gate.** Bodyweight-only + Athletic still produces ≥ 4 lifts on a full-body
day. Home-dumbbells + UPPER does not emit a barbell-only family with no
alternate.

---

## Explicitly not this plan

| Item | Why |
|---|---|
| New Room database / server | We have one. |
| LLM trainer | Signed non-goal. Offline promise. |
| Head-level anatomy | Cannot be derived from set logs. |
| Plate calculator, rest sound, font 2.0 | Different jobs. Easy to make the gym worse. |
| Scheduled auto-backup | Survival, not job 2. |
| Debug `applicationId` split | Safety rail. Do it when we next touch Gradle, not inside a generator packet. |
| Package / Drive rename | Breaks identity. |

---

## Suggested order (and when to deviate)

```
P0 imagery
  → P1 emphasis
    → P2 athletic
      → P3 preview copy
        → P4 later fills honour prefs
          → P6 catalog holes
            → P5 sex only if a real sentence exists
```

Deviate if:

- P0 on the phone still looks like a toy — stay on P0.
- Emphasis wants a split change more than a template change — change
  `SplitDerivation`, record the finding.
- Athletic is indistinguishable from Muscle in the catalog you have — do P6
  before finishing P2, not after.
- The owner’s phone says setup is already too long — cut a question, do not
  add sex.

---

## Phone gates (owner)

After each packet that ships UI:

- P0: Body flip + Library thumbs + empty mark.
- P1: New install or Settings → guided setup. 4-day UPPER vs LOWER.
- P2: Same, Athletic vs Muscle.
- P3: Screenshot the preview. Can a stranger read it?
- P4: Change emphasis, Suggest two open days, pins stay.

---

## Floor findings

1. **Do not build a second database.** Room + DataStore already persist the
   person. Job 2 is answers → generator → pins.
2. **Do not ask sex first.** Emphasis is the lever people actually mean.
3. **Imagery before generator depth.** The body is how the program is seen.
   Shared structure plates beat two independently-tuned silhouettes.
4. **Pins stay owned.** A preference change is not a rewrite of the week.
5. **P6 is not needed yet.** `carry`, `step-up`, and `kettlebell-swing` are
   already in the catalog. Bodyweight Athletic still fills ≥ 4 lifts; home
   dumbbells never emit a barbell-only family with no alternate.
6. **Suggest remaps days, it does not regenerate lifts.** Emphasis changes
   which open days Suggest fills. Athletic templates apply when you generate
   a week (setup / re-run). A Settings goal change does not rewrite routines.
7. **Week two with pins is already done.** Rule 6 resets satisfaction, not
   slots. The remaining hole — empty week, routines still present — is Job 3.
   Do not reopen this file for it.

---

## Verification

- [x] `./gradlew testDebugUnitTest` — 671 tests, 0 failures (merged `trunk`, 22 Aug 2026)
- [x] `./gradlew assembleDebug`
- [x] This file updated if we left the written line
- [ ] Phone gates P0–P4 (owner, at home). Code is on `trunk`.

What comes after this file: [JOB3_ACTION_PLAN.md](../archive/jobs/JOB3_ACTION_PLAN.md).
