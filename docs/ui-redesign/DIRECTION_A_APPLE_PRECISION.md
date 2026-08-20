# Apple Precision

A neutral-canvas, single-accent design language in the idiom of Apple Fitness/Health — Inter-driven typographic hierarchy, layered gray materials, tabular-numeral instrumentation, and quick physical motion — mapped onto every existing PersonalTrainer screen.

# Design Direction A — "Apple Precision"

A complete design language for PersonalTrainer in the spirit of Apple Fitness/Health and the HIG, buildable by one developer on Jetpack Compose / Material 3. It replaces the current "gym green" theme (`ui/theme/Color.kt`: Forest/Leaf/Lime/Sand) with a neutral instrument canvas and exactly one accent, and replaces the monospace `GymNumericStyle` (`ui/components/Common.kt:68-72`) with a real numeral type system. Everything below is written against the actual screens in `ui/` and the audit's design-system gaps (D-01…D-12).

---

## 1. Philosophy

- **Type is the interface.** Hierarchy comes from size, weight, and tracking — never from boxes, borders, or extra color. If a label needs a colored chip to be noticed, the type scale failed.
- **One accent, spent like money.** A single green tint appears only on: the primary action, the active state, and live data. Everything else is a gray. Whoop and Apple Fitness feel expensive because 95% of every screen is neutral.
- **Numbers are the product.** Weight, reps, e1RM, and the clock get their own display roles with tabular figures. A weight should look like it came off a machined dial, not a paragraph.
- **Depth by surface, not shadow.** Light mode: white cards on a cool-gray canvas. Dark mode: lightened layers on true black. Shadows exist only under things that float (sheets, dialogs, menus).
- **Motion is physics, not decoration.** Everything responds in under 300 ms with a decelerating curve or a stiff spring. The only slow moment in the app is a PR.

---

## 2. Color tokens

All tokens are semantic; no screen references a raw hex (today `BodyMap.kt:90` and `heatFill` hard-code hexes inline — that pattern is eliminated). `surfaceTint` is set to transparent so M3's tonal-elevation overlay never repaints these values.

### Neutrals — the instrument chassis
*Rationale: a cool, desaturated gray ramp (iOS-grouped-background lineage) makes the accent and the heat map the only chroma on screen.*

| Token | Light | Dark | Use |
|---|---|---|---|
| `canvas` | `#F4F4F6` | `#000000` | Screen background (edge-to-edge, behind bars) |
| `surface1` | `#FFFFFF` | `#151518` | Cards, list rows, nav bar |
| `surface2` | `#ECECF0` | `#202024` | Inset fills: inputs, chips, steppers, secondary buttons |
| `surface3` | `#FFFFFF` | `#2A2A2F` | Floating: sheets, dialogs, menus |
| `hairline` | `#E2E2E7` | `#FFFFFF` @ 10% | 0.5 dp separators; card border in dark only |
| `scrim` | `#000000` @ 40% | `#000000` @ 55% | Behind sheets/dialogs |

### Text
*Rationale: three fixed steps of emphasis replace today's ad-hoc `onSurfaceVariant` sprinkling.*

| Token | Light | Dark | Contrast (on surface1) |
|---|---|---|---|
| `textPrimary` | `#141518` | `#F4F4F6` | 18.3:1 light · 16.6:1 dark |
| `textSecondary` | `#5A5E66` | `#A2A5AC` | 6.5:1 light · 7.6:1 dark |
| `textTertiary` | `#8A8E96` | `#6C6F76` | 3.5:1 — decorative/metadata only, ≥13 sp, never sole carrier of meaning |

### Accent — "Signal Green"
*Rationale: keeps the product's green lineage but shifts it from forest-organic to spectral-instrument; two tuned values because one hex cannot pass contrast on both white and near-black.*

