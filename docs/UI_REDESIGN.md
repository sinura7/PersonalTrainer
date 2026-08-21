# UI Redesign — Design Critique and Direction

**Status:** design decision record. This is the visual-language companion to `docs/DESIGN_AUDIT.md` (the gym-floor usability audit). The audit says what must *work*; this document says what the product must *look and feel like*, why the current UI misses that bar, and exactly which styles and approaches the redesign uses.

**Brief (from the owner):** "modernized and high tech… almost like an Apple design or Whoop. A high reflection of super highly designed items and layouts. The workflow needs to be intentional. Clean, compact, and simple."

**Code baseline:** every file/line reference below is against commit `de1ffac` on `claude/android-fitness-app-review-uhlb1j`. Method: seven parallel design reviews (foundations; shell/Home/Schedule; workout flow; data surfaces; management surfaces; workflow/IA; motion/interaction), every finding re-verified adversarially against the code, plus two independently authored design-language proposals. 106 verified findings: 14 critical, 62 major, 26 minor, 4 polish. The full register is in Appendix A.

---

## 1. The verdict

The engineering under this app is better than the app looks. The data model, the rest-timer service, draft recovery, progression logic, and the navigation plumbing are disciplined, gym-aware, and in places genuinely clever (the PR moment is deliberately not a dialog; back and the top-bar X share one exit path; ghost values of the last session appear with zero taps). None of that is visible on screen.

What is visible is a **stock Material 3 template wearing a green tint that points at the wrong product category**. Forest/Leaf/Lime/Sand is the vocabulary of a meditation or plant-care app — the literal opposite of a high-tech instrument. The hero numerals — the pixels the product exists to show — are set in the system's debug-console monospace. The theme maps only ~18 of Material's color roles, so the bottom nav bar, every dialog, every sheet, **and every default card** silently render in Google's baseline purple-gray — the single loudest "unfinished template" tell in the product. There is no type scale for display numerals, no shape system (nine effective corner radii), a five-value spacing "token system" bypassed by ~110 inline literals, three animations in the entire app, zero haptics in the Compose layer, and a workflow whose money action — start training — is on none of the five tabs.

The good news is structural: because the screens are almost entirely on-scheme and already funnel through shared components (`GymCard`, `GymMetrics`, `GymNumericStyle`, `PrimaryGymButton`), **the app can be re-skinned from the theme layer outward without rewriting screens**. This is a rare and valuable property. The redesign is mostly choreography and tokens on top of correct plumbing — roughly two focused design passes, not a rebuild.

---

## 2. Fix before any design work (found during verification)

Two genuine defects surfaced while verifying the critique. Both belong on `claude/android-fitness-app-review-uhlb1j` before any redesign work starts:

1. **The branch head does not compile.** `HomeScreen.kt:78` and `ScheduleScreen.kt:60` call `LaunchedEffect`, but neither file imports `androidx.compose.runtime.LaunchedEffect` (each imports only `Composable` and `getValue`). The tip commit "clear five dead imports" evidently removed two imports that were live. Fix: one import line in each file, and gate future passes on an `assembleDebug` CI step.
2. **Data-dependent crash in the training calendar.** `TrainingCalendar.kt:58-77` computes `intensity = volume / busiest` where `busiest` considers only in-month days, but leading/trailing padding days keep their real volume — so a heavy month-end session followed by a light month makes `intensity > 1.0`, and `TrainingCalendarCard.kt:140` feeds it into `Color.copy(alpha = 0.35 + intensity * 0.65)`, which throws `IllegalArgumentException` for alpha > 1 and takes down the History tab on open. Fix: `coerceIn(0f, 1f)` at the source, plus a defensive clamp in `DayCell`.

---

## 3. What must survive the redesign

The critique below is severe; these things are right and must not be lost while fixing it:

- **The interaction skeleton of set logging** — big focal numerals, tap-to-type escape hatch ("steppers are for nudging, not setting"), the pinned 72 dp Log bar, prefilled drafts. Restyle the surfaces; keep the model.
- **The PR moment is non-modal** and sits where the eye already is. The code comment defending this is correct. It needs celebration treatment, not a rethink.
- **LastTimeStrip + ProgressionStrip** — evidence ("every working set last time") next to verdict ("Hit target. Add 2.5 kg." + one-tap Use) is better coaching IA than Strong ships. Best-in-class content design in a generic wrapper.
- **The navigation plumbing** — recreation-safe acked-StateFlow nav events, the stack rebuild after finishing (back from Summary can never re-enter a dead session), terminal states that recover instead of dead-ending.
- **On-scheme discipline** — outside the theme, only `BodyMap.kt` hard-codes hex. The whole app recolors from `Theme.kt` in one pass. Protect this property with guardrails (§8).
- **The componentization seed** — `GymMetrics`/`GymCard`/`GymSectionHeader`/`GymNumericStyle` are consumed across the app (55 GymMetrics usages in 11 files). The redesign grows this layer; it does not discard it.
- **Honest copy on destructive actions** — the restore/import confirms state exactly what is replaced and that a safety copy is written first. Restyle the dialogs, keep the words.
- **Craft details:** cold-start window background matched per night qualifier (no launch flash), keepScreenOn scoped to the workout, disabled-until-parsed number entry, error/notice separation in ProgressViewModel.

---

## 4. The critique — six root causes

Every one of the 106 findings traces to one of six root causes. Fix the causes, not the symptoms.

### 4.1 The identity points at the wrong product

