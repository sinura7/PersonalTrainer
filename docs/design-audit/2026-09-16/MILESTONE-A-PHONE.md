# Milestone A — workout phone acceptance

Status: prepared; signed drop and physical execution pending. This checklist
does not claim phone acceptance from emulator results. Use the existing
Obtainium **Temper Debug** entry; preserve its data and signing identity.

## Record once

- Phone model, Android version, display size and font-size settings.
- Version/build shown in Settings → About after the update.
- Existing history and settings remain present after upgrading in place.

## Focused workout trial

1. Start a workout and check the exercise name, working-set progress and main
   action at your normal display settings. Tap a value, replace it, Cancel,
   then enter and apply a decimal value. Confirm the displayed payload matches.
2. Apply a warm-up preset. Verify nothing is saved until Log warm-up. After
   saving, Working mode returns and working-set progress has not advanced.
3. Set RPE, change it, clear it, then log. Verify the receipt and the cleared
   effort selection. Log eight successive sets and confirm entry stays put.
4. At the planned count, verify Next exercise or Finish workout replaces Log.
   Add another set, save it, then advance with a deliberate tap. Rapid taps
   must not jump exercises or create another exercise's set.
5. Open View sets. Edit, delete and Undo a set; verify the progress and primary
   action follow the saved result. Cancel an edit without changing history.
6. Start rest; open the full timer; adjust −15/+15, close it and reopen it.
   Closing must keep rest running. Check the completed-rest return action.
7. Time a set. Stop timing and confirm no set was saved. For a hold, reach the
   target and confirm a separate Log hold action is still required.
8. Switch exercises while timing. Cancel once, then confirm. Verify the correct
   exercise and draft. Leave the workout running, visit each main tab and resume
   by tapping the elapsed time or set count in the activity bar.
9. Finish and compare the summary, history and saved sets. Reopen the app and
   confirm the result remains saved.

## Device-specific checks

- Rotate with a keyboard or sheet open. Use the phone's enlarged text setting;
  every entered value, Cancel/Set action and last list row must remain reachable.
- Background and lock the phone during rest. Check notification delivery,
  elapsed-time continuity, sound and haptics; record battery restrictions.
- Deny rest notifications and confirm logging remains usable with honest status.
- With TalkBack, complete value entry, log, View sets, timer and Next exercise.
  Check focus restoration and that timer ticks do not repeatedly interrupt speech.
- Record any visible delay with the action and screen. Formal p95 and frame-budget
  targets require traces on a documented physical device; subjective smoothness
  and emulator screenshots do not establish those performance numbers.

Report a failure with the step, displayed version, expected result and actual
result. Screenshots or a short recording are useful for clipping or interaction
issues. Keep existing history intact while collecting evidence.
