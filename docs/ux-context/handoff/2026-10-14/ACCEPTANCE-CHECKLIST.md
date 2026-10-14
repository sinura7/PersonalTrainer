# Temper acceptance checklist at handoff

This is a transfer snapshot of all 97 existing UX acceptance items at source `158f492b`. It retains every original ID and requirement. It is not a replacement backlog.

**Required:** 45 have bounded automated evidence, 48 remain Open and 3 have partial P05/P06 calendar contributions. **Optional:** 1 physical TalkBack item remains Open. No row is promoted to final end-user acceptance by this handoff.

The source top-level status often says Not executed while its completion field contains bounded automated evidence. The JSON export preserves both fields and all original ownership/evidence. Interpret the table as implementation/evidence status, not a whole-programme pass.

The original implementation/closure owner is retained below even when its packet was merged. P01 and P03 have residual obligations; P19/P20 also own final integration and physical aspects. Some criteria involve multiple implementation packets; see the JSON for that mapping.

## UX01 Clarify Home's planned versus freestyle actions

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX01-AC01 | Yes | P05 | Bounded automated evidence | Given both a planned workout and freestyle control, a participant can correctly explain which follows the routine and which starts empty. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX01-AC02 | Yes | P05 | Bounded automated evidence | Given another selected day, starting/logging behavior matches explicit copy and existing date rules; no silent backdating. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX01-AC03 | Yes | P05 | Bounded automated evidence | At 360 dp and large text, the planned row's start action is discoverable without an unrelated statistics detour; measure rather than assert this has improved. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |

## UX02 Separate starting now from recording completed activity

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX02-AC01 | Yes | P14 | Open | With 0, 3 and 50 routines, participants can find past strength, manual cardio, mixed entry and live cardio. | Implement or verify this exact requirement in P14, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX02-AC02 | Yes | P14 | Open | Selecting a manual entry does not start a timer; dismissing the sheet makes no training-data write. | Implement or verify this exact requirement in P14, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX02-AC03 | Yes | P14 | Bounded automated evidence | Opening the sheet while a session runs produces one clear answer and no duplicate session. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |

## UX03 Refine the existing logging dock and action transitions

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX03-AC01 | Yes | P04 | Bounded automated evidence | Rapid repeated activation while a write is pending commits one set. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX03-AC02 | Yes | P04 | Bounded automated evidence | Warmup, bodyweight, added-weight and assisted movements show the correct labels and units; no universal '0 kg' assumption. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX03-AC03 | Yes | P04 | Bounded automated evidence | Keyboard open at 360 dp and landscape leaves the active field and commit action reachable. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX03-AC04 | Yes | P04 | Bounded automated evidence | Undo/edit/next transitions preserve exercise identity and announce accepted results once. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |

## UX04 Make routine Save truthful and clarify autosave

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX04-AC01 | Yes | P09 | Bounded automated evidence; integrated | Inject details-write failure after editing a name: Save remains on screen, preserves text and offers retry. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX04-AC02 | Yes | P09 | Bounded automated evidence; integrated | Inject one staged-target failure: no blanket success/exit; retry persists the intended value once. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX04-AC03 | Yes | P09 | Bounded automated evidence; integrated | Back after successful edits remains consistent with existing autosave; empty new stubs retain their documented cleanup. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX04-AC04 | Yes | P09 | Bounded automated evidence; integrated | Process recreation during failure retains sufficient draft state; reopening shows the accepted persisted result. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |

## UX05 Separate saved receipts from missing or unavailable summaries

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX05-AC01 | Yes | P07 | Bounded automated evidence; integrated | Missing ID, deleted/restored-over row, computation failure after confirmed save and valid no-work data each render distinct truthful states. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX05-AC02 | Yes | P07 | Bounded automated evidence; integrated | Retrying summary never creates or re-finishes a workout. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX05-AC03 | Yes | P07 | Bounded automated evidence; integrated | A bodyweight-only workout does not appear worthless because its load-volume is zero. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX05-AC04 | Yes | P07 | Bounded automated evidence; integrated | Back and Done exit once and do not return to an already-finished live session. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |

## UX06 Stop numeric input from silently changing meaning

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX06-AC01 | Yes | P04 | Bounded automated evidence | 8.5 reps is rejected rather than saved as 85; -50 kg is rejected rather than saved as 50. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX06-AC02 | Yes | P04 | Bounded automated evidence | 1.2.3 and 8e2 do not become different accepted values; 62,5 and 62.5 behave equivalently under the supported decimal policy. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX06-AC03 | Yes | P04 | Bounded automated evidence | Existing valid values survive unit conversion/round trips without drift beyond specified display precision. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX06-AC04 | Yes | P19 | Open | All shared filter callers are covered, including composer, live cardio and routine targets. | Implement or verify this exact requirement in P19, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |

## UX07 Define draft preservation and leave-during-save behavior

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX07-AC01 | Yes | P14 | Open | Build a multi-row mixed draft, background, recreate the process and restore: date, rows, order and values survive. | Implement or verify this exact requirement in P14, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX07-AC02 | Yes | P16 | Open | Cancel/Back during delayed accepted save neither creates a duplicate nor tells the user committed work was discarded. | Implement or verify this exact requirement in P16, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX07-AC03 | Yes | P16 | Open | Failed save preserves the draft; successful save clears only that draft. | Implement or verify this exact requirement in P16, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX07-AC04 | Yes | P16 | Open | Onboarding draft restoration does not overwrite the saved current plan or create a new one without acceptance. | Implement or verify this exact requirement in P16, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |

## UX08 Make every onboarding step fit and remain editable

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX08-AC01 | Yes | P15 | Open | Every question and Continue/Skip action is reachable at 360×640 dp, font 2.0, and the smaller supported window configuration. | Implement or verify this exact requirement in P15, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX08-AC02 | Yes | P15 | Open | Typed bodyweight, wheel, unit switch and Skip produce consistent optional values. | Implement or verify this exact requirement in P15, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX08-AC03 | Yes | P15 | Open | Screen-reader focus lands on the new question and Back keeps the selected answer. | Implement or verify this exact requirement in P15, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX08-AC04 | Yes | P16 | Open | Accepting a plan once creates the intended program; reopening setup does not silently apply a draft. | Implement or verify this exact requirement in P16, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |

## UX09 Carry an exercise through Create routine

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX09-AC01 | Yes | P09 | Bounded automated evidence; integrated | From an empty routine catalogue, choose a lift → Create routine → save: exactly one routine contains that lift, without another search. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX09-AC02 | Yes | P09 | Bounded automated evidence; integrated | Cancel returns to the original context and leaves no empty stub. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX09-AC03 | Yes | P09 | Bounded automated evidence; integrated | Rotation/process recreation retains the pending exercise ID according to the draft contract. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |

## UX10 Reduce accidental custom-exercise creation and keep picker errors visible

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX10-AC01 | Yes | P03 | Open | Searching 'bench' with existing matching variants prioritizes choosing them over creating 'bench'. | Verify existing-variant prioritization in the actual Library/Create flow before adding a duplicate. Implement only a demonstrated gap; retain existing custom Save recovery. |
| UX10-AC02 | Yes | P03 | Open | Duplicate/disk/create errors are visible within the active sheet in single and multi modes. | Exercise duplicate, disk and Create failures inside the active single- and multiple-selection sheets. Verify reachable errors and preserved fields; P19 confirms shared callers. |
| UX10-AC03 | Yes | P03 | Open | Create then cancel activity follows the disclosed catalogue persistence rule. | Create a custom lift, cancel activity creation and verify the actual catalogue persistence matches its disclosure; retain exact canonical identity. |
| UX10-AC04 | Yes | P10 | Bounded automated evidence; integrated | Double confirmation adds the selected IDs once and preserves order. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |

## UX11 Make edit scope and secondary actions discoverable

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX11-AC01 | Yes | P08 | Bounded automated evidence; integrated | A participant can state the scope before saving a swap or schedule edit. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX11-AC02 | Yes | P08 | Bounded automated evidence; integrated | Routine management is reachable without relying solely on long press, or documented usability evidence supports retaining the existing affordance. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX11-AC03 | Yes | P08 | Bounded automated evidence; integrated | Deleting a routine preserves history and accurately explains its impact on scheduled days. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |

## UX12 Make calendar dates and statuses distinguishable at small widths

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX12-AC01 | Yes | P19 | Bounded P05/P06 contributions; full calendar/AppNav integration open | At 320/360/412 dp, date targets are measurable and usable without overlapping ambiguous activation regions, or an equivalent accessible selector is supplied. | Use existing P05/P06 evidence, then execute the complete calendar/AppNav criterion in P19; record actual date/target/semantics results. |
| UX12-AC02 | Yes | P19 | Bounded P05/P06 contributions; full calendar/AppNav integration open | A week containing done, skipped, missed, future and rest days has unambiguous spoken and visible detail. | Use existing P05/P06 evidence, then execute the complete calendar/AppNav criterion in P19; record actual date/target/semantics results. |
| UX12-AC03 | Yes | P19 | Bounded P05/P06 contributions; full calendar/AppNav integration open | Selecting a neighboring-month date opens the correct civil date; today remains distinguishable from selection. | Use existing P05/P06 evidence, then execute the complete calendar/AppNav criterion in P19; record actual date/target/semantics results. |

## UX13 Expose recurrence and ordering consequences without restoring clock clutter

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX13-AC01 | Yes | P08 | Bounded automated evidence; integrated | Add the same type once and recurring in separate scenarios; next week contains only the recurring case. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX13-AC02 | Yes | P08 | Bounded automated evidence; integrated | Reordering states exactly which future/reminder behavior changes and preserves completed history. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX13-AC03 | Yes | P08 | Bounded automated evidence; integrated | Home and Plan show the same dated occurrences after changes. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |

## UX14 Preview bulk missed-work changes before applying

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX14-AC01 | Yes | P08 | Bounded automated evidence; integrated | Preview and committed outcome match for missed work crossing week boundaries. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX14-AC02 | Yes | P08 | Bounded automated evidence; integrated | Completed sessions remain unchanged and are explicitly separated from future schedule adjustments. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX14-AC03 | Yes | P08 | Bounded automated evidence; integrated | Commit failure preserves the proposal and offers retry; repeated taps do not apply it twice. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |

## UX15 Make History's scope and deeper sections reachable

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX15-AC01 | Yes | P06 | Bounded automated evidence | With years of synthetic history, a participant can reach lifetime records and the newest activity without an unbounded scroll. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX15-AC02 | Yes | P06 | Bounded automated evidence | Changing a horizon produces exactly the documented scope across totals/list/records. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX15-AC03 | Yes | P07 | Bounded automated evidence; integrated | Editing a prior best refreshes visible results and record drill-down identifies the supporting session. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |

## UX16 Make chart axes, comparisons and sparse data honest

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX16-AC01 | Yes | P07 | Bounded automated evidence; integrated | Jan 1, Jan 2 and Apr 1 samples do not imply equal elapsed intervals without an explicit session-axis label. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX16-AC02 | Yes | P07 | Bounded automated evidence; integrated | Screen-reader/data-list users can recover values and dates without relying on the plotted line. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX16-AC03 | Yes | P07 | Bounded automated evidence; integrated | Bodyweight-rep series use reps, loaded series use the selected physical unit, and empty/flat series are truthful. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |

## UX17 Explain the body map as recorded muscle workload

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX17-AC01 | Yes | P11 | Open | Participants can explain that an uncolored muscle means no mapped work in the selected period, not a recovery guarantee. | Implement or verify this exact requirement in P11, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX17-AC02 | Yes | P11 | Open | All muscles remain reachable via full-size text rows with equivalent detail/actions. | Implement or verify this exact requirement in P11, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX17-AC03 | Yes | P11 | Open | The selected window and contributor data agree; caption remains readable at font 2.0. | Implement or verify this exact requirement in P11, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |

## UX18 Support mixed unit preferences deliberately

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX18-AC01 | Yes | P17 | Open | All four kg/lb × km/mi combinations render consistent input, summary, history and export behavior. | Implement or verify this exact requirement in P17, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX18-AC02 | Yes | P17 | Open | Changing preference does not change stored distance or weight. | Implement or verify this exact requirement in P17, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX18-AC03 | Yes | P17 | Open | Old backups lacking distance preference restore to the documented default and drafts do not reinterpret numbers. | Implement or verify this exact requirement in P17, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |

## UX19 Make bodyweight history correctable and clearing explicit

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX19-AC01 | Yes | P12 | Open | Correcting an older synthetic measurement changes that date and refreshes the affected review. | Implement or verify this exact requirement in P12, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX19-AC02 | Yes | P12 | Open | Clear/delete copy identifies the exact affected data; cancelling leaves it unchanged. | Implement or verify this exact requirement in P12, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX19-AC03 | Yes | P12 | Open | No bodyweight entry is required to record a workout or complete setup. | Implement or verify this exact requirement in P12, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |

## UX20 Reduce Settings scanning without burying recovery

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX20-AC01 | Yes | P17 | Open | A participant can change units/rest preferences and find export/restore with no worse success than the baseline. | Implement or verify this exact requirement in P17, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX20-AC02 | Yes | P17 | Open | Reopening Settings restores useful context without hiding an unresolved save error. | Implement or verify this exact requirement in P17, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX20-AC03 | Yes | P17 | Open | Production and debug build screenshots are evaluated separately. | Implement or verify this exact requirement in P17, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |

## UX21 Specify backup/recovery as a truthful multi-stage task

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX21-AC01 | Yes | P18 | Open | Wrong password, oversized input, unsupported version, offline Drive, live workout, partial preferences failure and completed restore each have a distinct actionable screen. | Implement or verify this exact requirement in P18, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX21-AC02 | Yes | P18 | Open | Displayed success follows the actual accepted operation; no UI-only fix masks R01–R04. | Implement or verify this exact requirement in P18, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX21-AC03 | Yes | P18 | Open | Back/rotation/permission return retains the operation's correct stage and never repeats a destructive commit. | Implement or verify this exact requirement in P18, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |

## UX22 Preserve context through navigation, notifications and external screens

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX22-AC01 | Yes | P04 | Bounded automated evidence | Repeated delivery/recreation of the same reminder intent does not stack duplicate live screens or start twice. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX22-AC02 | Yes | P04 | Bounded automated evidence | Returning from system settings keeps the active session/draft and reflects the real permission result. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX22-AC03 | Yes | P04 | Bounded automated evidence | Back from a completed summary cannot reopen the finished workout as live. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |

## UX23 Create a shared language for loading, failure, saving and retry

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX23-AC01 | Yes | P19 | Open | Injected initial/later load failures, invalid fields and post-commit side-effect failures produce the defined states. | Implement or verify this exact requirement in P19, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX23-AC02 | Yes | P19 | Open | Users can locate the error and retry at the point of action without losing authored data. | Implement or verify this exact requirement in P19, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX23-AC03 | Yes | P19 | Open | Missing, empty and unavailable never share a misleading generic success or start-new-workout remedy. | Implement or verify this exact requirement in P19, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |

## UX24 Apply responsive type and layout rules to actual content

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX24-AC01 | Yes | P19 | Open | Two long similarly named variants can be distinguished before selection. | Implement or verify this exact requirement in P19, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX24-AC02 | Yes | P19 | Open | At font 2.0, primary actions and full values/units are readable and not clipped. | Implement or verify this exact requirement in P19, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX24-AC03 | Yes | P19 | Open | Shared component changes preserve all callers and do not reduce touch targets. | Implement or verify this exact requirement in P19, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |

## UX25 Validate complete-screen accessibility, not just component tags

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX25-AC01 | Optional | P19 | Open | Physical TalkBack can complete planned start, set log/edit, cardio finish, routine create, history correction and export review. | Optional physical TalkBack refinement; keep labeled optional. This does not waive required automated accessibility or other physical checks. |
| UX25-AC02 | Yes | P19 | Open | Search and routine-name fields announce their purpose when empty and populated. | Implement or verify this exact requirement in P19, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX25-AC03 | Yes | P19 | Open | Large text/IME does not hide essential actions; measured issues have production-screen regression coverage. | Implement or verify this exact requirement in P19, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |

## UX26 Tune feedback timing without adding decorative motion

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX26-AC01 | Yes | P19 | Open | With reduced motion enabled, all data and records appear immediately and remain accessible. | Implement or verify this exact requirement in P19, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX26-AC02 | Yes | P19 | Open | An extended accessibility timeout is honored for relevant messages or equivalent persistent access exists. | Implement or verify this exact requirement in P19, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX26-AC03 | Yes | P19 | Open | Save failure remains actionable after any transient banner disappears. | Implement or verify this exact requirement in P19, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |

## UX27 Make exercise discovery precise and stateful

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX27-AC01 | Yes | P10 | Bounded automated evidence; integrated | A participant finds a specified muscle+equipment variant and clears a restrictive filter without losing orientation. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX27-AC02 | Yes | P10 | Bounded automated evidence; integrated | Returning from detail restores the original results position. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX27-AC03 | Yes | P10 | Bounded automated evidence; integrated | Load failure does not invite creating a duplicate exercise because the catalogue appeared empty. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |

## UX28 Improve setup preview and returning-user orientation

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX28-AC01 | Yes | P16 | Open | A user can inspect the proposed week, change equipment/days and return to an updated preview with other answers preserved. | Implement or verify this exact requirement in P16, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX28-AC02 | Yes | P16 | Open | An existing user can explain the acceptance impact before tapping Use this plan. | Implement or verify this exact requirement in P16, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX28-AC03 | Yes | P16 | Open | Dismissed/failed setup does not create a partial unexpected program. | Implement or verify this exact requirement in P16, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |

## UX29 Make activity-type differences explicit in details and receipts

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX29-AC01 | Yes | P14 | Open | Each supported type has an accurate receipt and correct units with clear action capabilities. | Implement or verify this exact requirement in P14, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |
| UX29-AC02 | Yes | P07 | Bounded automated evidence; integrated | Opening history detail does not replay a misleading new-save confirmation. | Retain cited bounded evidence; verify affected complete navigation/accessibility in P19 and participant/physical aspects in P20. This row is not final acceptance. |
| UX29-AC03 | Yes | P14 | Open | Repeat/edit where supported preserves correct IDs, chronology and plan linkage and cannot silently convert one type to another. | Implement or verify this exact requirement in P14, preserving existing working behavior; then record affected P19 integration and P20 physical acceptance as applicable. |

## UX30 Establish an evidence-led design delivery and regression process

| Criterion | Required | Original closure owner | Recorded evidence state | Original requirement | Next closure task |
|---|---|---|---|---|---|
| UX30-AC01 | Yes | P01 | Open | Each implemented UX item has acceptance evidence, source/commit links and limitations. | Complete the evidence/commit/limitation record as each remaining UX item is delivered; final audit is criterion reconciliation, not another broad redesign audit. |
| UX30-AC02 | Yes | P01 | Open | Global-navigation changes follow the repository's current comparative task gate; no extra tab appears from taste alone. | Retain the five destinations. If a global-navigation change is proposed, satisfy the current comparative task gate before accepting that change. |
| UX30-AC03 | Yes | P01 | Open | Required device/accessibility gaps remain visibly open until actually tested. | Keep required device/accessibility gaps open until recorded execution closes them; reconcile the final P19/P20 ledger. |

