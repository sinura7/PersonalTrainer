# Phase 8 — Imagery

> Executor packet. Standalone except for `docs/gameplan/PROTOCOL.md` (branch/PR/sign-off
> protocol — read it first, follow it exactly). Branch: `claude/phase-8-imagery`, one PR,
> owner merges. Do not start until the Phase 7 PR is merged.
>
> **THIS PHASE IS EXPLICITLY OPTIONAL.** It is last, and nothing in the game plan depends on
> it: the `imageKey` hook ships in Phase 3 regardless, `null` means "compose the thumb", and
> the initial-letter placeholder that ships today is a complete, coherent state. Deferring
> Phase 8 indefinitely — or never running it — costs the owner nothing and blocks no other
> work. It is executed only when the owner wants the pictures. Everything below is written to
> be executable verbatim on that day; being optional changes nothing about how it is run.
>
> **Execution order (phase numbers are identifiers, not sequence).** The order is
> 0 → 2 → 1 → 3 → 4 → 5 → 6a → 6b → 7 → **8**. Every other phase is merged before this one:
> **Phase 0** (decisions), **Phase 2** (test substrate), **Phase 1** (session hygiene — ONE PR
> from `claude/phase-1-session-hygiene`, both the 1A and 1B packets), **Phase 3** (schema v2),
> **Phase 4** (Plan tab), **Phase 5** (heat & coach), **Phase 6a/6b** (tabs, Body, Home),
> **Phase 7** (catalog + Library UX). Verify Phase 0 with `grep -c "Signed:" docs/ROADMAP.md`
> returning greater than 0 — `docs/gameplan/PROTOCOL.md` is committed on every branch, so its
> presence proves NOTHING.
>
> **Mandatory first commit: the re-baseline report (D-G, PROTOCOL.md §6).** Every count, line
> number, file path, and repo-state assertion in this packet is a baseline as of audit commit
> `2212628`, not an oracle — nine phase merges have landed since, and Phase 7 in particular
> reworked `ExercisePickerSheet.kt` and `ExerciseLibraryScreen.kt`. Your FIRST commit on
> `claude/phase-8-imagery` is a docs/PR-body re-baseline report, before any work item: the
> current trunk tip (`git log --oneline -1`) and which phases merged since `2212628`; the
> actual domain-test count and test-class count from a real run, not from this packet; and
> every packet literal that has drifted, with its verified current value — at minimum the §2
> line cites, the `ExerciseRow` / `ExerciseThumb` / `THUMB_SIZE` anchors, the shipped
> `Equipment` and `CanonicalMuscle` member lists your two `when`s must cover, and the
> release-APK baseline byte count used by WI-4. Drift fully explained by a merged prior phase
> or by the game plan's own commits is EXPECTED — record it, adopt the new value, proceed.
> Stop only on a mismatch with no such explanation.
>
> **Line-number caveat.** Every `file:line` below was verified on
> `claude/app-hierarchy-navigation-cjzigo` *before* Phases 0/2/1/3/4/5/6a/6b/7 landed. Those
> phases touch some of these files (notably the picker and library rows, in Phase 7). The named symbols are the
> anchors; re-verify each line with grep before editing. If a cited symbol has moved or been
> renamed, follow the symbol, not the number.

## 1. Mission

Fill the 40dp image slot that every exercise row has been reserving since the redesign
(DESIGN_AUDIT D-04, `docs/DESIGN_AUDIT.md:178`; slot reserved per
`docs/ui-redesign/DIRECTION_B_INSTRUMENT.md:182` and shipped as the initial-letter
placeholder in `ui/components/ExercisePickerSheet.kt:286-302`). The imagery is **composed
entirely in Compose DrawScope** — a mini body silhouette with the lift's primary muscle lit
from the Heat ramp, plus an equipment glyph badge — so it costs zero assets, zero APK bytes
beyond dex, and lives inside the token system the eight static checks police. This is the
last phase of the game plan: pure presentation, everything it consumes (equipment, loadType,
imageKey, the muscle junction) shipped in Phases 3 and 7.

**Optional by design.** This is the one phase the plan can drop without losing anything: the
40dp slot is already filled with a coherent initial-letter placeholder, `imageKey` already
exists and already means "compose", and no later work waits on pictures. Run it when the
owner wants the app to look finished; leave it un-run for as long as they don't. What it must
never become is a half-executed phase — if it runs, it runs to the §8 sign-off.