The palette (`Color.kt:5-11`) is organic wellness: warm beige field, botanical greens, warm gray. Whoop and Apple Fitness are built on the opposite formula — a near-neutral, slightly cool dark stage where exactly one high-voltage accent and the data itself are the only chroma. Dark mode, which should be this product's flagship (gyms, OLED, keepScreenOn), is a single-hue green wash: background `#0B1A14`, cards `#16382C`, surfaceVariant `#1E3A30`, and the accent shares the same hue — accent, card, and field are one continuous green soup where nothing can pop (`Theme.kt:31-50`). In dark mode `primaryContainer == surface`, so the running rest timer card and an idle card differ by ~1.16:1 — a state distinction the light theme has and dark silently loses.

The second identity failure is typographic: `GymNumericStyle = FontFamily.Monospace + Bold + "tnum"` (`Common.kt:68-72`). On-device that is Droid Sans Mono — the typeface of logcat — carrying the 52 sp weight and the 56 sp rest clock, the most prominent pixels in the product. (`tnum` is a no-op on a monospace face, which tells you the mono was a workaround for missing tabular figures, not a choice.) Whoop's identity lives almost entirely in its numeric display face; this one ships the placeholder.

### 4.2 The theme is half-mapped — Google's baseline purple leaks into the most-touched surfaces

`lightColorScheme`/`darkColorScheme` set ~18 slots; `outline`, `outlineVariant`, and the entire `surfaceContainer` ladder fall through to M3 baseline defaults derived from Google's purple-tinted neutrals. On material3 1.3.x those roles are not obscure:

- `NavigationBar` → `surfaceContainer` (baseline lavender-gray slab), selection pill → `secondaryContainer` (baseline lavender)
- `AlertDialog` → `surfaceContainerHigh`; `ModalBottomSheet` → `surfaceContainerLow`
- **`CardDefaults.cardColors()` → `surfaceContainerHighest`** — so every default `GymCard` (the app's dominant surface: Home cards, history rows, routine cards) renders baseline `#E6E0E9` light / `#36343B` dark. The designed dark card color (`CardDark` on `surface`) is never actually used by cards; the one intended green shows only on "today" cards via `primaryContainer`.
- The rest timer's `LinearProgressIndicator` track and every selected `FilterChip` → unmapped `secondaryContainer` — baseline purple inside the product's signature control.

Invisible in code review because no screen file names these colors; unmissable on screen. Meanwhile `surfaceTint` defaults to primary (Lime), so the one `tonalElevation` surface in the app — the LogBar behind the Log set button — already gets a lime smear in dark mode.

### 4.3 There is no design system, only habits

- **Type:** 7 of 15 M3 roles defined; `headlineSmall` (10 call sites) and `labelMedium` (6) fall through to stock Roboto with Google's tracking, so adjacent text mixes two systems. `headlineLarge` is defined and used nowhere. Zero `letterSpacing` anywhere — the all-caps micro-labels ("REST", "WEIGHT", "REPS") the UI leans on are set with no tracking. No display tier exists in an app whose hero content is 52-56 sp numerals; instead `GymNumericStyle` is copied at 12 call sites with per-site invented sizes (56/52/48/32/28/22/20 sp) — a second, ungoverned type ramp living outside `Type.kt`.
- **Shape:** `MaterialTheme` receives no `shapes`, so stock chips (8), text fields (4), dialogs/sheets (28) coexist with seven hand-scattered radii (21 `RoundedCornerShape` call sites, values 6-24 dp plus a percent). A confirm dialog (28) over a card (20) containing a button (16) next to a chip (8): four unrelated curvatures in one glance is exactly what makes UI read as assembled.
- **Spacing:** `GymMetrics` holds five values; ~110 inline padding/spacing literals sit beside the tokens they duplicate. `screenPadding == sectionGap == 20 dp`, so the spacing scale cannot express hierarchy at all. Header-to-card distance is 12 dp in two Home sections and 20 dp in the other two, on one screen.
- **Chrome:** two competing header systems — Home's hand-rolled greeting/brand/units header vs. twelve stock static `TopAppBar`s, none configured (default white container over the Sand background = a permanent seam on every light screen), none with scroll behavior. Settings invents `headlineSmall` section headers no list screen uses; one Backup section uses three different title scales; one sheet titles in `headlineSmall`, another in unstyled body text.
- **Depth:** one elevation site and two borders in the whole app; everything else relies on container-color deltas that are ~1.7:1 green-on-green in dark. Neither flat-by-design nor material-by-design — undesigned.
- **Motion:** three animations total (a 900 ms alpha blink, one `animateContentSize`, BodyMap color lerps), zero `AnimatedVisibility`, zero nav transitions, zero `animateItem`, zero haptics in the Compose layer. No `Motion.kt`, so every future animation will be another one-off.

### 4.4 Numbers — the product — are typeset as prose

Nearly every metric renders as an interpunct sentence in `bodyMedium`: "3 working sets · 1,240 kg · 42 min", "Set 1 · 100 kg × 8 · RPE 8". Numbers set in running prose cannot be compared down a list; weight columns never align; the most valuable datum has the same weight as the word "working". Within a single ActiveWorkout viewport, "weight × reps" appears in four different typographic voices. Home — the face of a fitness tracker — surfaces no designed numeral at all until a transient rest strip appears: training balance is six 16 dp dots, recent sessions are receipts.

The charts fail harder:

- The e1RM trend — the one chart answering "am I getting stronger?" — draws zero-based bars normalized to their own max, so 100 → 102.5 → 105 kg renders as 95/98/100 % bars: **visually identical rectangles; progress is mathematically invisible** (`TrendChart.kt:49`). Level variables need a focused-domain line; bars-from-zero are for additive volume only.
- Charts are mute: no values, no axis, no reference line, no scrub — and the two chart cards use the same two label slots for different meanings (dates on one, values on the other).
- Weeks with no training are silently skipped, so the time axis lies: a six-week layoff renders as two adjacent bars.
- "Training intensity" speaks three color dialects: green→amber→red heat on the body map, primary-alpha ramp on the calendar, heat dots on Home. Same concept, three encodings — and the heat ramp's green-low collides with the brand's green-action while red frames a well-trained muscle as an error.
- Calendar day numerals composite `onPrimary` text onto 35 %-alpha fills: ~2.0:1 contrast on light days — unreadable exactly where the alpha floor tried to keep them visible.

### 4.5 The workflow has no spine

Tap-counting the money jobs exposes the shape of the problem: log a set = 1 tap (excellent); see last time's sets = 0 taps (excellent); **start today's computed session = 1 tap on a secondary button, while the hero button opens a chooser that doesn't know today's plan**; start a routine from the Routines tab = impossible (card tap lands in an edit form whose own empty-state copy confesses: "Start it from Home when you're in the gym"); swap an exercise mid-workout = impossible (add-only).

Five tabs — Home, Body, Routines, Library, History — and none of them is Train. The "Body" tab is route `progress`, label "Body", icon Whatshot-flame: three metaphors for one screen. Schedule — the weekly spine — is a transient stack screen that opens with three preference-chip rows above the week itself, a first-run wizard that never left. A stale in-progress session locks every start surface in the app while the only discard affordance is two dialogs deep inside the workout it blocks. Six-plus modal `AlertDialog`s saturate the flagship flow (finish, leave, discard, delete-set, typed weight, typed reps, custom rest); finishing — safe, non-destructive, followed by a summary — is gated behind "Saves this session to history."; deleting a trivially restorable set demands a modal instead of undo; and "Finish workout" itself is the last item of the scroll, below the fold on any real session.

### 4.6 The moments that matter are flat

Breaking a personal record renders in the exact visual grammar of the error banner above it — same card anatomy, same radius, title+body+dismiss-X, zero motion, zero haptic, no dedicated color. The workout summary — "the one moment it has the lifter's full attention," per its own doc comment — is a TopAppBar titled page of interchangeable cards whose headline stats are smaller than the mid-workout stepper numerals. Logging a set — the most repeated physical action in the product — produces no haptic, no press response beyond stock ripple, and frequently no visible change (the sets list is often below the fold and nothing scrolls to acknowledge the commit; the pinned Log button never echoes the draft it will commit). The rest timer — the signature instrument — is a scrolling list item with a 12 dp linear bar that alpha-blinks identically at 2:00 and 0:03, its progress stepping once per second, preset chips still competing for touches mid-countdown.

---

## 5. The direction — decision

Both candidate languages are specified in full and are implementable by one developer on Compose:

- **Direction A — "Apple Precision"** (`docs/ui-redesign/DIRECTION_A_APPLE_PRECISION.md`): adaptive light+dark, neutral gray materials, Inter/Inter Display, single restrained green accent, iOS-grouped-list idioms.
- **Direction B — "Instrument"** (`docs/ui-redesign/DIRECTION_B_INSTRUMENT.md`): dark-only OLED near-black, one volt accent, Space Grotesk numerals over Inter, hairline-and-glow depth, the phone as gym equipment.

**Recommendation: Direction B — "Instrument" — with the amendments below.** Rationale:

1. It matches the brief and the product thesis. The owner named Whoop; `DESIGN_AUDIT.md`'s thesis is "a piece of gym equipment, not a form." B is that sentence as a design language.
2. It matches the usage physics: indoor/dim environments, `keepScreenOn` during multi-minute rests (OLED battery + burn-in), one user, one phone.
3. It matches the budget: a solo developer tunes one theme exactly rather than two adequately. B's dark-only argument is honest about the trade and the token architecture leaves a light ladder possible later.
4. It preserves brand DNA (volt `#C2FF44` is the existing Lime, sharpened) instead of a cold rebrand, and its colorblind-safe magma heat ramp is the strongest answer to the three-dialects problem (§4.4).

**Amendments (adopted from A / from verification):**

- **Populate all 15 M3 `Typography` roles and the full `ColorScheme`** (including the whole `surfaceContainer` ladder, `outline`, `outlineVariant`, `surfaceTint = Transparent`) so every stock component lands on-language before it is individually migrated. Both specs agree; this is non-negotiable given §4.2.
- **PR gold is its own family** (`signal/pr`), never shared with success/accent — A and B agree; carry it everywhere a record appears (banner, summary, exercise detail, history rows).
- **Font risk hedge:** Space Grotesk (numerals/display) + Inter (UI) is the recommendation; if Space Grotesk reads too styled in the first on-device build, fall back to A's Inter + Inter Display pairing without touching the token structure. Either way the debug equal-width `tnum` assertion from B §3 gets wired.
- **Ship A's "restraint" enforcement mechanically:** no `Color(0x…)` literal, no `tonalElevation`/`shadowElevation`, no raw `RoundedCornerShape(` outside `ui/theme/` — as a Konsist/lint check, not a convention (§8).
- If the owner in practice trains in bright daylight or wants OS light-mode parity, Direction A is the correct choice instead — the critique in §4 and the IA in §6 are identical under either skin.

### 5.1 Confirmed styles at a glance (Direction B, amended)

| Layer | Decision |
|---|---|
| Theme | Dark-only. `bg/pit #07090B` window, surface ladder `#0E1215 / #14191D / #1B2126`, hairlines `white @ 8%/14%`. No shadows, no tonal elevation — depth = luminance steps + 1 dp hairlines. Edge-to-edge for real (content under bars). |
| Accent | One: volt `#C2FF44` (≈16:1 on pit) = live/act/now, once or twice per screen. Semantics: PR gold `#FFC53D`, warn `#FFB020`, danger `#FF6B6B`, rest cyan `#33D6E8`. |
| Text | `#F2F5F7` primary (never #FFF), `#9BA7AE` secondary, `#5F6B73` decorative-only. |
| Numerals | Space Grotesk 500, `tnum` (+`zero` on hero), ramp: hero 76 / xl 56 / lg 36 / md 24 / sm 17. Units always subordinate (`unit` 13 sp secondary, baseline-aligned). Kills monospace. |
| UI text | Inter: display 28/700, title 16/600, body 14/400, caption 12, kicker 11/600/+8% caps — the instrument label voice, replacing every ad-hoc "REST/WEIGHT/REPS". |
| Heat | Magma-derived 5-stop ramp `#262C31 → #4A2480 → #9D2F86 → #E25A50 → #FCA05F` — luminance-monotonic (colorblind-safe), used identically by body map, calendar, and Home dots. One concept, one encoding. |
| Shape | 8 / 12 / 16 / 24 / full — one family, passed into `MaterialTheme.shapes`, zero inline radii. |
| Spacing | 4 dp grid (4…40), 16 dp gutter, section = kicker-owned 28 dp, touch floors 48/56/72 dp (commit tier non-negotiable). |
| Motion | Tokens: 90/150/240/350/650 ms + three springs. Fade-through nav, continuous ring sweep, ≤10 s warn recolor + 1 Hz pulse, PR gold flash + triple-pulse haptic, chart draw-in once, `animateItem` everywhere keyed lists mutate, `AnimatedVisibility` for all conditional content. |
| Haptics | Stepper/chip `EFFECT_TICK`, log set `EFFECT_HEAVY_CLICK`, rest done double-pulse, PR triple-pulse, destructive `EFFECT_DOUBLE_CLICK`, last-5 s tick/s. |
| Components | `InstrumentCard` (hairline, no elevation), stat tile (kicker + numeral/lg + unit), grouped-list rows replacing card-per-item stacks, rest ring (240 dp running / 96 dp idle / sticky 56 dp bar), gradient chart with scrubber + dashed gold PR line, hand-rolled bottom nav (volt tick, no M3 pill), full-width stacked dialog buttons. |

---

## 6. Target information architecture

Three tabs, one spine, one live surface (from the workflow/IA review; independent of the visual direction):

- **Today** — the current Home + week strip absorbed. Masthead: date kicker + today's answer ("PUSH DAY · 4 LIFTS") — never the app's own name. One hero card = today's plan with the screen's only filled button (Start/Resume); 7-day strip under it; stat tiles; grouped Recent. Settings = bare gear icon.
- **Plan** — Routines (start-first: card tap → preview with a dominant Start; edit behind it) with Library as a segment/sub-view, since the library's real job is feeding routines and the picker.
- **Progress** — History + training calendar + body map + PRs unified; the heat map becomes a module, not a tab.

Plus:

- **LiveSessionBar** — one persistent bar above the nav bar whenever a session is in progress (lift, set count, rest countdown; tap = return). Deletes the three hand-rolled Resume buttons, unlocks every screen during a session, and makes the "leave workout?" dialog unnecessary (back just leaves; discard moves to an overflow with its existing confirm).
- **One start spine** — the hero starts today's plan directly; "other options" opens a sheet (today preselected → routines by last-done → free workout last). `Route.StartWorkout` as an interstitial disappears. A stale session always offers "Discard current session…" next to Resume, right where it blocks.
  **Delivered in Phase 6b.** `StartOptionsSheet` replaces `StartWorkoutScreen`, and `Route.StartWorkout` is deleted — declaration, composable and every caller. The hero starts today's plan in one tap and opens the sheet only when there is a question to ask: a rest day, an empty week, or a session already running. The sheet's order is today → the coach's suggestion → routines → free workout, and when a session is live it shows "Go to session" and "Discard it" instead of any start. Home, Progress and History all open the same sheet, so there is one spine rather than three.
- **Mid-workout swap/remove** — overflow on the current lift header; `WorkoutRepository.removeExerciseFromSession` already exists with no UI caller.
- **Confirm destruction, never completion** — Finish executes immediately (dialog deleted, Finish also pinned in the top bar); delete-set becomes immediate + snackbar Undo; sheets host tasks, alerts only confirm (routine picker dialog → sheet).

## 7. Migration plan (each phase shippable)

0. **Repair (hours):** the two §2 fixes on the app branch; add `assembleDebug` to CI.
1. **Foundations (1-2 days):** full token set (`InstrumentColors/Type/Metrics/Motion`), all 15 type roles + full ColorScheme mapping, fonts in `res/font`, `surfaceTint = Transparent`, dark-only, edge-to-edge, window background. The entire app retints/re-types in one pass because the screens are on-scheme (§3).
2. **Shared components (≈3 days):** InstrumentCard, buttons tiers, steppers (+hold-repeat +haptics), rest ring, kicker/section header, grouped-list row, stat tile, bottom nav, header grammar. ~70 % of the visible app shifts here — every screen funnels through these.
3. **The instrument (≈1 week):** ActiveWorkout staging (rest ring hero while resting, entry panel compacted to one shared surface ≤220 dp, set table, draft echoed on the Log button, scroll-to-acknowledge, PR gold moment), Summary celebration (count-up, staggered tiles, gold), finish-dialog deletion.
4. **IA consolidation (≈1 week):** three tabs, LiveSessionBar, start spine + sheet, routine start-first + editor autosave (the editor currently discards uncommitted edits on back — silent data loss), swap/remove, stale-session escape hatch.
5. **Data surfaces (≈1 week):** MetricCluster rollout (kills interpunct prose), focused-domain line chart for e1RM + annotated/scrubbable charts + gap-preserving time axis, one heat ramp across map/calendar/dots, calendar cell legibility, records grid + gold, recommendation cards → evidence chip + labeled action.
6. **Polish pass:** motion/haptics sweep (`animateItem`, `AnimatedVisibility` rule, delay-gated spinners on local reads), Settings grouped lists, picker sheet full-height + multi-add, empty-state scaffolds for charts.

## 8. Guardrails (or the system erodes again)

- CI lint/Konsist: no `Color(0x…)`, `tonalElevation`, `shadowElevation`, or raw `RoundedCornerShape(` outside `ui/theme/`; no `GymNumericStyle.copy(fontSize=…)` (sizes come from the numeral ramp).
- A debug swatch screen rendering every ColorScheme slot + Card/chip/progress defaults, so no role ever ships at Material baseline again (§4.2 was invisible in code review).
- The debug equal-width numeral assertion (`"11111"` vs `"00000"`) guarding `tnum` across font updates.
- Accent budget rule in review: volt appears once or twice per screen; a third glow means one of the three is wrong.
- Every new gap, radius, duration, or size is a token first.

---

## 9. What shipped, and how it was verified

The redesign was implemented on this branch in the order set out in §7. Direction B was built as specified in §5.1, with the §5 amendments folded in.

**Delivered:** the two §2 defects; a dark-only theme with every Material colour role mapped explicitly; bundled Space Grotesk and Inter with real tabular figures; token files for colour, type, shape, spacing, motion and haptics; a rebuilt component library (grouped lists, metric clusters, stat tiles, instrument chips, the three banner tones, the rest ring, the merged set-entry panel, both chart kinds); and every screen migrated onto it. Design-token violations went from 22 to zero.

**Not delivered, and why.** Exercise imagery and the equipment field it needs belong with the Phase 4 schema change — the picker and library rows now reserve the leading slot for them, so they land without another layout pass. Mid-workout swap/remove, delete-set undo, and routine-editor autosave all need ViewModel or repository work that was deliberately out of scope for a design pass; the repository method for the first of them (`removeExerciseFromSession`) already exists with no caller. The three-tab IA consolidation in §6 and the persistent live-session bar are the largest remaining items: they change navigation structure rather than presentation, and the plumbing they touch is load-bearing enough to deserve their own change.

**Amended after Phases 1–6b.** That paragraph has largely been overtaken, and leaving it as
written would misreport the app. Delivered since: the live-session bar (1A); mid-workout
swap/remove and delete-set undo (1B and 5); routine-editor autosave, which now writes the
name and notes on exit rather than asking (`persistDetailsOnExit`); the equipment field and
the schema behind imagery (3); and the IA consolidation itself (6a/6b) — though as **four**
tabs, not three, per the signed D1 adjudication: Home · Body · Plan · History, with Library
demoted to a pushed route. The only item on that list still outstanding is exercise imagery
(Phase 8, optional); the leading slot in the picker and library rows is still reserved for it.

**How it was verified.** This work was done in an environment with no Android SDK and no route to Google's Maven, so **no compiler ever saw this code and no screen was ever rendered.** That is the single most important caveat on everything above, and it was compensated for rather than ignored:

- `tools/run-domain-tests.sh` runs the domain suite on a plain JVM — 179 tests, green — which is how the calendar fix was proved: it fails with `intensity 8.0` before the clamp.
- `tools/syntax-check.sh` parses every file with the real Kotlin compiler.
- `tools/check-named-args.py` matches every named argument against its declaration across the tree.
- `tools/check-internal-imports.py` resolves every in-project import and every design-token member against a real declaration.
- `tools/check-design-tokens.py` enforces §8.
- `tools/build-fonts.py` asserts the tabular-figure widths and re-derives the required glyph set from the source rather than trusting a hand-written range.
- An adversarial multi-agent audit read the whole diff against the exact pinned library versions, hunting for API mistakes, runtime hazards and dropped behaviour.

None of that substitutes for `./gradlew assembleDebug` and a device. **The first thing to do with this branch is build it and look at it**, starting with the previews in `ui/theme/ThemeGallery.kt` — a colour role that slipped back to a Material baseline shows up there as an obvious violet chip.

---

## Appendix A — full findings register

Severities shown are post-verification (10 findings corrected, 0 rejected; "verifier catch" = defect the adversarial pass added). Line numbers refer to `de1ffac`.

### Design foundations & tokens

| ID | Severity | Finding | Evidence | Verification |
|---|---|---|---|---|
| F-01 | major | The palette is organic wellness, not high-tech instrument | `Color.kt:5` | corrected |
| F-02 | critical | Dark theme is a single-hue green wash with no neutral layer | `Theme.kt:38` | corrected |
| F-03 | major | Half-mapped color scheme leaks Material-baseline purple into core chrome | `Theme.kt:10` | confirmed |
| F-04 | major | Type system is 7 of 15 roles, zero tracking, no display tier, dead tokens | `Type.kt:9` | confirmed |
| F-05 | major | Hero numerals are system Monospace with a shadow size ramp at call sites | `Common.kt:69` | confirmed |
| F-06 | major | No shape system: nine effective corner radii, restated at every call site | `Theme.kt:57` | confirmed |
| F-07 | major | The token layer is five dp values; spacing remains ad-hoc across 20 files | `GymSurfaces.kt:24` | confirmed |
| F-08 | minor | No depth strategy: one elevation in the whole app and an inert tonal system | `ActiveWorkoutScreen.kt:503` | confirmed |
| F-09 | minor | Motion values are inline magic numbers with no token layer | `Common.kt:404` | confirmed |
| F-10 | major | Top chrome is unthemed stock: white-on-sand seam and two title systems | `ProgressScreen.kt:60` | confirmed |
| F-11 | major | Data color has no token layer: two encoding systems, ten inline hexes | `BodyMap.kt:236` | confirmed |
| F-12 | minor | M3 role slots are hijacked in place of a semantic color layer | `HomeScreen.kt:214` | confirmed |
| F-13 | polish | Edge-to-edge is enabled but never designed for | `MainActivity.kt:26` | corrected |
| F-V1 | major | Baseline-role leak reaches the app's own component library: every default card, the rest timer's progress t... | `GymSurfaces.kt:64` | verifier catch |

### Navigation shell, Home & Schedule

| ID | Severity | Finding | Evidence | Verification |
|---|---|---|---|---|
| S-01 | critical | Tab bar and selection chrome render in baseline Material purple — the brand breaks in the most visible pixe... | `Theme.kt:31` | confirmed |
| S-02 | critical | Edge-to-edge is declared but never designed: doubled status-bar inset above every pushed screen, and conten... | `AppNav.kt:155` | confirmed |
| S-03 | major | Two competing chrome systems: a bespoke Home header vs eleven stock static TopAppBars, none with scroll beh... | `HomeScreen.kt:252` | confirmed |
| S-04 | major | The product's face leads with its own brand name; the settings affordance is a captioned double-ripple widget | `HomeScreen.kt:252` | confirmed |
| S-05 | major | Home has no hero metric — a fitness dashboard with zero designed numerals above the fold | `HomeScreen.kt:345` | confirmed |
| S-06 | major | CTA competition and duplicated affordances: stacked full-width buttons plus section-header actions that rep... | `HomeScreen.kt:119` | corrected |
| S-07 | major | Schedule opens as a settings form: prose + three preference chip rows before the week itself | `ScheduleScreen.kt:118` | confirmed |
| S-08 | major | Day cards have no calendar geometry and mount a filled primary button per training day | `ScheduleScreen.kt:262` | confirmed |
| S-09 | minor | Day-state styling is improvised inline: alpha-faded rest days and ad-hoc container swaps instead of tokens | `ScheduleScreen.kt:218` | confirmed |
| S-10 | minor | Regenerating the week plan is a bare Refresh glyph in the app bar | `ScheduleScreen.kt:78` | confirmed |
| S-11 | minor | Two different section rhythms on one screen: wrapped 12dp header-card gaps vs 20dp item gaps, and a gap sca... | `HomeScreen.kt:96` | confirmed |
| S-12 | minor | Tab iconography fights the mental model: a flame for 'Body', a book for exercises, and no Train destination | `AppNav.kt:100` | confirmed |
| S-V1 | critical | Home and Schedule do not compile: LaunchedEffect is used without an import in both anchor screens | `HomeScreen.kt:78` | verifier catch |
| S-V2 | major | Every content card falls back to baseline M3 surfaceContainerHighest — the app's dominant surface is off-br... | `GymSurfaces.kt:64` | verifier catch |

### Core workout flow

| ID | Severity | Finding | Evidence | Verification |
|---|---|---|---|---|
| W-01 | critical | Rest timer is a scrolling list item with a strip gauge, not an anchored instrument | `ActiveWorkoutScreen.kt:221` | confirmed |
| W-02 | major | No state choreography: resting and lifting are the same screen | `Common.kt:423` | corrected |
| W-03 | major | Preset chips stay mounted during the countdown, breaking one-primary-action | `Common.kt:513` | confirmed |
| W-04 | critical | Set entry is a stacked full-width form, not a compact logging instrument | `ActiveWorkoutScreen.kt:295` | confirmed |
| W-05 | major | Hero numerals are default system monospace — a debug-console face as the brand | `Common.kt:69` | confirmed |
| W-06 | major | The app's core datum — weight × reps — renders in three different typographic voices on one screen | `ActiveWorkoutScreen.kt:619` | confirmed |
| W-07 | major | The PR moment wears the visual grammar of an error banner | `ActiveWorkoutScreen.kt:645` | confirmed |
| W-08 | major | State is communicated as appended copy, not design | `ActiveWorkoutScreen.kt:818` | confirmed |
| W-09 | minor | The set ordinal gets hero-numeral treatment while set progress has no glyph | `ActiveWorkoutScreen.kt:578` | confirmed |
| W-10 | major | StartWorkout is a bare list picker that sells nothing about the session | `StartWorkoutScreen.kt:127` | confirmed |
| W-11 | major | The summary is a receipt, not a reward | `WorkoutSummaryScreen.kt:66` | corrected |
| W-12 | major | Six modal AlertDialogs saturate the flagship flow | `ActiveWorkoutScreen.kt:469` | confirmed |
| W-13 | minor | The instrument narrates itself with instructional captions | `Common.kt:508` | corrected |
| W-14 | major | The top bar is pure navigation chrome — zero live session telemetry | `ActiveWorkoutScreen.kt:159` | confirmed |
| W-V1 | major | Logging a set — the flagship tap — has no haptic or audible confirmation, and no haptics exist anywhere in ... | `ActiveWorkoutScreen.kt:521` | verifier catch |
| W-V2 | major | The pinned Log bar commits values that can be scrolled off-screen — the primary action is blind | `ActiveWorkoutScreen.kt:520` | verifier catch |
| W-V3 | major | The flagship flow has zero lift imagery or equipment identity — pure text against a thesis that makes the p... | `DESIGN_AUDIT.md:41` | verifier catch |

### Data & progress surfaces

| ID | Severity | Finding | Evidence | Verification |
|---|---|---|---|---|
| D-01 | critical | Every metric is typeset as a sentence, not a numeral | `Common.kt:131` | corrected |
| D-02 | critical | e1RM trend as zero-based bars normalized to own max — progress is invisible | `TrendChart.kt:49` | confirmed |
| D-03 | major | Charts are mute: no values, no axis, no emphasis, no interaction — and the track pills read as progress meters | `TrendChart.kt:53` | confirmed |
| D-04 | major | Heat scale runs green-to-red, colliding with the brand and framing training as danger | `BodyMap.kt:236` | corrected |
| D-05 | major | Three different visual encodings for the same concept: training intensity | `TrainingCalendarCard.kt:140` | confirmed |
| D-06 | major | Body map figure: two rounded rectangles, identical front and back, stretched per device | `BodyMap.kt:100` | confirmed |
| D-07 | major | RecommendationCards read as a numbered lint list, not coaching | `RecommendationCards.kt:32` | corrected |
| D-08 | major | Calendar day numerals are illegible at the intensity floor | `TrainingCalendarCard.kt:169` | confirmed |
| D-09 | major | Hero numerals are set in system Monospace — instrument data in a debugger's face | `Common.kt:69` | confirmed |
| D-10 | minor | Window picker: cryptic chip labels leaking into copy | `ProgressScreen.kt:207` | confirmed |
| D-11 | minor | PR records: three low-density cards and no PR color anywhere | `ExerciseDetailScreen.kt:114` | corrected |
| D-12 | minor | Trend sections silently vanish below two points — no scaffold toward the chart | `TrendChart.kt:39` | confirmed |
| D-13 | polish | SessionLogRow color hierarchy is inverted | `Common.kt:119` | corrected |
| D-V1 | critical | Calendar padding days can drive fill alpha past 1.0 and crash the History tab | `TrainingCalendar.kt:58` | verifier catch |
| D-V2 | major | Trend charts render non-uniform time on a uniform axis — training gaps vanish | `ExerciseHistory.kt:99` | verifier catch |
| D-V3 | minor | Both trend charts are silent to TalkBack — bare Canvas with no semantics | `TrendChart.kt:41` | verifier catch |

### Management surfaces

| ID | Severity | Finding | Evidence | Verification |
|---|---|---|---|---|
| M-01 | critical | Routine editor is a CRUD form: metadata chrome and two rival hero buttons come before the program itself | `RoutineEditorScreen.kt:131` | confirmed |
| M-02 | major | Training targets are edited through labeled admin text boxes, a different data language from the workout sc... | `RoutineEditorScreen.kt:266` | confirmed |
| M-03 | major | Routine cards read as to-do items: two identical gray lines, CMS metadata language, trash can as the only a... | `RoutinesScreen.kt:196` | confirmed |
| M-04 | major | Picker sheet: create-flow chrome dominates the pick flow, the title is unstyled body text, and results live... | `ExercisePickerSheet.kt:68` | confirmed |
| M-05 | major | Settings is five prose essays, not a settings screen: no grouped surfaces, oversized headers, unstyled body... | `SettingsScreen.kt:191` | confirmed |
| M-06 | major | Settings button hierarchy is inverted: file export gets the hero button, data-destroying restore is a plain... | `SettingsScreen.kt:361` | confirmed |
| M-07 | major | Header and sheet-title type roles are assigned per file, with three scales inside one Settings section | `SettingsScreen.kt:353` | confirmed |
| M-08 | major | Container grammar is arbitrary: the same pick-from-list task is a bottom sheet in one flow and a stack of t... | `ExerciseLibraryScreen.kt:310` | confirmed |
| M-09 | major | Two divergent creation forms exist for the same object, and the editor sheet shows two competing muscle-gro... | `ExercisePickerSheet.kt:73` | confirmed |
| M-10 | minor | Library card actions carry identical visual weight to metadata: up to 144dp of gray icon chrome per row | `ExerciseLibraryScreen.kt:259` | confirmed |
| M-11 | minor | Corner-radius token bypassed: raw 12dp M3 Cards sit among 20dp GymCards in the same flows | `RoutineEditorScreen.kt:240` | confirmed |
| M-12 | minor | Spacing tokens exist but Settings and both bottom sheets bypass them wholesale, plus a repeated 88dp magic ... | `SettingsScreen.kt:120` | confirmed |
| M-13 | minor | Status feedback is bare primary-colored strings injected into scroll content | `ExerciseLibraryScreen.kt:123` | confirmed |
| M-14 | polish | Dead Column wrapper around the exercise picker sheet | `RoutineEditorScreen.kt:173` | confirmed |
| M-V1 | major | The manual-save model does not just look wrong — it silently discards edits on back | `RoutineEditorScreen.kt:71` | verifier catch |
| M-V2 | major | The picker closes after every selection, so building a program costs one full round trip per lift | `RoutineEditorViewModel.kt:243` | verifier catch |
| M-V3 | minor | An invisible 1dp focus-sink hack suppresses the keyboard on the search picker and pollutes the focus order | `ExercisePickerSheet.kt:50` | verifier catch |

### Workflow & information architecture

| ID | Severity | Finding | Evidence | Verification |
|---|---|---|---|---|
| IA-01 | critical | Start hierarchy is inverted: the hero CTA opens a chooser, the computed plan gets a secondary button | `HomeScreen.kt:119` | confirmed |
| IA-02 | critical | The Routines tab cannot start a workout — tapping a routine lands in an edit form | `AppNav.kt:235` | corrected |
| IA-03 | major | StartWorkoutScreen is a data-free interstitial that doesn't know today's plan — two parallel start spines t... | `StartWorkoutScreen.kt:97` | confirmed |
| IA-04 | major | Five tabs, none of them 'Train'; the 'Body' tab's label, route, and icon are three different metaphors | `AppNav.kt:100` | confirmed |
| IA-05 | major | The weekly plan — the app's spine — lives on a transient stack screen with setup chips permanently inlined ... | `ScheduleScreen.kt:169` | corrected |
| IA-06 | major | Mid-workout exercise management is add-only: no swap, no remove | `ActiveWorkoutScreen.kt:557` | confirmed |
| IA-07 | major | Finishing a workout is gated behind a redundant confirmation dialog | `ActiveWorkoutScreen.kt:386` | confirmed |
| IA-08 | minor | Leaving a live workout costs a modal every time, even though leaving is already safe | `ActiveWorkoutScreen.kt:139` | confirmed |
| IA-09 | major | No persistent live-session surface: Resume is hand-rolled on three screens and unreachable from four of fiv... | `StartWorkoutScreen.kt:83` | corrected |
| IA-10 | minor | Recommendations drop their context at the navigation boundary | `RecommendationCards.kt:68` | confirmed |
| IA-11 | minor | The SessionDetail–ExerciseDetail bounce guard never engages; back replays the whole tour | `AppNav.kt:307` | confirmed |
| IA-12 | polish | A load failure on the Body tab is answered with 'Start workout' | `ProgressScreen.kt:76` | confirmed |
| IA-V1 | major | A stale in-progress session locks every start surface; the only discard is two dialogs deep inside the work... | `StartWorkoutScreen.kt:89` | verifier catch |
| IA-V2 | minor | Finish workout is the last item of the scrolling content — the session's terminal action must be hunted bel... | `ActiveWorkoutScreen.kt:344` | verifier catch |

### Motion, interaction & feedback

| ID | Severity | Finding | Evidence | Verification |
|---|---|---|---|---|
| MO-01 | critical | Logging a set — the product's core action — has zero physical feedback | `ActiveWorkoutScreen.kt:520` | confirmed |
| MO-02 | critical | Rest countdown motion is a constant alpha-blink over a 1-second-stepped bar — no urgency, no sweep | `Common.kt:404` | confirmed |
| MO-03 | major | No screen transition vocabulary: NavHost uses defaults and the bottom bar pops in and out | `AppNav.kt:152` | confirmed |
| MO-04 | major | The PR banner — the app's one reward moment — renders as a static card that pops into a list | `ActiveWorkoutScreen.kt:212` | confirmed |
| MO-05 | major | Rest card state changes swap content instantly inside an animating frame — half-committed motion | `Common.kt:410` | confirmed |
| MO-06 | major | Confirmation-dialog culture where undo should be: four AlertDialogs on the workout screen alone | `ActiveWorkoutScreen.kt:469` | confirmed |
| MO-07 | major | Keyed lazy lists never animate item placement — rows pop and jump on every mutation | `ActiveWorkoutScreen.kt:324` | confirmed |
| MO-08 | major | Loading strategy is a lone centered spinner on a local-first database — guaranteed spinner-flash | `Common.kt:115` | confirmed |
| MO-09 | minor | Steppers lack press-and-hold repeat and per-detent haptics — the code comment admits the cost | `Common.kt:155` | confirmed |
| MO-10 | minor | Conditional content snaps in and out everywhere — AnimatedVisibility appears nowhere in the codebase | `ActiveWorkoutScreen.kt:762` | corrected |
| MO-11 | minor | TrendBars is a picture of data, not an instrument — non-interactive, no readout, no settle | `TrendChart.kt:41` | confirmed |
| MO-12 | minor | No motion spec exists: the app's three animations use three unrelated ad-hoc curves | `BodyMap.kt:108` | confirmed |
| MO-V1 | major | The workout-complete summary — the product's biggest reward beat — has zero celebration motion | `WorkoutSummaryScreen.kt:47` | verifier catch |
| MO-V2 | major | Nothing ever scrolls to acknowledge a logged set — the confirming row is frequently below the fold | `ActiveWorkoutScreen.kt:201` | verifier catch |