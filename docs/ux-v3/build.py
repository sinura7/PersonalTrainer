import json,os,re,html
ROOT=os.environ.get('TEMPER_ROOT','/workspace/pt/')
U='app/src/main/java/com/sinura/personaltrainer/'
def P(x):return U+x
MISSING=[]
def ck(path):
    if not os.path.exists(ROOT+path):MISSING.append(path)
    return path
TB='Top bar 64dp: back 48dp target, title workoutTitle 20sp, subtitle caption 12sp'
notes={
'01_today':[[540,200,'Kicker date 11sp caps, then H1 display 28sp. 24dp below status bar'],[540,700,'TodayHeroCard: Surface1, 1dp outline, radius 24dp (Radius.lg), padding 16dp. Lift rows 56dp (Metrics.rowMin), thumb 40dp'],[540,1450,'Week chips 32dp tall, scroll horizontally'],[540,1870,'Bottom nav 80dp, 5 items, label 11sp']],
'02_plan_week':[[540,250,'H1 + Edit text button (48dp target)'],[540,640,'Day row: training 72dp, rest 56dp, gap 8dp. Today: 2dp Volt border (Metrics.emphasisBorder)'],[540,1640,'Secondary button 52dp']],
'03_plan_day':[[540,130,TB],[540,450,'Lift card 80dp min: drag handle 48dp, thumb 40dp, title 16sp, meta 14sp'],[540,960,'Add exercise: dashed 1dp outline, 56dp tall']],
'04_picker':[[540,130,TB],[540,250,'Search field 56dp'],[540,330,'Filter chips 40dp tall, 8dp gap'],[540,800,'Result row 64dp, check target 48dp']],
'05_targets':[[540,700,'BottomSheet: Surface2, top radius 24dp, grab bar 32x4dp'],[540,1000,'Steppers: 56dp round buttons, numeral numeralXl 56sp'],[540,1500,'Preset chips 48dp tall, equal width']],
'06_preview':[[540,130,TB],[540,330,'Lift row card 72dp; first lift has 2dp Volt border'],[540,1150,'Status chips: rest auto, warm-ups']],
'07_live_warmup':[[540,130,TB],[540,280,'SetPill row 48dp tall, 8dp gap'],[540,560,'Stepper: 56dp buttons, numeral numeralHero 76sp']],
'08_live_working':[[540,130,TB],[540,280,'SetPill row 48dp'],[540,560,'Weight stepper block 96dp; plate line caption 12sp'],[540,880,'TempoLine 40dp min, Volt label'],[540,1530,'EffortChips: 5 equal, 56dp tall, 8dp gap']],
'09_rest':[[540,560,'RestRing 240dp diameter, 10dp stroke, numeral 56sp'],[540,960,'Nudge buttons 52dp x 80dp'],[540,1170,'UpNext card, numeralLg 36sp'],[540,1330,'Logged set row with Undo, 56dp']],
'10_tempo':[[540,1100,'TempoSheet: mascot 48dp, title heroTitle 24sp'],[540,1350,'Before to after line: old value struck, new numeralLg'],[540,1530,'Two buttons: Keep (secondary) and Use (primary)']],
'11_set_options':[[540,900,'Action rows 64dp, icon tile 40dp']],
'12_between':[[540,250,'Completion header, kicker Volt'],[540,700,'UpNext card with 2dp Volt border'],[540,1150,'Mini RestRing 120dp']],
'13_finish':[[540,1450,'FinishSheet: per-lift completion list 14sp']],
'14_summary':[[540,500,'StatTile x3, numeralLg'],[540,780,'PR card: GoldContainer, PrGold kicker'],[540,1000,'Tempo next time card']],
'15_history_detail':[[540,130,TB],[540,500,'Set rows 40dp, warm-ups marked W']],
'16_past_session':[[540,130,TB],[540,260,'When row 56dp opens date and time picker'],[540,780,'SetTable cells 48dp']],
'17_cardio':[[540,450,'Elapsed numeral 88sp'],[540,870,'Input tiles 96dp']],
'18_live_font13':[[540,160,'Header wraps to 2 lines max at 1.3; subtitle 1 line'],[540,600,'Weight numeral scales with font (76sp x 1.3). Buttons stay 56dp. Label above numeral'],[540,1700,'Primary 72dp at 1.3 with 2-line label']]}
json.dump(notes,open('redline_notes.json','w'))
COL=[('Pit','#07090B','Existing','Screen background','n/a'),('Surface1','#0E1215','Existing','Cards','n/a'),('Surface2','#14191D','Existing','Sheets, inset rows','n/a'),('Surface3','#1B2126','Existing','Secondary buttons, stepper buttons','n/a'),('SurfacePressed','#232A30','Existing','Pressed state for surfaces','n/a'),('OutlineSolidVariant','#1E2429','Existing','Card outline','n/a'),('TextPrimary','#F2F5F7','Existing','Main text','18.22 on Pit, 16.16 on Surface2. AA pass'),('TextSecondary','#9BA7AE','Existing','Labels','8.10 on Pit, 7.19 on Surface2. AA pass'),('TextTertiary','#7F8B93','Existing','Hints, muted','5.71 on Pit, 5.07 on Surface2. AA pass'),('TextDisabled','#5F6B73','Existing','Disabled only','3.64 on Pit. Fails AA body; allowed for disabled controls (WCAG exempts inactive UI)'),('Volt','#C2FF44','Existing','Act now: primary fill, selection','Pit text on Volt 16.79. AA pass'),('VoltContainer','#212B13','Existing','Selected chip, done pill','Volt on it 12.45. AA pass'),('RestCyan','#33D6E8','Existing','Resting state only','Pit text on RestCyan 11.33; RestCyan on Surface2 10.05. AA pass'),('RestContainer (NEW)','#0B2A2F','New','Rest pill and up-next accent background','RestCyan on it 8.60. AA pass'),('PrGold','#FFC53D','Existing','Records only','9.67 on GoldContainer. AA pass'),('GoldContainer','#2E2410','Existing','PR card','n/a'),('Danger','#FF6B6B','Existing','Destructive only','7.19 on Pit, 6.33 on DangerContainer. AA pass'),('DangerContainer','#2A1214','Existing','Destructive confirm','n/a'),('Warn','#FFB020','Existing','Save warnings, rest overtime','not measured here')]
TYPE=[('numeralHero','SpaceGrotesk Medium','76','80','Live weight and reps'),('numeralXl','SpaceGrotesk Medium','56','60','Rest ring time, target steppers'),('numeralLg','SpaceGrotesk Medium','36','40','Up next target, summary stats'),('numeralMd','SpaceGrotesk Medium','24','28','Set pill values (wide)'),('numeralSm','SpaceGrotesk Medium','17','20','Set pill values'),('display','SpaceGrotesk Bold','28','32','Screen H1'),('heroTitle','Inter SemiBold','24','28','Sheet titles, lift name'),('commit','Inter SemiBold','20','24','Primary button label'),('workoutTitle','Inter Medium','20','24','Top bar title'),('title','Inter SemiBold','16','20','Card and row titles'),('body','Inter Normal','14','20','Body'),('bodyStrong','Inter Medium','14','20','Secondary button label'),('caption','Inter Normal','12','16','Hints, plate line, button sub-label'),('kicker','Inter SemiBold','11','13','Caps kickers, tracking 0.88sp'),('unit','Inter Medium','13','16','kg and reps units')]
SPACE=[('space1','4dp'),('space2','8dp'),('space3','12dp'),('space4','16dp, edge margin (Metrics.gutter)'),('space5','20dp'),('space6','24dp'),('space7','32dp'),('space8','40dp'),('sectionGap','28dp'),('touchMin','48dp, every tappable'),('control','56dp, chips and stepper buttons'),('rowMin','56dp')]
RAD=[('Radius.xs','8dp','set pills'),('Radius.sm','12dp','chips, inputs'),('Radius.md','16dp','buttons'),('Radius.lg','24dp','cards, sheet top corners'),('Radius.full','circle','stepper buttons, ring')]
MOT=[('Motion.TAP','90ms','press feedback'),('Motion.FAST','150ms','chip select, pill state change'),('Motion.BASE','240ms','sheet open, set to rest state swap'),('Motion.DRAW','650ms','ring first draw, PR reveal'),('Motion.TICK_MS','1000ms','rest tick'),('Motion.STATUS_DWELL_MS','6000ms','undo window base, scaled by system accessibility timeout (UndoFeedback.kt)'),('Easing','Motion.Standard (0.2,0,0,1) enter; Motion.Exit (0.3,0,1,1) leave','all'),('Reduced motion','LocalReducedMotion: cross-fade only, no ring sweep animation','all')]
HAP=[('Stepper step','Haptics.tick, tickLight on repeat','existing in StepperButton.kt'),('Effort chip select','Haptics.tick','new call site'),('Log set saved','Haptics.commit','existing in ActiveWorkoutScreen.kt'),('Save rejected or invalid','Haptics.reject','existing'),('Rest last 3 seconds','Haptics.warn','existing'),('Rest done','Haptics.holdDone','existing'),('PR logged','Haptics.recordAccent','existing'),('Workout finished','Haptics.commit','existing'),('Tempo suggestion used','Haptics.tick','new call site')]
COMP=[
('PrimaryButton','Replace styling of PrimaryGymButton','components/GymButtons.kt','Height 64dp (72dp with sub-label). Full width minus 16dp margins. Radius 16dp. Label commit 20sp Pit on Volt. Optional sub-label caption 12sp, 1 line. Rest variant: RestCyan fill.','Default Volt. Pressed: 8 percent Pit overlay, scale 0.98, Motion.TAP. Disabled: Surface3 fill, TextDisabled label. Loading: label kept, 20dp spinner replaces sub-label, not tappable. Error: label "Retry save", 1dp Danger outline. Selected: n/a.','1.0: 1 line label, 1 line sub. 1.3: height grows, label max 2 lines. 2.0: sub-label hidden, label max 2 lines. Never truncate the label verb.','Label is a verb phrase, max 24 characters. Sub-label says what happens next, max 40 characters.','fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, subLabel: String? = null, tone: ActionTone = ActionTone.Act, state: ActionState = ActionState.Enabled)'),
('SecondaryButton','Restyle SecondaryGymButton','components/GymButtons.kt','Height 52dp. Surface3 fill. Radius 16dp. Label bodyStrong 14sp TextPrimary.','Pressed SurfacePressed. Disabled TextDisabled. Loading spinner 16dp. Error and selected n/a.','1.3 and 2.0: height grows, max 2 lines.','Max 20 characters.','fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)'),
('GhostButton','Reuse QuietButton','components/QuietButton.kt','Min 48dp tall, no fill, TextSecondary body 14sp, centred.','Pressed: Surface2 fill. Disabled: TextDisabled.','Grows with font, max 2 lines.','Escape routes only, max 24 characters.','fun GhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier)'),
('DestructiveButton','Reuse DangerGymButton','components/GymButtons.kt','Height 52dp, DangerContainer fill, Danger label. Only inside a confirm dialog or sheet.','Pressed: Danger 12 percent overlay. Disabled TextDisabled. Loading spinner.','Grows, max 2 lines.','Names the loss: "Discard workout". Max 24 characters.','fun DestructiveButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier)'),
('Card and Panel','Reuse GymCard, add variants','components/GymSurfaces.kt','Card: Surface1, 1dp OutlineSolidVariant, radius 24dp, padding 16dp. Inset row: Surface2, radius 12dp, padding 12dp. Emphasis: 2dp Volt border.','Pressed (if clickable): SurfacePressed. Disabled: 60 percent alpha content. Selected: emphasis border. Loading: ScreenSkeleton card.','Height follows content.','n/a','fun Panel(modifier: Modifier = Modifier, emphasis: PanelEmphasis = PanelEmphasis.None, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)'),
('SetPill','New visual; replaces SetHistoryStrip row','workout/SetHistoryStrip.kt','Height 48dp, min width 56dp, radius 8dp. Label kicker 11sp over value numeralSm 17sp. Row: equal weight, 8dp gap, scrolls if more than 6.','Done: VoltContainer, Volt text, check. Now: 2dp TextPrimary border. Next: Surface2, TextSecondary. Warm-up: label "W1" TextTertiary. Extra set: dashed border. Selected for edit: Volt border. Loading: shimmer. Disabled and error n/a.','1.3: labels only ("Set 1"). 2.0: labels only, row scrolls.','Value "62.5x8" max 7 characters, else show label only.','fun SetPill(state: SetPillState, label: String, value: String?, onClick: () -> Unit, modifier: Modifier = Modifier)'),
('EffortChips','Restyle RpeSelector; keep FocusEffortChoice logic','workout/RpeSelector.kt','Five chips 6 to 10, equal width, 56dp tall, 8dp gap, radius 12dp. Header: "How hard?" + "Last time N" caption + "6 easy · 10 max" caption.','Default Surface2 with 1dp outline. Selected: VoltContainer, 2dp Volt border, bold Volt. Prefilled: dotted Volt outline, counts as chosen on Log. Pressed SurfacePressed. Disabled (warm-up, hold): hidden, caption shown. Error: header "Pick an effort" in Danger.','1.3: chips grow in height. 2.0: two rows (6 7 8 and 9 10).','Numbers only.','fun EffortChips(selected: Int?, prefilled: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, last: Int? = null)'),
('Stepper','Reuse StepperButton (repeats on hold, max 5 per second)','components/StepperButton.kt','Label caption above. Minus 56dp circle, numeral numeralHero 76sp min width 152dp (Metrics.stepperNumeralMinWidth), unit 13sp, plus 56dp circle. Plate line caption under weight.','Pressed: SurfacePressed, Haptics.tick. Long-press repeat with tickLight. Disabled at bound (0 kg external, 1 rep): TextDisabled glyph. Tap numeral: NumberEntryDialog. Error: numeral Danger plus field message.','1.3: numeral scales, label above. 2.0: numeral capped at 96sp, buttons stay 56dp.','Weight max 6 characters. Reps max 3.','fun ValueStepper(label: String, value: String, unit: String?, onMinus: () -> Unit, onPlus: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier, hint: String? = null)'),
('RestRing','Reuse RestSweepRing','components/RestTimerUi.kt','Diameter 240dp (mini 120dp). Stroke 10dp, track Surface3, sweep RestCyan. Centre: kicker "Rest" RestCyan, time numeralXl 56sp, caption "of 2:30".','Running: sweep per tick. Last 3 s: Haptics.warn. Done: ring Volt, text "Go", Haptics.holdDone. Overtime: counts up in Warn. Loading and error n/a.','Ring fixed. Time scales at 1.3. Caption hidden at 2.0.','Time m:ss.','fun RestRing(remainingMs: Long, totalMs: Long, modifier: Modifier = Modifier, size: RingSize = RingSize.Large)'),
('UpNextCard','New','NEW components/UpNextCard.kt','Panel with kicker "Up next · set 2 of 3", target numeralLg 36sp, Edit text button 48dp target, optional reason caption.','Default. Edited: "Edited" caption. Loading: shimmer on target.','Target wraps under kicker at 2.0.','Target max 14 characters.','fun UpNextCard(kicker: String, target: String, onEdit: () -> Unit, modifier: Modifier = Modifier, reason: String? = null)'),
('TempoLine and TempoSheet','Replace TempoCoachCard layout; keep TempoCoachInputs logic and TempoWhySheet','workout/TempoCoachCard.kt','TempoLine: 40dp min, mascot dot 24dp, "Tempo" Volt label, message body 14sp, caption "Already filled in · tap for why", Undo ghost. TempoSheet: mascot 48dp, title heroTitle, old value struck TextTertiary, new value numeralLg, reason body, Keep (secondary) and Use (primary).','Line: applied, not applied, unavailable (hidden). Sheet: default, applying (loading), error toast.','Line wraps up to 3 lines at 1.3, never ellipsis on the target. Sheet scrolls at 2.0.','Message max 48 characters.','fun TempoLine(message: String, applied: Boolean, onWhy: () -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier); fun TempoSheet(tip: TempoCoachTip, onUse: () -> Unit, onKeep: () -> Unit, onDismiss: () -> Unit)'),
('BottomSheet','Reuse sheet helper in Motion.kt (SheetPositionalThreshold 56dp)','theme/Motion.kt','Surface2, top radius 24dp, grab bar 32x4dp, padding 16dp, primary at bottom.','Open in Motion.BASE. Dismiss blocked while saving (canDismiss).','Scrolls.','Title max 32 characters.','Existing helper, no new signature'),
('Toast and Undo','Reuse FloorUndoOffers and UndoFeedback','workout/FloorUndoOffers.kt','Row 56dp above the dock: message body and Undo ghost.','Visible STATUS_DWELL_MS (6 s) scaled by system timeout. Undo: revert, Haptics.tick.','Wraps to 2 lines.','Message max 40 characters.','Existing, no new signature')]
for c in COMP:
    if not c[2].startswith('NEW'):ck(P('ui/'+c[2]))
