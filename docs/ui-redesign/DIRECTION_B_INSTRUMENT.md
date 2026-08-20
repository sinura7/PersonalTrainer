# Instrument

A dark-only, OLED-black data-instrument design language — one volt accent on layered near-black, huge tabular numerals, hairline-and-glow depth, and physical motion — that turns PersonalTrainer into a piece of gym equipment in the Whoop/Oura lineage.

# Design Direction B — "Instrument"

Dark-only, data-forward, precision-hardware. The phone is a piece of gym equipment: a rack-mounted readout, not a wellness journal. Everything below maps onto the existing screens under `app/src/main/java/com/sinura/personaltrainer/ui/` and is buildable by one developer in Jetpack Compose, partially on Material 3.

---

## 1. Philosophy

- **The number is the interface.** Weight, reps, clock, volume — set in huge tabular numerals; every label is a small caps kicker serving the numeral. If a screen's biggest element is a sentence, the screen is wrong.
- **One accent, earned.** A single volt green means "live / act / now". Everything else is a luminance step of near-black. Gold, amber, and red are semantic verbs (PR, urgency, destroy), never decoration.
- **Depth is light, not shadow.** On OLED black, elevation shadows are invisible. Hierarchy comes from surface luminance steps and 1px hairlines; glow is reserved for exactly two live moments (rest ring running, PR).
- **Motion is mechanical.** Short, decisive, spring-settled, always paired with a haptic when a rep of data is committed. Nothing floats, nothing bounces twice, nothing animates for fun.
- **Preserve what already behaves like equipment.** The two-stepper log architecture, the non-modal PR banner, the three-state rest card, and the existing `tnum` numeric style (`ui/components/Common.kt:68–72`) are correct instincts — this direction re-skins and amplifies them, it does not redesign the workflow.

---

## 2. Color tokens

### Light-theme stance: **dark-only, and on purpose.**

Ship one theme. Rationale: (a) strength training is an indoor, mixed-to-dim-light activity; the "bright sunlight" case that justifies light themes barely exists for a rack-side app, and a 17:1 white-on-black pair at full brightness survives harsh gym LED glare better than most light themes survive it. (b) The rest timer keeps the screen awake for minutes at a time (`ActiveWorkoutScreen.kt:117` `view.keepScreenOn = true`) — near-black is an OLED battery and burn-in win. (c) A solo developer maintaining two hand-tuned themes ships neither well; Whoop, and this direction, spend that budget making one theme exact. Mechanically: delete `LightColors` and the `isSystemInDarkTheme()` branch in `ui/theme/Theme.kt:10–29,54`; set `windowBackground` to `bg/pit` in `themes.xml` to kill the white launch flash. Mitigation for the real cost (astigmatic halation, users who need light UI): text is `#F2F5F7`, never `#FFFFFF`, on `#07090B`, never `#000000` — this measurably reduces halo without sacrificing the instrument look. The trade is logged in §10.

### Background & surface ladder (the "depth is light" system)

| Token | Hex | Use |
|---|---|---|
| `bg/pit` | `#07090B` | Window background, bottom nav, headers. Edge-to-edge. |
| `surface/1` | `#0E1215` | Grouped list containers, inset panels, chart plot area |
| `surface/2` | `#14191D` | Cards, tiles — the default component surface |
| `surface/3` | `#1B2126` | Sheets, dialogs, menus (topmost layer) |
| `surface/pressed` | `#232A30` | Pressed/selected fill |
| `hairline` | `#FFFFFF` @ 8% (`#14FFFFFF`) | Card borders, dividers, ring tracks |
| `hairline/strong` | `#FFFFFF` @ 14% (`#24FFFFFF`) | Focus, "today" ring, drag handles |

Rationale: each step is ~+3% luminance — visible as a layer, invisible as a color. Replaces the current single `CardDark`/`surfaceVariant` pair (`ui/theme/Theme.kt:40–42`) and all `tonalElevation` (e.g. the `LogBar` `Surface(tonalElevation = 4.dp)` at `ActiveWorkoutScreen.kt:503`), which produce M3's telltale grey-green wash.

### Text

| Token | Hex | Contrast on `surface/2` |
|---|---|---|
| `text/primary` | `#F2F5F7` | ≈15.5:1 (AAA) |
| `text/secondary` | `#9BA7AE` | ≈7.2:1 (AAA) |
| `text/tertiary` | `#5F6B73` | ≈3.2:1 — decorative/disabled ONLY, never load-bearing text |

### Signature accent + semantics