## 2. Read first

1. `docs/gameplan/PROTOCOL.md` — execution protocol: branch, PR, preflight-before-push, owner sign-off closes the phase.
2. `docs/DESIGN_AUDIT.md` §8 (lines 486–509) and D-04 (line 178) — the image spec this phase implements, as amended below (§4, "Sizes").
3. `docs/UI_REDESIGN.md` §5.1 (confirmed styles), §8 (guardrails), §9 (why the slot exists and nothing fills it).
4. `docs/ui-redesign/DIRECTION_B_INSTRUMENT.md` — the Instrument language; line 182 records the 40dp row slot; the accent-budget rule.
5. `app/src/main/java/com/sinura/personaltrainer/ui/progress/BodyMap.kt` — the drawing vocabulary you will reuse: `drawFigure` (:180–282), `BodyHotspot` (:72–78), `FRONT_HOTSPOTS` (:392–404), `BACK_HOTSPOTS` (:406–419), `FIGURE_ASPECT` (:390), `BodyView` (:66–69).
6. `app/src/main/java/com/sinura/personaltrainer/ui/theme/Color.kt` — Heat tokens (`HeatEmpty` :109, `Heat1`–`Heat4` :110–113 — note there is no `Heat0` token; the brief's "heat0" band name maps to `HeatEmpty`, a Phase-5 concern), `heatColor` (:130–136), `OutlineSolid`/`OutlineSolidVariant` (:50–51), text tokens.
7. `app/src/main/java/com/sinura/personaltrainer/ui/theme/Metrics.kt`, `Shape.kt` — spacing/hairline (Metrics.kt:46) and `Radius` (Shape.kt:21–33) tokens.
8. `app/src/main/java/com/sinura/personaltrainer/ui/components/ExercisePickerSheet.kt` — `ExerciseRow` (:167–218, thumb call at :189), placeholder `ExerciseThumb` (:286–302), `THUMB_SIZE = 40.dp` (:357, also used by `CreateExerciseRow` at :319). Note Phase 7 reworked this file; re-locate the symbols.
9. `app/src/main/java/com/sinura/personaltrainer/ui/library/ExerciseLibraryScreen.kt` — `LibraryRow` (:294–323) delegates to `ExerciseRow` at :300; library rows come free.
10. `app/src/main/java/com/sinura/personaltrainer/ui/exercise/ExerciseDetailScreen.kt` — `ExerciseDetailHeader` (:256–283, called at :113): back arrow + name only, no thumb today.
11. `app/src/main/java/com/sinura/personaltrainer/ui/workout/ActiveWorkoutScreen.kt` — `LiftSwitcher` (:678–703): `InstrumentChip` per lift, text-only, no placeholder today.
12. `app/src/main/java/com/sinura/personaltrainer/ui/components/Common.kt` — `InstrumentChip` (:838 ff.): no leading slot yet.
13. `app/src/main/java/com/sinura/personaltrainer/ui/theme/ThemeGallery.kt` — the preview-gallery pattern to mirror (e.g. `InstrumentTokensPreview` :86–131).
14. `tools/check-design-tokens.py` — the rules (:33–54): raw `Color(0x…)`, raw `RoundedCornerShape(<n>dp)`, system fonts, elevation are all violations outside `ui/theme/`.
15. `app/src/main/java/com/sinura/personaltrainer/domain/CanonicalMuscle.kt` — the 11-value enum (:14–113) your view-mapping `when` must cover exhaustively.
16. The Phase-3/7 exercise model: locate the `Equipment` type and the junction-backed primary/secondary muscle resolution on the domain `Exercise` (grep `equipment` and `imageKey` under `domain/` and `data/`). Your glyph `when` covers that enum exhaustively.

## 3. Binding doctrine

- **DESIGN_AUDIT §8** (`docs/DESIGN_AUDIT.md:486–509`): every listed lift gets a picture; equipment badge on the corner (:494); missing image = "stylized silhouette by equipment, never an empty gray box" (:495); key by `imageKey`, never display name (:506); never block logging on imagery. D-04 (:178) is the defect being closed.
- **UI_REDESIGN §8 guardrails** (`docs/UI_REDESIGN.md:164–172`): no raw colour/radius/elevation/system-font outside `ui/theme/` — enforced mechanically by `tools/check-design-tokens.py`; accent budget (volt once or twice per screen — the thumbnails use zero volt).
- **UI_REDESIGN §5.1**: heat ramp is "one concept, one encoding" — the thumb's muscle colouring comes from the Heat tokens, nothing else.
- **DIRECTION_B_INSTRUMENT**: rows carry a 40dp leading image slot (:182); hairline-and-surface depth; no shadows.
- **REVISED_STRUCTURE** (the game-plan brief): accepted finding 12 — imagery is Compose-drawn composed thumbnails ONLY; VectorDrawable XML carrying ramp colours is forbidden; the line-art commission is cut to a non-committal appendix. Phase 8 definition: "DrawScope, Heat tokens, ~20 glyph/silhouette primitives; imageKey stays null → composed; APK delta budget ≤ 2 MB."
- **Attack findings this packet must satisfy**: (tech, minor) thumbnail mechanism pinned to Compose-native drawing — layered VectorDrawable XML would duplicate the ramp as static resources outside the token system and `android:tint` cannot tint layers independently; (scope, major) the $1.5–4k commission is cut — appendix only; (executor, minor) composed thumbs need no `imageKey` — `null` means compose, and that is the shipping state.
- **ROADMAP**: imagery recorded as deferred to the schema phase at `docs/ROADMAP.md:127` and `:154` — this phase closes those rows.

## 4. Settled decisions

Stated as settled. Do not reopen.

- **Mechanism: Compose DrawScope only.** No VectorDrawable XML, no `res/drawable` additions, no WebP, no Coil/Glide, no bitmaps. DESIGN_AUDIT §8's implementation notes (`docs/DESIGN_AUDIT.md:504–509` — WebP resources, pictured grid) are **superseded** on mechanism by REVISED_STRUCTURE finding 12; its product rules (badge, silhouette fallback, imageKey-not-name) stand. `tools/check-design-tokens.py:33-54` is the fence: ramp colours exist once, in `ui/theme/Color.kt`.
- **Sizes.** DESIGN_AUDIT §8's 56/72/96 sizes (`:492`) are superseded by the shipped Direction-B 40dp slot (`DIRECTION_B_INSTRUMENT.md:182`, `ExercisePickerSheet.kt:357`). Settled: **rows 40dp** (picker + library), **exercise-detail header 56dp**, **in-workout chip glyph 20dp (glyph only, no silhouette — a silhouette is illegible at 20dp)**. No library grid.
- **Thumbnails are identity, not state.** The lit muscle uses a **fixed** Heat token, not the owner's current weekly band — the same lift always looks the same, and rows render in surfaces (picker, routine editor) that have no heat snapshot. Primary muscle regions fill with **`Heat3`**; secondary muscle regions with **`Heat3.copy(alpha = 0.4f)`**. The figure body draws in `OutlineSolidVariant`, exactly as `drawFigure` does (`BodyMap.kt:183`). Live heat belongs to the Body tab alone.
- **View derivation** (front or back figure) from the lift's **primary** muscle, matching the side its plates live on in `FRONT_HOTSPOTS` (`BodyMap.kt:392–404`) / `BACK_HOTSPOTS` (`BodyMap.kt:406–419`). Muscles on both tables (SHOULDERS, CALVES) are settled anatomically:
  - FRONT: `CHEST, BICEPS, CORE, QUADRICEPS, SHOULDERS`
  - BACK: `BACK, TRICEPS, GLUTES, HAMSTRINGS, CALVES`
  - `OTHER`: FRONT, no region lit (figure only).
  This `when` is exhaustive over `CanonicalMuscle` — `tools/check-when-exhaustive.py` proves it.
  A **secondary** muscle whose hotspots exist only on the *other* view (e.g. primary CHEST → FRONT figure, secondary TRICEPS has hotspots only in `BACK_HOTSPOTS`) has no region on the chosen view and is simply **not drawn** — never mirror it, never switch views for it.
- **Muscle source.** Primary + secondaries come from the junction-backed domain resolution Phase 3/7 established (catalog-first). If the domain `Exercise` does not expose them directly, resolve via the same path `MuscleLoadCalculator` uses; fall back to `MuscleNormalizer.normalize(exercise.muscleGroup)` (`domain/CanonicalMuscle.kt:136–144`) for anything unresolved — note it returns a `MuscleMapping`; use its `.primary` and `.secondaries`. Never crash on missing data — an unresolvable muscle renders the plain figure.
- **`imageKey` contract.** `imageKey == null` → composed thumb. `imageKey != null` → reserved for future commissioned art; **for now it also renders the composed thumb** — no code path reads the key's value yet, but the branch point exists in exactly one place (`ExerciseThumb`), commented as the future hook. No migration, no seed change, no key values are written in this phase.
- **The nine glyphs.** Keyed off the Phase-3 `Equipment` enum via an exhaustive `when` mapping every enum value to one of nine drawings; values without bespoke art map to OTHER. Each is drawn in a unit square (coordinates are fractions), stroke colour **`TextSecondary`**, `strokeWidth = Metrics.hairline.toPx() * 1.5f`, `StrokeCap.Round` — the hairline+text vocabulary, zero accent:
  1. **BARBELL** — bar: line (0.05, 0.5)→(0.95, 0.5); plate pairs: vertical rounded bars at x≈0.20 and 0.28 (heights 0.52 and 0.36, centred on y 0.5), mirrored at 0.72/0.80.
  2. **DUMBBELL** — short bar (0.32, 0.5)→(0.68, 0.5); one plate bar each end at x≈0.24 and 0.76, height 0.44.
  3. **MACHINE (stack)** — guide rod: line (0.5, 0.10)→(0.5, 0.34); stack: four horizontal slats (0.30→0.70 wide, 0.08 tall) stacked from y 0.36 to y 0.82 with 0.04 gaps.
  4. **CABLE** — pulley: stroked circle centre (0.5, 0.20) r 0.11; cable: line (0.56, 0.28)→(0.72, 0.60); stirrup handle: small stroked rounded rect (0.60, 0.60)–(0.86, 0.74).
  5. **SMITH** — rails: verticals at x 0.22 and 0.78, y 0.08→0.92; bar: line (0.10, 0.55)→(0.90, 0.55); hooks: 0.08-long ticks angled 45° up-right where bar crosses each rail.
  6. **KETTLEBELL** — body: stroked circle centre (0.5, 0.62) r 0.24; handle: arc from (0.32, 0.48) to (0.68, 0.48) peaking at y 0.16.
  7. **BAND** — loop: stroked circle centre (0.5, 0.44) r 0.28; crossing: two lines (0.38, 0.66)→(0.62, 0.88) and (0.62, 0.66)→(0.38, 0.88).
  8. **BODYWEIGHT** — head: stroked circle centre (0.5, 0.18) r 0.10; torso: (0.5, 0.30)→(0.5, 0.58); arms: (0.5, 0.38)→(0.28, 0.52) and →(0.72, 0.52); legs: (0.5, 0.58)→(0.34, 0.88) and →(0.66, 0.88).
  9. **OTHER** — hollow diamond: stroked path (0.5, 0.12)→(0.88, 0.5)→(0.5, 0.88)→(0.12, 0.5)→close.
  Tune coordinates freely during the gallery pass; the *identity* of each glyph (what it depicts) is settled.
- **Thumb layout** (40dp row / 56dp header): container = `Radius.xs` rounded box, `Surface1` fill, `Metrics.hairline` `Hairline` border — identical to today's placeholder (`ExercisePickerSheet.kt:288–293`). Inside: the figure, centred, height = box height − 2×`Metrics.space1`, width = height × `FIGURE_ASPECT` (0.52, `BodyMap.kt:390`); muscle regions drawn as rounded rects over the figure at the hotspot fractions. Badge: bottom-right, size = 45% of the thumb edge, `Surface2` fill, `Metrics.hairline` `Hairline` border, `Radius.xs` corners, glyph inset by 15% each side. The badge overlaps the figure — that is the point; it must never be clipped by the container.
- **Figure detail at mini scale:** `drawFigure` gains `detail: Boolean = true`; thumbs pass `false` — slabs, blobs and torso only, no sternum/spine/kneecap rules (sub-pixel at 21dp width). **Naming hazard:** `drawFigure` already declares a local `val detail = OutlineSolid` (`BodyMap.kt:184`) — rename that local (e.g. `detailColour`) when adding the parameter, or the two collide. The Body tab keeps `detail = true` and must render pixel-identically to today.
- **Accessibility:** the thumb is decorative. The row/header/chip title carries the lift's name; the thumb clears its semantics (`contentDescription = null`, no role).
- **API (interchangeable-executor contract):**
  ```kotlin
  // ui/components/ExerciseThumb.kt
  object ThumbSize { val row = 40.dp; val header = 56.dp; val chipGlyph = 20.dp }
  enum class EquipmentGlyph { BARBELL, DUMBBELL, MACHINE, CABLE, SMITH, KETTLEBELL, BAND, BODYWEIGHT, OTHER }
  fun glyphFor(equipment: Equipment): EquipmentGlyph          // exhaustive when
  fun thumbViewFor(primary: CanonicalMuscle): BodyView        // exhaustive when, table above
  @Composable fun ExerciseThumb(exercise: Exercise, modifier: Modifier = Modifier, size: Dp = ThumbSize.row)
  @Composable fun EquipmentGlyphIcon(glyph: EquipmentGlyph, modifier: Modifier = Modifier, size: Dp = ThumbSize.chipGlyph, tint: Color = TextSecondary)
  ```
  `ExerciseRow` changes signature from `(name: String, muscleGroup: String, …)` to `(exercise: Exercise, …)`: only `name` + `muscleGroup` collapse into `exercise`; the existing `modifier`, `onClick`, `tag`, `trailing` parameters (`ExercisePickerSheet.kt:167–173`) are retained unchanged — `LibraryRow` passes `tag` ("Custom") and trailing content and must keep compiling — and the subtitle line keeps reading `exercise.muscleGroup`. Both call sites (`ExercisePickerSheet.kt:146`, `ExerciseLibraryScreen.kt:300`) already hold the `Exercise`. `InstrumentChip` (`Common.kt:838`) gains `leading: (@Composable () -> Unit)? = null`, default preserving every existing call site.
- **Figure-art extraction.** `BodyView`, `BodyHotspot`, `drawFigure`, `hotspotsFor`, `FRONT_HOTSPOTS`, `BACK_HOTSPOTS`, `FIGURE_ASPECT`, `VERTEBRAE` move from `BodyMap.kt` to a new `ui/components/FigureArt.kt` (internal where possible; `BodyView` stays public — `ProgressScreen.kt:62` uses it). Pure move + the `detail` param (and the local-`val detail` rename it forces); zero visual change to the Body tab.

## 5. Work items

### WI-1 — `ExerciseThumb` composable and the figure-art extraction

**Build:** `ui/components/FigureArt.kt` (moved vocabulary, per above) and
`ui/components/ExerciseThumb.kt` (the API block above, the nine glyph drawings, the thumb
composition: Surface1 box → figure with `detail = false` → primary regions in `Heat3`,
secondary regions in `Heat3` at 0.4 alpha, drawn at the hotspot fractions for
`thumbViewFor(primary)` — secondaries with no hotspot on that view are skipped → equipment
badge bottom-right). The `imageKey` branch point lives
here: `if (exercise.imageKey != null) { /* future: keyed art */ }` falling through to the
composed drawing, with a comment citing this packet's appendix.

**Modify:** `ui/progress/BodyMap.kt` (delete the moved code, import from components),
`ui/progress/ProgressScreen.kt` (import path for `BodyView` only).

**Tests:** `app/src/test/java/com/sinura/personaltrainer/ui/components/ExerciseThumbLogicTest.kt`
— plain JVM assertions on the pure functions (they touch no Android runtime):
`thumbViewFor` returns the settled side for all 11 `CanonicalMuscle` values; `glyphFor`
covers every `Equipment` value; OTHER maps to `EquipmentGlyph.OTHER`. Runs under
`./gradlew testDebugUnitTest` (NOT the domain-jar lane — `tools/run-domain-tests.sh`
compiles `domain/` only; do not move presentation mappings into `domain/` to chase that
lane). Mechanical proof at push time: `tools/preflight.sh` — the two new `when`s are what
`check-when-exhaustive` exists for.

### WI-2 — Wiring the four surfaces

Current state, verified per site (re-verify after Phases 1–7):

1. **Picker rows** — `ExercisePickerSheet.kt:189` renders the initial-letter placeholder `ExerciseThumb(name)` (:286–302). Replace the placeholder with the real `ExerciseThumb(exercise)`; change `ExerciseRow` to take `exercise: Exercise` (:167, retained params per §4); delete the old private composable and `THUMB_SIZE` (:357). **`CreateExerciseRow` (:306–341) also uses `THUMB_SIZE` at :319** — replace that one usage with `ThumbSize.row` (same 40.dp; zero visual change) before deleting the constant; its volt `+` box is otherwise unchanged — it has no exercise. Both picker hosts (`ActiveWorkoutScreen.kt:416`, `RoutineEditorScreen.kt:197`) come free.
2. **Library rows** — `ExerciseLibraryScreen.kt:300` delegates to `ExerciseRow`; free once WI-2.1 lands. Update the call to pass the `Exercise` (keep passing `tag`/`onClick`/trailing exactly as today).
3. **Exercise-detail header** — `ExerciseDetailScreen.kt:256–283` is back-arrow + name only. Add `exercise: Exercise?` to `ExerciseDetailHeader`; when non-null, an `ExerciseThumb(exercise, size = ThumbSize.header)` sits between the arrow and the title with `Metrics.space3` gaps; null (loading/missing) renders exactly today's header. Call site :113 passes `state.exercise`.
4. **In-workout lift chips** — `ActiveWorkoutScreen.kt:685–702` (`LiftSwitcher`) renders text-only `InstrumentChip`s; **no placeholder exists today**. Add the `leading` slot to `InstrumentChip` (`Common.kt:838`) and pass `EquipmentGlyphIcon(glyphFor(item.exercise.equipment))` — glyph only, 20dp, `TextSecondary` in both selected and unselected states (the glyph is metadata, never load-bearing). Chip height and label format (:689–693) unchanged.

`imageKey` handling at every site is identical because it lives inside `ExerciseThumb`:
null → composed; non-null → composed for now (stated in WI-1).

**Tests:** `tools/preflight.sh` (notably `check-screen-wiring`, `check-named-args` across the
signature changes); `./gradlew testDebugUnitTest` stays green; visual proof is WI-3 + the
owner checklist.

### WI-3 — Thumb gallery for aesthetic review

**Build:** `ui/components/ExerciseThumbGallery.kt`, mirroring the `ThemeGallery.kt` pattern
(`ui/theme/ThemeGallery.kt:39–131`: private `@Preview` composables, no route, no shipping
UI). Previews: (a) all nine `EquipmentGlyph`s at 20dp and at badge scale, labelled;
(b) a grid of sample thumbs at 40dp — one per equipment × a representative muscle spread
covering both figure views (e.g. barbell/CHEST, barbell/QUADRICEPS, dumbbell/SHOULDERS,
machine/BACK, cable/TRICEPS, smith/GLUTES, kettlebell/HAMSTRINGS, band/CALVES,
bodyweight/CORE, other/OTHER); (c) the same spread at 56dp. Owner reviews on device via
Android Studio's run-preview-on-device gutter action, and via the real Library list (after
Phase 7 the catalog spans the equipment families).

