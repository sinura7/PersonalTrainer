# P20 phone checklist

102 checks. Temper Debug 128 (`debug-live/2026-10-16`, commit `ca822118`). One walk, in order. Tick a box only when you saw the pass. If you did not try it, leave it blank.

Use the **Temper Debug** icon. Do not open the gym-floor Temper app. Do not use your real workouts, weights, or body measurements. If Debug already has history you care about, stop. Otherwise clear storage for Temper Debug only (Android Settings, Apps, Temper Debug, Storage, Clear storage), then open it again.

Made-up values to reuse: body weight 70 kg, bench 40 kg for 8 reps, a second set 42.5 kg for 8, cardio 10 minutes and 1 km, routine name `P20 Push`, custom lift name `P20 Press`.

Set the phone font to the largest size before you start, unless a step says otherwise. Screen reader and TalkBack are not in this list.

## 1. First open

[ ] **UX20-AC03**
Do: Confirm the icon you opened is Temper Debug, and that the gym-floor Temper icon is still a separate app.
Pass: Two apps. You only used Debug.

[ ] **UX30-AC02**
Do: Look at the tabs along the bottom.
Pass: Home, Plan, Body, History, Settings. No sixth tab.

[ ] **UX08-AC01**
Do: Walk every setup question. Try Continue, Skip, and Back on each one.
Pass: You can reach every question and those buttons without anything cut off.

[ ] **UX08-AC02**
Do: Enter 70 on the weight step, switch kg and lb, then Skip the weight step on a second look if Skip is there.
Pass: 70 kg and the lb label describe the same weight. Skip leaves weight optional.

[ ] **UX28-AC03**
Do: Back out of setup before Use this plan. Look at Home.
Pass: Home does not show a surprise week or a half-built program.

[ ] **UX07-AC04**
Do: Start setup again. Leave the app on a later question. Open Debug again.
Pass: Your answers are still there. Nothing new was saved as the current plan until you accept.

[ ] **UX28-AC01**
Do: On the preview, change days or equipment, go back, then forward again.
Pass: The preview matches the new answers. Earlier answers are still there.

[ ] **UX28-AC02**
Do: Read the line above Use this plan.
Pass: You can say, in your own words, that this will add routines and start a block, before you tap.

[ ] **UX08-AC04**
Do: Tap Use this plan once. When setup is done, open setup again if Settings offers it, and leave without accepting.
Pass: One week appears. A second visit does not silently add another copy.

## 2. Home

[ ] **UX02-AC01**
Do: Before the plan exists you already checked an empty Home if you did section 1. After the plan, open the start choices again.
Pass: You can find a planned start, an empty workout, Past workout, Cardio, and Mixed. (Fifty routines is not part of this walk. Leave the box blank if you want that case tried later.)

[ ] **UX02-AC02**
Do: Open Past workout, then close the sheet without saving.
Pass: No timer starts. History does not gain a workout.

[ ] **UX01-AC01**
Do: Start the planned day, then discard it if it is empty enough to discard. Start an empty workout.
Pass: The planned start follows the routine. The empty start has no lifts until you add one.

[ ] **UX01-AC03**
Do: On Home, with the largest font, find the button that starts today's plan.
Pass: You can see and tap it without hunting through stats.

[ ] **UX01-AC02**
Do: Pick another day on the week strip, then start what that day offers. Cancel if it would log.
Pass: The day you picked is the day that opens. Nothing is saved under today by mistake.

[ ] **UX19-AC03**
Do: Start the empty workout without logging a body weight first.
Pass: The workout opens.

[ ] **UX02-AC03**
Do: While that workout is still open, go Home and try to start another one.
Pass: One clear message. You still have one live workout, not two.

## 3. Logging a set

[ ] **UX03-AC02**
Do: Add Bench Press. Turn Warm-up on and off. If you can pick a bodyweight move and an assisted move, glance at their labels.
Pass: Warm-up is labeled as warm-up. Loaded work shows kg. Nothing pretends a bodyweight move is 0 kg.

[ ] **UX03-AC01**
Do: Type 40 and 8. Tap Log set twice, quickly.
Pass: One set is saved, 40 kg for 8.