| Token | Hex | Contrast | Meaning |
|---|---|---|---|
| `accent/volt` | `#C2FF44` | ≈16:1 on `bg/pit` | THE accent: live timer, primary CTA, active nav, "now" datapoint. Ink on volt buttons is `bg/pit` (same ≈16:1). |
| `accent/volt-dim` | `#C2FF44` @ 14% | — | Selected chip fill, active track tint |
| `signal/pr` | `#FFC53D` | ≈12:1 on `bg/pit` | PR gold. Records, trophies, the PR moment. Never used for anything else. |
| `signal/warn` | `#FFB020` | ≈10.5:1 | Urgency: rest ≤10s, missed-target hints, backup stale |
| `signal/danger` | `#FF6B6B` | ≈6.5:1 | Destructive only: delete set/routine, discard workout |
| `signal/rest` | `#33D6E8` | ≈11:1 | Cool cyan: recovery/rest-day identity, second chart-gradient stop |

Rationale: the current palette spends green three ways (brand, primary, heat-low) so nothing means anything. Here volt = action is a strict grammar; gold is emotionally distinct from the accent so a PR feels like an event, not a highlight. Volt also keeps continuity with the existing Lime brand DNA (`ui/theme/Color.kt:7`) rather than a cold rebrand.

### Muscle-heat scale — colorblind-safe

Replaces `heatFill()`'s green→gold→red ramp (`ui/progress/BodyMap.kt:232–243`), which collapses for deuteranopes exactly at its extremes. New scale is magma-derived: **luminance rises monotonically (~4% → ~45%) so intensity is carried by lightness; hue (cool violet → hot orange) is redundant encoding.** Safe for deutan/protan/tritan by construction, and it literally reads as heat.

| Token | Hex | Meaning |
|---|---|---|
| `heat/0` | `#262C31` | No data — neutral, deliberately off-ramp |
| `heat/1` | `#4A2480` | Light |
| `heat/2` | `#9D2F86` | Moderate |
| `heat/3` | `#E25A50` | High |
| `heat/4` | `#FCA05F` | Very high |

Interpolate between stops in OKLab (small hand-rolled lerp; no library). Always pair fills with the numeral (sets/volume) — color is never the only channel.

### Chart gradient

`chart/start` `#33D6E8` (past) → `chart/end` `#C2FF44` (now), horizontal, so the most recent data glows in the accent. Area fill: vertical `#C2FF44` 14% → 0%. PR reference line: `signal/pr` dashed 1dp.

---

## 3. Typography

Two faces, both OFL / Google Fonts, bundled as variable TTFs in `res/font` (subset to Latin; ~600KB total):

- **Space Grotesk** (500, 700) — display + all numerals. Geometric grotesque with Space Mono DNA: techy, characterful, near-uniform digits. Ships `tnum`.
- **Inter** (variable) — all UI text. Guaranteed `tnum`, `zero`; superb at 11–16sp on Android.

**Tabular figures are law:** every style that can contain a digit sets `fontFeatureSettings = "tnum"` (Compose passes this CSS-syntax string to HarfBuzz). Timers additionally use fixed-width layout so `1:59 → 2:00` cannot shift. Add `"zero"` (slashed zero) to `numeral/hero` and `numeral/xl` for instrument character. Build-time guard: a debug assertion renders `"11111"` vs `"00000"` and asserts equal measured width for each numeral style; if Space Grotesk's tnum ever regresses, numerals fall back to Inter. This upgrades — and keeps the spirit of — the existing `GymNumericStyle` monospace hack (`Common.kt:68–72`).

| Token | Face | Size/Line | Weight | Tracking | Use |
|---|---|---|---|---|---|
| `numeral/hero` | Space Grotesk | 76/80 | 500 | −1% | Rest clock, weight & reps entry |
| `numeral/xl` | Space Grotesk | 56/60 | 500 | −1% | Summary volume, exercise-detail e1RM |
| `numeral/lg` | Space Grotesk | 36/40 | 500 | −0.5% | Stat tiles |
| `numeral/md` | Space Grotesk | 24/28 | 500 | 0 | Set rows, "Set 3 of 4" |
| `numeral/sm` | Space Grotesk | 17/20 | 500 | 0 | Inline metrics, list trailing values |
| `display` | Space Grotesk | 28/32 | 700 | −0.5% | Screen titles |
| `title` | Inter | 16/20 | 600 | 0 | Card titles, exercise names |
| `body` | Inter | 14/20 | 400 | 0 | Prose, helper text |
| `body/strong` | Inter | 14/20 | 500 | 0 | Emphasis inside prose |
| `caption` | Inter | 12/16 | 400 | 0 | Meta lines, axis labels (`text/secondary`) |
| `kicker` | Inter | 11/13 | 600 | +8%, ALL CAPS | Section labels: `REST`, `LAST 7 DAYS`, `VOLUME` — the instrument label voice |
| `unit` | Inter | 13/16 | 500 | 0 | `kg` / `lbs` / `min` suffixes, `text/secondary`, never scaled with the numeral |

