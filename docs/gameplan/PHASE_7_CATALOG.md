# Phase 7 — Catalog to ~98 + Library UX

> Executor packet. Standalone except for `docs/gameplan/PROTOCOL.md` (branch/PR/sign-off rules — read it first, follow it exactly). Branch: `claude/phase-7-catalog`. One PR. The phase closes only on owner sign-off of §8.

## 1. Mission

Grow the seeded catalog from 37 movements (batch 1, upgraded in place by Phase 3) to 98 curated movements via two staged seed bumps, and make the Library and picker survive that scale: family grouping by `movementKey`, equipment chips, recency/prominence ordering, alias-aware and wildcard-safe search. This phase also owns three contract cleanups the catalog unlocks: the muscle-filter route flips to canonical keys end-to-end, per-loadType add-to-routine defaults replace the hardcoded 3×5/90, and the per-loadType increment table replaces the global 2.5 kg (the recorded "+5.5 lbs" defect, ROADMAP.md:149). It ships now because Phases 3–6 built the schema, seeder, heat semantics, and navigation this content lands on — content last was the plan's explicit value ordering.

## 2. Read first

1. `docs/gameplan/PROTOCOL.md` — execution protocol: branch truth, PR-per-phase, preflight, owner checkpoints.
2. `docs/gameplan/PHASE_3_*.md` completion state **as shipped in code** (see item 3) — Phase 7 inherits Phase 3's substrate; the shipped code wins over this packet's assumptions wherever they differ.
3. The Phase-3-touched data layer as it now exists on the branch: `app/src/main/java/com/sinura/personaltrainer/data/local/entity/ExerciseEntity.kt`, the junction entity/DAO for `exercise_muscles`, the seeder (versioned, mutex-guarded — evolved from `ExerciseRepository.seedDefaultsIfEmpty`, ExerciseRepository.kt:47-51, launched at PersonalTrainerApp.kt:33-40), `seed_meta`, and the catalog source (evolved from `domain/DefaultExercises.kt`). Learn the exact shapes: field names, the catalog-row type, `CATALOG_VERSION`, the review-artifact generator, the collision (`nameKey`) mechanics.
4. `app/src/main/java/com/sinura/personaltrainer/domain/DefaultExercises.kt` — the 37 names and the id slug rule (`"ex-" + kebab(name)`, DefaultExercises.kt:44-47). Built-in ids are never re-slugged.
5. `app/src/main/java/com/sinura/personaltrainer/data/local/dao/ExerciseDao.kt` — the unescaped LIKE search (ExerciseDao.kt:19-27).
6. `app/src/main/java/com/sinura/personaltrainer/ui/library/ExerciseLibraryViewModel.kt` — in-memory filter (70-77), free-string group chips (103-109), hardcoded 3/5/null/90 add (238-245).
7. `app/src/main/java/com/sinura/personaltrainer/ui/library/ExerciseLibraryScreen.kt` — chip row (137-156), list body (186-208), `initialMuscle` handling (79-83). Plus `ExerciseEditorSheet.kt` (rename path for collisions).
8. `app/src/main/java/com/sinura/personaltrainer/ui/components/ExercisePickerSheet.kt` — shared row anatomy; `ExerciseRow` already has a `tag` slot (167-218) and the reserved 40dp thumb (278-302); create-inline gate (87).
9. `app/src/main/java/com/sinura/personaltrainer/ui/navigation/AppNav.kt` — `Route.Library` + `create(muscle)` (114-118), registration (272-287), the two `Library.create` call sites (244-252, 259-267). **Phase 6a/6b re-plumbed navigation — re-grep `Route.Library.create` and retarget whatever exists now.**
10. `app/src/main/java/com/sinura/personaltrainer/ui/progress/RecommendationCards.kt` — the `catalogLabel` string hop to delete (77) and `actionLabel` (56-66); `ProgressScreen.kt:190-202, 318` and `HomeScreen.kt:174-181` (dispatch call sites — same Phase-6 caveat).
11. `app/src/main/java/com/sinura/personaltrainer/domain/CanonicalMuscle.kt` — alias index (122-129), `matchesFilter` (148-152).
12. `domain/ProgressionCalculator.kt` (INCREMENT_KG :11), `domain/WeightFormat.kt` (`WeightUnit.step` :12-25, `incrementKg` :55-58), `domain/RecommendationEngine.kt:202-228`, `ui/workout/ActiveWorkoutScreen.kt:129` — the three increment systems that must become one.
13. `data/repository/WorkoutRepository.kt` — `addExerciseToSession` defaults (114-138), `progressionFor` (259-277), `readyForProgression` (394-415); `data/repository/RoutineRepository.kt:54-78` (`addExercise`).
14. `ui/routines/RoutineEditorScreen.kt` — picker wiring (196-221), `RoutineExerciseCard` (330-419), `NEW_LIFT_TARGETS` (504); `RoutineEditorViewModel.kt:256-315`.
15. The Phase-5-shipped mid-workout swap/remove UI (current-lift overflow) — Phase 7 extends its sheet, never rebuilds it.
16. `docs/DESIGN_AUDIT.md` §6.7, §7, §9; `docs/UI_REDESIGN.md` §5.1, §6, §8 + Appendix A; `docs/ui-redesign/DIRECTION_B_INSTRUMENT.md` §8 (Library spec); `docs/DEVELOPMENT.md` (static checks, :59-65).

## 3. Binding doctrine