| Token | Light | Dark | Use |
|---|---|---|---|
| `accent` | `#0B7A4B` (5.4:1 on white) | `#35D07F` (9.1:1 on `#151518`) | Primary buttons, active tab, live timer, links |
| `accentPressed` | `#08603B` | `#2AB56C` | Pressed state |
| `accentSubtle` | `#E2F4EA` | `#12291D` | Selected chip fill, trained calendar day |
| `onAccentSubtle` | `#0A5C39` | `#63E3A0` | Text/icons on `accentSubtle` |
| `onAccent` | `#FFFFFF` | `#04140C` | Text on `accent` fills (7.1:1 light, 9+:1 dark) |

### Status
*Rationale: PR gold is its own family — a personal record is the emotional peak of the product and must not share color with generic "success".*

| Token | Light | Dark | Use |
|---|---|---|---|
| `prGold` | `#8F6A00` (4.6:1 on white) | `#FFD60A` (12.9:1 on `#151518`) | PR text, PR badge stroke |
| `prGoldFill` | `#FFF3C2` | `#3A3110` | PR banner/badge fill |
| `warning` | `#B45309` | `#FFB454` | Rest-timer final seconds, thin-history notices |
| `danger` | `#BE3A2E` | `#FF6B5E` | Destructive confirm, delete, discard |
| `dangerSubtle` | `#FBE9E7` | `#331512` | Destructive row/banner fill |

### Muscle-heat scale (Progress body map + Home highlights)
*Rationale: neutral→green→amber→red reads as load, not decoration; stops are luminance-tuned per theme so "low" is visible on black and "max" is not neon on white. Interpolate linearly between adjacent stops (replaces `heatFill` in `BodyMap.kt:232-236`).*

| Stop | Light | Dark |
|---|---|---|
| 0.0 none | `#E8EAED` | `#232327` |
| 0.25 low | `#86CFA0` | `#2F9E63` |
| 0.50 moderate | `#F5C044` | `#E0A83E` |
| 0.75 high | `#F08A3C` | `#F07539` |
| 1.0 max | `#E25141` | `#FF5A47` |

### Charts
| Token | Light | Dark | Use |
|---|---|---|---|
| `chartPrimary` | `accent` | `accent` | Current/latest series & bar |
| `chartMuted` | `#0B7A4B` @ 28% | `#35D07F` @ 30% | Historical bars/points |
| `chartSecondary` | `#2563EB` | `#4C8DFF` | Second series (e1RM vs top-set weight) |
| `chartGrid` | `hairline` | `hairline` | Baselines only — no full grids |

---

## 3. Typography

**Family: Inter (+ Inter Display for ≥28 sp).** Both on Google Fonts under OFL, bundleable as static TTFs or one variable font. Why Inter: it is the closest credible SF Pro analogue that can legally ship on Android — designed for UI at screen sizes, tall x-height, nine weights, true tabular/lining figures via OpenType, and Inter Display's tighter apertures give large numerals the machined look SF Pro Display gives Apple Fitness. Rejected: Roboto Flex (reads as Android default — exactly what the owner wants to escape), Manrope (too rounded/branded), IBM Plex Sans (editorial, not instrument).