Rule: units are typographically subordinate — `142.5` in `numeral/hero` + `kg` in `unit`, baseline-aligned. Replaces the current pattern of unit text in `titleMedium` at near-numeral size (`Common.kt:188–191`). M3 mapping: keep `Typography` populated (all 15 roles this time) so stock components inherit Inter, but screens consume the named tokens above.

---

## 4. Spacing & layout grid

4dp base. Tokens: `space/1..10` = 4, 8, 12, 16, 20, 24, 32, 40.

- **Gutter 16dp** (down from `GymMetrics.screenPadding = 20.dp`, `ui/components/GymSurfaces.kt:25`) — instrument density; the saved 8dp goes to the numerals.
- **Card inner padding 16dp**; intra-card vertical rhythm 8dp.
- **List gap 8dp** between cards (down from 12); **section gap 28dp**, where a section = `kicker` + 8dp + content — the kicker owns the whitespace above it (20dp of the 28).
- **Grids:** stat tiles 2-up, 8dp gap; muscle highlights 6-up; calendar 7-col, 4dp gap.
- **Touch:** 48dp minimum everywhere; gym-glove tier 56dp (buttons, chips row); commit tier 72dp (Log set — keep the existing `height = 72.dp`, `ActiveWorkoutScreen.kt:522`; steppers 96×72).
- **Edge-to-edge:** content draws under status/nav bars; `bg/pit` bleeds to the physical edge; LazyColumn `contentPadding` handles insets.

---

## 5. Shape & depth

**Radius scale:** `r/xs` 8 (chips, calendar cells, tags) · `r/sm` 12 (inputs, list groups) · `r/md` 16 (cards, buttons) · `r/lg` 24 (sheets, hero panels) · `r/full` (pills, ring). One family, plain `RoundedCornerShape` — retires the current 16/20dp mix (`GymSurfaces.kt:29` vs `ActiveWorkoutScreen.kt:354`).

**Depth strategy on near-black — three tools, strict order:**
1. **Surface step** (§2 ladder) — primary. A layer above is one step lighter. `tonalElevation` and `shadowElevation` are banned app-wide (shadows are invisible on `#07090B` and M3 tonal tint fights the ladder).
2. **Hairline** — every `surface/2`+ container gets a 1dp `hairline` border. This is what makes cards read as machined panels instead of grey blobs.
3. **Glow** — exactly two, ever: the live rest ring and the PR moment. Implemented as a pre-baked radial-gradient Box (`accent @ 18% → transparent`, ~96dp radius) behind the element — never `RenderEffect` blur (low-end GPU cost). If a third glow appears in a PR review, one of the three is wrong.

Scrims: 60% black. Dialog/sheet = `surface/3` + hairline, no shadow.

---

## 6. Motion

**Durations:** `tap` 90ms · `fast` 150ms · `base` 240ms · `slow` 350ms · `draw` 650ms.
**Easings:** `standard` cubic-bezier(0.2, 0, 0, 1) · `exit` (0.3, 0, 1, 1) · `emphasized` (0.05, 0.7, 0.1, 1).
**Springs:** `press` spring(dampingRatio 0.85, stiffness 900) — scale to 0.97 · `settle` spring(0.8, 380) · `celebrate` spring(0.55, 600).

Named moments:

| Moment | Spec |
|---|---|
| **Nav (tab/push)** | Fade-through: outgoing fade 90ms `exit`; incoming fade+scale 98→100% over 210ms `standard`. No horizontal slides — instruments switch readouts, they don't scroll rooms. |
| **Sheet rise** | Translate-in 280ms `emphasized`; scrim fade 240ms. Dismiss 200ms `exit`. |
| **Ring countdown** | Sweep animates linearly 1000ms per tick (continuous, never stepped). ≤10s: track+sweep recolor to `signal/warn` over 300ms, ring scale pulses 1.00→1.015 at 1Hz sine. ≤5s: `EFFECT_TICK` haptic each second, synced to the audible tick (audit R-04). |
| **PR moment** | Gold hairline flash sweeps the banner 120ms in / 900ms decay; numeral pops with `celebrate` spring; glow fades in 150ms, out 1200ms. Non-modal, exactly as today (`ActiveWorkoutScreen.kt:632` — keep). |
| **Chart draw-in** | 650ms `emphasized` left→right clip reveal of the gradient stroke; area fill fades in the last 200ms; last-point dot pops with `celebrate`. Runs once per data change, skipped when animator scale = 0. |
| **Log set commit** | Button scale 0.97→1 `press`; new set row slides in 240ms with 4dp rise; rest ring auto-starts 150ms later — a visible cause→effect chain. |
| **Calendar fill** | Cells stagger in, 20ms apart, fade+scale 92→100%. |

**Haptics map** (Android `HapticFeedbackConstants` / `VibrationEffect`): stepper tap & RPE chip → `EFFECT_TICK` · log/save set → `EFFECT_HEAVY_CLICK` · rest done → waveform double-pulse (60ms, 80ms gap, 120ms) · PR → triple pulse asc. amplitude · destructive confirm → `EFFECT_DOUBLE_CLICK` · timer last-5s → `EFFECT_TICK`/s. All decorative motion respects the system animator-scale setting; haptics respect the system haptic toggle.

---

## 7. Component specs

- **Bottom nav** — `bg/pit` (not a lighter bar), top hairline, 64dp + insets. Icon 24dp + `kicker` 10sp label. Active: volt icon + volt label; inactive: `text/tertiary`. No M3 pill indicator — a 3dp volt tick above the active icon. Kills the default `NavigationBar` tonal wash (`ui/navigation/AppNav.kt:133`).
- **Header** — screen title in `display` on `bg/pit`, left-aligned, no TopAppBar container color; on scroll, title collapses to `title` and a hairline fades in. Utility actions are bare 24dp icons — retires the icon+"Settings" caption column on Home (`HomeScreen.kt:259–271`).
- **Card** — `surface/2`, `r/md`, hairline, 16dp padding, no elevation. Interactive: `surface/pressed` + `press` scale. One `InstrumentCard` replaces `GymCard` (`GymSurfaces.kt:61`).
- **Stat tile** — `kicker` label, `numeral/lg` + `unit` baseline-aligned, optional delta (`▲ 4%` in volt / `▼` in `text/secondary` — never red for a down week). 2-up grid. This becomes the atom of Home, Summary, and ExerciseDetail.
- **Buttons** — Primary: volt fill, `bg/pit` text, `r/md`, 56dp (72dp commit tier). Secondary: `surface/2` + hairline, `text/primary`. Tertiary: bare volt text. Destructive: bare `signal/danger` text; danger fill only inside confirm dialogs.
- **List row** — grouped list = one `surface/1` container at `r/sm` with internal hairline dividers; rows 56dp min, `title` + `caption` left, `numeral/sm` value right. Replaces card-per-row spam (History, sets, muscles).
- **Weight/rep input** — keep the stepper+type architecture (`Common.kt:148–265` — a genuine strength). Restyle: center numeral in `numeral/hero` + `unit`; steppers become `surface/2` hairline plates, 96×72, `−2.5` label in `numeral/md`; long-press auto-repeats at 12/s with `EFFECT_TICK` per step; active edit target gets a volt hairline.
- **Rest-timer ring** — the signature component, replacing the linear bar (`Common.kt:440–446`). 240dp ring, 10dp round-cap stroke; track `hairline`; sweep = chart gradient; center: `REST` kicker + clock in `numeral/hero` + preset label in `caption`; volt glow while running; urgency per §6. Idle: 96dp compact ring + presets row. Finished: gold fill flash + "BACK TO THE BAR" kicker. When scrolled off, a 56dp sticky bar with mini-ring pins below the header.
- **Chart** — 2dp gradient stroke, 14%→0% area, `surface/1` plot, no gridlines except a hairline baseline and the dashed gold PR line; `caption` axis labels, `tnum`. Tap = volt scrubber dot + value flag in `numeral/sm`. `TrendBars` (`ui/components/TrendChart.kt:32`) keeps its honest self-max scaling but bars get gradient fill and `r/full` caps.
- **Calendar cells** — 40dp, `r/xs`. Rest day: transparent, `text/tertiary` date. Trained: `surface/2` + heat-scale dot (volume band) + date in `text/primary`. Today: `hairline/strong` ring. Selected: volt ring. Never a filled volt square — the accent is a state, not wallpaper.
- **Sheets & dialogs** — `surface/3`, `r/lg` top corners, 32×4dp `hairline/strong` drag handle, 16dp gutter. Dialog actions: full-width stacked buttons (primary on top), not M3 text-button pairs — glove-friendly and unambiguous for the discard/delete flows (`ActiveWorkoutScreen.kt:438–465`).