[ ] **UX06-AC01**
Do: Try reps `8.5` and weight `-50` on a new set, then a normal 42.5 for 8.
Pass: `8.5` is not stored as 85. Negative weight is not stored as 50. The normal set saves.

[ ] **UX06-AC02**
Do: Try weight `1.2.3`, `8e2`, and `62,5`.
Pass: The odd forms are rejected or shown as the same number the app already uses for a comma decimal. They do not become a surprise value.

[ ] **UX06-AC03**
Do: Note the kg you just saved. Switch the phone to lb in Settings, look at that set, then switch back to kg.
Pass: The saved weight matches again, within the usual rounding (40 kg stays about 40, not a new number).

[ ] **UX03-AC04**
Do: Edit the 40 kg set to 41 kg. Undo if Undo is offered. Then use Next lift or add another lift.
Pass: The edit stays on Bench Press. The next lift is a different exercise. You do not get two copies of the same set.

[ ] **P04-ANOTHER**
Do: After the planned sets, look for Another set. Tap it once and log 42.5 kg for 8.
Pass: Another set is on screen and tappable. One extra set appears. You are not stuck with the button off the bottom edge.

[ ] **P04-CLOSE**
Do: Look for Close, Done, or the back control on this logging screen.
Pass: You can see it and it leaves, or it asks first. It is not hidden under the log bar.

[ ] **P10R-native65**
Do: On Bench Press, open Swap (or Change exercise). Wait until the list shows.
Pass: The list opens and you can see lifts. It does not sit on a blank sheet. Cancel so Bench Press stays.

[ ] **UX24-AC01**
Do: In that list, find two long names that start the same way, or scroll until names wrap.
Pass: You can tell them apart before you tap. The ending of the name is not cut off to the same stub.

[ ] **UX03-AC03**
Do: Turn the phone sideways. Open the keyboard on the weight field.
Pass: You can still see the weight field and the Log set (or Save set) button, and you can tap them. The keyboard does not cover the only way to save.

[ ] **UX25-AC03**
Do: Stay sideways, largest font, keyboard open. Look at Log set and back or Close.
Pass: Those actions are still on screen or one scroll away, and a tap hits them, not the keyboard.

[ ] **P09-IME**
Do: Finish or discard this workout first if you must leave. On Plan, edit `P20 Push` or any routine. Open a lift's sets, reps, or weight so the keyboard shows, sideways.
Pass: You can still tap the number you are editing and the button that keeps it. The keyboard does not swallow that button.

## 4. Rest, sound, and the lock screen

Log one more set so rest starts. Turn rest sound and vibration on in Settings, Rest timer, if they are off. Then log again.

[ ] **P20-SOUND**
Do: Let rest run with sound on.
Pass: You hear the cue you turned on, or silence if the phone is on silent and the app says so. You do not get a stuck beep.

[ ] **P20-HAPTIC**
Do: With vibration on, log a set, and reject a bad value if the phone offers a buzz for that.
Pass: A short buzz on the success, and a different buzz on the reject if you tried one. No endless vibration.

[ ] **P20-OVERLAY**
Do: While rest is running, leave Debug and look at the rest card or the rest screen.
Pass: The rest countdown is visible over the app or on its own rest page. Minus, plus, and Skip do what they say.

[ ] **P20-LOCK**
Do: Lock the phone during rest. Wake it.
Pass: The rest alert or lock-screen widget shows the countdown. Skip works, or the doc on screen tells you the widget is not on this phone. You do not get a second workout.

## 5. Finish

[ ] **UX05-AC03**
Do: If you can log a bodyweight move with no added weight, do one, then continue.
Pass: The summary does not call the workout empty or worthless only because load is zero.

[ ] **UX05-AC01**
Do: Finish the workout. Read the summary.
Pass: It matches the sets you logged. A missing or broken summary says so in words, and does not pretend the workout failed to exist.

[ ] **UX05-AC02**
Do: If Retry appears, tap it once. If it does not, leave this blank.
Pass: Retry does not create a second workout and does not finish the same one twice.

[ ] **UX05-AC04**
Do: Tap Done or Back on the summary. Then press the phone Back button once more.
Pass: You leave once. The finished workout does not reopen as live.

[ ] **UX22-AC03**
Do: From History, open that workout, then Back.
Pass: It stays a finished workout. Back does not drop you into a live logger.

