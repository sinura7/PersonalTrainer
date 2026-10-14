# Temper implemented work and remaining scope

For the full comparison against every original P01–P20 requirement, including open criteria inside integrated packets, read [the plan reconciliation](PLAN-RECONCILIATION.md). The [acceptance checklist](ACCEPTANCE-CHECKLIST.md) preserves all 97 original items and their next closure tasks. This file is the shorter implementation summary.

## What is available

The compact illustrated workout, Home, History/Summary, Plan and Library direction from Debug 125 is retained. Subsequent work on `trunk` adds durable recovery and closes bounded core journeys. It does not complete the whole-app redesign or prove the phone experience.

| Packet | Implemented outcome | Integrated reference |
|---|---|---|
| P01 | Reconciled UX acceptance ownership and captured the connected Debug 125 baseline. Preserved original failed attempts and subsequent passes. | PR 493, `a4829bfd` |
| P02 | Versioned typed private checkpoints, atomic/ordered writes, ownership and revision checks, pending commands, explicit discard and recovery outcomes. Checkpoints stay outside exported backups. | PR 494, merge `630f58f2` |
| P03 | Reconstructed custom-exercise fields and Create/Edit identity; stable create IDs, frozen commands, guarded Save and exact saved-result inspection/retry. | PR 495, merge `f7a40389` |
| P04 | Workout draft continuity and interrupted-save reconciliation; owner-bound manual/keypad callbacks, removed-owner retirement, notes and exit/reopen corrections. | PR 496 `e21ee990`; corrective PR 497 `830f82a8` |
| P05 | Durable Home day selection, clearer date choice and retained return/start/retry behavior across the covered states. | PR 498, `eb3a5797` |
| P06 | Retained History period/calendar context and a compact readout; selected ranges govern the covered totals and rows. | PR 499, `074f9c50` |
| P07 | Durable notes, historical correction and truthful detail/receipt handling, with an actual merged correction-to-History journey. | PR 500, `1ab4c1ab` |
| P08 | Scheduling consequence previews, durable proposal recovery and ordering, preserving completed history. | PR 501, `944d37fb` |
| P09 | Routine staged-field recovery and illustrated import confirmation; partial-save/exact-retry handling retains the existing write-through contract. | PR 502, `928cd374` |
| P10 | Library query/facets/position persistence and read-health recovery; owned routine/workout/activity/week picker context and custom-lift handoff recovery. | PR 503 `ddc4a49c`; corrective PR 504 `eea668ce` |

The P10 correction also keeps refused empty Activity Save editable, retains healthy parent fields after a corrupt child picker reset, and handles demonstrated lost-acknowledgement recovery cases. Full Activity Save replay remains P14; full custom-week Apply recovery remains P16. The private P10 submission fence does not close those later contracts.

## What was not delivered

Debug 126 preparation changes only `debugLiveCode` from 125 to 126 and updates three existing UX documentation files. It adds no further frontend redesign. Both clean source reviewers found zero Critical/High/Medium/Low issues in that four-file packet. Its complete gate was interrupted at the owner's request, so PR 505 is a draft and no signed Drop A was published.

## Remaining packets

| Packet | Remaining required work |
|---|---|
| P11 | Body image/heat/selection/hit registration using one measured transform; front/back alignment, clipping and equivalent full-size muscle actions. Show a connected concept before changing the area. Existing map and list behavior should be retained where sufficient. |
| P12 | Body period/read states, mapped-work interpretation, optional measurement history, correction/deletion and mixed unit presentation. |
| P13 | Live cardio inputs/timer continuity; distinct Finish, Leave running and Discard; one-live-activity rule and exact repeated/failed Finish. |
| P14 | Backdated/mixed entry, dates and timezone ambiguity, units/notes, persistent drafts, stable accepted-once Save and partial/uncertain-result reconciliation. Manual entry must not start a timer. |
| P15 | Scrollable onboarding with keyboard/large-text reachability, durable answers/step/units, predictable Back and truthful descriptions of existing choices. |
| P16 | Actual proposed-week preview, retained answers when changing equipment/days, explicit existing-user consequences and accepted-once/partial Apply recovery. |
| P17 | Settings groups, summaries, status on permission return, units/rest/sound/haptics/reminders, diagnostics/About, shared roles and focus behavior. |
| P18 | Backup select/validate/compare/confirm/progress/reconcile; accurate failure distinctions, safety snapshot and restore-journal contracts. No false success on mixed state. |
| P19 | Whole-app AppNav, accessibility and layout closure, including unresolved earlier native observations and full relevant matrix. |
| P20 | Required physical-device and owner acceptance after planned development: touch/system surfaces/interruption/sound/haptics/upgrade/performance; focused corrections and final signed drop if needed. |

P01–P10 closure is bounded by recorded automated/source/native evidence. Participant interpretation, complete cross-app acceptance and physical performance remain separate. Do not relabel these as finished because test counts are high.

## Known open observations and contracts

1. **Native65 Swap opening:** the accepted-Swap method passed raw instrumentation; the normal-Swap method timed out before the picker opened in the same combined batch. The aggregate batch failed. Isolated normal Swap passed without changes, and one merged AppNav normal Swap passed. Cause remains unproven; retain it for P19. It is not established as a Windows-only or fixture-only problem.
2. **P09 landscape IME:** semantic entry was demonstrated; physical touch behavior in that condition is not established. Retain the recorded P19 limit.
3. **P14/P16 pending commands:** finish domain-level stable replay and partial/unknown Save/Apply reconciliation; do not treat P10 private fencing as completion.
4. **P19/P20:** full navigation/layout/accessibility and required physical checks remain open. Manual physical TalkBack refinement is optional under the existing Debug policy; automated accessibility is required.
5. **Compatibility queue:** six dependency PRs 447–452 are open separately. They were not incorporated into the UX completion packet.

## Confirmed product constraints

- Compact dark Instrument surfaces, complete names and matching lift artwork; readable essential values, quiet secondary actions, one dominant primary action.
- Preserve Android/Compose, the five destinations and existing calculations/canonical IDs. No Room schema, public API or backup-format change is planned for UX work.
- Manual values remain authored values; working repetition sets require effort; Apply changes the draft without logging; progression is explicit; rest alone does not block logging.
- Private durable drafts restore only in the owning form. Back/Close retains staged input; explicit Discard clears it. Routine Back retains its committed write-through behavior.
- Freeze and persist a command before mutation. Resolve an uncertain result by identity and exact intended values before retrying. Confirm acceptance before clearing a draft.
- Signed Temper Debug via Obtainium is the delivery route; never replace it with an incompatible local signer or use real owner history as disposable test data.
- Owner testing follows planned development. Remaining major-area concepts and native previews are required; routine implementation does not require approval pauses.
- Notification expansion, twelve-week coaching, coach naming, sync activation, iOS and commercialization remain separate.

The requested deployment order remains Drop A after P10, Drop B after P14 and Drop C after P19. Drop A is still pending. Future version numbers and free suffixes must come from a fresh planner, not this document.