SCR=[]
def S(id_,name,before,primary,secondary,regions,states,copy,nav_in,nav_out,back,edges,files):
    for f in files:
        if not f.startswith('NEW'):ck(P('ui/'+f))
    SCR.append(dict(id=id_,name=name,before=before,primary=primary,secondary=secondary,regions=regions,states=states,copy=copy,nav_in=nav_in,nav_out=nav_out,back=back,edges=edges,files=files))
S('01_today','Today','flow03_step01_tab-home','Start workout (today_start_cta)','Log cardio, Log past session, tap a lift (opens preview), week chips',
 'Header (date kicker, H1, day subtitle); TodayHeroCard (lift list, time estimate); week chips; dock (primary and 2 secondary); bottom nav',
 dict(empty='No plan: hero shows "No plan yet" and primary "Build your week" (today_build_week_cta).',loading='ScreenLoading skeleton with 1 hero card (components/EmptyState.kt).',error='Inline panel "Could not load today" and Try again (today_error_retry).',first='After onboarding: hero shows plan day 1 with caption "Your first workout" (today_first_caption).',resume='Session in progress: hero becomes "Workout in progress" and primary "Resume workout" (today_resume_cta). LiveSessionBar hidden on Today to avoid a duplicate.',death='State comes from DB flows (HomeViewModel); HomeDayMemory restores the selected day.'),
 [('today_kicker_date','{weekday} {day} {month}'),('today_title','{dayName} today'),('today_title_rest','Rest day'),('today_start_cta','Start workout'),('today_start_sub','{dayName} · {liftCount} lifts'),('today_resume_cta','Resume workout'),('today_in_progress','Workout in progress'),('today_log_cardio','Log cardio'),('today_log_past','Log past session'),('today_build_week_cta','Build your week'),('today_no_plan','No plan yet'),('today_error','Could not load today'),('today_error_retry','Try again'),('today_first_caption','Your first workout'),('today_train_anyway','Train anyway'),('today_estimate','About {minutes} min · {sets} working sets')],
 'App launch, bottom nav, Summary Done','Preview 06, Cardio live 17, Past session 16, Plan week 02','System back exits the app (existing)',
 'Rest day: H1 "Rest day", primary becomes secondary "Train anyway". Cardio-only day: list shows cardio type. Very long routine name: 2 lines then ellipsis. Landscape: hero and dock side by side (HomeBoardLayout).',
 ['home/HomeScreen.kt','home/DailyAgendaCard.kt','home/HomeBoardLayout.kt','home/DayBlock.kt','home/HomeStartSheet.kt','home/HomeViewModel.kt','home/HomeDayMemory.kt','navigation/AppNav.kt','navigation/LiveSessionBar.kt'])