## 6. History

[ ] **P07-READY**
Do: Open History, then open the workout you just saved. Wait if it says it is loading.
Pass: The sets show when it is ready. You are not left on a spinner, and you are not scrolled to the wrong workout.

[ ] **UX15-AC01**
Do: On History, look for a way to jump range or see a record without flicking through every day. You only have one fake workout, so also try the control that would show a longer range.
Pass: You can change the range. You are not forced to scroll an endless list.

[ ] **UX15-AC02**
Do: Switch the range (week, month, all, or whatever is offered).
Pass: The totals and the list match the range you picked. Your one fake workout appears only in ranges that contain today.

[ ] **UX15-AC03**
Do: Open the set and change 40 kg to 41 kg if edit is offered. Go back to the record or the chart for that lift.
Pass: 41 kg shows, and you can see it belongs to this fake workout.

[ ] **UX16-AC01**
Do: Open a chart for Bench Press if one is offered.
Pass: Dates are labeled. Two sessions are not drawn as if the same time passed between them when you only have one session. With one session, the chart says it has little to plot, and does not invent a trend.

[ ] **UX16-AC03**
Do: Look at the bench chart and, if you logged one, a bodyweight-move chart.
Pass: Bench uses kg. A reps-only move uses reps. An empty chart says it is empty.

[ ] **UX29-AC01**
Do: Open the finished strength workout. After section 7, open the cardio or mixed session too.
Pass: Strength shows kg and the sets you logged. Cardio shows 10 minutes and 1 km. The actions match that kind of session.

[ ] **UX29-AC02**
Do: Open the strength workout from History twice.
Pass: The second open does not congratulate you as if you just saved it again.

[ ] **UX29-AC03**
Do: If Repeat or Edit is offered, change a note and save.
Pass: It is still the same workout, on the same day, still strength. It did not become cardio.

[ ] **UX19-AC01**
Do: In Settings or Body, add a fake body weight of 70 kg for today, then change it to 71 kg.
Pass: Today shows 71. Yesterday, if empty, stays empty.

[ ] **UX19-AC02**
Do: Start to delete that 71 kg entry, read the line, then cancel. Delete it only if you want it gone.
Pass: Cancel keeps 71. The delete line names the weight entry, not the workout.

## 7. Cardio and mixed

[ ] **UX07-AC01**
Do: Start a mixed or past session. Add a strength row 40 kg for 8 and a cardio row 10 min, 1 km. Leave Debug. Open the same draft again.
Pass: The day, the order, and both rows are still there.

[ ] **UX07-AC02**
Do: Tap Save once. While it is saving, tap Back if the button is still there.
Pass: One saved session. You are not told the saved work was thrown away.

[ ] **UX07-AC03**
Do: If you can, turn on airplane mode and try a save that needs the network. If save does not need the network, leave this blank. Then save for real with airplane mode off.
Pass: A failed save keeps the draft and offers a way to try again. A successful save clears that draft only.

## 8. Plan, week, and calendar

[ ] **UX12-AC01**
Do: On Home, tap each day of the week strip, including the first and last.
Pass: Each tap selects that day only. Days do not overlap. You can hit them with a thumb at the largest font.

[ ] **UX12-AC02**
Do: Look at a week that has your finished workout, an empty day, and a future day.
Pass: You can read what each day is (done, empty, or still to come) in words or a clear mark, not by color alone.

[ ] **UX12-AC03**
Do: Move to a day in the next month if the strip or the day list allows it. Also look at today.
Pass: The date you open is the date you tapped. Today looks different from the day you merely selected.

[ ] **UX13-AC03**
Do: Compare today's plan on Home and on Plan.
Pass: Same day, same sessions.

[ ] **UX13-AC01**
Do: On Plan, add a one-off note or extra day if the screen allows, and look at next week. Do not delete your real plan. Use the fake week only.
Pass: A one-off stays on that date. Next week does not copy it unless you chose a repeating plan.

[ ] **UX13-AC02**
Do: Reorder two days or two lifts if the screen allows, and read the confirm line.
Pass: The line says what moves. Your finished workout in History is unchanged.

