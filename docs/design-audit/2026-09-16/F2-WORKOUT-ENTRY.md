# F2 — Workout entry and composition

Status: implementation and verification in progress on `codex/workout-entry`,
based on integrated F1 `97d08ca458a9823f4cd6b62bea78a84be9b14084`.
This is not the complete workout milestone; F3 owns completion and timing.

## Changed contracts

- Current exercise uses 64 dp uncropped artwork, full exercise name, equipment,
  working-set progress, an explicit Switch exercise label and a separate menu.
  Session-wide elapsed time, set counts and work are in **Session summary**.
- Entry remains weight above reps/duration. The editable centers have visible
  edit marks, baseline-aligned units and no ellipsis. Values and adjustment
  labels are measured before choosing an inline or stacked layout.
- Working/Warm-up is a radio choice. Ramp presets say **Use [weight]** and never
  publish selected semantics. A recommendation is supporting text. Presets only
  change the draft; the existing save/reset and calculation rules are retained.
- RPE stays optional, toggles off, has Clear and persistent help, and reflows
  instead of compressing five enlarged choices. Warm-ups explain why RPE is absent.
- Entry and effort precede recommendations. Applying an already-entered planned
  weight is no longer repeated as a redundant action.
- Latest saved set is compact. **View sets** opens full labeled working/warm-up
  history with edit/delete menus. A receipt identifies the actual saved/edited
  row, including when an earlier row was edited. It cannot mark another exercise's
  latest set as newly saved.
- Saving does not scroll to the growing history. Editing deliberately reveals
  entry; switching retains the established viewport reset.
- The footer has one 56 dp minimum companion and one 72 dp minimum commit.
  Error/undo/context takes the companion while an active clock remains accessible.
  Receipts do not introduce another footer panel or an empty reserved row.
- Entered load meanings remain distinct. **Added weight** is explicit; zero
  external load is described as no external load rather than bodyweight.

No schema, stored measurement, exercise ID, backup format, distribution signer
or version-code change is part of this packet.

## Executed development evidence

Development runs are retained below, including failed probes and their fixes.
The 38 references from `20260917-085721688` have been visually reviewed and
explicitly accepted for the pinned Windows renderer. Fresh comparison, platform,
hosted and integrated results are recorded separately from baseline acceptance.

| Run | Result | Purpose |
|---|---|---|
| `20260917-080022629` | 15/18 passed | First layout probe exposed stale density sampling and a test expecting unrounded large-value text. Captures also exposed tall presets, split adjustment labels and clipped rest time. |
| `20260917-081039885` | 18/18 passed | Five logical sizes at 1.0/1.6/2.0, plus warm-up and large-value states; native viewport, footer clearance, reachability and full numeric rendering. |
| `20260917-081529791` | 4/4 passed | Real-window numeric input, eight consecutive saves, warm-up/RPE, saved-set editing/deletion/undo using real Room and the production ViewModel. |
| `20260917-082917826` | 44/44 passed | 38 viewport/load/state/RTL cases, four entry journeys and two existing real-navigation workout journeys. |
| `20260917-084340405` | 43/44 passed | Review fixes and system-font-2 journeys; landscape setup assumed the launcher could rotate and failed that assumption. |
| `20260917-084643008` | 5/6 passed | Actual OS-font-2 input/IME, saved-set correction, denial timing and normal logging passed. Landscape needed a lazy-list scroll before locating View sets. |
| `20260917-084925844` | 0/1 passed | Landscape reached first/last row menus, then exposed a legacy floor-only visibility gate hiding Add another set in the new sheet. Gate corrected. |
| `20260917-085123091` | 44/44 passed | All 38 viewport cases and six durable/real-window journeys, including long-name landscape and denied notifications. Enlarged error-context wrapping still needed visual refinement after this run. |
| `20260917-085721688` | 38/38 passed | Final viewport capture after error/undo companion reflow. The four changed error/undo images were inspected at full size; the other 34 are pixel-identical to the previously reviewed run. All 38 references accepted. |
| `20260917-090559673` | 81/81 passed | Fresh comparison: 38 F2 and 32 F1 image references, six new entry journeys, two existing workout journeys, and shared-control/shell interaction checks. No missing references, failures or skips. |
| `20260917-091057572` | 41/44 passed | Android 16: all six real-window journeys passed. Three 412 dp geometry assertions rejected valid integer-pixel rounding; diagnostic run below isolated it. |
| `20260917-091357253` | 35/38 passed | Measured 164 px at density 2.28125: exactly Compose's rounded 72 dp minimum, but 71.89041 dp when divided back. Assertion now compares the rounded pixel constraint. |
| `20260917-091640157` | 44/44 passed | Corrected Android 16 run: all geometry checks and real-window journeys passed. All 43 captures visually reviewed; default test-host navigation scrim is gone and keyboard/sheet actions remain readable. |
| `20260917-092014184` | 43/44 passed | Android 8: all 38 viewport checks and five journeys passed. The landscape journey stopped when the old platform UiAutomation screenshot API returned null. |
| `20260917-092400978` | 6/6 passed | Android 8 real-window journeys with native shell screencap fallback. All five observations were retained, including both landscape sheet positions. |