- **DESIGN_AUDIT** L-03 (equipment chips), L-05 (add defaults "3×5 with no preview"), L-07 (search+chips scale), L-09 ("Other" junk bucket — picker-created customs), L-10 (missing machines: leg press variants, hack squat, pec deck, seated row machine, hip abduction, preacher — all land in the tail), §7 (equipment/loadType field semantics), §9 (machines are a format: `Leg Press · Machine`, equipment sections in the add sheet). §8's image spec is **Phase 8**, not here.
- **UI_REDESIGN** §5.1 (Instrument tokens — chips `r/xs`, volt-dim selected fill; no new colors, no inline radii), §6 (the library's job is feeding routines and the picker), §8 guardrails plus Appendix A findings M-08/M-10 (container grammar, icon-chrome restraint) stay satisfied.
- **DIRECTION_B_INSTRUMENT** §8 "Library": search field as `surface/1` hairline bar, muscle chips volt-dim when selected, 40dp leading slot reserved. §4 touch floors (48dp min, 56dp chip rows).
- **REVISED_STRUCTURE** (the binding brief): Phase 7 scope (its phase list), item 11 (skip-and-surface, no merge tool), item 13 (staged tail at lower scrutiny), item 16 (seeder never resolves collisions; maintenance mutex), item 17 (junction keys normalize via alias index; `muscleGroup` survives as display text), item 19 + INCREMENT TABLE block (per-loadType increments; BODYWEIGHT suppresses add-weight hints; route param flips to canonical key **in this phase, end-to-end**).
- **attacks.md** constraints this packet must visibly satisfy: integration finding "Library muscle-filter route contract breaks silently" (work item 3 enumerates every call site and deletes the RecommendationCards.kt:77 hop); scope findings "98-at-once maximizes silent error" (two batches, each with a review artifact) and "merge tool cut" (work item 4 is rename/keep-both only); integration finding "two increment systems already disagree" (work item 5 names all three sites).
- **Mechanical proof**: `tools/preflight.sh` green before every push; cite `tools/check-when-exhaustive.py` and `tools/check-screen-wiring.py` output in the PR wherever enums or callbacks change (DEVELOPMENT.md:59-65).

## 4. Settled decisions

Stated as settled. Do not reopen.

1. **Substrate (from Phase 3, do not modify):** `exercises` carries `equipment` (BARBELL, DUMBBELL, CABLE, MACHINE, SMITH, KETTLEBELL, BAND, BODYWEIGHT, OTHER), `loadType` (EXTERNAL, STACK, BODYWEIGHT, BODYWEIGHT_PLUS, ASSISTED), `movementKey`, `imageKey?` (null = composed, Phase 8), `nameKey` (+ plain index). `exercise_muscles(exerciseId, muscleKey TEXT, weight REAL)`. `seed_meta(catalogVersion)`. Seeding/restore/collision detection run behind the DB-maintenance mutex; the seeder inserts built-ins unconditionally and never resolves collisions. If shipped names differ, the shipped code wins — adapt this packet's identifiers, record the mapping in the hand-back.
2. **Weight model:** primary credit weight = 1.0 (the Phase-5 band model counts a primary set as one set). Secondaries ∈ {0.25, 0.5}; sum of secondaries per exercise ≤ 1.0. Every tail row below conforms. If Phase 3's shipped invariant test encodes a stricter rule, rescale **secondaries** proportionally, never primaries, and note it.
3. **`muscleKey` vocabulary (tail):** lowercase canonical labels — `chest, back, shoulders, biceps, triceps, quads, hamstrings, glutes, calves, core`. All resolve via the alias index (CanonicalMuscle.kt:122-129; "quads" → QUADRICEPS at :60). Never invent a key that normalizes to OTHER.
4. **`isCompound(exercise)`** := any junction secondary has weight ≥ 0.5. Pure derivation, no column.
5. **`sortRank` and `searchTerms` are code-side catalog metadata, NOT columns** — no schema change in this phase (out-of-scope rule). They live in the authored catalog source; expose id-keyed lookups `CatalogMeta.sortRank(id): Int` (default `Int.MAX_VALUE` for customs) and `CatalogMeta.searchTerms(id): Set<String>` (empty for customs) in `domain/`.
6. **Ordering:** Library, empty query, no filter → grouped by `movementKey`, families ordered by min member sortRank, members by sortRank; single-member families render as plain rows (no header). Library with query or filter → flat, sortRank then name. Picker, empty query → flat, last-logged desc (never-logged last) then sortRank. Picker with query → flat, sortRank then name. Recency comes from a new observed DAO query (work item 2) — no schema change.
7. **Search:** the DAO LIKE gains `ESCAPE '\\'` with a tested `LikeEscaper`; `ExerciseRepository.search` unions alias hits from `CatalogMeta.searchTerms`; the Library VM's in-memory filter gains the same alias matching. Literal shapes in work item 2.
8. **Route contract:** `Route.Library.create` takes `CanonicalMuscle?`; the query param value is the **enum name** (e.g. `QUADRICEPS`). Parse with `CanonicalMuscle.entries.firstOrNull { it.name == raw }`, falling back to `MuscleNormalizer.primaryOf(raw)` for any stale string. The `catalogLabel` hop at RecommendationCards.kt:77 is deleted. Filtering is junction-based: an exercise matches the selected muscle if any of its junction rows normalizes to it; customs without junction rows fall back to `MuscleNormalizer.primaryOf(muscleGroup)`.
9. **Collisions are derived, and there is NO merge tool** (REVISED_STRUCTURE item 11; the FK re-pointing merge is cut — state this in the PR too). A collision = a custom row whose `nameKey` equals a built-in's `nameKey`. Actions: **Rename mine** (opens the existing editor sheet) and **Keep both** (dismiss). Dismissals persist as a DataStore string-set preference `library_collision_dismissed_ids` in `PreferencesRepository`. Dismissals survive restores; a re-detected collision after restore stays hidden if dismissed — accepted, noted in hand-back. No history FK is ever rewritten.
10. **Add-to-routine defaults** (replaces every hardcoded 3/5/null/90) — `AddDefaults.forExercise(loadType: LoadType?, isCompound: Boolean): TargetDefaults`, plus a convenience `AddDefaults.forExercise(exercise: Exercise)` that derives both (null or unknown loadType → the fallback row); `targetWeightKg` always null; fallback for unknown/custom = EXTERNAL isolation row:

    | loadType | compound? | sets×reps | rest |
    |---|---|---|---|
    | EXTERNAL | yes | 3×5 | 150 s |
    | EXTERNAL | no | 3×10 | 90 s |
    | STACK | yes | 3×10 | 90 s |
    | STACK | no | 3×12 | 60 s |
    | BODYWEIGHT_PLUS | any | 3×6 | 120 s |
    | BODYWEIGHT | yes | 3×8 | 90 s |
    | BODYWEIGHT | no | 3×12 | 60 s |
    | ASSISTED | any | 3×8 | 90 s |

11. **Increment table** (REVISED_STRUCTURE, verbatim): step comes from `(loadType, displayUnit)` — EXTERNAL/STACK/BODYWEIGHT_PLUS/ASSISTED: kg → 2.5 kg, lbs → 5 lb (2.26796 kg applied); BODYWEIGHT → **no add-weight hints**, rep-progression copy instead. One `IncrementTable` object replaces `ProgressionCalculator.INCREMENT_KG` AND the coach copy quoting it; `WeightUnit.step` reads it. Three sites must agree (work item 5).
12. **Catalog versioning:** batch 2 = `CATALOG_VERSION` N+1, batch 3 = N+2 (N = Phase 3's shipped value). One commit per batch; each batch regenerates a review artifact `docs/catalog/batch-<n>-review.md` with Phase 3's generator (family-grouped table + invariant results + diff against the previous version). Batches land "at lower scrutiny" than batch 1 — the owner reviews the artifacts during PR review, not row-by-row on device.
13. **Curation counts** (the content-critic's numbers; buckets are curation buckets, not necessarily junction primaries — e.g. the Hinge bucket's primaries are back/glutes):

    | Bucket | batch 1 | tail | total |
    |---|---|---|---|
    | Chest | 5 | 7 | 12 |
    | Back | 7 | 8 | 15 |
    | Hinge | 2 | 3 | 5 |
    | Shoulders | 4 | 7 | 11 |
    | Biceps | 2 | 6 | 8 |
    | Triceps | 3 | 5 | 8 |
    | Quads | 7 | 5 | 12 |
    | Hamstrings | 2 | 5 | 7 |
    | Glutes | 1 | 5 | 6 |
    | Calves | 1 | 3 | 4 |
    | Core | 3 | 7 | 10 |
    | **Total** | **37** | **61** | **98** |

14. **movementKey vocabulary + family labels** (the whole 98): `squat`→Squat, `lunge`→Lunge, `step-up`→Step-Up, `leg-press`→Leg Press, `leg-extension`→Leg Extension, `deadlift`→Deadlift, `romanian-deadlift`→Romanian Deadlift, `good-morning`→Good Morning, `back-extension`→Back Extension, `kettlebell-swing`→Swing, `hip-thrust`→Hip Thrust, `hip-abduction`→Hip Abduction, `glute-kickback`→Kickback, `pull-through`→Pull-Through, `leg-curl`→Leg Curl, `nordic-curl`→Nordic Curl, `calf-raise`→Calf Raise, `bench-press`→Bench Press, `chest-fly`→Chest Fly, `push-up`→Push-Up, `dip`→Dip, `overhead-press`→Overhead Press, `lateral-raise`→Lateral Raise, `rear-delt`→Rear Delt, `row`→Row, `pulldown`→Pulldown, `pull-up`→Pull-Up, `pullover`→Pullover, `shrug`→Shrug, `curl`→Curl, `triceps-extension`→Triceps Extension, `plank`→Plank, `crunch`→Crunch, `sit-up`→Sit-Up, `leg-raise`→Leg Raise, `rollout`→Rollout, `twist`→Twist, `dead-bug`→Dead Bug, `carry`→Carry.
    Batch-1 expected keys (align the tail to **shipped** batch-1 values if they differ): Back/Front/Goblet Squat→`squat`; Bulgarian Split Squat, Walking Lunge→`lunge`; Leg Press→`leg-press`; Leg Extension→`leg-extension`; Conventional/Trap Bar Deadlift→`deadlift`; Romanian Deadlift→`romanian-deadlift`; Hip Thrust→`hip-thrust`; Leg Curl→`leg-curl`; Standing Calf Raise→`calf-raise`; Barbell/Incline/Dumbbell Bench Press + Close-Grip Bench Press→`bench-press`; Push-Up→`push-up`; Chest Fly→`chest-fly`; Overhead Press, Seated Dumbbell Press→`overhead-press`; Lateral Raise→`lateral-raise`; Face Pull→`rear-delt`; Barbell/Pendlay/One-Arm Dumbbell Row, Seated Cable Row→`row`; Lat Pulldown→`pulldown`; Pull-Up, Chin-Up→`pull-up`; Barbell/Dumbbell Curl→`curl`; Tricep Pushdown, Skull Crusher→`triceps-extension`; Plank→`plank`; Hanging Leg Raise→`leg-raise`; Cable Crunch→`crunch`.
15. **sortRank for the 37** (assigned here — sortRank is Phase-7 metadata): Barbell Back Squat 100, Conventional Deadlift 105, Barbell Bench Press 110, Overhead Press 115, Barbell Row 120, Pull-Up 125, Romanian Deadlift 130, Front Squat 135, Incline Bench Press 140, Dumbbell Bench Press 145, Lat Pulldown 150, Seated Cable Row 155, Chin-Up 160, Hip Thrust 165, Leg Press 170, Bulgarian Split Squat 175, Walking Lunge 180, Goblet Squat 185, Trap Bar Deadlift 190, Seated Dumbbell Press 195, Lateral Raise 200, Face Pull 205, Pendlay Row 210, One-Arm Dumbbell Row 215, Leg Curl 220, Leg Extension 225, Standing Calf Raise 230, Barbell Curl 235, Dumbbell Curl 240, Tricep Pushdown 245, Skull Crusher 250, Close-Grip Bench Press 255, Chest Fly 260, Push-Up 265, Plank 270, Hanging Leg Raise 275, Cable Crunch 280.
    High-value searchTerms for batch 1: `ex-overhead-press` {ohp, military press, strict press}; `ex-romanian-deadlift` {rdl}; `ex-conventional-deadlift` {deadlift, dl}; `ex-barbell-back-squat` {squat}; `ex-barbell-bench-press` {bench, bp}; `ex-lat-pulldown` {pulldown}; `ex-skull-crusher` {lying triceps extension, french press}; `ex-tricep-pushdown` {rope pushdown, cable pushdown}; `ex-hanging-leg-raise` {hlr}.

## 5. Work items

### WI-1 — The tail catalog: 61 authored rows in two seed bumps

**Build:** append the rows below to the catalog source (the Phase-3 evolution of `domain/DefaultExercises.kt`), in two commits: batch 2 (33 upper-body rows, `CATALOG_VERSION` = N+1) then batch 3 (28 lower/core rows, N+2). ids from the existing slug rule (DefaultExercises.kt:44-47). `muscleGroup` display text = the primary's `catalogLabel` (keeps `MuscleGroups.presentIn`, ExerciseUsage.kt:43-47, coherent). `imageKey` = null everywhere. `notes` = "" (L-04 setup cues are welcome but optional — never blocking). Primary weight 1.0; secondaries as listed. Regenerate the review artifact after each batch.

**Files:** catalog source (Phase 3's), `docs/catalog/batch-2-review.md`, `docs/catalog/batch-3-review.md`, extended catalog invariant test.

**Batch 2 — upper (33 rows, sec = secondaries, terms = searchTerms):**

| Name | id | movementKey | equip | load | primary | sec | terms | rank |
|---|---|---|---|---|---|---|---|---|
| Incline Dumbbell Bench Press | ex-incline-dumbbell-bench-press | bench-press | DUMBBELL | EXTERNAL | chest | shoulders@0.5, triceps@0.5 | incline db press | 300 |
| Machine Chest Press | ex-machine-chest-press | bench-press | MACHINE | STACK | chest | triceps@0.5, shoulders@0.25 | chest press machine | 305 |
| Dip | ex-dip | dip | BODYWEIGHT | BODYWEIGHT_PLUS | chest | triceps@0.5, shoulders@0.25 | chest dip, weighted dip | 310 |
| Cable Fly | ex-cable-fly | chest-fly | CABLE | STACK | chest | shoulders@0.25 | cable crossover | 315 |
| Pec Deck | ex-pec-deck | chest-fly | MACHINE | STACK | chest | — | seated fly, butterfly | 320 |
| Decline Bench Press | ex-decline-bench-press | bench-press | BARBELL | EXTERNAL | chest | triceps@0.5 | — | 325 |
| Smith Machine Bench Press | ex-smith-machine-bench-press | bench-press | SMITH | EXTERNAL | chest | triceps@0.5, shoulders@0.25 | smith bench | 330 |
| T-Bar Row | ex-t-bar-row | row | BARBELL | EXTERNAL | back | biceps@0.5 | tbar | 335 |
| Machine Seated Row | ex-machine-seated-row | row | MACHINE | STACK | back | biceps@0.5 | row machine | 340 |
| Chest-Supported Dumbbell Row | ex-chest-supported-dumbbell-row | row | DUMBBELL | EXTERNAL | back | biceps@0.5 | seal row | 345 |
| Inverted Row | ex-inverted-row | row | BODYWEIGHT | BODYWEIGHT | back | biceps@0.5, core@0.25 | bodyweight row | 350 |
| Close-Grip Lat Pulldown | ex-close-grip-lat-pulldown | pulldown | CABLE | STACK | back | biceps@0.5 | neutral grip pulldown | 355 |
| Straight-Arm Pulldown | ex-straight-arm-pulldown | pullover | CABLE | STACK | back | triceps@0.25 | lat prayer | 360 |
| Barbell Shrug | ex-barbell-shrug | shrug | BARBELL | EXTERNAL | back | — | traps | 365 |
| Dumbbell Shrug | ex-dumbbell-shrug | shrug | DUMBBELL | EXTERNAL | back | — | — | 370 |
| Push Press | ex-push-press | overhead-press | BARBELL | EXTERNAL | shoulders | triceps@0.5, quads@0.25 | — | 375 |
| Arnold Press | ex-arnold-press | overhead-press | DUMBBELL | EXTERNAL | shoulders | triceps@0.5 | — | 380 |
| Machine Shoulder Press | ex-machine-shoulder-press | overhead-press | MACHINE | STACK | shoulders | triceps@0.5 | shoulder press machine | 385 |
| Cable Lateral Raise | ex-cable-lateral-raise | lateral-raise | CABLE | STACK | shoulders | — | side raise | 390 |
| Machine Lateral Raise | ex-machine-lateral-raise | lateral-raise | MACHINE | STACK | shoulders | — | — | 395 |
| Reverse Pec Deck | ex-reverse-pec-deck | rear-delt | MACHINE | STACK | shoulders | back@0.25 | reverse fly machine | 400 |
| Dumbbell Rear-Delt Fly | ex-dumbbell-rear-delt-fly | rear-delt | DUMBBELL | EXTERNAL | shoulders | back@0.25 | reverse fly, bent over fly | 405 |
| EZ-Bar Curl | ex-ez-bar-curl | curl | BARBELL | EXTERNAL | biceps | — | ez curl | 410 |
| Hammer Curl | ex-hammer-curl | curl | DUMBBELL | EXTERNAL | biceps | — | — | 415 |
| Preacher Curl | ex-preacher-curl | curl | BARBELL | EXTERNAL | biceps | — | — | 420 |
| Incline Dumbbell Curl | ex-incline-dumbbell-curl | curl | DUMBBELL | EXTERNAL | biceps | — | — | 425 |
| Cable Curl | ex-cable-curl | curl | CABLE | STACK | biceps | — | — | 430 |
| Machine Bicep Curl | ex-machine-bicep-curl | curl | MACHINE | STACK | biceps | — | preacher machine | 435 |
| Overhead Cable Triceps Extension | ex-overhead-cable-triceps-extension | triceps-extension | CABLE | STACK | triceps | — | overhead extension | 440 |
| Overhead Dumbbell Triceps Extension | ex-overhead-dumbbell-triceps-extension | triceps-extension | DUMBBELL | EXTERNAL | triceps | — | french press | 445 |
| Machine Triceps Extension | ex-machine-triceps-extension | triceps-extension | MACHINE | STACK | triceps | — | — | 450 |
| Diamond Push-Up | ex-diamond-push-up | push-up | BODYWEIGHT | BODYWEIGHT | triceps | chest@0.5 | close grip pushup | 455 |
| Bench Dip | ex-bench-dip | dip | BODYWEIGHT | BODYWEIGHT | triceps | chest@0.5, shoulders@0.25 | — | 460 |

**Batch 3 — lower + core (28 rows):**

| Name | id | movementKey | equip | load | primary | sec | terms | rank |
|---|---|---|---|---|---|---|---|---|
| Hack Squat | ex-hack-squat | squat | MACHINE | EXTERNAL | quads | glutes@0.5 | — | 500 |
| Smith Machine Squat | ex-smith-machine-squat | squat | SMITH | EXTERNAL | quads | glutes@0.5 | smith squat | 505 |
| Reverse Lunge | ex-reverse-lunge | lunge | DUMBBELL | EXTERNAL | quads | glutes@0.5, hamstrings@0.25 | — | 510 |
| Dumbbell Step-Up | ex-dumbbell-step-up | step-up | DUMBBELL | EXTERNAL | quads | glutes@0.5 | step up | 515 |
| Bodyweight Squat | ex-bodyweight-squat | squat | BODYWEIGHT | BODYWEIGHT | quads | glutes@0.5 | air squat | 520 |
| Sumo Deadlift | ex-sumo-deadlift | deadlift | BARBELL | EXTERNAL | glutes | back@0.5, quads@0.5 | sumo | 525 |
| Kettlebell Swing | ex-kettlebell-swing | kettlebell-swing | KETTLEBELL | EXTERNAL | glutes | hamstrings@0.5, back@0.25 | kb swing | 530 |
| Back Extension | ex-back-extension | back-extension | BODYWEIGHT | BODYWEIGHT_PLUS | back | glutes@0.5, hamstrings@0.5 | hyperextension | 535 |
| Seated Leg Curl | ex-seated-leg-curl | leg-curl | MACHINE | STACK | hamstrings | — | — | 540 |
| Dumbbell Romanian Deadlift | ex-dumbbell-romanian-deadlift | romanian-deadlift | DUMBBELL | EXTERNAL | hamstrings | glutes@0.5, back@0.25 | db rdl | 545 |
| Single-Leg Romanian Deadlift | ex-single-leg-romanian-deadlift | romanian-deadlift | DUMBBELL | EXTERNAL | hamstrings | glutes@0.5, core@0.25 | single leg rdl | 550 |
| Good Morning | ex-good-morning | good-morning | BARBELL | EXTERNAL | hamstrings | glutes@0.5, back@0.5 | — | 555 |
| Nordic Ham Curl | ex-nordic-ham-curl | nordic-curl | BODYWEIGHT | BODYWEIGHT | hamstrings | — | nordic curl | 560 |
| Barbell Glute Bridge | ex-barbell-glute-bridge | hip-thrust | BARBELL | EXTERNAL | glutes | hamstrings@0.25 | glute bridge | 565 |
| Machine Hip Thrust | ex-machine-hip-thrust | hip-thrust | MACHINE | STACK | glutes | hamstrings@0.25 | — | 570 |
| Hip Abduction Machine | ex-hip-abduction-machine | hip-abduction | MACHINE | STACK | glutes | — | abductor | 575 |
| Cable Kickback | ex-cable-kickback | glute-kickback | CABLE | STACK | glutes | hamstrings@0.25 | glute kickback | 580 |
| Cable Pull-Through | ex-cable-pull-through | pull-through | CABLE | STACK | glutes | hamstrings@0.5 | pull through | 585 |
| Seated Calf Raise | ex-seated-calf-raise | calf-raise | MACHINE | STACK | calves | — | — | 590 |
| Leg Press Calf Raise | ex-leg-press-calf-raise | calf-raise | MACHINE | EXTERNAL | calves | — | calf press | 595 |
| Single-Leg Calf Raise | ex-single-leg-calf-raise | calf-raise | BODYWEIGHT | BODYWEIGHT_PLUS | calves | — | — | 600 |
| Machine Crunch | ex-machine-crunch | crunch | MACHINE | STACK | core | — | ab machine | 605 |
| Decline Sit-Up | ex-decline-sit-up | sit-up | BODYWEIGHT | BODYWEIGHT_PLUS | core | — | situp | 610 |
| Side Plank | ex-side-plank | plank | BODYWEIGHT | BODYWEIGHT | core | — | — | 615 |
| Ab Wheel Rollout | ex-ab-wheel-rollout | rollout | OTHER | BODYWEIGHT | core | shoulders@0.25 | ab rollout | 620 |
| Dead Bug | ex-dead-bug | dead-bug | BODYWEIGHT | BODYWEIGHT | core | — | deadbug | 625 |
| Russian Twist | ex-russian-twist | twist | DUMBBELL | BODYWEIGHT_PLUS | core | — | — | 630 |
| Farmer's Carry | ex-farmer-s-carry | carry | DUMBBELL | EXTERNAL | core | back@0.25 | farmers walk, farmer walk | 635 |

**Tests (WI-1):** extend the Phase-3 catalog invariant test to the full 98: unique `nameKey` among built-ins; every id matches the slug rule and no batch-1 id changed (diff against the shipped v-N catalog snapshot); primary weight 1.0; secondaries ∈ {0.25, 0.5} and sum ≤ 1.0; every muscleKey normalizes to a non-OTHER `CanonicalMuscle`; every movementKey in the vocabulary map; per-bucket counts equal the table in §4.13 (encode the bucket assignment as an expected id list per bucket in the test — buckets are curation buckets and are not derivable from junction data); sortRank unique across built-ins. Plus a Robolectric-lane seeder test: seed at v-N, bump to N+2, assert 98 built-ins, idempotent on second run, customs untouched.

### WI-2 — Library & picker at scale: grouping, equipment chips, ordering, escaped + alias search

**Build:**
- **Escaping** — ExerciseDao.kt:19-27 becomes:
  ```sql
  SELECT * FROM exercises
  WHERE name LIKE '%' || :query || '%' ESCAPE '\\'
     OR muscleGroup LIKE '%' || :query || '%' ESCAPE '\\'
  ORDER BY name COLLATE NOCASE
  ```
  New `domain/LikeEscaper.kt`: `fun escape(raw: String) = raw.replace("\\\\", "\\\\\\\\").replace("%", "\\\\%").replace("_", "\\\\_")`. `ExerciseRepository.search` (ExerciseRepository.kt:31-39) escapes before the DAO call and unions alias hits:
  ```kotlin
  fun search(query: String): Flow<List<Exercise>> {
      val trimmed = query.trim()
      if (trimmed.isEmpty()) return observeAll()
      return combine(exerciseDao.search(LikeEscaper.escape(trimmed)), exerciseDao.observeAll()) { likeHits, all ->
          val aliasHits = all.filter { CatalogMeta.matchesSearchTerms(trimmed, it.id) }
          (likeHits + aliasHits).distinctBy { it.id }.map { it.toDomain() }
              .sortedWith(compareBy({ CatalogMeta.sortRank(it.id) }, { it.name.lowercase() }))
      }.orLogAndFallback("exercise search", emptyList())
  }
  ```
  `CatalogMeta.matchesSearchTerms` = case-insensitive substring over each term. The Library VM filter (ExerciseLibraryViewModel.kt:70-77) adds `|| CatalogMeta.matchesSearchTerms(needle, exercise.id)`.
- **Recency** — new DAO query in `WorkoutDao.kt` (it owns the `set_logs` queries):
  ```kotlin
  @Query("SELECT exerciseId AS exerciseId, MAX(completedAt) AS lastLoggedAt FROM set_logs GROUP BY exerciseId")
  fun observeLastLogged(): Flow<List<ExerciseRecencyRow>>
  ```
  (`set_logs.exerciseId/completedAt` exist — SetLogEntity.kt:8-36.) Picker result flows (RoutineEditorViewModel.kt:73, ActiveWorkoutViewModel.kt:335) combine with it and apply the §4.6 ordering via a pure `domain/ExerciseOrdering.kt` (`fun pickerOrder(exercises, lastLoggedById): List<Exercise>`).
- **Family grouping (Library only)** — pure `domain/LibraryGrouping.kt`: `fun group(exercises, sortRankById): List<LibraryFamily>` where `LibraryFamily(movementKey: String, label: String, members: List<Exercise>)`; label from the §4.14 map (unknown key → first member's name); single-member families flagged for plain rendering. `ExerciseLibraryUiState` gains `families: List<LibraryFamily>` (populated only when query blank and no filter) and the VM holds `expandedFamilies: MutableStateFlow<Set<String>>` with `toggleFamily(key)`. Screen: family header row (family label + member count + chevron, `InstrumentRow` idiom, 48dp+) → expandable member `ExerciseRow`s with `tag = equipment display label` (the existing `tag` slot, ExercisePickerSheet.kt:167-218). Filtered/searched views stay flat rows with the equipment tag.
- **Equipment chips** — second `LazyRow` of `InstrumentChip`s under the muscle row (ExerciseLibraryScreen.kt:137-156 pattern): "All" + equipment present in the catalog, fixed enum order, labels Barbell/Dumbbell/Cable/Machine/Smith/Kettlebell/Band/Bodyweight/Other (L-03). Single-select toggle; muscle and equipment filters AND-combine.
- **Picker** stays a flat list (no grouping; grid is Phase 8) — only ordering and alias search change there.

**Files:** `ExerciseDao.kt`, `WorkoutDao.kt`, `ExerciseRepository.kt`, new `domain/LikeEscaper.kt`, `domain/CatalogMeta.kt`, `domain/LibraryGrouping.kt`, `domain/ExerciseOrdering.kt`, `ExerciseLibraryViewModel.kt`, `ExerciseLibraryScreen.kt`, `RoutineEditorViewModel.kt`, `ActiveWorkoutViewModel.kt`.

**Tests:** `LikeEscaperTest` (`100%` → `100\\%`, `_` and `\\` cases), `CatalogMetaSearchTest` ("ohp" hits Overhead Press; "rdl" hits both RDLs), `LibraryGroupingTest` (bench-press family has 8 members — batch 1's Barbell/Incline/Dumbbell/Close-Grip plus batch 2's Incline Dumbbell/Machine Chest Press/Decline/Smith; single-member family flagged plain), `ExerciseOrderingTest` (logged-yesterday beats rank-100 never-logged; never-logged ties break by sortRank).

### WI-3 — Muscle-filter contract flip (canonical keys end-to-end)

**Build:** per §4.8. Concretely:
1. `Route.Library.create(muscle: CanonicalMuscle? = null)` emits `?muscle=${muscle.name}` (AppNav.kt:114-118); registration (AppNav.kt:272-287) unchanged shape.
2. `dispatchRecommendation`'s `onOpenLibrary` becomes `(CanonicalMuscle?) -> Unit`; the hop at RecommendationCards.kt:77 becomes `onOpenLibrary(recommendation.actionMuscle)` — **`catalogLabel` no longer appears in dispatch code**. `actionLabel` (RecommendationCards.kt:56-66) may keep `catalogLabel` for display copy only.
3. Every `Library.create(muscle)` call site passes the enum. As of this packet's baseline: AppNav.kt:244-252 (Home `onOpenLibraryMuscle` ← HomeScreen.kt:174-181 dispatch), AppNav.kt:259-267 (Progress `onOpenLibrary` ← ProgressScreen.kt:159 dispatch and the muscle sheet's `onFindLifts` at ProgressScreen.kt:190-202/318). Phases 6a/6b re-plumbed these — **run `grep -rn "Library.create" app/src/main/java` at execution and retarget every hit**; the contract is: no free-text muscle string crosses the nav boundary anywhere.
4. `ExerciseLibraryScreen.initialMuscle` parses per §4.8; `ExerciseLibraryViewModel` replaces `selectedGroup: String?` with `selectedMuscle: CanonicalMuscle?` and filters via junction credits (primary-or-secondary matches; junction-credited primaries sort before secondary-only matches, then sortRank). Muscle chips become the `CanonicalMuscle`s present in the catalog (displayName), replacing `MuscleGroups.presentIn` free strings; `MuscleNormalizer.matchesFilter` (CanonicalMuscle.kt:148-152) drops out of the Library path (it may retain other callers — grep before deleting).

**Tests:** VM/domain-level filter test: `QUADRICEPS` filter includes Leg Extension (primary) and Sumo Deadlift (quads@0.5 secondary credit) per chosen inclusion rule; a custom with `muscleGroup = "quads"` and no junction rows still matches; a stale string param (`"Hamstrings"`) falls back correctly. Run `tools/check-screen-wiring.py` and cite it — callback signatures change.

### WI-4 — Skip-and-surface collisions: the "Needs attention" section

**Build:** derived collision list per §4.9. New DAO query:
```kotlin
@Query("""
  SELECT c.* FROM exercises c
  WHERE c.isCustom = 1 AND EXISTS (
    SELECT 1 FROM exercises b WHERE b.isCustom = 0 AND b.nameKey = c.nameKey AND b.id != c.id
  )
""")
fun observeBuiltInCollisions(): Flow<List<ExerciseEntity>>
```
`ExerciseLibraryUiState` gains `needsAttention: List<Exercise>` (collisions minus dismissed ids from the `library_collision_dismissed_ids` preference). Screen: when non-empty, a section above the catalog — kicker header "NEEDS ATTENTION", one row per custom: name, caption "Same name as a built-in lift — your history stays on yours.", actions **Rename** (calls existing `openEdit`, ExerciseLibraryViewModel.kt:116-128) and **Keep both** (persists the id, row disappears). Renaming to a non-colliding name clears it automatically (derived). No merge, no FK rewrite, no deletion — state this in the PR description verbatim: *"The FK re-pointing merge tool is cut (REVISED_STRUCTURE item 11); collisions are rename-or-keep-both only."* This is Phase 3's insert-and-flag contract's UI half; the seeder and restore reconciliation (which re-runs detection) are untouched.

**Files:** `ExerciseDao.kt`, `PreferencesRepository.kt` (new string-set pref), `ExerciseRepository.kt` (expose collisions flow), `ExerciseLibraryViewModel.kt`, `ExerciseLibraryScreen.kt`.

**Tests:** collision derivation (custom "bench press" vs built-in Bench Press nameKey → flagged; dismissed id filtered; renamed custom no longer flagged) — pure/VM-level plus a Robolectric DAO test for the query.

### WI-5 — Per-loadType add defaults + the increment table

**Build A — AddDefaults** (`domain/AddDefaults.kt`, table and signatures in §4.10, `data class TargetDefaults(sets: Int, reps: Int, restSeconds: Int)`), replacing every hardcoded 3/5/null/90:
1. `RoutineEditorScreen.kt:204-207` and `:214-217` — pass `AddDefaults.forExercise(exercise)`; delete `NEW_LIFT_TARGETS` (:504). Create-inline path (unknown loadType) uses the fallback row.
2. `ExerciseLibraryViewModel.addToRoutine` (ExerciseLibraryViewModel.kt:238-245) — same.
3. `WorkoutRepository.addExerciseToSession` (WorkoutRepository.kt:114-138) — delete the default parameter values; `ActiveWorkoutViewModel.addExerciseInternal` (ActiveWorkoutViewModel.kt:473-494) passes `AddDefaults.forExercise(exercise)`.
`isCompound` derives from junction credits per §4.4 (expose on the domain `Exercise` or via a `CatalogMeta`/repository helper — match Phase 3's shipped shape).

**Build B — IncrementTable** (`domain/IncrementTable.kt`, values in §4.11):
```kotlin
object IncrementTable {
    fun displayStep(loadType: LoadType, unit: WeightUnit): Double?  // 2.5 / 5.0 / null for BODYWEIGHT
    fun stepKg(loadType: LoadType, unit: WeightUnit): Double?       // 2.5 / 2.26796 / null
    fun stepLabel(loadType: LoadType, unit: WeightUnit): String?    // "2.5 kg" / "5 lbs"
}
```
The three sites that must agree — all three change in this item, none is left on the old constant:
1. **Calculator** — `ProgressionCalculator` (ProgressionCalculator.kt:11): delete `INCREMENT_KG`; `suggestWeightKg`/`hint` gain `stepKg: Double?` — null step ⇒ suggested weight = last weight, INCREASE becomes a rep hint. Callers: `WorkoutRepository.progressionFor` (:259-277, resolve the exercise's loadType via `exerciseDao.getById`) and `readyForProgression` (:394-415, has `item.exercise`); both gain the display unit (thread it from their callers — grep `progressionFor(` and `readyForProgression(`) and pass `IncrementTable.stepKg(loadType, unit)`. lbs users now get +2.27 kg ⇒ displayed exactly "+5 lbs" — the ROADMAP.md:149 defect dies here.
2. **Coach copy** — RecommendationEngine.kt:213-223: `stepLabel(loadType, unit)` per named lift — thread the lift's class by adding `loadType: LoadType?` to `ProgressionHint` (a domain model, not schema; the repository knows the exercise at hint-build time); a BODYWEIGHT-only ready set uses rep copy ("Hit target reps. Add a rep next session."), never "+2.5 kg". ActiveWorkoutScreen.kt:129 (`incrementLabel`) and the hint strip (:816): same source; BODYWEIGHT exercises render the rep-progression line, no weight delta.
3. **Stepper** — `WeightUnit.step` (WeightFormat.kt:12-25): remove the per-constant constructor values (2.5/5.0) and redefine as `val step: Double get() = IncrementTable.displayStep(LoadType.EXTERNAL, this)!!` — the general-purpose stepper increment for any weight field, so the plain weight stepper keeps stepping for BODYWEIGHT_PLUS added load; `WeightConverter.incrementKg` (WeightFormat.kt:55-58) unchanged in shape. One table, three readers — assert in a test that calculator-kg-step and stepper-display-step agree per unit.

**Tests:** `AddDefaultsTest` (all 8 table rows + fallback), `IncrementTableTest` (kg/lbs/BODYWEIGHT-null; 2.26796 round-trips to "5 lbs" via `WeightConverter`; `WeightUnit.step` equals `displayStep(EXTERNAL, unit)` per unit), updated `ProgressionCalculatorTest` (null step ⇒ hold weight + INCREASE), `RecommendationEngineTest` copy assertions (lbs label "5 lbs"; bodyweight rep copy). Run `tools/check-when-exhaustive.py` (LoadType whens) and cite it.

### WI-6 — "Swap equipment" in the routine editor and the active workout

**Build:** siblings = built-ins sharing `movementKey`, excluding self and exercises already present in the routine/session.
- **Routine editor:** `RoutineExerciseCard` (RoutineEditorScreen.kt:330-419) gains a "Swap" `TextButton` beside "Remove" (:404-407 row). Opens a `ModalBottomSheet` of sibling `ExerciseRow`s (equipment tag). Selecting calls new `RoutineRepository.swapExercise(routineId: String, itemId: String, newExercise: Exercise)`: guards routine/item exist and no duplicate (message like RoutineEditorViewModel.kt:269-274), then `UPDATE routine_exercises SET exerciseId = :newId WHERE id = :itemId` (new `RoutineDao` query) keeping id/sortOrder/targets, then `touch(routineId)`. Empty-sibling case: sheet not offered (button hidden when sibling count is 0).
- **Active workout:** Phase 5 shipped mid-workout swap/remove on the current-lift overflow with its zero-logged-sets rule and repository method — **do not rebuild or relax either**. Extend the existing swap sheet with a "Same movement" section pinned above the general list (siblings by movementKey, equipment tags). Eligibility is exactly Phase 5's: swap only while the session exercise has zero logged sets; otherwise the action is disabled with Phase 5's reason copy.

**Tests:** pure sibling-computation test (`bench-press` family: Barbell → the 7 siblings Dumbbell/Incline/Incline Dumbbell/Decline/Smith/Machine Chest Press/Close-Grip, excluding self and already-present); repository swap test (targets and sortOrder preserved, duplicate rejected) in the Robolectric lane; swap-eligibility test pins the zero-set rule.

## 6. Out of scope

- **Imagery** — `imageKey` stays null; no thumbnails, no glyphs, no 2-column grid picker (Phase 8). The 40dp initial-letter thumb stays as is.
- **Any schema change** — no new columns, tables, or indexes; sortRank/searchTerms/isCompound are code-side; Room version does not move. If you believe you need a migration, stop and report.
- **Seeder/mutex/restore mechanics** — Phase 3's; consume, don't modify (beyond appending catalog rows and bumping the version constant).
- **The FK re-pointing merge tool** — cut permanently. Also: no auto-rename, no auto-delete of colliding customs.
- **Heat/band/coach rule changes** — Phase 5 owns bands, RPE, imbalance; WI-5 changes only increment steps and their copy.
- **Recommendation surfaces** — the surface map is fixed (Phase 5/6b); no new recommendation slots in Library.
- **`machineKind`, `defaultRestSeconds`, per-routine equipment override** (DESIGN_AUDIT §7 extras) — cut or deferred by Phase 0's recorded list.
- **Backup format** — v2 already carries catalog fields (Phase 3); nothing here adds backed-up state except the dismissal preference (in scope only if Phase 3's backup covers preferences; otherwise it is device-local, note it in hand-back).
- **Drag-and-drop reordering, multi-add picker, routine editor redesign** — not this phase.
- **Localization of catalog content** — English only.

## 7. Acceptance gate

Run before every push and finally on the finished branch:

```bash
tools/preflight.sh                 # all eight static checks + domain tests — expect: exit 0, every check green
./gradlew testDebugUnitTest        # owner machine / CI — expect: BUILD SUCCESSFUL, no failures
./gradlew assembleDebug            # expect: BUILD SUCCESSFUL
```

CI (`claude/**` trigger) green on the PR. Review artifacts `docs/catalog/batch-2-review.md` and `docs/catalog/batch-3-review.md` committed and regenerated from the final catalog (invariant sections all passing).

Domain/JVM tests by name, all green:
- `CatalogInvariantsTest` (extended: 98 rows, counts, weights, nameKeys, slugs, sortRank uniqueness)
- `CatalogSeedBumpTest` (Robolectric: v-N → N+2, idempotent, customs untouched)
- `LikeEscaperTest`, `CatalogMetaSearchTest`, `LibraryGroupingTest`, `ExerciseOrderingTest`
- `LibraryMuscleFilterTest` (junction filter + fallback + stale-param parse)
- `CollisionDetectionTest` (+ DAO collision query test)
- `AddDefaultsTest`, `IncrementTableTest`, `ProgressionCalculatorTest` (updated), `RecommendationEngineTest` (updated copy)
- `SwapSiblingsTest`, `RoutineSwapTest`, `SwapEligibilityTest`

Mechanical proofs cited in the PR: `tools/check-when-exhaustive.py` output (LoadType/CanonicalMuscle whens), `tools/check-screen-wiring.py` output (changed callbacks), `grep -rn "catalogLabel" app/src/main/java` showing no hit in navigation/dispatch code, `grep -rn "INCREMENT_KG" app/src/main/java` showing zero hits.

## 8. Owner device checklist

Install the phase APK over your existing install (normal upgrade — no reset). Then:

1. Open the Library. You should see movement families (e.g. "Bench Press", "Row", "Curl") with counts; tap "Bench Press" — it expands to 8 variants, each with an equipment tag (Barbell, Dumbbell, Machine, Smith…). Total catalog should feel roughly ~100, not 37, and your custom lifts must all still be there.
2. In the Library search, type `ohp` — Overhead Press appears. Clear it and type `rdl` — both Romanian Deadlifts appear.
3. Type `100%` into search — the app must not crash and must simply show "No matches" (not everything).
4. Tap the "Machine" equipment chip — only machine lifts remain; add the "Chest" muscle chip — only machine chest lifts remain.
5. Go to Body and tap a recommendation that says "Find … lifts" (or a muscle's "Find … lifts" button). The Library must open already filtered to that muscle, and the list must include lifts where that muscle is a secondary (e.g. the Quads filter shows Sumo Deadlift, where quads are a secondary credit).
6. Add "Machine Chest Press" to a routine. Its targets must default to 3×10 · 90s rest — not 3×5. Add "Dip": 3×6 · 120s. Add "Barbell Back Squat" into a fresh routine: 3×5 · 150s. (Expectations follow the §4.10 table; if a Phase-3-shipped loadType or junction weighting puts a batch-1 lift in a different class, the table row for its actual class applies — note any such case in the hand-back.)
7. If your unit is lbs (Settings): a "ready to progress" lift must say **+5 lbs** (never +5.5). If kg: +2.5 kg. For Push-Up — and Pull-Up too, if Phase 3 shipped it as BODYWEIGHT rather than BODYWEIGHT_PLUS — the hint must talk about adding a **rep**, never weight.
8. In a routine, tap **Swap** on a lift — a sheet of same-movement variants appears; pick one; sets/reps/rest/position must be unchanged, only the lift name/equipment differs.
9. Start a workout, log one set on a lift, open that lift's overflow — swap must be disabled with an explanation. On a lift with no sets yet, swap offers a "Same movement" section on top.
10. If you ever created a custom lift with the same name as a built-in (e.g. your own "Bench Press"): the Library shows a "Needs attention" row. Tap **Keep both** — it disappears and stays gone after an app restart. (If you have no such custom, create one named exactly "Bench Press", reopen the Library, verify the row, then Keep both or Rename.)
11. Review `docs/catalog/batch-2-review.md` and `batch-3-review.md` in the PR: skim each family's muscles/weights for anything that offends you as a lifter; comment on the PR rather than blocking on perfection — batch-level scrutiny, not row-by-row.

Report results on the PR. The phase closes only on your sign-off.

## 9. Estimates

- **Executor:** 4-5 days. WI-1 authoring+invariants 1 d; WI-2 1-1.5 d; WI-3 0.5 d; WI-4 0.5 d; WI-5 1 d (three-site increment wiring is the fiddly part); WI-6 0.5-1 d.
- **Owner:** ~1 day total, staged — two review-artifact skims (~30 min each), the 11-step device pass (~45 min), PR review. No blocking mid-phase checkpoint; both batch artifacts are reviewed in the one PR.

## 10. Hand-back

The completion report to the owner must contain:
1. PR link, branch, and the final catalog count (must be 98 built-ins) with the per-bucket table actual vs. §4.13.
2. Links to both review artifacts and a one-paragraph note on any authored-weight judgment calls or deviations from the tables in §5 (any row renamed, re-bucketed, or re-weighted, and why).
3. The reconciliation note against Phase 3's shipped substrate: actual `CATALOG_VERSION` values used, any identifier/shape differences from this packet's assumptions (§4.1), and whether batch-1 movementKeys matched §4.14 (mismatches: which side won — shipped data always wins). Include the shipped loadType of every batch-1 bodyweight-family lift (Pull-Up, Chin-Up, Push-Up, Plank, Hanging Leg Raise) since §8 steps 6-7 read off them.
4. Proof lines: preflight output tail, test counts, the two grep proofs (`catalogLabel` out of dispatch, `INCREMENT_KG` gone), check-when-exhaustive / check-screen-wiring citations.
5. Behavior deltas the owner will notice and why they are correct: new add-defaults per lift class, "+5 lbs" fix, rep-hints on bodyweight lifts, Library grouping/ordering, muscle-filter now including secondary-credit lifts.
6. Recorded caveats: collision dismissals are preference-stored (state whether they ride the backup per Phase 3's preference coverage; if not, they are device-local and a restore-resurrected collision stays hidden only on the same device); ASSISTED rows are absent from the 98 by design.
7. Owner checklist results (which of the 11 steps passed, verbatim owner notes) and the sign-off statement — the phase is not closed without it.