[ ] **UX11-AC01**
Do: Swap a lift on the plan, or edit one day's schedule, and read the text before you save.
Pass: You can tell whether you are changing this day or the whole routine, before you commit.

[ ] **UX11-AC02**
Do: Open routine actions from Plan using the buttons on screen.
Pass: You can edit, swap, or remove without a hidden long-press being the only way.

[ ] **UX11-AC03**
Do: Delete a fake routine you do not need, such as an extra you added. Read the warning. Restore nothing of your real gym app.
Pass: History still has the workout you logged. The plan day explains that the routine is gone.

[ ] **UX14-AC01**
Do: If Plan offers missed work or a week preview, open it and read the dates.
Pass: The preview matches what Save would keep. A week boundary does not move a finished day.

[ ] **UX14-AC02**
Do: Leave that preview without saving if you are unsure. Check History.
Pass: Finished sessions stay as they were.

[ ] **UX14-AC03**
Do: If you do save a plan change, tap the confirm once, and again if it is still there.
Pass: One change. A failure, if you see one, keeps the proposal and lets you retry.

[ ] **UX24-AC03**
Do: On Plan and in a picker, tap a few rows and buttons with the largest font.
Pass: Rows and buttons are still easy to hit. Nothing you used to reach is now a tiny sliver.

## 9. Lifts and routines

[ ] **UX10-AC01**
Do: In a lift picker, search `bench`.
Pass: Existing bench variants show first. You are not pushed to create a new lift named bench.

[ ] **UX27-AC01**
Do: Filter to Chest and a piece of equipment, then clear the filter.
Pass: You find one matching bench, and clearing the filter brings the wider list back without losing your place for long.

[ ] **UX24-AC02**
Do: On that picker and on the log bar, look at the main buttons and the kg values at the largest font.
Pass: The button words and the full weight are readable. They are not cut off.

[ ] **UX10-AC04**
Do: If you can select several lifts to add, select two and confirm once, then again.
Pass: Those two lifts are added once, in the order you picked.

[ ] **UX09-AC01**
Do: Create a routine named `P20 Push`, add Bench Press, save.
Pass: One routine, and it contains Bench Press. You did not have to search again after save.

[ ] **UX09-AC02**
Do: Start another routine, then cancel.
Pass: You are back where you started. No empty routine named nothing is left on Plan.

[ ] **UX09-AC03**
Do: Start a routine, add a lift, leave Debug, and open that draft again.
Pass: The lift you picked is still on the draft.

[ ] **UX04-AC01**
Do: Rename `P20 Push` to `P20 Push A`. If a save error appears, tap Retry.
Pass: The new name is what you typed. A failure stays on screen with the name intact and a retry. If you never see a failure, you may still tick this when the rename sticks.

[ ] **UX04-AC02**
Do: Change the target reps on Bench Press inside the routine and save.
Pass: The new reps stick. A failure does not claim success. If you see no failure, tick only if the new reps remain after you leave and come back.

[ ] **UX04-AC03**
Do: Open the routine, change nothing, and go back.
Pass: Back does not wipe the name or the lift. A brand new routine you cancelled earlier is still gone.

[ ] **UX04-AC04**
Do: Start editing the routine name, leave Debug from the recents screen, and open Debug again.
Pass: The draft name is still there, or the last successful name is there. You do not see a blank routine.

[ ] **UX10-AC02**
Do: Try to create a custom lift named Bench Press if one already exists.
Pass: The sheet tells you it already exists, in the sheet, not as a fake success.

[ ] **UX10-AC03**
Do: Start creating `P20 Press`, then cancel.
Pass: The cancel text matches what happens. If cancel keeps a custom lift, the screen said it would. If cancel drops it, Plan and the library do not list `P20 Press`.

[ ] **UX27-AC02**
Do: Search `bench`, open a lift, then come back.
Pass: You return to the bench results, not the top of the whole library.

[ ] **UX27-AC03**
Do: Turn on airplane mode and open the library. Then turn it off.
Pass: A load failure does not look like an empty library with a big Create button as the only next step.

[ ] **UX06-AC04**
Do: Set distance to miles. Log 1 km of cardio in the composer, in live cardio, and look at a routine target if it shows distance or weight.
Pass: Each place uses the unit you expect, and a saved 1 km does not become a mile.

## 10. Body

