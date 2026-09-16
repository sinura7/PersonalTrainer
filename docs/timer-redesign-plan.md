# Timer redesign plan — a standalone instrument, compact at rest

*Read-only review + plan. No code changed. Audited against `origin/trunk` at Live 76
(`7a5563a4`, "compact rest, hold, and set bar"). All heights below are derived from the
layout code (`heightIn` minimums + spacing tokens), not measured on-device — the numbers
are floors; text size 2.0 grows them, and the estimates say by how much.*

*Screenshot note: the six Live 76 screenshots named in the assignment did not exist on
this worker's machine, so this audit works from the assignment's written descriptions
plus the shipped Live 76 code. Nothing below depends on a screenshot alone — every claim
cites a file and symbol.*

## Verdict

The owner is right. Live 76 made the **running** timer genuinely good — one 56 dp
instrument bar shared by REST, HOLD, and SET — but left the **idle** timer as a small
settings panel that is *taller than the live clock*: 112 dp minimum whenever "Time set"
is offered (which is nearly always on strength lifts), versus 56 dp while running. The
nothing-active state is the tallest dock state, and it looks like every other entry row
instead of like an instrument. The fix is one idea carried through every state: **the
timer is always the same instrument bar; idle is that instrument at rest, not a panel of
options.** Duration editing (presets, custom, fine ±15) moves one tap away into a sheet,
and "Time set" shrinks from a full-width row into a single stopwatch button inside the
idle bar. Idle drops from 112 dp to 56 dp, matches the running geometry pixel for pixel,
and finally looks like its own tool.

## What is good today (keep it)

1. **One 56 dp instrument bar for all live clocks.** `FloorInstrumentBar` in
   `app/src/main/java/com/sinura/personaltrainer/ui/components/RestTimerUi.kt` gives
   REST, HOLD, and SET the same geometry: countdown fill (`RestCyanDim`), kicker
   (`REST` / `HOLD` / `SET`), tabular clock (`InstrumentType.numeralMd`, 24 sp), mode
   controls. Reserved height is `Metrics.logTimerRow` (56 dp). This is the design to
   extend, not replace.
2. **Running rest reads at a glance.** `REST 2:30` + `−15` / `+15` / `Skip` on one row,
   cyan while running, `Warn` for the last ten seconds with the words "Last ten seconds"
   (never colour alone — `ADR-023`), gold `Back to the bar · 0:00` flash for 3.5 s
   (`Motion.FINISHED_DWELL_MS`) when done. Timer ID–keyed flash (`RestFinishFlash`) so
   Skip never fakes a finish.
3. **One clock, enforced twice.** `FloorTimedMode` (`NONE | REST_IDLE | REST_RUNNING |
   REST_COMPLETE | HOLD_RUNNING | STOPWATCH_RUNNING`) plus the `timedGeneration` counter
   in `ActiveWorkoutViewModel` (every start path calls `bumpTimedGeneration()`, which
   cancels hold, stopwatch, and pending-rest jobs). Priority: hold beats stopwatch beats
   rest (`FloorTimedModeResolver.resolve`). Starting SET or HOLD cancels a hidden rest
   alarm so nothing rings mid-set. Covered by `FloorTimedModeTest`,
   `FloorTimerSurfaceTest`, `FloorCompactRestBarTest`.
4. **Rest starts itself correctly after a log.** `RestTimer.shouldStartAfterLog`
   (no clock after warm-ups, none after the last prescribed set) plus
   `shouldStartAfterExtra` (past-prescription sets still rest), fired after the 180 ms
   row-settle (`Motion.ROW_SETTLE_MS`) via `scheduleRestAfterReceipt`. Success haptic
   stays after durable storage.
5. **Honesty without height.** Battery, notification-recovery, persistence, and
   exact-alarm copy live in the 56 dp context rail (`RestHonestyRow`, picked by
   `RestHonestyCopy.pick`), never inside the timer row. `Log` (72 dp filled Volt, the
   one loud control per `ADR-005`) never moves.