S('02_plan_week','Plan week','flow03_step02_tab-plan','Tap a day (opens Plan day 03)','Edit, Add a training day, Library',
 'Header; 7 day rows (training 72dp, rest 56dp); dock secondary; bottom nav',
 dict(empty='No week: "No week yet" and primary "Build the week" (opens CustomWeekScreen).',loading='Header and skeleton rows. Today trunk renders PlanHeader and ScreenLoading, which matches the blank capture (see R0).',error='Panel with PlanUiState.error and Try again.',first='Suggested week from onboarding with caption "Suggested. Change anything." (plan_suggested_caption).',resume='If a session is live, a row "Workout in progress" links to it.',death='PlanViewModel reloads from DB.'),
 [('plan_title','Your week'),('plan_subtitle','{count} training days'),('plan_edit','Edit'),('plan_add_day','Add a training day'),('plan_empty_title','No week yet'),('plan_empty_cta','Build the week'),('plan_rest','Rest'),('plan_today','today'),('plan_library','Library'),('plan_suggested_caption','Suggested. Change anything.'),('plan_live_row','Workout in progress'),('plan_error_retry','Try again')],
 'Bottom nav, Today "Build your week"','Plan day 03, CustomWeekScreen, Library','Back goes to Today tab (existing tab behaviour)',
 'Missed workouts: MissedWorkCard stays above rows. Block review banner kept. Two routines on one day: row lists both names.',
 ['plan/PlanScreen.kt','plan/PlanViewModel.kt','plan/MissedWorkCard.kt','routines/CustomWeekScreen.kt','components/EmptyState.kt'])
S('03_plan_day','Plan day (routine editor)',None,'Save day (routine_save)','Reorder (D14), open a lift, Add exercise, back',
 'Top bar; lift cards (handle, thumb, name, targets); add row; dock',
 dict(empty='"Add your first lift" panel with Add exercise primary.',loading='Skeleton cards (RoutineEditorUiState.isLoading).',error='saveError inline with Try again; missing routine: existing "Routine missing" panel.',first='Same as empty.',resume='Unsaved edits restored with banner "Resumed unfinished edits" (existing RoutineRecoveryState).',death='SavedStateHandle drafts (existing RoutineRecoveryState).'),
 [('routine_subtitle','{weekday} · {count} lifts'),('routine_add_exercise','Add exercise'),('routine_add_first','Add your first lift'),('routine_save','Save day'),('routine_resumed','Resumed unfinished edits'),('routine_unsaved_title','Unfinished edits'),('routine_unsaved_keep','Keep editing'),('routine_unsaved_discard','Discard changes'),('routine_missing','Routine missing'),('routine_try_again','Try again')],
 'Plan week 02','Add exercise 04, Set targets 05, Plan week 02','Back with edits opens the existing unsaved dialog (RoutineExitOutcome.Unsaved)',
 'Very long lift name: 2 lines then ellipsis. Hold lifts show seconds. Bodyweight shows "BW". Reorder: INFERRED unknown whether routine items store order; D14.',
 ['routines/RoutineEditorScreen.kt','routines/RoutineEditorViewModel.kt','routines/RoutineEditorWrites.kt','routines/RoutineRecoveryState.kt'])
S('04_picker','Add exercise (picker)',None,'Add N exercises (picker_add_n)','Search, filter chips, Create lift, back',
 'Top bar; search; filter chips; Recent and results with check targets; dock',
 dict(empty='No results: "No lifts match", Clear filters, Create lift.',loading='ExercisePickerProgress busy: skeleton rows.',error='Inline error row with Try again (existing PickerViewRecovery).',first='Recent hidden; Suggested for the day shown.',resume='Picked ids restored (RoutineEditorUiState.pickedIds).',death='Existing OwnedPickerRecovery.'),
 [('picker_title','Add exercise'),('picker_search_hint','Search lifts'),('picker_recent','Recent'),('picker_suggested','Suggested'),('picker_add_n','Add {count} exercise (plural: Add {count} exercises)'),('picker_add_sub','Then set targets'),('picker_none','No lifts match'),('picker_clear','Clear filters'),('picker_create','Create lift'),('picker_try_again','Try again')],
 'Plan day 03, live swap (11)','Set targets 05, back','Back keeps the selection draft until confirm (existing)',
 'Multi-select already exists (ExercisePickerSheet multiSelect). Swap mode is single select with swapSiblings pinned.',
 ['components/ExercisePickerSheet.kt','components/OwnedPickerRecovery.kt','components/PickerViewRecovery.kt','library/ExerciseLibraryScreen.kt','routines/RoutineEditorViewModel.kt'])
