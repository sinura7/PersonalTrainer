# Phase 8 — re-baseline report

> PROTOCOL.md §6 / packet preamble. Committed before any work item. The packet's `file:line`
> cites were taken at audit commit `2212628`; ten phase commits have landed since, and Phase 7
> in particular reworked both files the thumbnail work has to edit. This is the reconciliation.

## 1. Trunk state

- Tip at start of Phase 8: `9accabb` — *Phase 7: hand-back, and the bodyweight decision the
  owner has to make*.
- Branch: `claude/app-hierarchy-navigation-cjzigo` (same standing deviation from the packet's
  `claude/phase-8-imagery`, same reason: the executor's branch instruction outranks the packet).
- **Phase 0 verified**: `grep -c "Signed:" docs/ROADMAP.md` → **3**.
- Every prior phase is landed: 0, 2, 1a, 1b, 3, 4, 5, 6a, 6b, 7. Execution order
  `0 → 2 → 1 → 3 → 4 → 5 → 6a → 6b → 7 → 8` is intact and this is the last item.

## 2. Test lane, measured

From a real `tools/preflight.sh` at `9accabb`: **341 domain tests across 54 classes**, all
green, and **ten static checks**, not the packet's eight — Phase 6b added
`check-state-members.py` and Phase 7 added `check-annotation-targets.py`. §7's gate line
("all eight static checks report 0 violations") reads as ten; the command is unchanged.

## 3. Packet literals: drift found and adopted

| Anchor | Packet | Shipped | Verdict |
|---|---|---|---|
| `ExerciseRow` | `ExercisePickerSheet.kt:167–218` | **:232** | ⚠️ moved (Phase 7 added the siblings section above it) |
| placeholder `ExerciseThumb` | `:286–302` | **:351** | ⚠️ moved |
| `THUMB_SIZE = 40.dp` | `:357` | **:422** | ⚠️ moved, value unchanged |
| `CreateExerciseRow` second `THUMB_SIZE` use | `:319` | **:384** | ⚠️ moved, still exactly one other use |
| `LibraryRow` → `ExerciseRow` | `ExerciseLibraryScreen.kt:294–323`, call at `:300` | `LibraryRow` at **:463**, call at **:470** | ⚠️ moved (Phase 7 added `FamilyHeader` and `CollisionRow` above it) |
| `ExerciseDetailHeader` | `ExerciseDetailScreen.kt:256–283` | **:256** | ✅ unmoved |
| `LiftSwitcher` | `ActiveWorkoutScreen.kt:678–703` | **:688** | ⚠️ moved |
| `InstrumentChip` | `Common.kt:838` | **:842** | ⚠️ moved |
| `drawFigure` local `val detail` | `BodyMap.kt:184` | **:184** | ✅ the naming hazard is real and exactly where the packet says |
| `FIGURE_ASPECT` | `BodyMap.kt:390` | **:390**, value `0.52f` | ✅ |
| `FRONT_HOTSPOTS` / `BACK_HOTSPOTS` | `:392–404` / `:406–419` | **:392** / **:406** | ✅ |
| `hotspotsFor` | not cited | **:378** | — also moves |
| `VERTEBRAE` | cited as moving | **:421** | ✅ |
| `BodyView` | `BodyMap.kt:66–69` | **:66** | ✅ public, used at `ProgressScreen.kt:67` |
| `Heat3`, `HeatEmpty`, `OutlineSolidVariant` | `Color.kt:112`, `:109`, `:51` | **:112**, **:109**, **:51** | ✅ |

Every ⚠️ is a line move explained by Phase 7, which the packet predicted in its own
line-number caveat. Symbols followed, not numbers. Nothing unexplained; nothing to escalate.

## 4. The two enums the new `when`s must cover

- **`CanonicalMuscle`** — 11 values: CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, QUADRICEPS,
  HAMSTRINGS, GLUTES, CALVES, CORE, OTHER. Matches the packet's settled front/back table
  exactly, so `thumbViewFor` is written from it verbatim.
- **The equipment enum is named `EquipmentType`, not `Equipment`.** The packet's API block says
  `fun glyphFor(equipment: Equipment)`; Phase 3 shipped `EquipmentType` and Phase 7 gave it a
  `label`. Adopted: `glyphFor(equipment: EquipmentType)`. Its nine values — BARBELL, DUMBBELL,
  CABLE, MACHINE, SMITH, KETTLEBELL, BAND, BODYWEIGHT, OTHER — are a one-to-one match for the
  packet's nine glyphs, so the mapping is the identity and nothing falls through to OTHER.

## 5. Shipped state that changes the work

**Phase 7 already put an equipment `tag` on every row.** `ExerciseRow` now renders
`exercise.equipment.label` as a text tag in the picker, the library, the swap sheet and the
siblings section. The badge this phase adds is therefore a *second* equipment signal on the
same row. Kept anyway, and deliberately: the tag is read, the badge is recognised, and the
checklist's step 2 ("tell them apart from the badge alone at arm's length") is a claim about
glance-ability that a text tag does not satisfy. Recorded here so the redundancy is a decision
rather than an oversight; if the owner finds it noisy on device, dropping the text tag from the
picker is a one-line follow-up.

**`ExerciseRow` already takes what it needs.** The packet's signature change from
`(name, muscleGroup, …)` to `(exercise, …)` still applies, and every call site already holds
the `Exercise` — including the two Phase 7 added (the picker's siblings section and the routine
editor's swap sheet), which the packet could not have known about.

**WI-4 cannot run here.** The release-APK measurement needs the Android SDK and Gradle. This
environment has neither, and no compiler has seen any of this code. The commands stay in the
packet as written and the numbers must come from the owner's machine; the hand-back records
them as outstanding rather than guessed.