6. **Accessibility foundations are right.** TalkBack order ends
   … → recent sets → timer → context rail → Log; the running clock is *not* a live
   region (no per-second chatter), only the finished flash announces politely
   (`TalkBackPolicy.announceRestKicker`); presets use selectable chips with role +
   selected state; reduced motion kills the 1.5% pulse and sweep animation but keeps
   dwell times (`ADR-023`, `Motion`). Notifications: HIGH silent lock-screen chronometer
   while running (frozen at zero per A-03, never `−0:01`), DND-bypassing done channel,
   full-screen `RestLockActivity` for rest-done only — none of which is an overlay on the
   log (`ADR-012`).
7. **The Rest page stays the big clock.** `RestTimerScreen` keeps the 280 dp sweep ring
   (`RestSweepRing`, `min(280, height − 120)` in landscape), presets + filled `Start
   rest` when idle, `−15s / Skip / +15s` when running. The dock never competes with it.

## What is wrong (idle state)

**The core defect: idle is taller than running, and looks like nothing in particular.**

| Dock timer state (Live 76, 360×800, font 1.0) | Min height | Composition |
|---|---|---|
| `NONE` (empty session) | 0 dp | hidden entirely (`emptySessionHidesTimerDock`) |
| `REST_RUNNING` | **56 dp** | `FloorInstrumentBar`: kicker + `numeralMd` + 3 controls |
| `REST_COMPLETE` flash (3.5 s) | **56 dp** | same bar, gold, no controls |
| `HOLD_RUNNING` | **56 dp** | same bar, kicker + clock, no controls |
| `STOPWATCH_RUNNING` | **56 dp** | same bar + `Stop` |
| `REST_IDLE`, hold lift, presets closed | **56 dp** | `RestIdleRow` bar only |
| `REST_IDLE`, strength lift, presets closed | **112 dp** | 56 bar + 8 gap + 48 `Time set` button |
| `REST_IDLE`, presets open (`picking`) | **112 dp** | 56 bar + 8 gap + 48 preset chips (`Time set` hides) |
| Landscape idle | 0 dp | hidden (`LandscapeChrome.hideIdleRest`) |

At font scale 2.0 the `heightIn(min = …)` rows grow instead of clipping (correct per
standing law): running ≈ 96–112 dp; idle closed + Time set ≈ 150–180 dp; presets open ≈
150–185 dp. On an 800 dp viewport the idle dock (timer + 56 rail + 72 Log) eats ~240 dp
minimum at font 1.0 versus ~184 dp running — the nothing-active state steals the most
room from the scrolling middle (steppers, RPE, recent sets).

Why idle is tall, precisely (`RestIdleRow`, `RestTimerUi.kt`):

1. **`Time set` is a full-width 48 dp row under the bar**, visible whenever
   `offerSetClock` is true — i.e. has-lifts + non-hold lift + rest idle/complete, which
   is nearly always on deadlift/squat/press days (`FloorTimedModeResolver.offerSetClock`
   + `ActiveWorkoutViewModel.uiState.offerSetClock`). A whole row for one quiet verb.
2. **Presets expand inline.** Tapping the duration toggles `picking`, which inserts a
   48 dp chip row (`RestPresetChips`: 0:30 / 1:00 / 1:30 / 2:00 / 3:00 + Custom) that
   pushes everything above it with no transition. Worse, `picking` is `rememberSaveable`
   and only closes on select or re-tap — once opened it survives rotation and feels
   stuck-on, which matches the "presets always visible" complaint.
3. **The idle bar carries three controls** (`−15` / `+15` / `Start`) plus glyph plus
   label in one 56 dp row — the same control boxes as the running bar, so at font 2.0 it
   grows as fast as the live clock while saying less.

Why idle feels un-designed:

4. **Visual sameness with entry UI.** Idle is a `Surface2` rounded rect with a
   `bodyStrong` secondary-colour label (`Rest 2:30`, deliberately *not* `numeralMd` per
   `RestIdlePresentationTest`), no accent, no fill, no numeral. Running is kicker +
   cyan + tabular numeral + countdown fill. They share control boxes but no instrument
   identity — idle reads as "another settings row," not "the timer, waiting."
5. **Inverted hierarchy.** The live instrument every eye should find between sets is the
   *shortest* thing in the dock; the dormant planner is the tallest. Attention follows
   height, so attention lands on the wrong row.
6. **`Time set` is undiscoverable *and* bulky.** A grey full-width text button that
   vanishes while presets are open (`offerSetClock && !picking`) and never appears on
   hold lifts teaches nobody what it is, yet costs 56 dp wherever it shows.