S('05_targets','Set targets sheet',None,'Save targets (targets_save)','Steppers, presets, swipe down',
 'BottomSheet: lift header; Sets, Reps, Rest steppers; preset chips; note; primary',
 dict(empty='n/a, always prefilled from AddDefaults.',loading='Primary loading while saving.',error='Inline field message (RoutineNumberEntries).',first='Defaults from domain/AddDefaults.kt.',resume='Values kept in SavedStateHandle (NEW key).',death='Restored from SavedStateHandle.'),
 [('targets_subtitle','Set targets'),('targets_sets','Sets'),('targets_reps','Reps'),('targets_seconds','Seconds'),('targets_rest','Rest'),('targets_note','Start weight comes from Tempo after the first session.'),('targets_save','Save targets')],
 'Picker 04, tap lift in Plan day 03','Plan day 03','Back or swipe down discards sheet edits, no confirm',
 'Hold lift: Reps becomes Seconds. Rep ranges: INFERRED, check RoutineNumberEntries before R5.',
 ['routines/RoutineNumberEntries.kt','routines/RoutineEditorScreen.kt','NEW routines/TargetsSheet.kt'])
S('06_preview','Pre-workout preview','flow02_step03_onboarding-accepted-home','Begin with {firstLift} (preview_begin)','Reorder or swap, back',
 'Top bar; numbered lift cards with start weight; status chips; dock',
 dict(empty='Routine with 0 lifts: primary "Add a lift" (preview_add_lift).',loading='Skeleton cards while ProgressionHint loads.',error='Hint unavailable: "Last {weight}" or "First time"; never blocks.',first='"First time" on every lift.',resume='Not shown if a session is live; Today goes to Resume.',death='Stateless; rebuilt from routine.'),
 [('preview_subtitle','{count} lifts · about {minutes} min'),('preview_begin','Begin with {liftName}'),('preview_begin_sub','Warm-up 1 · {target}'),('preview_begin_sub_working','Set 1 · {target}'),('preview_reorder','Reorder or swap'),('preview_first_time','First time'),('preview_last','Last {weight}'),('preview_add_lift','Add a lift')],
 'Today 01, History detail Repeat','Live set 07 or 08','Back to Today; nothing created (session starts on Begin)',
 'NEW screen. Today the start sheet creates the session at once; Begin must call the same start path. D6.',
 ['workout/StartOptionsSheet.kt','workout/StartOptionsViewModel.kt','NEW workout/WorkoutPreviewScreen.kt','navigation/AppNav.kt'])
LIVE_STATES=dict(empty='Session with no lifts: existing empty floor, primary "Add exercise".',loading='SessionLoadState.LOADING: skeleton. MISSING: "Workout not found" and Back. FAILED: Try again (existing).',error='WorkoutSavePhase FAILED or CONFLICT: primary becomes "Retry save" or "Review save" (existing in WorkoutPrimaryAction.kt).',first='First session of a lift: no last values, effort not prefilled, TempoLine hidden if suggestionUnavailable.',resume='Reopen from notification or LiveSessionBar: pendingResumeDraft restores draft; pills show saved sets.',death='FloorPrivateDrafts and FloorUndoOffers checkpoint restore draft and undo stack (existing).')
S('07_live_warmup','Live set, warm-up','flow04_step01_strength-live-entry','Done, warm-up N (live_warmup_done)','Steppers, Skip warm-ups (overflow), set pills',
 'Top bar with progress; SetPill row; stepper panel; caption; dock',LIVE_STATES,
 [('live_subtitle_warmup','Lift {i} of {n} · Warm-up {w} of {wn}'),('live_warmup_caption','Warm-ups skip effort and rest'),('live_warmup_done','Done · warm-up {n}'),('live_next_sub','Next: {target}'),('live_skip_warmups','Skip warm-ups'),('live_not_found','Workout not found')],
 'Preview 06','Next warm-up or Working set 08','Back keeps the workout running (WorkoutExit.Kept); LiveSessionBar shows',
 'Code: warm-ups never start rest (RestTimer.shouldStartAfterLog) and need no effort (SetLogRules.requiresEffort). Auto warm-up generation is unknown; D9.',
 ['workout/ActiveWorkoutScreen.kt','workout/ExerciseHeader.kt','workout/WorkoutHeader.kt','workout/WorkoutPrimaryAction.kt','workout/SetHistoryStrip.kt','workout/WorkoutDock.kt'])
S('08_live_working','Live set, working','flow04_step02_strength-set-1-logged','Log set N (live_log_set)','Steppers, effort chips, TempoLine, set pills, overflow',
 'Top bar; SetPill row; stepper panel (weight, plate line, reps); TempoLine; dock (EffortChips and primary)',LIVE_STATES,
 [('live_subtitle_working','Lift {i} of {n} · Set {s} of {sn}'),('live_effort_label','How hard?'),('live_effort_last','Last time {rpe}'),('live_effort_scale','6 easy · 10 max'),('live_effort_missing','Pick an effort'),('live_log_set','Log set {n}'),('live_log_sub','{weight} × {reps} · effort {rpe} · rest {rest} starts'),('live_log_sub_last','{weight} × {reps} · effort {rpe}'),('live_bw','BW'),('tempo_label','Tempo'),('tempo_applied','Already filled in · tap for why'),('tempo_undo','Undo')],
 'Warm-up 07, Rest 09, Tempo 10, Between lifts 12','Rest 09; What next 11 after the last set','Back keeps the workout running (WorkoutExit.Kept)',
 '0 kg working weight is rejected (SetLogRules ZERO_WORKING_WEIGHT) unless load type allows it. LoadType BODYWEIGHT shows "BW"; BODYWEIGHT_PLUS shows added kg; ASSISTED shows assist kg; STACK uses pin steps. Hold lifts: reps become seconds, effort hidden. Unilateral: no per-side logging found, not designed (ASSUMPTION). Supersets: none in code. Landscape: LandscapeChrome keeps steppers left and dock right.',
 ['workout/ActiveWorkoutScreen.kt','workout/ActiveWorkoutViewModel.kt','workout/WorkoutDock.kt','workout/WeightRepsEditor.kt','workout/RpeSelector.kt','workout/FocusEffortChoice.kt','workout/WorkoutReadiness.kt','workout/WorkoutPrimaryAction.kt','workout/TempoCoachCard.kt','workout/SetHistoryStrip.kt','workout/LandscapeChrome.kt'])
S('09_rest','Rest state','flow04_step03_strength-rest-after-set-1','Start set N now (rest_start_now)','-15, +15, Edit up next, Undo last set',
 'Top bar; RestRing; nudge row; UpNextCard; logged row with Undo; dock (RestCyan primary)',
 dict(empty='n/a.',loading='Ring renders from RestTimerUiState at once; up next shimmers until the hint loads.',error='Existing message "Set saved. Timer or feedback could not update." as toast; screen returns to set entry.',first='Caption "Phone buzzes at 0:00" for the first 3 sessions (D13).',resume='Reopen mid-rest: ring resumes from RestTimerController (existing).',death='Rest end stored in RestTimerController; restored (existing).'),
 [('rest_kicker','Rest'),('rest_of_total','of {total}'),('rest_minus','−15'),('rest_plus','+15'),('rest_up_next','Up next · set {s} of {sn}'),('rest_edit','Edit'),('rest_start_now','Start set {n} now'),('rest_start_sub','Or wait. Phone buzzes at 0:00'),('rest_logged_row','Set {n} · {weight} × {reps} · effort {rpe}'),('rest_go','Go'),('rest_saved_toast','Set saved')],
 'Working set 08 (auto after Log set)','Working set 08','Back keeps running; rest continues in overlay and notification',
 'Overtime counts up in Warn. Rest off in settings: skip state, toast "Set saved". Last working set: no rest unless RestTimer.shouldStartAfterExtra; go to What next 11.',
 ['workout/RestTimerCard.kt','workout/RestCommands.kt','workout/RestTimerViewModel.kt','components/RestTimerUi.kt','workout/ActiveWorkoutScreen.kt','NEW components/UpNextCard.kt'])
