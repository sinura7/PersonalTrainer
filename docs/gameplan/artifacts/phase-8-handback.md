# Phase 8 — hand-back

> Packet §10. The last phase of the game plan. Not closed until the owner signs off §8's
> device checklist and supplies the two owner-side numbers below.

## 1. What shipped

Branch `claude/app-hierarchy-navigation-cjzigo` (same standing deviation as every prior phase),
on a current Phase 7 base — `9accabb`, the Phase 7 hand-back, was the tip when this started.

| Commit | Work |
|---|---|
| `d09544c` | Re-baseline report — the mandatory first commit |
| `8065db5` | WI-1/2/3 — `FigureArt` extraction, `ExerciseThumb`, four surfaces wired, gallery, tests |

## 2. Screenshots

**Not supplied — this environment cannot render.** No compiler has seen this code and no screen
has been drawn. The four surfaces to capture during the device pass are: a library row, a picker
row, the exercise-detail header, and the in-workout lift chips. Checklist steps 1–7 walk exactly
those.

## 3. Proof lines

```
tools/preflight.sh   →  preflight: OK
    check-internal-imports    0 unresolved
    check-missing-imports     0 missing
    check-design-tokens       0 violations      ← no raw colour, radius or font in the thumb code
    check-screen-wiring       0 unwired
    check-state-members       0 unresolved
    check-annotation-targets  0 stranded
    check-named-args          0 mismatches (main and test)  ← across the ExerciseRow,
                                                              InstrumentChip and
                                                              ExerciseDetailHeader signature changes
    check-when-exhaustive     0 non-exhaustive  ← covers thumbViewFor and glyphFor
    check-unused-imports      0 unused
    syntax-check              NO SYNTAX ERRORS
    domain tests              OK (341 tests)
```

`./gradlew testDebugUnitTest` — **owner-side, outstanding.** `ExerciseThumbLogicTest` lives under
`ui/components/` and runs on the Gradle JVM lane; the jar lane here compiles `domain/` only, and
a presentation mapping does not move into `domain/` to chase a test runner.

The one claim the test lane could not check, checked directly against the catalog data instead:
**all 98 built-in lifts resolve to a real muscle whose plate exists on the view they select** —
back 16, quads 12, chest 12, shoulders 11, glutes 10, core 10, biceps 8, triceps 8, hamstrings
7, calves 4, and zero unmapped. No lift can render as a blank body.

## 4. APK budget

**Outstanding, owner-side.** The measurement needs the Android SDK and Gradle, neither of which
exists here. Run per the packet's literal commands:

```bash
git checkout 9accabb && ./gradlew assembleRelease
stat -c %s app/build/outputs/apk/release/app-release*.apk   # BEFORE
git checkout claude/app-hierarchy-navigation-cjzigo && ./gradlew assembleRelease
stat -c %s app/build/outputs/apk/release/app-release*.apk   # AFTER
```

**Expectation: a few tens of KB of dex, nothing more.** This phase adds no assets, no `res/`
entries, no image library and no bitmaps — the drawings are code. The gate is ≤ 2 MB, and
anything over ~200 KB means something was added that should not have been.

## 5. Glyph verdicts

**Outstanding, owner-side** — checklist step 9. Open `ExerciseThumbGallery.kt` in Android Studio
and run the three previews on the phone: the nine glyphs at chip and badge scale, then the thumb
spread at 40dp and at 56dp. Per glyph, the verdict is recognisable-at-a-glance or redraw:

| Glyph | Verdict | Notes |
|---|---|---|
| BARBELL | | bar with two plate pairs |
| DUMBBELL | | short bar, one plate each end |
| MACHINE | | guide rod over a four-slat stack |
| CABLE | | pulley, cable, stirrup handle |
| SMITH | | two rails, bar, angled hooks |
| KETTLEBELL | | bell with a curved handle |
| BAND | | loop with a crossing |
| BODYWEIGHT | | stick figure |
| OTHER | | hollow diamond |

Coordinate tuning is in-phase work; changing what a glyph *depicts* is not.

## 6. Explicit statements

- **The Body tab is unchanged.** The anatomy moved files and nothing else: all 233 coordinates —
  `drawFigure`'s 136, the two hotspot tables' 92, the five vertebrae — were diffed and are
  character-identical to what shipped. The tab passes `detail = true`, which is the old
  behaviour, and the `detail` flag guards only the fine rules the thumbs skip. Checklist step 8
  is the observation that confirms it on screen.
- **No catalog data, schema, or seed change.** `CATALOG_VERSION` stays at 4, Room's version does
  not move, no migration, no `imageKey` values written.
- **`imageKey` is null on every row and renders composed.** Nothing reads it today. It is read
  in exactly one place when art arrives — inside `ExerciseThumb` — so keyed art would reach every
  surface at once or none, and writing a key before there is art to load can never blank a row.
- **Zero accent.** The thumbnails and glyphs use `TextSecondary`, the outline tokens and `Heat3`.
  The volt budget is untouched.

## 7. Recorded, not built

- **Imagery on further surfaces** — history rows, session detail, routine-editor rows, Home. Each
  is a one-line change if the owner wants it after seeing these four; none is built.
- **The equipment text tag is now redundant with the badge.** Phase 7 put
  `exercise.equipment.label` on every row as text; Phase 8 adds the badge beside it. Kept
  deliberately — a tag is read, a badge is recognised, and the checklist's arm's-length claim is
  about glance-ability — but if it reads as noise on device, dropping the tag from the picker is
  one line.
- **Commissioned line art** stays exactly what the packet's appendix says it is: a someday
  option, hooked but unscheduled, depended on by nothing.

## 8. The re-baseline report

[`phase-8-rebaseline.md`](phase-8-rebaseline.md) — seven anchors had moved by the time the plan's
last phase ran, all of them explained by Phase 7 reworking the same two files; the packet's own
line-number caveat predicted it. One identifier correction: the packet's API block names the
enum `Equipment`, and Phase 3 shipped `EquipmentType`.

---

## The plan is finished

Phase 8 was the last item in `0 → 2 → 1 → 3 → 4 → 5 → 6a → 6b → 7 → 8`. Every phase has run.

**What still needs the owner, across the whole plan:**

1. **A real build.** No compiler has seen any of this — ten static checks and a pure-JVM domain
   lane are not a build. `./gradlew testDebugUnitTest assembleDebug` on the owner's machine is
   the gate every phase has been deferring.
2. **`app/schemas/…/2.json`** — Phase 3's exported Room schema, which needs a real Gradle build
   to generate. Flagged loudly at the time and still outstanding.
3. **The bodyweight decision** — Phase 7 hand-back §7. 18 lifts loaded by bodyweight, and the app
   stores the owner's bodyweight nowhere.
4. **Device passes** — each phase's §8 checklist, and the sign-offs that formally close them.