Smaller findings (fix while here, not the headline):

7. Hold has no in-dock stop: `onStop` is only composed for stopwatch
   (`onStopSetClock.takeIf { stopwatchRunning && !holdActive }`). An over-long hold ends
   by logging, which is correct, but a mis-started hold has no quiet exit on the bar.
8. The idle bar's tap target *is* the duration text ("Tap for rest presets" in the
   spoken copy) with no visible chevron or affordance — a sighted user gets no hint.
9. `RestIdleCopy.KICKER` (`"Not running"`) is composed only on the Rest page ring, never
   in the dock; the dock idle state therefore has no kicker at all, widening the gap
   between the two surfaces.

## Design principles for a timer that is its own instrument

1. **One bar, five moods.** Every dock timer state occupies exactly one
   `FloorInstrumentBar`-geometry row: same 56 dp reserve, same kicker + tabular-clock
   left block, same control boxes right. Idle is the instrument at rest (dim, empty
   fill); running is the instrument live (cyan, draining fill); done is the instrument
   rung (gold). A user who learns one state has learned all five.
2. **The clock numeral is the instrument's face.** `numeralMd` appears in *every* dock
   state including idle — dimmed (`TextSecondary`) and undrained when planned, bright
   (`TextPrimary`) and draining when live. Standing law forbade the live numeral on idle
   to avoid a "running" misread (`RestIdlePresentationTest`,
   `DESIGN_AUDIT.md` G-05); the redesign keeps the honesty (dim + "REST" kicker + empty
   track + `Start` verb) while granting the identity. Planned time deserves the
   instrument face; dimness says "not started."
3. **Planning is one tap away, never inline.** Presets, custom entry, and fine ±15
   leave the dock for a bottom sheet (or the existing Rest page for the full treatment).
   The dock shows the *choice* (`REST 2:30 · Start`); the sheet makes the choice. The
   dock never grows to accommodate planning.
4. **Between sets, the timer is the headline.** When rest is the thing the user is
   doing, nothing in the dock competes: one bar, live fill, three controls. When nothing
   is active, the bar shrinks to its quietest true form (dim numeral + one `Start` +
   one stopwatch mark) and yields the screen to entry.
5. **Every control earns its 48 dp.** No full-width single-verb rows. `Time set` becomes
   a 48 dp stopwatch button *inside* the idle bar, not a 56 dp row beneath it.
6. **Unchanged laws.** One Volt (`Log`), one seconds clock (`FloorTimedMode` mutex),
   Log never moves (reserved 56 + 56 + 72 dock), colour never the only channel
   (`ADR-023`), reduced motion snaps (`ADR-005` §5), no overlay clock on the log
   (`ADR-012`), no light theme, no second clock.

## Spatial blueprint per state (360×800)

All states: one row, `heightIn(min = 56)`, `Surface2`, 8 dp radius, horizontal padding
8/4, controls `widthIn(min = 48)` / `heightIn(min = 48)`. Left block (weight 1f):
kicker (11 sp tracked) + `numeralMd` clock (24/28). Right block: mode controls.

### 1. Idle — planned rest, nothing active — 56 dp (was 112)

```text
┌────────────────────────────────────────┐
│ ◷ REST  2:30 dim      [◔ time] [Start] │  56 dp, empty track, dim numeral
└────────────────────────────────────────┘
```

- Left: rest glyph (`TemperIcons.FloorRest`, cyan at 40% or `TextSecondary`) + kicker
  `REST` (dim) + clock `2:30` in `numeralMd` at `TextSecondary`. Empty track behind
  (0% fill) so the bar is visibly the same instrument, drained.
- Tapping the clock block opens the duration sheet (presets + custom + ±15). A small
  chevron after the clock marks the affordance (fixes finding 8).