The layout test includes the production navigation parent's Scaffold padding and
consumed system insets. Geometry uses the measured root node's own density rather
than a separately sampled composition value. Native-window dialogs use a real
device window; ForcedSize is not used to claim keyboard or dialog coverage.

The first full gate stopped at the icon-family ratchet (19 against ceiling 18).
The new stock icon was replaced; no checker baseline was raised. The second gate
identified obsolete source-presentation assertions; the third full gate passed
2,536 unit tests, debug build, lint and instrumented-source build. The final
reviewed-source gate passed 2,537 unit tests (zero failures/errors), debug build,
lint and instrumented-source build in 2m 24s. It includes the timestamp-order
receipt regression. Behavioral tests remain in place. Old requirements for a 112 dp workout hero, independent
context reservation, recommended-as-selected RPE, and history-driven scrolling
are deliberately replaced under ADR-026. The eight-save native test measures the
behavior previously represented by the now-unused scroll-on-growth helper.

Independent and adversarial review both identified notification-denial loss of
idle timing access and a non-scrolling long saved-sheet identity. Adversarial
review also identified wrong edited-row spoken ordinals. Fixes retain an idle
Timers route, keep completion accessible, scroll dynamic sheet identity/footer,
and compute edit ordinals from preceding saved rows of the same type. Regression
checks include permission denial, a ten-row long-name landscape sheet at system
font 2.0, and edits of earlier working/warm-up rows including type changes.
The receipt review additionally required set-number order rather than captured
timestamp order; the unit fixture now reverses timestamps to model clock correction.
Both final review passes found no remaining actionable F2 source blocker and
verified the manifest hashes. Their approval remains conditional on the platform,
hosted and integrated checks recorded with this packet.

The first four window journeys applied a Compose font override; they did not
prove system-font-2 dialogs or visible IME. The corrected lane sets Android's
font scale before Activity creation and asserts an actual input-method window.
Those corrected results are tracked separately; old captures are observations.

Android 16 review additionally exposed the generic test Activity's default light
navigation contrast scrim in captures. On API 35+, where edge-to-edge is enforced,
the harness now applies the same dark/transparent system-bar policy already used
by MainActivity. This is test-host parity, not a product inset change. The API 29
reference window, logical insets and comparison allowance are unchanged. Captures
now precede geometry assertions so a failed bound retains visual evidence.
On Android 8, a display-size override can make UiAutomation's screenshot return
null. The observation harness falls back to native shell screencap and still
fails if no valid image is produced. The API 26 viewport captures and all five
successful window observations were visually reviewed. Its legacy compositor
letterboxes the resized landscape app in the physical portrait display.

After these test-only corrections the complete local gate passed again in
1m 21s: static checks reran and unchanged unit/build/lint outputs were reused.
Original recording source hashes remain intact; subsequent harness hashes and
verification are recorded separately in `native/f2/verification.json`.

PR [#350](https://github.com/sinura7/PersonalTrainer/pull/350) is a draft until
acceptance is complete. Initial hosted required verification passed on
`bfc4781e699af844a4e114e43c68f4d49b1a20c4`. Its 180 native tests had exactly 38
missing new references and nine legacy workout image differences assigned to F3.
All 38 new hosted renders were reviewed and independently reproduced in push and
PR runs (`35203754406`, `35203773061`): three pixel-identical, the remainder within
the unchanged one-level/256-pixel rounding allowance. They are separate hosted
references with a manifest; no Windows baseline was reused across renderers.

Captures use only synthetic fixture data on the repository-owned emulator.
The original phone screenshots are not part of public implementation evidence.

## Acceptance still required

- Final API 29 comparison after the test-only corrections.
- PR hosted deterministic check and clean integrated verification after merge.
- F3: derived Next/Finish/extra-set action contract, operation-specific retry,
  complete timer/switcher/resume work and replacement of legacy workout goldens.
  Commit agreement includes the pre-existing one-decimal display formatting for
  two-decimal typed drafts. Extremely short-window error/timing combinations
  belong to the derived companion-state work, before the workout milestone.
- Physical TalkBack, phone settings, release-equivalent performance, stable-signer
  upgrade and owner phone trial remain milestone acceptance, not emulator claims.

The legacy hosted workout golden debt recorded with F1 remains assigned to F2/F3;
it is not hidden by accepting old images or changing the pixel tolerance.