**Numeral rule (hard requirement):** every style that can render a weight, rep, duration, e1RM, or clock uses
`fontFeatureSettings = "tnum"` (Inter's tabular-numeral feature; add `"tnum, zero"` in editable fields if 0/O ambiguity matters). All text styles set `PlatformTextStyle(includeFontPadding = false)` and `LineHeightStyle(Alignment.Center, Trim.None)` so numerals center optically in tiles and rings. This retires the monospace `GymNumericStyle` — monospace is the #1 "engineer-built" tell in the current app.

| Role | Family | Size/Line (sp) | Weight | Tracking | Use |
|---|---|---|---|---|---|
| `numeralXL` | Inter Display | 64/68 | 600 | −1.0 sp | Rest clock (running), stepper focal number |
| `numeralL` | Inter Display | 44/48 | 600 | −0.5 sp | Summary hero stat, PR weight |
| `numeralM` | Inter Display | 28/32 | 600 | −0.25 sp | Stat tiles, docked rest clock, e1RM |
| `numeralS` | Inter | 17/22 | 500 | 0 | Numbers inside list rows/set table |
| `largeTitle` | Inter Display | 32/38 | 700 | −0.4 sp | Screen large titles (Home "Today", History) |
| `title1` | Inter Display | 24/30 | 600 | −0.3 sp | Summary header, exercise name on ActiveWorkout |
| `title2` | Inter | 20/25 | 600 | −0.2 sp | Section headers, sheet titles |
| `headline` | Inter | 17/22 | 600 | −0.1 sp | Card titles, row titles, collapsed top bar |
| `body` | Inter | 16/22 | 400 | 0 | Default prose |
| `subhead` | Inter | 14/19 | 400 | 0 | Row second lines, helper text |
| `footnote` | Inter | 13/18 | 400 | +0.1 sp | Metadata, chart axis labels (tnum) |
| `label` | Inter | 11/14 | 600 | +0.6 sp, UPPERCASE | Eyebrows: "REST", "WEIGHT", "REPS", tile captions |
| `caption` | Inter | 12/16 | 500 | +0.2 sp | Tab labels, calendar day numbers (tnum) |

M3 mapping (so unmigrated screens inherit correctly): `displayLarge→numeralXL`, `displaySmall→numeralL`, `headlineLarge→largeTitle`, `headlineMedium→title1`, `titleLarge→title2`, `titleMedium→headline`, `bodyLarge→body`, `bodyMedium→subhead`, `labelLarge→headline(14sp variant)`, `labelSmall→label`. This fills all 15 roles (audit D-01).

---

## 4. Spacing & layout grid

4-pt base grid. Tokens (replace `GymMetrics` in `Common.kt:24-32`):

- **Scale:** `s1=4, s2=8, s3=12, s4=16, s5=20, s6=24, s8=32, s10=40`.
- **Screen margin:** 16 dp (down from the current 20 dp `screenPadding`) — Apple's compact margin; two stat tiles fit with a 12 dp gutter.
- **Section rhythm:** 24 dp between sections (`s6`), 8 dp between a section header and its first card, 8 dp between sibling cards (down from `listGap=12` — grouped-inset lists read as one object).
- **Card padding:** 16 dp horizontal, 14 dp vertical; compact tiles 12 dp.
- **List rows:** one-line 52 dp, two-line 64 dp; set-table rows 44 dp visual with full-width 48 dp tap target (per audit hit-target rules); stat tile min-height 76 dp.
- **Buttons:** primary 50 dp, hero primary (Start workout only) 56 dp, secondary 44 dp, steppers 64×88 dp (kept large — gym gloves, audit R-rules).
- **Baseline discipline:** every vertical gap is a scale value; no more free `6.dp`/`10.dp` literals (today `GymCard` uses `spacedBy(6.dp)`).

---

## 5. Shape & depth

**Radius scale:** `rSm=8` (badges, calendar pills), `rMd=12` (buttons, inputs, chips-rect, tiles), `rLg=16` (cards — down from today's 20 dp which reads bubbly), `rXl=24` (sheet top corners, dialogs), `rFull` (capsule chips, progress tracks, timer ring). One `Shapes` object; grep-able rule: zero inline `RoundedCornerShape(` outside the theme package.

**Depth model — how "material" is achieved in Compose:**

- **Light:** elevation = contrast. `surface1` white on `#F4F4F6` canvas, **no borders, no shadows** on resting cards (`CardDefaults.cardElevation(0.dp)`). Floating layer (`surface3` sheets/dialogs/menus) gets a single soft shadow: `shadow(16.dp, rXl, ambientColor = #000 @ 8%, spotColor = #000 @ 12%)`.
- **Dark:** elevation = lightening only. Layers are the explicit tokens `#000 → #151518 → #202024 → #2A2A2F`; **never** M3 tonal elevation (set `surfaceTint = Color.Transparent` and `tonalElevation = 0.dp` everywhere) and never shadows (invisible on black). `surface1` cards on black additionally carry a 0.5 dp `hairline` border — this is the Whoop/Apple-dark trick that separates card from void on OLED.
- **Pressed states:** overlay `textPrimary @ 6%` (light) / `@ 8%` (dark) via a shared `Indication`, not M3 ripple defaults with accent tint.

---

## 6. Motion

**Durations & curves (named tokens in `PtMotion`):**

| Token | Value | Applies to |
|---|---|---|
| `instant` | 100 ms, `LinearOutSlowIn` | Chip/tab selection fill, pressed overlays |
| `quick` | 200 ms, standard `CubicBezier(0.2, 0, 0, 1)` | Crossfades, color changes, heat-map fills (`animateColorAsState` in BodyMap keeps this) |
| `standard` | 300 ms, decelerate `CubicBezier(0.05, 0.7, 0.1, 1)` | Push navigation, sheet enter |
| `springLayout` | `spring(dampingRatio = 0.9f, stiffness = 400f)` | `animateContentSize`, `animateItem` placement, RestTimerBar expand/collapse |
| `springPop` | `spring(dampingRatio = 0.65f, stiffness = 600f)` | PR banner scale-in (0.92→1), logged-set row insert |

**Where each applies:**
- **Tab switches:** crossfade 200 ms, no slide — tabs are siblings, not a stack.
- **Push (session detail, exercise detail, editor):** incoming slides 24 dp up + fade over `standard`; outgoing fades to 0 in 90 ms. Predictive back stays stock Android.
- **Sheets:** M3 `ModalBottomSheet` with `standard` enter; scrim fade `quick`.
- **Rest ring:** progress interpolates linearly between second ticks (tween 1000 ms, `LinearEasing`) so the sweep is continuous, not stepped; at ≤5 s the ring color animates `accent→warning` over `quick` and the numerals scale-pulse 1.0→1.06 per second with `springPop`. Replaces the current alpha-pulse loop (`Common.kt:399-409`) — opacity pulsing reads as a glitch, scale reads as a heartbeat.
- **PR moment:** `springPop` scale + `prGold` stroke draw-on 450 ms. One moment of theater; nothing else in the app exceeds 300 ms.
- **List changes:** `Modifier.animateItem()` with `springLayout` on History, set log, routine editor reorder.

**Haptics map** (via `LocalHapticFeedback` on Compose 1.8+, `View.performHapticFeedback` fallback):

| Event | HapticFeedbackType (fallback constant) |
|---|---|
| Set logged | `Confirm` (`CONFIRM`) |
| PR detected | `Confirm` ×2, 120 ms apart |
| Stepper increment/decrement | `SegmentFrequentTick` (`KEYBOARD_TAP`) |
| Chip / tab / preset select | `SegmentTick` (`CLOCK_TICK`) |
| Rest final 3 s, each second | `SegmentTick` (`CLOCK_TICK`) |
| Rest complete (app foreground) | `LongPress` |
| Invalid numeric entry / disabled confirm tap | `Reject` (`REJECT`) |
| Destructive action confirmed | `LongPress` |

---

## 7. Component specs

- **Bottom nav bar:** hand-rolled 64 dp row (not M3 `NavigationBar` — its pill indicator is the strongest "stock Material" signal in the app, audit 6.12). `surface1` at 97% opacity over content, 0.5 dp `hairline` top edge, edge-to-edge behind gesture area. 24 dp icon over 11 sp `caption` label; active = `accent` icon+label with a 150 ms crossfade to the filled glyph; inactive = `textTertiary`. No background pill, no ripple bloom — pressed overlay only.
- **Top bars / headers:** large-title pattern. Expanded: 32 sp `largeTitle` left-aligned at the 16 dp margin with `footnote` metadata line under it (date, unit); collapses on scroll to a 52 dp bar with centered 17 sp `headline` and a `hairline` bottom edge that fades in with content scroll. Implement once as `PtLargeTitleScaffold` wrapping `LargeTopAppBar` scroll behavior with custom styling; replaces today's three-line `HomeHeader` (`HomeScreen.kt:237-273` — greeting + app name + "Weights in kg" + a labeled Settings column is four ideas where Apple would show one).
- **Card (`PtCard`):** `surface1`, `rLg`, 16/14 dp padding, no elevation, dark-mode hairline border. Interactive cards get the pressed overlay. Internal vertical rhythm `s2`.
- **Stat tile (`PtStatTile`):** min 76 dp, `rMd`, `surface1`; anatomy top-to-bottom: `label` eyebrow in `textSecondary` ("VOLUME"), `numeralM` value with unit in `subhead` `textSecondary` baseline-aligned ("4 320 **kg**"), optional trend glyph in `accent`/`textTertiary`. Tiles compose into 2-up grids with 12 dp gutters (Summary, ExerciseDetail, Home).
- **Buttons:** Primary = `accent` fill, `onAccent` 17 sp `headline` text, 50 dp, `rMd` (hero 56 dp reserved for Start/Resume/Finish). Secondary = `surface2` fill, `textPrimary` label, 44 dp — **no outlined buttons anywhere**; outlines are wireframe-speak (replaces `SecondaryGymButton`, `OutlinedButton` steppers, `Common.kt:650`). Tertiary = borderless `accent` text button. Destructive = `danger` text or `dangerSubtle` fill.
- **List row (`PtRow`):** 52/64 dp, `headline` title + `subhead` `textSecondary` second line; trailing slot for `numeralS` value or chevron in `textTertiary`; rows group inside one card with inset `hairline` dividers (start-padded 16 dp) — grouped-inset lists replace today's one-card-per-item stacks (`SessionLogRow` becomes a row, not a card).
- **Weight/reps input (steppers):** keep the current interaction model (big focal number, tap-to-type dialog — it is genuinely good, preserve it). Restyle: focal number `numeralXL` Inter Display with `tnum` (drops monospace), `label` eyebrow, unit in `headline` `textSecondary`; stepper buttons become `surface2` fills 64×88 dp with 24 sp `−`/`+` and the step value in `footnote` beneath; long-press auto-repeats at 6 Hz with `SegmentFrequentTick` per step.
- **Rest-timer module:** the full-width `RestTimerBar` card becomes a ring instrument. Idle: 44 dp row — `label` "REST" + `numeralS` preset + preset capsule chips. Running: 96 dp ring (8 dp round-cap stroke, `hairline` track, `accent` sweep draining clockwise) with `numeralM` mm:ss centered; right of ring a vertical stack of −15 s / Skip / +15 s as `surface2` capsules. Finished: ring flashes `accentSubtle` fill once, then collapses via `springLayout`. Same component docks on Home (`RestRemainingStrip` replacement) at 44 dp with a 20 dp mini-ring.
- **Chart style:** keep the hand-drawn Canvas approach (`TrendChart.kt` — right call for an offline app). Restyle: historical bars `chartMuted`, latest bar `chartPrimary`, drop the full-height track rectangles (today every bar sits in a gray pill — that's chart chrome, not data); single `hairline` baseline; start/end labels `footnote` `tnum` in `textTertiary`; bar width ≤ 8 dp with `rFull` caps. Line charts (e1RM trend): 2 dp `chartPrimary` stroke, 3 dp dot on latest point only, no area fill in light / 8% fill in dark.
- **Calendar cells:** 40 dp square, day number in `caption` `tnum`. Trained = `accentSubtle` circle with `onAccentSubtle` number; intensity shown by a 0–3 stack of 3 dp dots under the number in heat-scale stops (color-blind-safe count + color). Today = 1.5 dp `accent` ring; selected = `accent` fill `onAccent`. Off-month `textTertiary`.
- **Sheets & dialogs:** sheet = `surface3`, `rXl` top corners, 36×5 dp `hairline` grabber, 16 dp content margin, title in `title2`; used for pickers, muscle detail, exercise editor (already the pattern — keep). Dialog = only for destructive confirms and number entry: `surface3`, `rXl`, title `title2`, actions right-aligned text buttons (`danger` for destructive). Everything else that is a dialog today should become a sheet.

---

## 8. Screen-by-screen application

**Home** — Large title "Today" with date + unit as the `footnote` metadata line; Settings becomes a bare 24 dp gear in the collapsed bar (kills the labeled icon column, `HomeScreen.kt:259-271`). Order: docked rest mini-ring (when live) → hero Start/Resume button → "This week" card → 2-up stat tiles (7-day volume, sessions) with the six-muscle heat dots row inside one card → grouped Recent list. Greeting line is deleted — the app is an instrument, not a concierge.

**Schedule** — Week as a grouped-inset card: seven 52 dp rows, day letter in `label`, routine name in `headline`, rest days in `textTertiary`; today's row carries a 3 dp `accent` leading bar. Thin-history notice uses `warning` text on `surface2`, not an error banner.

**Routines & editor** — Routine card: name `headline`, exercise-count/muscle summary `subhead`, trailing chevron; grouped list. Editor: exercises as 64 dp `PtRow`s with drag handles in `textTertiary`, set/rep scheme as trailing `numeralS`; reorder animates with `springLayout`; add-exercise is a tertiary accent row pinned last, not a FAB.

**Library** — Search field `surface2` `rMd` 44 dp; muscle filter as capsule chips (selected = `accentSubtle`/`onAccentSubtle`, not filled accent — restraint). Exercises grouped by muscle under `label` eyebrows with inset dividers; equipment metadata in `footnote` `textTertiary`.

**StartWorkout** — Suggested day as one dominant card: `label` eyebrow "TODAY · PUSH", routine name `title1`, lift preview `subhead`, hero primary button inside; alternatives as plain rows below. One decision per screen.

**ActiveWorkout** — The instrument panel. Collapsed top bar only (routine name `headline`, elapsed `numeralS` `tnum` trailing, X leading). Rest ring module directly under the bar. Lift switcher: capsule chips with set-progress "2/4" in `tnum`. Current exercise name in `title1`; steppers per spec; set log as a 44 dp-row table (SET · KG · REPS columns, `label` header row, logged rows `numeralS`, PR rows tinted `prGold`). Log-set bar docks above the keyboard/nav inset at 56 dp full-width `accent`.

**Summary** — Celebration ceiling: `title1` "Workout complete", duration/volume/sets as a 2-up `numeralL`/`numeralM` tile grid, PR list as `prGoldFill` rows with `prGold` trophy glyph and `springPop` entrance. No confetti; gold + haptic is the ceremony.

**History (+calendar +session)** — Large title; calendar card per §7 cells; month volume as `numeralM` in the card header. Session list grouped by month under `label` eyebrows. Session detail mirrors Summary's tile grid, then per-exercise grouped tables identical to ActiveWorkout's set log — same table component, zero relearning.

**ExerciseDetail** — Header: exercise name `title1`, muscle + equipment `footnote`. PR row: three stat tiles (best weight, e1RM, best volume) with `prGold` accents. Trend chart per §7 with 30/90/all capsule switcher. History as grouped rows.

**Progress (+body map)** — Large title "Body". Front/back and 7/14/week switchers as capsule chips in one row. Body map card: silhouette fills from the heat scale, `hairline` outlines (replaces hard-coded `#2C4A3E`/`#C5D5CB`, `BodyMap.kt:90`), legend as footnote dots. Muscle rows: 52 dp with 10 dp heat dot, name `headline`, sets+volume trailing `numeralS`. Recommendations: max two, `surface2` fill with `accent` leading glyph — advisory, not alarming.

**Settings** — Pure iOS-grouped-list treatment: `label` section eyebrows (UNITS, BACKUP, DATA), 52 dp rows, values right-aligned `textSecondary`, chevrons `textTertiary`; Drive backup status as a `footnote` line under its row, destructive "Delete all data" isolated in its own group in `danger`.

---

## 9. Compose implementation strategy

**Token architecture:** a custom design-system object as the single source of truth, with M3 as a compatibility shim:

```kotlin
@Immutable class PtColors(val canvas: Color, val surface1: Color, /* … */)
object PtTheme {
  val colors: PtColors @Composable get() = LocalPtColors.current
  val type: PtType …; val metrics: PtMetrics …; val motion: PtMotion …
}
```

`PersonalTrainerTheme` (in `Theme.kt`) provides `LocalPtColors/PtType/PtMetrics` **and** maps them into `lightColorScheme`/`darkColorScheme` (`primary=accent`, `background=canvas`, `surface=surface1`, `surfaceVariant=surface2`, `outlineVariant=hairline`, `surfaceTint=Color.Transparent`, `error=danger`) plus the M3 `Typography` mapping from §3 — so unmigrated screens and stock components (dialogs, text fields, snackbars) land ~on-language automatically from day one. New code uses `PtTheme.*` only; `GymMetrics` becomes a deprecated alias delegating to `PtMetrics` during migration.

**Files:** `ui/theme/PtColors.kt`, `PtType.kt` (fonts in `res/font/`: Inter + Inter Display variable TTFs, weights via `FontVariation.weight`), `PtShapes.kt`, `PtMotion.kt` (durations, easings, springs, haptic helper), `PtTheme.kt`.

**Dynamic color: off.** Material You wallpaper tinting is the opposite of a precise brand; the audit's bar is Apple/Whoop, both of which are famously *not* user-tintable. Honor system light/dark only (already wired via `isSystemInDarkTheme()`).

**Edge-to-edge:** `enableEdgeToEdge()` in the Activity; `canvas` paints behind status and gesture bars; the custom nav bar and docked log bar consume `WindowInsets.navigationBars`; large-title scaffold consumes status-bar inset. Dark mode + true-black canvas makes the status bar disappear into the hardware — the cheapest "premium" win available.

**Migration order (leverage-sorted, one developer):**
1. **Foundations** — PtTheme + M3 mapping + Inter fonts + kill `surfaceTint` (1–2 days): the entire app retints and re-types at once.
2. **Shared components** (`components/`: PtCard, buttons, steppers, RestTimer ring, section headers, PtRow, tiles) — every screen funnels through these (3 days).
3. **ActiveWorkout + rest ring + set table** — the product's core loop (2 days).
4. **Home** (header rework, tiles, grouped Recent) (1–2 days).
5. **Progress / BodyMap** heat-scale + silhouette tokens (1 day).
6. **Summary, History + calendar, ExerciseDetail** charts/tiles (2 days).
7. **Schedule, Routines, Library, StartWorkout, Settings** grouped-list pass (2 days).
8. **Motion + haptics + edge-to-edge + custom nav bar** (2 days).

≈ 14–15 focused dev-days; the app is shippable after every phase because the M3 shim keeps unmigrated screens coherent.

---

## 10. Risks / trade-offs of this direction

- **Identity flattening.** Moving from warm Forest/Sand to neutral gray + green risks "generic fintech dashboard." Mitigation is discipline elsewhere — the Inter Display numerals, gold PR family, and heat scale must carry personality; if those are half-executed the result is *more* generic than today.
- **True black is tuned for the wrong room.** Whoop-dark shines at night; gyms are bright. Light mode must be first-class, not the afterthought it is in most "premium dark" fitness apps — hence equal token tuning above. If the owner mostly trains in dark-mode, fine; but ship both at parity.
- **Fighting M3 costs real hours.** The custom nav bar, de-pilled chips, gray secondary buttons, and large-title scaffold are hand-rolled; every stock component that sneaks in (a default `FilterChip`, an unstyled `TopAppBar`) will visibly break the language. Budget the §9 component phase honestly or don't start.
- **Apple idioms on Android can misfire.** Centered collapsed titles and gray fills diverge from Android muscle memory; keep system behaviors (predictive back, ripple timing, insets) stock so the app feels Apple-*grade*, not iOS-cosplay.
- **Restraint is organizationally fragile.** The single-accent rule dies with the first "add some color here." Enforce mechanically: no `Color(0x…)` literals outside `ui/theme/` (a grep/CI rule — today's inline hexes in `BodyMap.kt` are the exact failure mode), and every new gap/radius must be a token.
- **Inter is everywhere.** Thousands of apps use it; the font alone buys nothing. The Apple feel comes from the tracking table, the numeral roles, and `tnum` discipline — cutting those corners yields "default web app," which is a step *down* from the current app's honest, if generic, Material look.