- Right: exactly two 48 dp targets — stopwatch icon-button (`◔`, spoken "Time this
  set", opens SET stopwatch; hidden on hold lifts) and `Start` (spoken "Start rest,
  2 minutes 30 seconds"). Idle `−15`/`+15` leave the bar for the sheet.
- Spoken (TalkBack, one focus on the clock block + two buttons): "Rest is not running.
  Rest 2:30. Tap to change duration." / "Time this set." / "Start rest."
- Font 2.0: bar grows to ≈ 80–96 dp (single line, controls wrap to two lines max);
  sheet scrolls independently. No second dock row in any configuration.
- After warm-up: `Warm-up · 2:30` copy stays, same geometry (`RestIdleCopy.dockDuration`).

### 2. Idle, duration sheet open — dock stays 56 dp

```text
┌────────────────────────────────────────┐
│  Rest length              2:30          │
│  [0:30][1:00][1:30][2:00][3:00][Custom]│  sheet (overlay, dock unchanged)
│  [−15]  [＋15]        [Time set]        │
└────────────────────────────────────────┘
```

- Bottom sheet (new, or `LiftSwitcherSheet`-style surface): preset chips
  (`RestTimer.PRESETS_SECONDS` + Custom → existing `CustomRestDialog`), quiet `−15` /
  `+15` with tick haptics (HA-12 preserved), and a `Time set` text row as a second
  discovery path for the stopwatch.
- Selecting a preset applies + dismisses (current `onSelect` behaviour, minus the
  inline growth). Sheet is TalkBack-modal; scrim dismiss; reduced-motion snap.
- Alternative if a sheet is judged too heavy: route duration editing to the Rest page
  (`onOpenRest`, already wired on the running clock) and make the idle clock tap open
  it too. Cheaper, but adds a screen jump for a 5-second task — the sheet is the
  recommendation.

### 3. Running — rest counting down — 56 dp (unchanged)

```text
┌────────────────────────────────────────┐
│ REST  1:47 ████████░░░░  [−15][+15][Skip]│  56 dp, cyan draining fill
└────────────────────────────────────────┘
```

- Exactly Live 76: `FloorInstrumentBar` with `RestTimer.sweepFraction` fill, cyan →
  `Warn` + "Last ten seconds" copy in the last 10 s, `Skip` with commit haptic
  (HA-13). Clock tap opens the Rest page. No change except: keep.

### 4. Done — rest just finished — 56 dp for 3.5 s, then idle (unchanged)

```text
┌────────────────────────────────────────┐
│ Back to the bar  0:00   (gold, no fill)│  56 dp, polite announcement
└────────────────────────────────────────┘
```

- Exactly Live 76: `PrGold` kicker + `0:00`, `liveRegion = Polite` (the only announcing
  state), auto-return to idle after `FINISHED_DWELL_MS`. No change except: keep.

### 5. Hold — static work counting down — 56 dp (near-unchanged)

```text
┌────────────────────────────────────────┐
│ HOLD  0:24 ██████░░░░░░  (no controls) │  56 dp, remaining (never 0:00 live)
└────────────────────────────────────────┘
```

- Keep Live 76 remaining-countdown (`HoldWork.liveDockSeconds`, ≥ `0:01` while running)
  and `HOLD DONE` gold terminal with elapsed clock. Consider adding a quiet `Stop`
  (same box as SET's) for mis-starts — optional, decide in implementation; logging
  remains the normal exit.

### 6. Set stopwatch — strength set being timed — 56 dp (unchanged)

```text
┌────────────────────────────────────────┐
│ SET  0:06  (full quiet track)   [Stop] │  56 dp, count-up + Stop
└────────────────────────────────────────┘
```

- Exactly Live 76: `SET` + elapsed + `Stop`; `used` stays sticky per lift so a pause
  still writes seconds (`FloorTimerSurface.durationToLog`); lift-switch guard dialog
  (`SetStopwatchCopy.SWITCH_*`) unchanged.

### Dock totals after redesign (font 1.0)

| State | Timer | Rail | Log | Dock total |
|---|---|---|---|---|
| Idle (all lifts) | 56 | 56 | 72 | **184** (was 240 on strength lifts) |
| Running / done / hold / set | 56 | 56 | 72 | **184** (unchanged) |

Every state costs the same 184 dp. The middle scroll region gains a stable 56 dp back
on strength days, and — more important than the pixels — the timer occupies one
recognisable instrument slot in all six states.

## Interaction model

- **Rest starts three ways (unchanged):** auto after a logged working set that isn't
  the last prescribed one (`shouldStartAfterLog` / `shouldStartAfterExtra`, 180 ms
  after the row settles); `Start` on the idle bar (`startSelectedRest`); `Start rest`
  on the Rest page. Warm-ups never start the clock; the idle bar says `Warm-up · 2:30`.
- **Duration changes one way:** tap the idle clock → sheet → preset / custom / ±15 →
  applied + persisted (`setLastRestPresetSeconds`), sheet dismisses. Running keeps its
  inline `−15`/`+15` (mid-rest adjustment must not open a sheet). Planned-duration ±15
  moves off the bar into the sheet — one extra tap, repaid by 56 dp in every idle
  frame. The last preset survives per lift via `secondsToStart` precedence
  (prescribed → exercise → last → default), unchanged.
- **Skip stays running-only** with the confirm haptic; returns to idle with no finish
  flash (current `RestFinishFlash` semantics — Skip is not done).
- **`Time set` discovery, three paths:** (a) the stopwatch button in the idle bar
  (primary, always visible on non-hold lifts); (b) a row inside the duration sheet;
  (c) unchanged TalkBack copy. Starting SET still cancels rest generation (one-clock
  mutex untouched); stopping still leaves `used` sticky so the logged set keeps its
  seconds.
- **Rest page:** clock tap opens it in *every* state (today only the running clock is
  tappable — `onClockClick` is null on idle). The page remains the big-ring, keep-screen-on
  surface; the sheet is the quick editor. No conflict: sheet for seconds, page for
  minutes.
- **Hold lifts:** idle bar shows `REST` planned + `Start` but no stopwatch button
  (holds keep `startHoldSet` from the entry surface); `HOLD`/`HOLD DONE` bar unchanged.

## What stays / moves / is removed

**Stays (do not touch):** `FloorTimedMode` + `FloorTimedModeResolver` + `timedGeneration`
(one clock); `FloorInstrumentBar` running/done/hold/set rendering; auto-start rules
(`shouldStartAfterLog`, `shouldStartAfterExtra`, `scheduleRestAfterReceipt`);
`RestFinishFlash` + 3.5 s gold dwell; `RestHonestyRow` in the context rail;
`RestTimerService` / notifications / `RestLockActivity` (ADR-012 path);
`SavedStateFloorTimer` + rest store persistence; `Log` 72 dp Volt + reserved
56/56/72 dock; TalkBack live-region-only-on-finish; reduced-motion policy
(`LocalReducedMotion`, `Motion` tokens); the Rest page ring + controls;
`PRESETS_SECONDS` values; haptic vocabulary (tick ±15, commit Skip/Stop).

**Moves:** presets + Custom + idle ±15 → duration sheet (new composable, same domain
functions `selectRestDuration`, `selectCustomRest`, `nudgeRest`); `Time set` → idle-bar
stopwatch button + sheet row (same `startSetStopwatch`); idle clock tap → opens sheet
(or Rest page under the cheaper alternative).

**Is removed:** the full-width `Time set` TextButton row; inline `picking` expansion in
the dock (`rememberSaveable picking` + inline `RestPresetChips` in `RestIdleRow`); the
idle bar's inline `−15`/`+15`; the `Surface2`-settings-row idle visual (replaced by the
dim instrument bar); the affordance-free duration tap (replaced by clock + chevron).

## Risks and tradeoffs

1. **Preset discoverability.** Today presets are one tap away *inside* the dock (once
   you find the invisible tap target); after, one tap away *in a sheet*. Risk is low —
   the sheet is a bigger, clearer surface — but the clock must carry a visible chevron
   and the spoken "Tap to change duration," or duration editing becomes *less*
   discoverable than today's already-hidden version. Mitigate with the chevron + a
   first-run nudge only if phone evidence demands it (no coach-marks by default).
2. **Extra tap for planned ±15.** Idle `−15`/`+15` leave the bar. Owners who fine-tune
   planned rest every set pay one tap. Mitigate: presets already cover 30/60/90/120/180
   and the sheet keeps ±15 with the same haptics; running ±15 is untouched, which is
   where adjustment is urgent.
3. **`numeralMd` on idle re-opens G-05.** Standing law deliberately kept the live
   numeral off idle so nothing looks running that isn't. The redesign answers with
   three non-colour channels (dim ink + empty track + `Start` verb) plus the existing
   spoken "Rest is not running." If the phone check shows *any* "is it running?"
   confusion, fall back to `bodyStrong` for the planned clock and keep everything else.
   This is the one judgement call in the plan — flag it for the phone pass, not the JVM
   gate.
4. **Sheet vs Rest page overlap.** A duration sheet plus the Rest page is two surfaces
   for rest. Keep them crisply divided (sheet = quick duration edit, page = the live
   big clock) and never put presets in the dock again, or the three surfaces blur.
5. **Font 2.0 bar growth.** The unified bar still grows with text (correct — controls
   never shrink). Cap risk: clock `maxLines = 1` with ellipsis, kicker fixed, controls
   `maxLines = 2`. Verify the idle bar at 2.0 stays ≤ ~96 dp and Log stays reachable.
6. **Hold `Stop` (if added) changes a quiet contract.** Today a hold ends by logging or
   reaching target. A bar `Stop` must freeze elapsed (like `stopSetStopwatch`) and keep
   `used`-sticky logging semantics, or omit it. Default: omit in phase 1, revisit from
   phone evidence.
7. **Deutan + Warn≈Gold.** The dim-cyan / bright-cyan / gold progression must keep its
   non-colour channels (kicker words, fill presence, verbs) per ADR-023 — no hue-only
   state anywhere.

## Validation gates

Every gate must pass before the drop; the JVM gates run here on Cursor, the phone gate
on Temper Debug via Obtainium:

1. **Log never moves.** Bottom coordinate of `Log` identical across idle / sheet-open /
   running / done / hold / set / receipt / error / undo / completion at 360×800, font
   1.0 and 2.0 (goldens + `WorkoutLogBarTest`-style assertions).
2. **Heights.** Idle dock timer row = 56 dp min at font 1.0 in all configurations;
   no inline expansion anywhere in the dock; sheet overlays without moving the dock.
3. **TalkBack.** Order header → hero → set context → coach → weight → reps → RPE →
   recent sets → timer (clock block, stopwatch, Start) → context rail → Log; no
   per-second announcements; finished flash announces once politely; presets as
   selectable chips in the sheet; "Time this set" and "Start rest, N minutes" spoken
   labels exact.
4. **Font 2.0.** No clipped critical copy; timer bar ≤ ~96 dp; sheet scrolls; RPE and
   coach wrap per image-led law; 360/412/600 dp preview profiles.
5. **Reduced motion.** Sheet + bar transitions snap (`LocalReducedMotion`); pulse off;
   3.5 s done dwell and announcements unchanged.
6. **One clock.** `FloorTimedModeTest` + `FloorTimerSurfaceTest` + mutex tests green;
   starting SET/HOLD cancels rest generation; no two seconds-changing numerals composed
   (NONE/IDLE/RUNNING/COMPLETE/HOLD/STOPWATCH assertions in `FloorCompactRestBarTest`).
7. **Rest integrity.** Auto-start rules, Skip-no-flash, claim ledger
   (`RestTimerClaimLedger`), reboot-clear, notification/lock behaviour unchanged —
   existing timer JVM tests green, no new exact-alarm surface.
8. **JVM gate.** `tools/preflight.sh` then `./gradlew testDebugUnitTest assembleDebug`
   (static checks ride along — no `-PskipStaticChecks`).
9. **Phone pass (owner).** One Temper Debug pre-release; one-handed gym check: idle
   compactness, sheet discovery, running glanceability, the G-05 "is it running?" trap,
   TalkBack traversal with the screen off between sets.

## Phased implementation sequence

(Plan only — no code in this packet.)

- **Phase 0 — Lock the baseline.** Record dock goldens at 360×800, font 1.0/2.0, for
  all six timer states on Live 76. Files: existing golden harness + `FloorImageLedHeroTest`.
- **Phase 1 — Unify the idle bar (no behaviour change).** Rebuild `RestIdleRow` on the
  `FloorInstrumentBar` geometry: dim `REST` kicker + dim `numeralMd` planned clock +
  empty track + chevron; keep `−15`/`+15`/`Start` inline *for this phase only*; keep
  inline `picking` but add the chevron. Proves the dim-numeral identity in isolation;
  G-05 fallback (revert clock to `bodyStrong`) costs one line. Touches:
  `RestTimerUi.kt` (`RestIdleRow`), `RestIdleCopy`, `RestIdlePresentationTest` (update
  the `numeralMd` ban to ban *bright* `numeralMd` on idle).
- **Phase 2 — Duration sheet.** New sheet composable (presets + Custom via existing
  `CustomRestDialog` + ±15 + Time-set row); wire idle clock tap → sheet; delete inline
  `picking` and the idle-bar ±15. Touches: `RestTimerUi.kt`, `ActiveWorkoutScreen.kt`
  (sheet host), `ActiveWorkoutViewModel` (no logic change — same `selectRestDuration` /
  `selectCustomRest` / `nudgeRest`), new sheet test.
- **Phase 3 — Time-set button.** Replace the full-width `Time set` row with the 48 dp
  stopwatch button in the idle bar (+ sheet row); hide on hold lifts (existing
  `offerSetClock` predicate); clock tap opens sheet, running clock keeps opening the
  Rest page. Touches: `RestTimerUi.kt` (`RestIdleRow`, `FloorTimerSlot`),
  `SetStopwatchCopy`, TalkBack copy.
- **Phase 4 — Gates.** Goldens for all states × font 1.0/2.0 × reduced motion;
  TalkBack order test updates; height assertions (56 min idle); `./gradlew
  testDebugUnitTest assembleDebug`. No visual tuning without a failing gate.
- **Phase 5 — Temper Debug drop + phone check.** `python3 tools/debug-drop-plan.py` for
  suffix/number, tag `debug-live-YYYY-MM-DD`, attach `PersonalTrainer-*-debug.apk`,
  Obtainium phone pass against gate 9; tune only from phone evidence.

## Side note (different thread, not planned here)

Deadlift/squat showing 0 / no-weight by default is the Live 74 zero-weight packet
(`Allow working weight 0 for bodyweight and empty-hands lifts`, `c6a12487`) — a
separate concern from the timer. Mentioned only so it isn't confused with timer scope;
no proposal here.

## Audit appendix — ownership map (Live 76)

- **Dock frame:** `LogBar` in `WorkoutLogBar.kt` — 56 dp timer box (`TIMER_ROW`) +
  56 dp context rail (`CONTEXT_RAIL`) + 72 dp `Log`. `Log` label/payload via
  `LogBarCopy.commit`.
- **Mode switch:** `FloorTimerSlot` (`RestTimerUi.kt`) → `FloorTimerSurface.mode` →
  `FloorTimedModeResolver.resolve` → `RestDock` or `SetWorkDock`.
- **Running/done:** `RestDock` → `FloorInstrumentBar` (`workout-rest-bar`); controls
  `workout-rest-minus/plus/skip`; flash keyed on `completedTimerId`.
- **Idle:** `RestDock` → `RestIdleRow` (`workout-rest-idle`, `workout-start-rest`,
  `workout-start-set-clock`); presets `RestPresetChips` + `CustomRestDialog`;
  copy `RestIdleCopy`; no numeral (ban asserted in `RestIdlePresentationTest`).
- **Hold/set:** `SetWorkDock` (`workout-hold-clock`) → `FloorInstrumentBar`; hold
  remaining via `HoldWork.liveDockSeconds`; `Stop` via `workout-stop-set-clock`
  (stopwatch only).
- **State:** `ActiveWorkoutViewModel` — `restTimer` (service-backed) + `_holdTimer` +
  `_setStopwatch` (local tickers) + `timedGeneration` mutex + `pendingRestJob`
  (180 ms post-log delay); `RestTimerUiState`, `HoldTimerUiState`,
  `SetStopwatchUiState`; persistence via `SavedStateFloorTimer` + rest store.
- **Rest page:** `RestTimerScreen` + `RestTimerViewModel` — 280 dp `RestSweepRing`,
  `RestFloorIdleControls`, `RestFloorTags.*`, `keepScreenOn`.
- **Notifications:** `RestTimerNotifications` (`rest_timer_running_v2` chronometer,
  `rest_timer_done_v3` DND-bypass), `RestTimerService`, `RestLockActivity`,
  `RestTimerAlerts` (sound/vibration/tick), `RestNotificationCopy`.
- **Standing law:** `ADR-005` (one Volt, Instrument only), `ADR-012` (exact rest,
  no overlay clock, reboot-clear), `ADR-023` (palette collisions stay, motion
  collapses); image-led plan (`artifacts/plans/image_led_workout_redesign_e180e63f.plan.md`):
  112 dp hero, stable dock, TalkBack order, font-2.0 rules, `Log` never moves.