---

## 8. Screen-by-screen application

**Home** — Header per §7; greeting demoted to `caption`. Above the fold: Start/Resume as the only volt element, then a 2-up stat-tile row (this week's sessions · volume). Training-balance card: six heat dots become 6-up mini-tiles with `heat/*` fills + `numeral/sm` set counts. Recent sessions: one grouped list, not a card stack; "Weights in kg" moves to Settings.

**Schedule** — Week as seven horizontal rows: `kicker` day, `title` routine, trailing heat dot for logged volume; today gets a volt left rail (3dp). "This week" card on Home shrinks to a single-row strip: `TODAY · PUSH DAY` kicker + Start tertiary button.

**Routines + editor** — Routine card: `title` name, `caption` lift-count/est-duration, right-aligned `numeral/sm` per-week frequency. Editor: lifts as a grouped list with drag handles; sets×reps rendered as `numeral/md` `4 × 8`, targets edited via the stepper dialog — never a free `OutlinedTextField`. Save is the screen's only volt element.

**Library** — Search field as a `surface/1` hairline bar. Muscle filter chips: `r/xs`, `accent/volt-dim` fill + volt hairline when selected. Rows in the grouped list with the 40dp leading slot reserved for exercise images (audit R-01/R-02 land here without relayout).

**StartWorkout** — Two panels: "From routine" grouped list, and a `Free workout` secondary button. The routine the schedule expects today is pre-highlighted with a volt hairline + `TODAY` kicker; starting it is one tap.

**ActiveWorkout** — The flagship. Order: rest ring (sticky-capable) → lift switcher (chips restyled: `surface/2` hairline, volt-dim selected, `2/4` count in `numeral/sm`) → current lift: name in `title`, then `SET 3 OF 4` as `kicker`+`numeral/md` (inverting today's hierarchy at `ActiveWorkoutScreen.kt:570–589` where the lift name outweighs the set count) → last-time strip as a horizontal row of `numeral/sm` pills → steppers → set list as grouped rows with warmup tagged by a cyan tick, latest by a volt left rail. PR banner: `surface/2` + gold hairline + glow, gold trophy tick, per §6. Log bar: `bg/pit` + top hairline, 72dp volt button.

**Summary** — The receipt. Hero: total volume in `numeral/xl` + gold `NEW PR ×2` chip when earned. 2-up tiles: sets · duration · top set · e1RM. Per-lift breakdown as grouped rows with `numeral/sm` volumes. Done = full-width volt button; the moment gets the chart draw-in treatment on the session's volume bars.

**History + calendar + session** — Calendar card per §7 cells; month header `kicker`; below, sessions as the grouped list (title / date `caption` / trailing volume `numeral/sm`). Session detail mirrors Summary's layout exactly (same tiles, same rows) so "just finished" and "last March" are the same instrument readout.

**ExerciseDetail** — Hero: current e1RM in `numeral/xl` + `unit`, delta chip vs 30 days. PR row: three stat tiles (weight · reps · e1RM) with gold `kicker` labels. Trend chart per §7 with dashed gold PR line. History as grouped rows.

**Progress + body map** — Window picker: segmented `surface/1` control (7 / 14 / WEEK), volt-dim selected — not three `FilterChip`s (`ProgressScreen.kt:199–214`). Body map on `surface/1` panel; muscle fills from the new `heat/*` OKLab ramp; selected muscle gets a volt hairline outline; legend swatches pair color + `kicker` label + set-count range so color is never the sole channel. Muscle list: grouped rows, heat dot + `numeral/sm` volume. Recommendation cards: rank as `numeral/lg` watermark, one-line `title`, tertiary action.

**Settings** — Grouped lists per §7, section `kicker`s. Google Drive backup state as an instrument row: `LAST BACKUP` kicker + relative time in `numeral/sm`; `signal/warn` tint when stale. Danger zone rows in `signal/danger` at the very bottom.

---

## 9. Compose implementation strategy

**Token architecture.** One file set under `ui/theme/`: `InstrumentColors`, `InstrumentType`, `InstrumentMetrics`, `InstrumentMotion` — plain immutable objects exposed via `staticCompositionLocalOf`, read as `Instrument.colors.voltAccent` etc. Keep `MaterialTheme` wrapping with a single `darkColorScheme` mapped from tokens (`primary = volt`, `background = pit`, `surface = surface2`, `surfaceVariant = surface1`, `outline = hairline`, `error = danger`) so stock M3 components (dialogs, text fields, snackbars) inherit sane colors before they're individually migrated. Delete `LightColors` and the `darkTheme` parameter (`Theme.kt:10–29, 53–58`). Alias the old names — `GymNumericStyle`, `GymMetrics` — to new tokens for one release to avoid a 40-file churn commit (grep shows both used across every screen).

**Fonts.** `res/font/space_grotesk_var.ttf`, `res/font/inter_var.ttf`; styles built once in `Type.kt` with per-style `fontFeatureSettings = "tnum"` (+ `"zero"` on hero/xl); debug-only equal-width assertion per §3.

**Edge-to-edge.** `enableEdgeToEdge()` in the Activity; `windowBackground = @color/pit` in `themes.xml`; transparent system bars; insets consumed at the Scaffold level per screen.

**Migration order (each phase shippable):**
1. **Foundations (days):** theme/type/metrics tokens, dark-only, edge-to-edge, restyle `PrimaryGymButton`/`SecondaryGymButton`/`GymCard`/`GymSectionHeader` (→ kicker), bottom nav, headers. The whole app shifts ~70% of the way on this phase alone because every screen already routes through these shared components — the existing componentization is the redesign's biggest asset.
2. **Instruments (1–2 weeks):** rest-timer ring + sticky variant, stepper restyle + auto-repeat + haptics, ActiveWorkout hierarchy, PR moment, Summary tiles, chart gradient + draw-in.
3. **Data surfaces (1–2 weeks):** grouped-list component and its rollout (History, sets, muscles, Settings), heat-scale swap in `heatFill` + legend, calendar cells, Progress segmented control, Home tiles, remaining screens.

**Guardrails.** A `debugImplementation` lint (or Konsist test) forbidding `Color(0x…)` literals outside `ui/theme/`, `tonalElevation`, `shadowElevation`, and raw `RoundedCornerShape(…dp)` outside the shape tokens — the mechanism that keeps the language from re-fragmenting.

---

## 10. Risks / trade-offs of this direction

1. **Dark-only excludes real users.** Astigmatic halation and bright-environment reading genuinely favor light UIs for a minority; forcing dark also ignores the OS-level preference. Mitigated (§2: `#F2F5F7` on `#07090B`, AAA everywhere) but not eliminated. If it proves wrong, the token architecture admits a light ladder later — but budget it as a v2 project, not a toggle.
2. **Volt green is one notch from "gamer RGB".** The entire direction hinges on the discipline in §2: volt appears once or twice per screen. The moment volt becomes a decoration (headers, icons, dividers), the app reads as a cheap fitness skin instead of an instrument. Enforce via the accent-count rule in design review.
3. **Identity break.** The warm Sand/Forest daylight look (`Color.kt:5–11`) disappears; existing users experience a rebrand. Volt keeps the green DNA, but this is a one-way door for the "organic" brand direction — the owner's Whoop brief says take it; say so explicitly in release notes.
4. **Glow and gradient are performance and taste hazards.** Radial-gradient fakes (not blur) keep low-end devices safe, but overdraw on the always-on workout screen needs profiling; and gradients on every chart can tip into dashboard kitsch — the two-stop, one-direction rule is the fence.
5. **Space Grotesk `tnum` is asserted, not guaranteed forever.** The debug width-assertion and Inter fallback (§3) make this a contained risk, but it must actually be wired, or a font update silently breaks every timer alignment.
6. **Stock M3 components will fight the look.** `FilterChip`, `AlertDialog`, `OutlinedTextField` carry M3 shapes/tints; half-migrated screens will look worse than today's consistent-if-generic baseline mid-flight. The phasing (§9) keeps each release coherent by migrating shared components first, but partial phases must not ship mid-phase.
7. **Density cuts against gym gloves.** Tighter gutters and grouped 56dp rows increase information density while the audit demands miss-proof targets; the commit-tier sizes (72dp log, 96×72 steppers) are non-negotiable floors, and any density change to the ActiveWorkout screen must re-verify one-handed reach.
8. **The heat-ramp swap changes learned meaning.** Users who internalized green-good/red-hot must relearn violet→orange; the legend with numeral ranges (§8 Progress) is required, not optional, in the same release as the swap.
