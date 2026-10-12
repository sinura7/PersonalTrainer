# ADR-026 — Frontend redesign and native evidence

- **Status:** Accepted
- **Date:** 16 September 2026
- **Amended:** 23 September 2026 — decision 8 by
  [ADR-032](ADR-032-jvm-evidence-lanes.md): JVM renders are the visual
  evidence; emulator goldens retire as baselines; 29 September 2026 —
  decision 3: effort is required on a working set (owner decision, P2a of
  the eight-asks plan of 29 September, `docs/owner-eight-plan-2026-09-29.md`
  once #444 lands); warm-ups and
  holds stay optional. Supersedes W1b's "Effort · optional" (#393).
- **Supersedes / Related:** ADR-002 §3 branch prefix only; ADR-005/023 visual
  identity retained; ADR-017 day-picker geometry may adapt; ADR-022 Body
  registration refined without replacing keyed catalog stills; ADR-024 gates
  retained. Refines the current Foundation Program's frontend work.

## Context

The owner supplied twelve screenshots and explicitly approved the comprehensive
frontend redesign plan. The audit found composition, state-clarity and full-screen
evidence gaps despite substantial functional tests. Source constraints requiring
a 112 dp workout hero, a blank 56 dp context rail, Log after target completion,
or totals-only History filtering describe the previous design, not the new target.

## Decision

1. Execute [FRONTEND_REDESIGN.md](../FRONTEND_REDESIGN.md) in one packet at a time,
   with independent/adversarial review and integrated verification. Short-lived
   branches use `codex/`; `trunk` remains the sole long-lived branch.
2. Preserve Instrument, offline functionality, five destinations, canonical IDs,
   calculation rules, captured-time semantics, persistence and backup formats.
3. Recompose workout around compact exercise identity, explicit set type,
   editable values, effort (optional until 29 September 2026; required on a
   working set since, `SetLogRules.requiresEffort`), latest saved set and a
   two-row action area.
   Unapplied suggestions are visibly and semantically distinct from selections.
4. Completed planned work offers Next exercise (next unfinished, wrapping), then
   Finish workout. Extra sets remain explicit. No automatic advancement.
5. History has one selected period/range for totals, calendar, progress and list.
   Lifetime records and blocks remain available in clearly labeled secondary views.
6. The day picker may scroll to preserve touch targets. Navigation may reflow
   3 + 2 when all five full labels cannot fit; destinations and order stay fixed.
7. Body base, heat masks, highlights and hit testing use registered geometry.
   Known exercise image keys still resolve first, with the existing fallbacks.
8. Missing required visual baselines fail. Native renders, semantics/interaction
   checks and physical-device evidence have distinct acceptance roles. Hosted
   emulator checks remain nonblocking; the deterministic/local gates stay intact.
   *Amended 23 September 2026 by [ADR-032](ADR-032-jvm-evidence-lanes.md):* the
   required baselines are the JVM render set; the 17 September emulator goldens
   are retired.
9. Home retains ADR-021's current start sheet and no Add row. The approved plan's
   instruction to preserve the existing recurrence choice does not resurrect the
   obsolete Home add flow removed on 13 September; Plan remains its current author.

## Consequences

Old presentation assertions must be updated to test the approved behavior, not
bypassed. No schema reset, new backend, sixth tab or signing change is authorized
by visual work. Obtainium milestones need increasing codes and stable signatures.
Physical checks remain explicit outstanding acceptance until actually performed.

## Review questions

- Can an HTML concept close a native UI packet? No.
- Does Next exercise automatically advance after logging? No; it requires a tap.
- Can History display all sessions beneath Month-selected totals? No.
- Can a generated test APK overwrite the owner's Obtainium app? No.
- Does this authorize parallel implementation packets? No.

## Amendment — 11 October 2026, compact finish receipt

The owner's approved connected flow concept carries the flat Home agenda and
workout entry into Summary. Replace the separate hero and metric cards with a
clear full workout title and a wrapping line of labeled recorded measures. Keep
the existing headline choice and canonical units: volume, bodyweight repetitions
or working sets. A mixed session retains its additional bodyweight repetitions;
a set-count headline is displayed once. Count-up ownership, settled spoken values
and reduced motion remain intact.

Show the lift breakdown as a flat illustrated ledger with complete names and
values. Exercise identity still resolves by its exact ID and catalog image key.
A hold keeps its recorded duration and set count without an empty weight metric;
this does not invent an aggregate hold duration. Full session remains the route to
all saved sets and correction. Notes use plain text, personal records retain their
existing event treatment, and Done and full-session actions remain pinned.

Loading, missing, read failure, confirmed saved work, Retry, retained Summary
refresh and exit navigation keep their existing rules. This is a presentation
change, with no calculation, write, schema, backup or public API change. Native
readability, unit/measure branches and the saved correction journey must pass;
browser concepts do not establish native or physical-phone acceptance.

## Amendment — 11 October 2026, compact History ledger

Carry the approved flat composition into History. The selected period has a
complete range title and wrapping, labeled session, day, set, active-time and
available record totals. Loading or failed progress does not display a successful
zero record count. Period choices use a flat radio group with a structural selected
underline: one line at normal phone text size, wrapping at larger sizes. Previous,
Next and Current remain quiet, reachable actions. Clearly labeled lifetime records and completed blocks use
quiet controls that wrap while preserving their full labels and touch targets.

Sessions, lifetime records and completed blocks use a flat ledger. Session rows
retain matching keyed exercise illustrations, complete names, captured dates,
exact kind/ID destinations and the separate Repeat action. Show session set/work
measures when present and retain actual active time; cardio does not acquire empty
strength measures, and holds do not acquire an invented weight or duration total.
The default Home row composition remains intact.

The selected period still governs totals, calendar, progress and chronology.
Immutable read projection, lifetime-reader ownership, loading/failure/Retry,
calendar restoration, units and existing calculation rules remain unchanged.
Verify rendered measures and reachable actions across the native display matrix
and preserve the saved-work journey. This changes presentation without changing
writes, schema, backup format, public API or physical-phone acceptance.

## Amendment — 11 October 2026, compact Plan workshop

Extend the owner's approved flat, illustrated direction into Plan. Routines are
visible directly under a named section, with complete names, ordered exercise
previews and the first three exact exercise illustrations. A single tap opens
that routine's editor; separate options and the existing long-press shortcut
retain Edit, Delete and its confirmation. This supersedes ADR-021 decision 6's
collapsed catalog and its earlier note that Plan keeps the old date surface.

Plan's date targets use the accepted flat week-strip presentation while keeping
selected civil day, occurrence status, today marker, horizontal exploration and
measured touch geometry. The selected-day board uses flat illustrated rows and
dividing rules. A scheduled routine resolves artwork through its exact rule and
routine IDs; cardio or an unresolved routine does not receive an invented lift
picture. Tapping a planned row still edits that day, and Add session retains its
existing selected-day add picker and one-filled-action priority.

Capture one immutable Plan state for deferred list content. Retain empty/loading/
error, proposal and missed-work recovery, recurrence, stored-time ordering,
Library, editor Save/cancel and confirmed-deletion scope. Opening a reusable
routine performs no write; deleting it preserves captured workout results.
No scheduling, calculation, schema, backup, public API or distribution change is
included. Native full-name/preview/target evidence, exact-ID actions and the
existing weekly-add, cancellation, Save and saved-history journeys must pass.

## Amendment — 12 October 2026, compact routine lift rows

Carry the approved flat illustrated composition into the reusable routine
editor and its shared lift strip in custom week setup. Replace each enclosing
lift panel with a plain row and a dividing rule. Keep its exact exercise image,
order, complete name, full work/rest/load preview and existing target controls.
Long names and previews wrap instead of truncating. A selected row has a thicker
rule and selected semantics. At font scale 1.6 and above, identity text uses the
full row width beneath its image/order line; normal text keeps the image beside
the name. This reduces squeezing without reducing the requested text size.
Work/rest/load facts use a local wrapping layout;
weight and its canonical display unit stay together without a one-line clamp.
Bodyweight-only rows omit a load fact and spoken load even when a legacy or
reclassified routine retains a positive weight column. The existing no-weight
editor rule governs its preview as well; this does not erase that stored value.
Its action says Close targets while expanded and
Edit targets while closed. Both states expose a button role.

Keep exact-ID selection, target staging/commit, validation, folding cleanup,
reorder, Remove, Swap, Save/cancel and the custom week's draft-until-Confirm
contract. Opening targets and editing unrelated fields must preserve an absent
weight field's exact stored column, including a retained legacy target. Existing scrolling and
bring-into-view ownership remain unchanged. This packet changes presentation,
with no ViewModel write, schema, backup, public API or distribution change.

Verify real Routine Editor/Room preparation failure, exact Retry, complete long
names, reachable target editing and stale-selection return across the native
display matrix, with loaded and ranged-hold previews in kilograms and pounds
and a bodyweight-only routine retaining an irrelevant positive weight column.
Preserve Plan's editor Save/scope/history journey and the shared
custom-week ordering, validation and draft regression cases. Full custom-week
screen layout, import and broader editor architecture remain separately scoped
F7/onboarding work; component checks do not establish their complete acceptance.

## Amendment — 12 October 2026, Library names and return context

Carry the approved flat illustrated direction into the pushed Library route.
Keep exact exercise pictures and identities, complete names and wrapping
metadata. At font 1.6 and above, give the name full width below the image/action
line. Keep Add as the direct nested action and custom management behind options;
both expose explicit 48 dp buttons and their own spoken labels. The options
sheet opens fully and scrolls when long names or larger text require it.
In landscape, move the existing Create action into the header, using the actual
parent dimensions, so the floating button cannot cover a row's Options action.
Portrait retains its existing Create placement.
Empty/no-match content scrolls within its remaining viewport so its explanation
and Create action remain reachable; opening and cancelling Create saves no row.

Clear filters resets muscle and equipment together while retaining the raw
typed query. It remains visible beside the horizontal equipment strip, so
scrolling the choices does not hide this action. Preserve the separate Clear
search control, AND filtering, grouping rules, exact-ID callbacks, create/edit
validation, cancellation and reference-aware deletion. The header becomes
quieter within the existing Instrument type system and five-tab architecture.

Save query, canonical facet names, expanded families and the once-per-route
initial-muscle marker in the existing SavedStateHandle mechanism. Reconstructing
that owner must not reapply a Body filter that the user deliberately cleared.
Unknown saved facet names degrade to no facet. This does not create a global
saved-search feature or establish custom-editor draft recovery.

Use actual Library/Room native journeys and the framework SavedState factory,
with a genuinely new Activity/ViewModel owner, to verify these contracts. A
saved Bundle test does not prove OS process death. Keep required-read recovery,
manual editor-draft continuity, full AppNav, picker/onboarding and physical
acceptance separately scoped; no schema, backup, recording, algorithm or
distribution change is included.