S('10_tempo','Tempo suggestion sheet','flow04_step07_strength-set-lift2-1-logged','Use {target} (tempo_use)','Keep current, dismiss',
 'Small rest ring behind; TempoSheet',
 dict(empty='No tip: sheet never opens.',loading='Use button loading while the draft updates.',error='Toast "Could not apply. Values unchanged."',first='Intro line once: "Tempo suggests changes between sets."',resume='Sheet not restored after death; TempoLine shows the tip.',death='As resume.'),
 [('tempo_sheet_kicker','Tempo suggests'),('tempo_use','Use {target}'),('tempo_keep','Keep {current}'),('tempo_apply_failed','Could not apply. Values unchanged.'),('tempo_intro','Tempo suggests changes between sets.')],
 'Rest 09 (auto, rule D5)','Rest 09 then Working set 08','Back or swipe equals Keep',
 'Only opens when the tip changes weight or reps of the next set. Add-a-set tips (AddASetPolicy) appear in What next 11.',
 ['workout/TempoCoachCard.kt','workout/TempoWhySheet.kt','workout/TempoCoachInputs.kt','workout/NextSetRecommendation.kt','workout/TempoCoachMascot.kt','NEW workout/TempoSheet.kt'])
S('11_set_options','What next sheet',None,'Next: {nextLift} (next_lift_cta)','Add another set, Swap, Skip to a lift, Edit a saved set',
 'BottomSheet with 4 action rows and primary',
 dict(empty='Last lift: primary becomes "Finish workout".',loading='Rows disabled while saving.',error='Toast with existing save error.',first='n/a.',resume='After death the sheet is closed; set entry shows "Planned sets complete" and the primary.',death='As resume.'),
 [('next_title','What next?'),('next_add_set','Add another set'),('next_add_set_sub','{target} again'),('next_swap','Swap this lift'),('next_swap_sub','Same muscles: {siblings}'),('next_skip','Skip to a different lift'),('next_skip_sub','Pick from the {count} left'),('next_edit','Edit a saved set'),('next_lift_cta','Next: {liftName}'),('next_finish_cta','Finish workout'),('next_planned_done','Planned sets complete')],
 'Working set 08 after the last planned set','Between lifts 12, picker (swap), set edit','Back closes the sheet',
 'Replaces inline "Add another set", "Timers", "Next exercise" (flow04_step06) and the LiftSwitcherSheet entry.',
 ['workout/WorkoutSetOptionsSheet.kt','workout/LiftSwitcherSheet.kt','workout/WorkoutOverflowMenu.kt','workout/WorkoutPrimaryAction.kt'])
S('12_between','Between lifts','flow04_step06_strength-set-3-logged','Start {nextLift} (between_start)','Choose a different lift',
 'Completion header with stat chips; UpNextCard (emphasis); mini RestRing; dock',
 dict(empty='n/a.',loading='Next lift hint shimmer.',error='Hint unavailable: "Start {weight}" from plan target.',first='Next lift first time: "First time, start light".',resume='Restored as set entry of the next lift.',death='As resume.'),
 [('between_kicker','Lift {i} of {n} complete'),('between_title','{liftName} done'),('between_sets','{count} sets'),('between_volume','{kg} kg'),('between_pr','PR {value}'),('between_up_next','Up next · {i} of {n}'),('between_start','Start {liftName}'),('between_other','Choose a different lift'),('between_first_time','First time, start light')],
 'What next 11','Working set 08 (next lift)','Back returns to the finished lift',
 'PR chip only when PersonalRecordMoment exists. Rest keeps running (D10).',
 ['workout/ActiveWorkoutScreen.kt','workout/WorkoutPrimaryAction.kt','NEW workout/LiftTransitionPanel.kt'])
S('13_finish','Finish confirm',None,'Finish and see summary (finish_cta)','Keep training',
 'BottomSheet: title, counts, per-lift list, primary, ghost',
 dict(empty='0 sets: Destructive "Discard workout" instead (existing showDiscard).',loading='Primary loading while finishing.',error='Inline error and Try again.',first='n/a.',resume='n/a.',death='Sheet closed; nothing lost.'),
 [('finish_title','Finish workout?'),('finish_counts','{done} of {planned} working sets saved · {minutes} min'),('finish_cta','Finish and see summary'),('finish_keep','Keep training'),('finish_discard','Discard workout'),('finish_incomplete','{count} planned sets not done'),('finish_try_again','Try again')],
 'What next 11 (last lift), overflow','Summary 14','Back closes the sheet',
 'Open correction: existing guard must still block (FinishWithOpenCorrectionRenderTest). Incomplete lifts shown with a muted dash.',
 ['workout/WorkoutOverflowMenu.kt','workout/WorkoutPrimaryAction.kt','components/EndWorkoutDialog.kt','NEW workout/FinishSheet.kt'])
S('14_summary','Summary','flow04_step10_strength-finished-summary (black capture)','Done (summary_done)','View in History',
 'Saved kicker and title; 3 StatTiles; PR card; Tempo next-time card; dock',
 dict(empty='hasWork false: existing empty copy.',loading='ScreenLoading (existing); R0 checks whether this is the black capture.',error='failed and missing flags (existing) with Back.',first='"First workout saved" line.',resume='n/a.',death='Reloads from DB by sessionId.'),
 [('summary_kicker','Workout saved'),('summary_sets','sets'),('summary_volume','kg volume'),('summary_prs','PR (plural: PRs)'),('summary_pr_kicker','Personal record'),('summary_tempo_kicker','Tempo for next time'),('summary_done','Done'),('summary_history','View in History'),('summary_first','First workout saved')],
 'Finish 13, Cardio 17','Today 01, History detail 15','Back equals Done (existing BackHandler)',
 'Tempo block only when progression has a change (D11). Cardio summary hides volume.',
 ['summary/WorkoutSummaryScreen.kt','summary/WorkoutSummaryViewModel.kt','components/GymSurfaces.kt'])
S('15_history_detail','History detail','flow03_step04_tab-history','Repeat this workout (history_repeat)','Edit, tap a set',
 'Top bar; lift cards with set rows; caption; dock',
 dict(empty='n/a.',loading='Skeleton.',error='Existing SessionDetail error.',first='n/a.',resume='n/a.',death='Reload.'),
 [('history_detail_subtitle','{date} · {minutes} min'),('history_edit','Edit'),('history_effort_hint','Right side number is effort. Tap a set to edit.'),('history_repeat','Repeat this workout'),('history_warmup_mark','W')],
 'History list','Preview 06 (Repeat), SetEditSheet','Back to History list',
 'Repeat is NEW and needs a start path (INFERRED none exists).',
 ['history/SessionDetailScreen.kt','history/SessionDetailViewModel.kt','history/SetEditSheet.kt','history/HistoryScreen.kt'])
S('16_past_session','Past-session logger','flow06_step02_mixed-composer-filled','Save session (past_save)','When picker, Add set, Add exercise or cardio, overflow Discard',
 'Top bar; When row; Title; lift card with SetTable; add rows; dock',
 dict(empty='Save disabled with sub-label "Add a lift or cardio".',loading='Saving spinner.',error='Field messages per cell; future time refused (existing rule).',first='When defaults to today at noon (existing).',resume='Draft restored (SavedStateComposerDraft).',death='Same.'),
 [('past_title','Log past session'),('past_subtitle','Saved to history, not live'),('past_when','When'),('past_title_hint','Title (optional)'),('past_add_set','Add set'),('past_add_more','Add exercise or cardio'),('past_save','Save session'),('past_save_sub','{lifts} exercise · {sets} sets'),('past_save_empty','Add a lift or cardio'),('past_discard','Discard draft')],
 'Today 01','History detail 15','Back keeps the draft and leaves; Discard only in overflow',
 'Replaces the Earlier / date / Later row with one When field.',
 ['activity/ActivityComposerScreen.kt','activity/ActivityComposerViewModel.kt','activity/SavedStateComposerDraft.kt','components/SetTable.kt'])