**Tests:** compiles; included in the owner checklist.

### WI-4 — APK budget measurement

Code-only drawing: expected release-APK delta ≈ 0 (a few tens of KB of dex). The gate
measures it rather than asserts it. On the owner's machine (executor environments have no
Android SDK — `docs/DEVELOPMENT.md:81`, `docs/UI_REDESIGN.md` §9):

```bash
git checkout <phase-8 merge base> && ./gradlew assembleRelease
stat -c %s app/build/outputs/apk/release/app-release*.apk   # record BEFORE
git checkout claude/phase-8-imagery && ./gradlew assembleRelease
stat -c %s app/build/outputs/apk/release/app-release*.apk   # record AFTER
```

An unsigned release APK (no `keystore.properties`) is fine for measurement. Record both
numbers in the PR description. **Gate: AFTER − BEFORE ≤ 2 MB** (budget per the game-plan
brief); anything over ~200 KB is a red flag to investigate before merge, because nothing in
this phase should add assets at all.

## 6. Out of scope

- **Anatomical silhouette repaint of the Body tab** — recorded deferred. `drawFigure` moves files and gains `detail:`; its geometry does not change by one coordinate.
- **Any catalog data change** — no seed bump, no `catalogVersion` change, no `imageKey` values written, no migration, no schema touch.
- Loading art by `imageKey` (Coil/Glide, assets, `res/drawable`, WebP) — the hook renders composed; the appendix owns the future.
- VectorDrawable XML in any form — forbidden (REVISED_STRUCTURE finding 12; `tools/check-design-tokens.py` guards the colour side).
- DESIGN_AUDIT §8's 2-column pictured picker grid (`docs/DESIGN_AUDIT.md:509`) — superseded; the picker stays a list.
- Imagery on surfaces beyond the four wired sites (history rows, session detail, routine-editor rows, Home). If the owner wants more after seeing these, that is a one-line follow-up per site — record, don't build.
- Per-user heat in thumbnails; photo support; empty-state illustrations; app icon.