[ ] **UX17-AC01**
Do: Open Body. Look at a muscle you did not train in the fake workout.
Pass: You can tell it means no work in this period, not that the muscle is recovered.

[ ] **UX17-AC02**
Do: Open the list of muscles, not only the picture. Tap one you did train (chest, if you benched).
Pass: Every muscle you try has a full-size row, and the row opens the same detail as the picture.

[ ] **UX17-AC03**
Do: Read the caption for the period at the largest font.
Pass: The caption is readable, and the chest detail agrees that you logged bench in that period.

## 11. Motion and failures

[ ] **UX26-AC01**
Do: Turn on Remove animations in the phone's accessibility settings. Open Body and a chart.
Pass: Numbers and the chart show right away. Nothing waits on an animation before you can read it.

[ ] **UX26-AC02**
Do: Trigger a short message if you can (a saved choice, a small banner). Wait.
Pass: Either it stays until you dismiss it, or it stays long enough to read at the largest font. The only way to act is not a flash you missed.

[ ] **UX26-AC03**
Do: Turn airplane mode on and try a Settings change or a note save that can fail. Then turn airplane mode off.
Pass: After any banner goes away, you can still see the failure and retry, or the choice simply stayed on the old value and the screen says that.

[ ] **UX23-AC01**
Do: With airplane mode on, open History and a Drive backup screen.
Pass: Loading, empty, and needs-a-network look different. None of them say the workout saved when it did not.

[ ] **UX23-AC02**
Do: On a failed save, look next to the button you tapped.
Pass: The error is by that button, your typed text is still there, and Retry is there.

[ ] **UX23-AC03**
Do: Read an empty History range and a failed load, if you can show both.
Pass: Empty does not say try a new workout as if the data failed. A failure does not say you have no history.

## 12. Settings, units, backup

[ ] **UX18-AC01**
Do: Try kg with km, kg with miles, lb with km, and lb with miles. Log nothing important. Look at Home, the set you saved, and a backup preview if one is on screen.
Pass: Weight and distance follow the pair you picked. 41 kg does not change because you picked miles.

[ ] **UX18-AC02**
Do: On kg and miles, switch weight to lb and back to kg.
Pass: The stored set is still about 41 kg. Distance stays miles until you change distance.

[ ] **UX18-AC03**
Do: You do not need an old backup file. If you have a debug backup from before miles existed, restore it onto this Debug app only.
Pass: Distance falls back to the rule you were told (it follows weight). Your fake set is not rewritten into a different number. If you have no old file, leave this blank.

[ ] **UX20-AC01**
Do: Change rest default and units. Find Export or Backup.
Pass: You can change them and you can find backup. Nothing is worse than the last debug build you used.

[ ] **UX20-AC02**
Do: Leave Settings from Rest timer, then open Settings again.
Pass: You land somewhere useful. A failed save, if one is showing, is still visible.

[ ] **UX21-AC01**
Do: Start a restore with the wrong password. If you can, also try with airplane mode and Drive.
Pass: Wrong password has its own heading, and the password box does not cover it. Offline Drive says it needs a connection. A finished restore and a half-finished one do not share the same heading.

[ ] **UX21-AC02**
Do: Read the heading after any backup action you tried.
Pass: The heading matches what happened. A failure is not titled like a success.

[ ] **UX21-AC03**
Do: Open the restore confirm, turn the phone, and Back out.
Pass: You return to the same step. Nothing restores twice. Back does not commit.

[ ] **UX22-AC01**
Do: If a workout reminder is on, trigger it once while a workout is already open, or skip if you have no reminder.
Pass: One screen. It does not start a second live workout.

[ ] **UX22-AC02**
Do: From a permission explanation, open system Settings and come back.
Pass: Your draft or live workout is still there. The app matches the permission you actually set.

## 13. Paper check

[ ] **UX30-AC01**
Do: You do not need the code. This file is the phone record for the IDs above.
Pass: Tick when you have walked the list and written any miss in the notes.

[ ] **UX30-AC03**
Do: Look back. Any box you left blank is still an open gap.
Pass: Tick when you agree blank boxes stay open, and you did not mark a miss as a pass.

## Notes

Write misses here. Include the ID and what you saw.

- 
- 