S('17_cardio','Cardio live','flow05_step02_cardio-live','Finish {type} (cardio_finish)','Pause (D12), Distance, Effort, overflow Discard',
 'Top bar; elapsed numeral; input tiles; dock (Pause and Finish)',
 dict(empty='Before 60 s: Finish disabled, sub-label "Available after 1 min".',loading='n/a, local clock.',error='Save fails: Try again.',first='Type picker first (existing).',resume='Clock survives leaving (existing LiveCardioViewModel).',death='SavedStateCardioDraft.'),
 [('cardio_elapsed','Elapsed'),('cardio_distance','Distance'),('cardio_effort','Effort'),('cardio_add','Add'),('cardio_pause','Pause'),('cardio_resume','Resume'),('cardio_finish','Finish {type}'),('cardio_finish_wait','Available after 1 min'),('cardio_discard_hint','Discard is in the menu'),('cardio_try_again','Try again')],
 'Today 01','Summary 14','Back opens LeaveCardioDialog (existing)',
 'Pause: INFERRED absent in LiveCardioViewModel; NEW behaviour, D12.',
 ['activity/LiveCardioScreen.kt','activity/LiveCardioViewModel.kt','components/LeaveCardioDialog.kt','activity/SavedStateCardioDraft.kt'])
S('18_live_font13','Live set at font scale 1.3','flow09_step01_strength-live-font-1-3','Log set N (live_log_set)','Same as 08',
 'Same as 08 with reflow rules',LIVE_STATES,[('live_pill_short','Set {n}'),('live_warm_short','W{n}')],
 'Same as 08','Same as 08','Same as 08',
 'Rules: subtitle 1 line; pills show labels only; stepper label above numeral; numerals scale; TempoLine max 3 lines, no ellipsis on the target; dock grows; content above the dock scrolls (LogLoopBringIntoView keeps the stepper visible).',
 ['workout/ActiveWorkoutScreen.kt','workout/LogLoopBringIntoView.kt','theme/LogLoopScale.kt','theme/Type.kt'])
for s in SCR:
    s['edges']=s['edges'].replace('Reorder: INFERRED unknown whether routine items store order; D14.','Reorder: RoutineExerciseEntity.sortOrder already exists and the editor already reorders (ERR_REORDER in RoutineEditorViewModel.kt); no migration.')
DEC=[('D1','Prefill effort from the last working set of this lift?','Yes, shown as a dotted prefill that counts as chosen when Log is tapped. Today rpe is never prefilled (prefill() in ActiveWorkoutViewModel.kt).'),
('D2','Keep effort required for working sets?','Yes. Keep SetLogRules.requiresEffort. D1 removes the friction.'),
('D3','Where does Finish live?','Overflow at all times, and as the primary after the last planned set of the last lift.'),
('D4','Rest primary colour','RestCyan fill with Pit text (11.33:1). Volt is kept for acting on a set.'),
('D5','When does the Tempo sheet open by itself?','Only during rest, and only when the tip changes weight or reps of the next set. Otherwise TempoLine only.'),
('D6','Show the pre-workout preview?','Yes, by default. A setting "Skip preview" is out of scope.'),
('D7','Navigation order and names','Keep the current order; rename Home to Today.'),
('D8','Move copy to strings.xml?','Yes, for every screen a packet touches, using the ids in this blueprint. Today only 19 strings exist.'),
('D9','Generate warm-ups automatically?','No. Show warm-ups only where the plan has them; applyWarmupRamp stays as is.'),
('D10','Does rest keep running between lifts?','Yes; the mini ring shows on Between lifts.'),
('D11','Tempo next-time card on Summary','Only when progression shows a change.'),
('D12','Cardio Pause and Finish','Finish enabled after 60 s. Pause ships only if it needs no data model change; else defer.'),
('D13','First-time rest caption storage','A DataStore counter (settings), shown for the first 3 sessions. No Room change.'),
('D14','Reorder in Plan day','Keep the existing reorder (sortOrder exists); restyle only.')]
RULES=[('Weight prefill','progression.suggestedWeightKg, then planned.targetWeightKg, then last time, then 0.0','Code today: suggested, then planned target, then 0.0 (prefill() near line 1075). Last time is offered as applyLastTimeSet, not as a prefill. Blueprint keeps code order; "last time" is a chip only.'),
('Reps prefill','planned targetReps, else 5; holds use seconds','Matches code (ActiveExerciseDraft reps 5).'),
('Effort prefill','Last working set rpe of this lift in this session, else the last session, else none','NEW (D1). Code leaves rpe null.'),
('Effort required','Working, non-hold sets','Matches SetLogRules.requiresEffort.'),
('Zero weight','Rejected for working sets with an external load','Matches SetLogRules ZERO_WORKING_WEIGHT.'),
('Undo window','STATUS_DWELL_MS 6 s scaled by system accessibility timeout; one offer stack','Matches FloorUndoOffers and UndoFeedback.systemUndoTimeout.'),
('Rest auto-start','After a working set that is not the last target set','Matches RestTimer.shouldStartAfterLog. Extra sets follow shouldStartAfterExtra. Length from RestTimer.resolvePlan.'),
('Tempo timing','TempoLine always when a tip exists; sheet per D5; applying is undoable','Code shows TempoCoachCard inline; sheet is NEW.'),
('Finish validation','Blocked while a correction is open or while saving; 0 sets offers Discard','Matches canFinish, showDiscard and FinishWithOpenCorrectionRenderTest.')]
SM_LIVE=[('Loading','loadState FOUND','Set entry (warm-up or working)','ActiveWorkoutUiState.loadState'),('Loading','MISSING or FAILED','Missing or Failed panel','SessionLoadState'),('Set entry','Log tapped and logCommitReady','Saving','logSet() / WorkoutSavePhase.SAVING'),('Set entry','Log tapped, effort missing','Set entry with "Pick an effort"','effortMissingForCommit'),('Saving','saved, shouldStartAfterLog true','Rest','startRestAfterSet()'),('Saving','saved, last target set','What next sheet','RestTimer.shouldStartAfterLog false'),('Saving','saved, warm-up','Next set entry','isWarmup'),('Saving','error','Set entry with Retry save','WorkoutSavePhase FAILED or CONFLICT, retrySave()'),('Rest','Start now or ring done','Set entry','skipRest()'),('Rest','tip changes next target (D5)','Tempo sheet','TempoCoachTip'),('Tempo sheet','Use','Rest with updated UpNext','applyTempoCoachTip()'),('Tempo sheet','Keep or back','Rest','none'),('Any','Undo in window','Previous state','undoTopOffer()'),('What next','Add another set','Set entry (extra)','shouldStartAfterExtra'),('What next','Next lift','Between lifts','NEW LiftTransitionPanel'),('What next','Finish (last lift)','Finish sheet','canFinish'),('Between lifts','Start','Set entry of next lift','applySelection()'),('Finish sheet','Finish','Summary','finishWorkout(), WorkoutExit.Finished'),('Finish sheet','Discard (0 sets)','Today','discardWorkout(), WorkoutExit.Discarded'),('Any','Back','Leaves screen, session kept','WorkoutExit.Kept')]
SM_PLAN=[('Plan week Loading','loaded','Week list','PlanUiState.isLoading'),('Week list','tap day','Plan day Loading','AppNav route'),('Plan day Loading','hydrated','Editing','RoutineEditorUiState.isLoading, hydrate()'),('Editing','Add exercise','Picker','ExercisePickerSheet'),('Picker','Add N','Targets sheet','pickedIds'),('Targets sheet','Save targets','Editing','stageTargets(), commitTargets()'),('Editing','Save day','Saving','saveAndLeave(), saving'),('Saving','ok','Plan week','onExitHandled()'),('Saving','error','Editing with error','saveError'),('Editing','Back with edits','Unsaved dialog','leave(), unsavedOnBack'),('Unsaved dialog','Discard','Plan week','leaveAnyway()'),('Any','process death','Editing with Resumed banner','RoutineRecoveryState')]
PK=[('R0','Investigate blank Plan and black Summary captures','No product code until cause is known. Read PlanScreen.kt loading branch and WorkoutSummaryScreen.kt; rerun walkthrough harness (PR 557, not in trunk) with longer settle.','ui/plan/PlanScreen.kt, ui/summary/WorkoutSummaryScreen.kt, ui/components/EmptyState.kt','Probably none; a fix packet if a real bug','Single lane','PlanLoadsRenderTest (NEW), SummaryRendersRenderTest (NEW)','flow03_step02_tab-plan, flow04_step10_strength-finished-summary','None','Low. Risk: harness timing, not app','Root cause written; both captures show content'),
('R1','Components and live set','Tokens (RestContainer), PrimaryButton, SetPill, EffortChips, ValueStepper, TempoLine; live set 07, 08, 18; effort prefill D1; strings','ui/theme/Color.kt, ui/components/GymButtons.kt, ui/workout/SetHistoryStrip.kt, ui/workout/RpeSelector.kt, ui/workout/WeightRepsEditor.kt, ui/workout/WorkoutDock.kt, ui/workout/ActiveWorkoutViewModel.kt, ui/workout/ActiveWorkoutScreen.kt, app/src/main/res/values/strings.xml','ui/components/ValueStepper.kt, ui/components/SetPill.kt','Lane A components; Lane B VM prefill. Shared: ActiveWorkoutScreen.kt (Lane A only), strings.xml (append, rebase)','EffortPrefillTest, SetPillRenderTest, EffortChipsRenderTest, LargeTextEntryRenderTest update, DockCommitRenderTest update','flow04_step01..02, flow09_step01_strength-live-font-1-3','raw_dp and named_color must not rise; new tokens in theme','Medium: dock layout tests','All 08 and 18 acceptance items pass; baselines flat'),
('R2','Rest state and Tempo sheet','Rest 09 redesign, UpNextCard, TempoSheet, D4, D5, D13','ui/workout/RestTimerCard.kt, ui/components/RestTimerUi.kt, ui/workout/TempoCoachCard.kt, ui/workout/ActiveWorkoutScreen.kt','ui/components/UpNextCard.kt, ui/workout/TempoSheet.kt','Lane A rest UI; Lane B Tempo sheet. Shared: ActiveWorkoutScreen.kt (Lane A)','FloorCompactRestBarTest update, TempoSheetRenderTest, RestCompletionLayoutInstrumentedTest update','flow04_step03, flow04_step07','motion must not rise (use Motion.kt)','Medium: timer overlay','Rest and Tempo items pass'),
('R3','What next, between lifts, finish and summary','Screens 11 to 14, D3, D10, D11','ui/workout/WorkoutSetOptionsSheet.kt, ui/workout/WorkoutPrimaryAction.kt, ui/workout/WorkoutOverflowMenu.kt, ui/summary/WorkoutSummaryScreen.kt','ui/workout/LiftTransitionPanel.kt, ui/workout/FinishSheet.kt','Lane A sheets; Lane B summary','FinishWithOpenCorrectionRenderTest keep green, WorkoutCompletionJourneyInstrumentedTest update, FinishSheetRenderTest','flow04_step04..06, flow04_step08..10','None','Medium: finish guards','Finish never bypasses guards'),
('R4','Today and preview','Screens 01 and 06, D6, D7','ui/home/HomeScreen.kt, ui/home/DailyAgendaCard.kt, ui/workout/StartOptionsSheet.kt, ui/navigation/AppNav.kt','ui/workout/WorkoutPreviewScreen.kt','Lane A Today; Lane B preview and AppNav.kt','PreviewBeginRenderTest, WorkoutEntryJourneyInstrumentedTest update','flow03_step01, flow02_step03','None','Medium: start path','Begin starts exactly one session'),
('R5','Plan builder, past session, cardio, history','Screens 02 to 05, 15 to 17, D12, D14','ui/plan/PlanScreen.kt, ui/routines/RoutineEditorScreen.kt, ui/activity/ActivityComposerScreen.kt, ui/activity/LiveCardioScreen.kt, ui/history/SessionDetailScreen.kt','ui/routines/TargetsSheet.kt','Lane A plan; Lane B logging, cardio, history','TargetsSheetRenderTest, CardioFinishGateTest','flow03_step02, flow03_step04, flow05_step02, flow06_step02','None','Medium: many screens','All remaining items pass')]
for p in PK:
    for f in p[3].split(', '):
        ck(f if f.startswith('app/') else P(f))