## 7. Acceptance gate

Executor, before every push (per PROTOCOL.md):

```bash
tools/preflight.sh
# expected: all eight static checks report 0 violations, domain tests green.
# check-when-exhaustive must pass over the two new whens (CanonicalMuscle, Equipment);
# check-design-tokens must report 0 — the thumb code contains no raw colour, radius, or font;
# check-screen-wiring and check-named-args must pass across the ExerciseRow /
# InstrumentChip / ExerciseDetailHeader signature changes.
```

Owner machine (or CI, if the standing billing errand has been done):

```bash
./gradlew testDebugUnitTest        # green, including ExerciseThumbLogicTest
./gradlew assembleDebug            # builds; install for the device checklist
# WI-4 release-size measurement, per the literal commands above: delta ≤ 2 MB
```

Test list by name:
- `ExerciseThumbLogicTest` — `thumbViewFor` exhaustive + settled table; `glyphFor` exhaustive; OTHER fallback. (Gradle unit-test lane; no new domain-lane tests — this phase adds nothing under `domain/`.)

Phase closes only on owner sign-off of §8 (PROTOCOL.md).

## 8. Owner device checklist

Install the debug build from the PR branch (Android Studio ▶, per `docs/DEVELOPMENT.md`).

1. Open **Library**. Every row's leading square now shows a small body figure with one region coloured plus an equipment badge in its corner. No row shows a letter initial any more.
2. Scan rows of different equipment (barbell vs dumbbell vs machine vs cable at minimum). You can tell them apart from the badge alone at arm's length.
3. Filter or search to a back-side lift (e.g. a row or a deadlift). Its thumb shows the **back** figure with the back/hamstring/glute region lit; a bench press shows the **front** figure with chest lit.
4. Open one lift's detail screen. A larger (56dp) thumb sits left of the title; a two-line name (e.g. "Incline Dumbbell Press") still fits without clipping.
5. Start a workout with at least two lifts. Each chip in the lift switcher leads with a small equipment glyph; the name and set count read exactly as before; tapping chips still switches lifts.
6. In that workout, tap **+ Add lift**: the picker rows show the same thumbs as the Library.
7. Type a name that matches nothing in the picker: the volt "Create …" row looks exactly as it did before this phase.
8. Open the **Body** tab: the silhouette, plates and colours are pixel-for-pixel what they were before this phase.
9. Aesthetic verdict: in Android Studio, open `ExerciseThumbGallery.kt` and run the previews on the phone (gutter ▶ on each preview). For each of the nine glyphs, judge: recognisable at a glance, or redraw. Report the redraw list (coordinate tuning is in-phase; identity changes are not).
10. Confirm the PR description carries the before/after release-APK byte counts and the delta is within budget.