ACC=[]
def A(text,ev,pk):ACC.append(dict(id='A%02d'%(len(ACC)+1),text=text,evidence=ev,packet=pk))
A('Plan tab shows content after load, never blank','screenshot flow03_step02_tab-plan + PlanLoadsRenderTest','R0')
A('Summary screen renders, never black','screenshot flow04_step10_strength-finished-summary + SummaryRendersRenderTest','R0')
A('Primary button is 64dp tall and full width minus 16dp','DockCommitRenderTest','R1')
A('Every tappable is at least 48dp','checker + LargeTextEntryRenderTest','R1')
A('Effort prefilled from the last working set, dotted','EffortPrefillTest','R1')
A('Log stays disabled with "Pick an effort" when no effort and none prefilled','FirstWorkingSetRenderTest','R1')
A('Warm-ups show no effort chips and never start rest','screenshot flow04_step01 + ActiveWorkoutViewModelTest','R1')
A('Font scale 1.3: numerals scale, nothing truncated, Log visible','screenshot flow09_step01_strength-live-font-1-3 + LargeTextEntryRenderTest','R1')
A('Font scale 2.0: effort chips wrap to two rows','LargeTextEntryRenderTest','R1')
A('Set pills show done, now, next, warm-up','SetPillRenderTest','R1')
A('No checker baseline increases','tools/checker-baselines.toml diff','R1-R5')
A('Rest primary uses RestCyan and says Start set N now','screenshot flow04_step03 + FloorCompactRestBarTest','R2')
A('Rest ring done turns Volt with holdDone haptic','RestCompletionLayoutInstrumentedTest','R2')
A('Tempo sheet opens only when next target changes','TempoSheetRenderTest','R2')
A('Using Tempo can be undone within the undo window','FloorPacketGUndoTest','R2')
A('After the last planned set, What next sheet opens','screenshot flow04_step06','R3')
A('Between lifts shows next lift and keeps rest running','screenshot flow04_step06','R3')
A('Finish blocked while a correction is open','FinishWithOpenCorrectionRenderTest','R3')
A('Summary shows sets, volume, PRs; Tempo card only on change','screenshot flow04_step10','R3')
A('Today primary is Start workout with day and lift count','screenshot flow03_step01_tab-home','R4')
A('Preview Begin creates exactly one session','PreviewBeginRenderTest + WorkoutEntryJourneyInstrumentedTest','R4')
A('Plan day save handles back with unsaved edits','RoutineEditor tests','R5')
A('Past session uses one When field','screenshot flow06_step02','R5')
A('Cardio Finish enabled only after 60 s','screenshot flow05_step02 + CardioFinishGateTest','R5')
A('History detail shows warm-ups marked W and effort hint','screenshot flow03_step04','R5')
A('All strings on touched screens come from strings.xml ids','lint grep in review','R1-R5')
for s in SCR:A('%s: all six states implemented as specced'%s['name'],'render test per state for '+s['id'],next(p[0] for p in PK if s['id'][:2] in {'R0':[],'R1':['07','08','18'],'R2':['09','10'],'R3':['11','12','13','14'],'R4':['01','06'],'R5':['02','03','04','05','15','16','17']}[p[0]]))
TR=[('v2 F1 Log disabled with no explanation','Effort prefill plus "Pick an effort"','08','R1'),('v2 F2 Plan tab blank','Investigate','02','R0'),('v2 F3 Summary black','Investigate','14','R0'),('v2 F4 Too many equal buttons in the dock','One primary per screen','07, 08, 09','R1'),('v2 F5 Rest looks like set entry','RestCyan rest state','09','R2'),('v2 F6 Tempo card pushes content','TempoLine and sheet','08, 10','R2'),('v2 F7 Large text truncates','Reflow rules','18','R1'),('v3 F1 Next lift unclear','What next and Between lifts','11, 12','R3'),('v3 F2 Finish hidden','Finish sheet','13','R3'),('v3 F3 No preview','Preview','06','R4'),('v3 F4 Past-session date row','When field','16','R5'),('v3 F5 Cardio finish too early','60 s gate','17','R5'),('v3 F6 Plan builder targets','Targets sheet','05','R5')]
TD=ROOT+'app/src/test/java/com/sinura/personaltrainer/'
import glob
existing={os.path.basename(f)[:-3] for f in glob.glob(ROOT+'app/src/**/*Test.kt',recursive=True)}
def tagtests(t):
    return re.sub(r'\b([A-Z][A-Za-z]+Test)\b',lambda m:m.group(1)+('' if m.group(1) in existing else ' (NEW)'),t)