## 9. Estimates

- **Executor: 2–4 days.** ~1 day extraction + thumb + glyphs, ~0.5 day wiring, ~0.5 day gallery + tests, the rest is glyph-coordinate iteration from the owner's verdicts.
- **Owner: 0.5–1 day.** One device pass over the checklist, one gallery verdict round, the APK measurement, PR review and merge.

## 10. Hand-back

The completion report to the owner must contain:

1. PR link (`claude/phase-8-imagery`) and confirmation the phase-7 base was current.
2. Screenshots (or the owner's own, from the checklist) of all four wired surfaces: library row, picker row, detail header, workout chips.
3. `tools/preflight.sh` output (0 violations) and the `testDebugUnitTest` summary including `ExerciseThumbLogicTest`.
4. The release-APK before/after byte counts and delta vs the 2 MB budget.
5. The glyph verdict table from checklist step 9 — per glyph: accepted / tuned / flagged, with what changed.
6. Explicit statements: Body tab unchanged (checklist step 8 observed); no catalog data, schema, or seed change shipped; `imageKey` remains null everywhere and renders composed.
7. Any surfaces the owner asked to add imagery to (recorded as follow-ups, not built).
8. The re-baseline report from your first commit (D-G) — the record of what had drifted by the
   time the plan's last, optional phase actually ran.

---

## Appendix — commissioned line art (non-committal)

Recorded per the game-plan brief, verbatim in spirit: commissioned line art remains a
someday option if the owner ever wants it — `imageKey` is the hook; a future pass would fill
keys and teach `ExerciseThumb`'s existing branch to load keyed art. **No phase depends on
it, nothing schedules it, and this phase ships complete without it.**