L=[]
w=L.append
w('# Temper redesign implementation blueprint\n\nDate 2026-10-28. Trunk 2db47c2c ("Raise the debug live version to 130 (#556)", 2026-10-20). Source: v3 redesign (approved). No code was changed.\n\nConventions: paths are relative to app/src/main/java/com/sinura/personaltrainer/ unless they start with app/ or tools/. NEW marks files or tests that do not exist yet. INFERRED marks facts read from code but not run. ASSUMPTION marks guesses. Values like 62.5 kg are example data.\n')
w('## 1. Scope, non-goals, decisions\n\nScope: the 18 v3 screens, shared components, tokens, two state machines, six packets.\n\nNon-goals: TalkBack work; cloud sync (deferred); data model migrations (none needed; any found must be flagged); changes to the signer and Obtainium release flow; supersets; per-side logging; automatic warm-ups.\n\n| Id | Decision | Recommended default |\n|---|---|---|')
for d in DEC:w('| %s | %s | %s |'%d)
w('\n## 2. Design tokens\n\n### Colours\n\n| Token | Hex | Status | Use | Contrast (WCAG AA 4.5 body, 3.0 large) |\n|---|---|---|---|---|')
for c in COL:w('| %s | %s | %s | %s | %s |'%c)
w('\nCode: ui/theme/Color.kt. New token RestContainer goes in Color.kt.\n\n### Type (ui/theme/Type.kt, InstrumentType)\n\n| Token | Font and weight | Size sp | Line height sp | Use |\n|---|---|---|---|---|')
for t in TYPE:w('| %s | %s | %s | %s | %s |'%t)
w('\n### Spacing and sizes (ui/theme/Metrics.kt)\n\n| Token | Value |\n|---|---|')
for s in SPACE:w('| %s | %s |'%s)
w('\n### Radii (ui/theme/Shape.kt)\n\n| Token | Value | Use |\n|---|---|---|')
for r in RAD:w('| %s | %s | %s |'%r)
w('\nElevation: none. Depth comes from Surface1 to Surface3 steps and 1dp outlines (checker shadow baseline 0, defaults_elevation 1).\n\n### Motion (ui/theme/Motion.kt)\n\n| Token | Value | Use |\n|---|---|---|')
for m in MOT:w('| %s | %s | %s |'%m)
w('\n### Haptics (ui/theme/Haptics.kt)\n\n| Event | Haptic | Status |\n|---|---|---|')
for h in HAP:w('| %s | %s | %s |'%h)
w('\nMinimum touch target 48dp everywhere (Metrics.touchMin).\n\n## 3. Components\n')
for c in COMP:
    w('### %s\n\n- Reuse or replace: %s (%s)\n- Anatomy: %s\n- States: %s\n- Font scale: %s\n- Content rules: %s\n- Signature: `%s`\n'%(c[0],c[1],c[2],c[3],c[4],c[5],c[6],c[7]))
w('## 4. Screens\n')
for i,s in enumerate(SCR,1):
    w('### 4.%d %s (%s)\n\n![redline](redlines/%s_redline.png)\n\n- Current capture: %s\n- Primary: %s\n- Secondary: %s\n- Regions: %s\n'%(i,s['name'],s['id'],s['id'],s['before'] or 'none (not captured)',s['primary'],s['secondary'],s['regions']))
    w('| State | Spec |\n|---|---|')
    for k,n in [('empty','Empty'),('loading','Loading'),('error','Error'),('first','First time'),('resume','Resume'),('death','Process death')]:w('| %s | %s |'%(n,s['states'][k]))
    w('\n| String id | Copy |\n|---|---|')
    for a,b in s['copy']:w('| %s | %s |'%(a,b))
    w('\n- In: %s\n- Out: %s\n- Back: %s\n- Edge cases: %s\n- Files: %s\n'%(s['nav_in'],s['nav_out'],s['back'],s['edges'],', '.join(s['files'])))
w('Edge cases for all strength screens: bodyweight shows BW, cardio uses screen 17, no plan uses the Today empty state, 0 kg rejected for external load, unilateral and supersets not supported (non-goal), long names wrap to 2 lines then ellipsis, landscape uses LandscapeChrome.\n')
w('## 5. State machines\n\n![live](sm_live.png)\n\n### Live session (ui/workout/ActiveWorkoutViewModel.kt)\n\n| From | Event | To | Code |\n|---|---|---|---|')
for t in SM_LIVE:w('| %s | %s | %s | %s |'%t)
w('\n![plan](sm_plan.png)\n\n### Plan builder (PlanViewModel.kt, RoutineEditorViewModel.kt)\n\n| From | Event | To | Code |\n|---|---|---|---|')
for t in SM_PLAN:w('| %s | %s | %s | %s |'%t)
w('\n## 6. Behaviour rules\n\n| Rule | Blueprint | Code today |\n|---|---|---|')
for r in RULES:w('| %s | %s | %s |'%r)
w('\n## 7. Packets\n\nEvery packet: no checker baseline increase (tools/checker-baselines.toml), two green hosted gates, walkthrough screenshots refreshed. Walkthrough harness lives in PR 557, which is not in trunk; R0 depends on it.\n')
for p in PK:
    w('### %s %s\n\n- Content: %s\n- Files touched: %s\n- New files: %s\n- Lanes and conflicts: %s\n- Tests: %s\n- Screenshots to update: %s\n- Baselines: %s\n- Risk: %s\n- Done when: %s\n'%(p[0],p[1],p[2],p[3],p[4],p[5],tagtests(p[6]),p[7],p[8],p[9],p[10]))
w('## 8. Acceptance checklist\n\n| Id | Item | Evidence | Packet |\n|---|---|---|---|')
for a in ACC:w('| %s | %s | %s | %s |'%(a['id'],a['text'],tagtests(a['evidence']),a['packet']))
w('\n## 9. Traceability\n\nFinding labels paraphrase the v2 and v3 documents (ASSUMPTION: numbering added here).\n\n| Finding | Fix | Screen | Packet |\n|---|---|---|---|')
for t in TR:w('| %s | %s | %s | %s |'%t)
w('\n## 10. Risks\n\n- R0 cause unknown; Plan blank matches the loading branch (hypothesis), Summary black may be a capture issue.\n- PR 557 harness not in trunk; screenshot names depend on it.\n- ActiveWorkoutScreen.kt is a hotspot for R1 and R2; serialise those edits.\n- Preview (R4) must reuse the single start path or it can create duplicate sessions.\n- Cardio Pause may need storage; D12 defers it if so.\n- Moving strings (D8) touches many files; append-only to strings.xml to limit conflicts.\n')
md='\n'.join(L)
md=md.replace('\u2014',', ').replace('~','about ')
open('blueprint.md','w').write(md)
json.dump(dict(trunk='2db47c2c',items=[dict(a,evidence=tagtests(a['evidence'])) for a in ACC]),open('acceptance.json','w'),indent=1)
print('MISSING',MISSING)
