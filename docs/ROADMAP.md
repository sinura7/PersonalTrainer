# Roadmap

> **Superseded as current law on 24 August 2026.** This file is the historical
> record of how the strength logger was built (audits, game plan, Jobs 1–6).
> The current program is [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md).
> Signed decisions live in [architecture/](architecture/README.md).
>
> Historical “Room v3 won’t” and “no backdated session creation” are
> **superseded** by [ADR-010](architecture/ADR-010-schema-reset-migrations.md)
> and [ADR-007](architecture/ADR-007-activity-model.md).
> `fallbackToDestructiveMigration`, a sixth tab without a new ADR, and an
> LLM-as-author remain forbidden. Settings is a tab
> ([ADR-014](architecture/ADR-014-settings-tab.md)).
>
> Executors verify current decisions in `docs/architecture/`, not by grepping
> `Signed:` in this file.
>
> 26 Sep 2026 — Audit packet R4 (audit X6's follow-ups from R2-6;
> reminders): with nothing live, tapping Start on a plan reminder while a
> workout's summary was showing did nothing, then started the session by
> itself when the summary was closed. The Start reaches Home, and Home
> only acts while it is on screen; switching to Home's tab brought the
> summary back over it. Now the screens left over Home are closed and
> the planned session opens at once. An activity being logged or a
> routine being edited is not closed, since it asks before it is left:
> the Start stays there, says "You are editing", and the reminder stays
> in the shade. Also, a planned day's reminder kept its Snooze, Move and
> Skip while that day's session ran (Move moved the day away under you
> and left a copy for a later day), unless the session was opened from
> the reminder. Now its reminder leaves the shade as soon as the session
> opens, from any start; a reminder due during it is not shown; and
> Snooze, Move or Skip on one still showing do nothing to the day.
> Reminders still ahead are kept, so a day whose session was discarded
> is still reminded of. On the next phone checklist: finish a workout,
> then tap Start on a plan reminder while its summary shows (the planned
> session opens at once); start a planned day from Home while its
> reminder is in the shade (the reminder goes). Visible.
>
> 26 Sep 2026 — Audit packet R3 (audit X6, RM-1 and RM-6; Temper Debug
> only): tapping Update on Temper Debug's banner downloaded the new build
> and brought the app forward, but Android's install sheet never
> appeared, so the phone stayed on the old build. Android answers an
> install from an ordinary app with "the owner must confirm" and the
> sheet to open; that answer went to the main screen, which never read
> it. It now goes to a Temper Debug-only receiver that no other app can
> reach, and the app opens Android's sheet while it is in front: at once,
> or when the owner comes back if they left during the download (Android
> lets an app open a screen only then; if Android closed Temper
> meanwhile, the banner offers Update again). A build Android refuses shows
> "The update didn't finish. Try again.", and cancelling the sheet
> leaves Update to tap again. Each download used to stay in the app's
> cache (up to 96 MB); now one is kept, and none once the install ends
> or the newer build is running. On the next phone checklist: when a
> newer drop is out, tap Update on the banner (Android's install sheet
> opens; install it); once, tap Update, leave the app during the
> download and come back (the sheet opens then, or when the download
> ends: Android may pause a download while the app is away; if the
> banner says the update didn't finish, tap Try again and stay in the
> app). Obtainium is unchanged. Quiet (Temper Debug only).
>
> 26 Sep 2026 — Rest-alarm packet, part 3 (audit X6, RT-4 and RT-5): the
> two rare rest-alarm quirks. The rest timer keeps a bookmark of the
> running rest on disk and sets a wake-up for its end; after Android
> stops the app, the wake-up reads the bookmark to know the rest is
> done. In a few timing windows the wake-up could be set before the
> bookmark was saved, or for a newer rest than the one saved, and a kill
> in that moment could end the rest in silence or announce the rest
> before it. Now the bookmark is always saved first, and the wake-up is
> set only for the rest that was saved. A kill before a bookmark is saved
> still loses that rest; nothing can prevent that. This completes the
> rest-alarm packet. Quiet.
>
> 26 Sep 2026 — Rest-alarm packet, part 2 (audit X6, RT-1): on a phone
> set to the light theme, the rest countdown in the notification shade
> and on the lock screen was near-white text on a white card, unreadable.
> Android draws that card itself, in the phone's theme; the countdown's
> text had one fixed colour, chosen for the app's dark screens. It now
> uses Android's own notification text colours, so it is dark on a light
> card and, from Android 10, light on a dark one, with the same size and
> figures. On the next phone checklist: set the phone to the light theme,
> start a rest, pull down the shade and lock the phone on your usual
> wallpaper (the countdown reads in both; on a Samsung, also with the
> lock screen's see-through cards); then the same in the dark theme.
> Visible.
>
> 26 Sep 2026 — Rest-alarm packet, part 1 (audit X6, RT-2): with
> notifications off, the "Rest alerts" box came back every time a
> workout or the rest page opened, for as long as notifications stayed
> off, and after two refusals its Continue brought up nothing. It said it
> asked once, but it remembered the answer only while that one screen
> was open. Now the answer (Continue, Not now, or closing the box) is kept
> on the phone and the box stays down; the compact "Rest alerts off" row on the workout
> and the rest page is the way back. A restore leaves the answer alone,
> since notification permission belongs to the phone. On the next phone
> checklist: turn Temper Debug's notifications off, open a workout, tap
> Not now, go back and open it again, then open the rest page (no box;
> the row is there). Visible.
>
> 26 Sep 2026 — Packet N1 (a data-loss bug found while preparing W2d-3;
> owner decisions of 25 September, later): a workout reopened while its
> lift was still loading could bring back an older note and, half a
> second later, save it over the newer one; it could also show another
> lift's typed weight on the lift you had just picked. It took a note
> typed on one lift, added to on a second, then a third lift tapped and
> the workout left at once and reopened from Home; or, with no timing at
> all, a lift swapped and the workout then emptied. Now a reopened
> workout restores only the lift you were on, with its own numbers, and
> the notes are kept once for the whole workout, so the newest words come
> back: notes you cleared stay cleared, and words typed while a lift was
> loading, or with no lift chosen, are kept. On the drop 105 phone
> checklist: type a note on your first lift, add to it on a second, tap
> a third and press Back at once, then reopen the workout from Home (the
> whole note shows, and the third lift shows its own numbers); and in a
> one-lift free workout, type a note, swap the lift, add to the note,
> wait a second, remove the lift, press Back and reopen (the whole note
> shows).
>
> 26 Sep 2026 — Whole-app audit packet W2d-2: the workout floor's set
> save (the one set being written, the copies kept of it for when Android
> stops the app, what a failed or refused save says, and the check that
> settles a save whose outcome is unknown) moves out of the workout
> ViewModel into its own small helper. Nothing changes on screen; tests
> written on the old code prove it. Quiet.
>
> 26 Sep 2026 — Packet T3 (a flaky test; owner decisions of 25
> September, later): the Log's notes test that failed once in seven
> runs of its class, and never since, now waits for what it reads. It
> typed before the lift had finished loading, and in the test a copy of
> the notes saved on a database thread could land a moment after the
> last words; on the phone every one of those saves happens on one
> thread, so the newest words always win. New tests hold, on every run,
> what the old one only met by luck: notes typed while a lift is still
> loading, then Back, then a restart after Android stops the app, keep
> the last words. Four more hold the notes' saves where nothing held
> them: a pause saves into a workout with no notes yet, Back leaves the
> last words ready at once, a phone killed while a lift loads brings the
> typed words back over the older ones, and nothing is saved before the
> typing pause. Tests only; nothing changes on the phone. Quiet.
>
> 26 Sep 2026 — Audit packet R2-6 (audit X6, UI-1 on Home and the app
> shell; owner decision of 25 September): tapping a workout reminder while
> you are already in a workout used to switch to Home, which put Home in
> place of a workout you had opened from another tab, and it used the
> reminder up at the tap, although the start was then refused. Now the tap
> first checks what is running. With a workout or cardio live, it leaves
> you where you are and says "You are in a workout: finish or discard it
> first", and a Start's reminder stays in your notifications for later;
> this holds when the tap opens the app, too. A reminder for the session
> you are already in (started early, from Home or Plan) opens that session
> and is cleared. A reminder is marked used, and taken out of the shade,
> only once its Start goes through (the session, or a mixed day's
> composer, opens), including after "Discard and start"; a reminder whose
> session is no longer planned is simply dismissed, and a reminder whose
> planned day cannot be read says so instead of closing the app. Rides along
> with the next drop; phone check: start a free workout, then tap Start on
> a workout reminder (you stay, the app says why, the reminder stays); and
> start today's planned session early, then, when its reminder comes, tap
> Start (that workout opens and the reminder goes).
>
> 25 Sep 2026 — Audit packet R2-5 (audit X6, UI-2 on the workout floor;
> owner decision of 25 September, "Finish blocked or warned"; warned was
> taken): tapping Finish while you are correcting a set you already
> logged used to end the workout and drop the correction without a word.
> On the workout screen, the "End workout?" question now says first that
> a set is open for changes and offers "Back to my change"; Save as is
> still ends the workout with that set as it was saved. The question
> scrolls, so at the largest text or with the phone sideways nothing is
> cut off. The bottom bar's Finish, on other screens, no longer finishes
> while a set is open for changes: it says to open the workout first.
> Rides along with the next drop; phone check: open a logged set, change
> its weight, tap Finish, and read the warning.
>
> 25 Sep 2026 — Audit packet R2-4 (audit X6, DM-1; owner decision of 25
> September): the coach no longer talks in reps about planks, dead
> hangs, wall sits and stretches. A hold is logged in seconds with no
> reps, and the coach counts reps: its Next card offered "1 rep" before
> the first hold and said "Hold 0 reps" after it, and the rest page said
> "Next: 0 reps". For a hold the Next card and the rest page's Next line
> are gone, Apply cannot give it a rep, and a hold is never "ready to
> progress". The rest page's "Last set" reads as the hold's time
> ("30s"), where it said "0 reps". The coach still sets the rest after a
> hold (two minutes, as before) and suggests an effort. Rides along with
> the next drop; phone check: log a plank, and neither the Log nor the
> rest page mentions reps.
>
> 25 Sep 2026 — Audit packet R2-3 (audit X6, AR-2; owner decision of 25
> September): the diagnostics you can send from Settings now start
> before the safety copy the app takes ahead of a database upgrade. A
> copy that fails while being written is now recorded there for as long
> as the app stays open; before, it reached only the developer log,
> which needs a computer to read. A copy skipped because the phone is
> nearly full, or because the database cannot be read, is still not
> recorded, and nothing keeps the record once the app closes. Rides
> along with the next drop; nothing to check on the phone.
>
> 25 Sep 2026 — Audit packet R2-2 (audit X6, UI-12; owner decision of 25
> September): finishing or discarding from the bottom bar no longer
> closes the app when the small write after it fails (the link between
> the workout and its planned day). The summary opens; if the phone
> refuses these writes, the planned day may stay not done. The same
> write after a finish on the workout screen, and after starting a
> planned session from Home, no longer undoes what just happened
> either. Rides along with the next drop; nothing to check on the phone
> (a failing write is not something to make on purpose).
>
> 25 Sep 2026 — Audit packet R2-1 (audit X6, DB-1, AR-1 and UI-17; owner
> decision of 25 September, R2 crash and coach): Home and History no
> longer close the app when a database read fails. Home keeps the numbers
> it last read and goes on updating from everything else; History keeps
> the workouts, says it may be behind, and now offers Retry on that line.
> The past-blocks section keeps what it showed. If a read fails before
> Home has shown anything, Home waits on its spinner until the next visit
> instead of crashing; F4 makes that a read-error state. Rides along with
> the next drop; nothing to check on the phone (a failing database is not
> something to make on purpose).
>
> 25 Sep 2026 — Audit packet R1-5 (audit X6, DB-2 and L-3; owner
> decision of 25 September): a damaged settings file no longer locks the
> app. It is started again from defaults, and it says so: Settings →
> Backup shows a note until dismissed. Settings save again and the front
> door's Retry is no longer stuck. Workouts, the weigh-in history and
> blocks live in the database and are untouched; settings (units, rest,
> reminders, setup answers, the current bodyweight, the Drive account)
> go back to their defaults, automatic backup is switched off, and the
> app may ask its first-launch questions again. While the settings file
> cannot be read for any other reason, the save question no longer
> covers the "Settings unavailable" screen, and neither answering it nor
> the launch can crash the app. This completes R1. Rides along with the
> next drop; nothing to check on the phone (a damaged file is not
> something to make on purpose).
>
> 25 Sep 2026 — Audit packet R1-4 (audit X6, UI-3; owner decision of 25
> September): Settings → Week generator → "Generate a week" asks first,
> and says what it does: a fresh set of routines added to your plan,
> removing nothing, so the routines already on your week keep their days
> (whether it should replace them instead is an owner question). It no
> longer replaces the training block you are in, finished or not, never
> logs an old bodyweight as today's weigh-in, and a second confirm while
> it runs adds nothing; with no block yet, one starts. Rides along with
> the next drop; phone check, from a block's second week on: tap Generate
> a week, Cancel changes nothing; tap again and Generate, and the Plan
> tab's "Week N of 12" has not gone back to week 1.
>
> 25 Sep 2026 — Audit packet R1-3 (audit X6, BK-4; owner decision of 25
> September): the Drive copy after a finished workout no longer stops
> when you leave the summary. It ran inside the summary screen, so
> tapping Done a few seconds after finishing cancelled it without a
> word; the app now owns it and the summary only shows how it is going.
> A phone that signed out of Drive before R1-1 and has finished no
> workout since still has automatic backup on; its next finished workout
> switches it off instead of signing back in by itself. Signing out, or
> switching automatic backup off, now also stops a copy still going, and
> only one Drive backup runs at a time. Rides along with the next drop;
> phone check: with automatic backup on, finish a workout and tap Done
> at once; Settings → Backup then shows a new Last backup.
>
> 25 Sep 2026 — Whole-app audit packet W2d-1: the workout floor's undo
> list (what a delete or a removed lift can take back, in what order,
> for how long, and the copy that survives Android stopping the app)
> moves out of the workout ViewModel into its own small helper.
> Nothing changes on screen; tests written on the old code prove it.
> Quiet.
>
> 25 Sep 2026 — Audit packet R1-2 (audit X6, BK-3; owner decision of 25
> September): the check that stops an empty backup from wiping the phone
> now knows about goals, saved activity templates and the weekly plan. A
> phone holding only those refuses a file with nothing of yours in it; a
> backup holding only those is no longer refused as empty; and the
> restore question counts them on both sides ("This file: … 4 planned
> weekly sessions, … 2 goals"), naming the plan once rather than as days
> and again as plan entries, and leaving out goals and templates when
> there are none. The safety copy taken before a restore is checked
> against the same counts. Rides along with the next drop; nothing to
> check on the phone unless you restore.
>
> 25 Sep 2026 — Audit packet R1-1 (audit X6, BK-1; owner decision of 25
> September): signing out of Google Drive now does what the privacy page
> says. In the same write that forgets the account, it switches automatic
> backup off and forgets the backup password kept on the phone for it.
> Before, the password stayed on the phone and automatic backup carried
> on as if you had never signed out. Now it stays off until you turn it
> on again. With automatic backup on, Sign out now asks first: the
> backups it wrote open only with that password, so the question points
> to Show backup password. Rides along with the next drop; phone check:
> note your backup password, then with automatic backup on tap Sign out
> in Settings → Backup; the question appears; sign out, sign back in, and
> the switch is off. (Signing back in should show no Google consent
> sheet: sign-out has never withdrawn Drive access, a separate fix.)
>
> 25 Sep 2026 — Audit packet T2 (a flaky test; owner decision of 25
> September): the Log's test that failed only on GitHub's machines, about
> one run in fifteen, is fixed at its cause. Its two sets were saved in
> the same millisecond, and then the saved-sets sheet called the first of
> them "Latest" while the Last set cell called the second. The sheet now
> takes the later set, as the cell does, and the test now gives its two
> sets the same time on every run. Nobody taps two sets in one
> millisecond, so nothing changes on the phone. The emulator lane's font
> and keyboard waits go from 10 s to 30 s: each missed once in 79 runs,
> on the same code that passed three other times. Quiet.
>
> 25 Sep 2026 — Audit packet X7, the release lane (audit X6, BR-1, BR-2,
> BR-7; owner decision of 25 September): the signed gym-floor Temper
> builds again. One shrinker rule for slf4j, which Temper Account's
> sign-in library brought in on 21 September; release.yml's SDK step and
> its publish step fixed; and the push gate's `assembleDebug` now builds
> the release too, so it cannot break unseen again, and never signs it.
> The shrunk APK was inspected for what Android and the libraries find by
> name; no shrunk Temper has been launched yet, so the first signed
> release gets a launch on the phone before anyone relies on it. Quiet.
>
> 25 Sep 2026 — Whole-app audit packet W2c (audit C-2): the coach no
> longer redoes its sum on every tap in the Log or every second on the
> rest page; it is asked again only when something it reads changes.
> The lift picker sorts the library only while it is open, and the lift
> it suggests follows edits to that lift. Nothing changes on screen.
> ADR-029, ADR-008. Quiet.
>
> 25 Sep 2026 — Whole-app audit packet W2b-4 (owner decision of 24
> September): the rest page's "Next" line now says exactly what the Log's
> Next card says: after Another set it names the extra set, on a lift's
> first set it keeps last time's effort, and it goes quiet where the Log
> does (after the lift's last set, on a warm-up, while a set is being
> corrected, while a failed save waits). The rest length it plans is
> unchanged. ADR-012. Quiet; on the drop 105 phone checklist.
>
> 24 Sep 2026 — Whole-app audit packet W2b-3 (owner decision of 24
> September): Skip on the lock screen and on the rest page now ends only
> the rest the screen shows, as the notification's Skip already did. A
> tap just as the rest runs out keeps "rest done"; a tap on a screen that
> has not caught up with a newer rest leaves that rest running. The
> Log's own Skip is unchanged. ADR-012. Quiet; on the drop 105 phone
> checklist.
>
> 24 Sep 2026 — Whole-app audit packet W2b-2: the Log's dock and the
> rest page now share one copy of their rest controls (saving a picked
> length, starting a rest by hand, the battery tip, the coach's hint)
> instead of two. Nothing changes on screen; tests written against the
> old code prove it. ADR-012 decision 18 gains the rule the app already
> follows: a length picked on the dock holds until a logged set starts
> a rest or the lift changes. Quiet.
>
> 24 Sep 2026 — Whole-app audit packet X5 (owner decision of 24
> September): the local coverage check now counts the code the JVM tests
> reach through Robolectric. It had been leaving that code out, so the
> timer and workout floors failed on a measuring fault, not on missing
> tests (timer now reads 74.7 %, workout 86.8 %). The floors are re-based
> just under the true numbers. Tooling only; nothing in the app changes.
>
> 24 Sep 2026 — Whole-app audit packet W2a: about 1,600 lines of app
> code that nothing used are gone (the history set sheet's unused compact
> entry, unused components and ViewModel functions, test-only constants,
> the old RPE helper), and four design-token ceilings are lower, each swap
> proven identical. One visible change, by owner decision of 24
> September: with the phone's reduce motion on, the rest-length sheet
> opens in place instead of sliding up. ADR-023. Quiet; rides along with
> the next drop.
>
> 24 Sep 2026 — Whole-app audit packet T1c-2 (tests only): the last
> eleven workout test files stop reading the app's code as text. What they
> pinned is now checked on the screen (the dock's one bright button, the
> options button, the lift's picture and name, the entry wheels and their
> feel, the switcher, session notes, an empty workout, reduced motion), and
> every "never do this" rule is kept. One old rule that had never run now
> does. Quiet.
>
> 24 Sep 2026 — Whole-app audit packet W2b-1d (owner decision of 24
> September): the notification's Skip names the rest its card shows. A tap
> just as the rest runs out keeps "rest done" instead of turning it into a
> skip, and a tap on a card that has not caught up with the next set's
> rest leaves that rest running. ADR-012. Quiet; rides along with the next
> drop.
>
> 24 Sep 2026 — Whole-app audit packet W2b-1c (owner decision of 24
> September): a rest that finishes on one thread can no longer cancel the
> save of the next set's rest starting on another, which left that rest
> counting down with no row and no wakeup, so nothing would end it. A rest
> started as the last one finishes still reaches the shade. ADR-012.
> Quiet; rides along with the next drop.
>
> 24 Sep 2026 — Whole-app audit packet W2b-1b (owner decisions of 24
> September): rare rest-timer glitches. A stop now names the rest it is
> for, so a stop the service reads after the next set's rest has started
> leaves that rest running. While the app that skipped it is still
> running, a skipped rest never says "Rest done", even when its alarm
> fired as Skip was tapped, and a recovery never brings it back. A Skip
> tapped on a card still on screen after its rest finished leaves the rest
> done, not skipped. ADR-012. Quiet; rides along with the next drop.
>
> 24 Sep 2026 — Whole-app audit packet T1c-1 (tests only): nine more
> workout test files stop reading the app's code as text. What they pinned
> is now checked on the screen or in the ViewModel (the undo, the Log
> button's words, the hold bar, the rest page's fit, landscape), and every
> "never do this" rule is kept, now reading both workout packages so
> W2d's planned moves cannot quietly empty them. Quiet.
>
> 23 Sep 2026 — Whole-app audit packet W2b-1: the rest timer's ±15 s and
> its finish can no longer undo each other. They run on different threads
> (the screens and the notification on the main thread, the alarm's finish
> on a background one), and a +15 landing as the rest ended could bring the
> finished rest back, or a finish landing as a +15 went in could wipe the
> extra time just bought. Both now take effect only if nothing changed
> underneath them (compare-and-set). A late ±15 on a finished rest now does
> nothing, so it can no longer make "rest done" look like a skip. ADR-012
> decision 1 now holds across threads. Quiet; rides along with the next
> drop.
>
> 23 Sep 2026 — Whole-app audit packet X2b: before Room migrates `temper.db`,
> the app now copies it — the file with its WAL, byte for byte — into
> `files/pre-migration/temper-v<n>/`, and keeps the newest two. The legacy
> copy covered only the old `personal_trainer.db` and was already done on
> every phone, so until now the next schema bump would have migrated the
> whole history with nothing to roll back to. The copy is written into a
> `.partial` folder and renamed only when whole, so a copy cut short by a
> crash is never kept. When it cannot be taken — disk full, too little room
> left for the migration, an unreadable file — the app still opens and that
> bump migrates without one (owner decision). ADR-010 decision 12. Quiet;
> rides along with the next drop.
>
> 23 Sep 2026 — Whole-app audit packet W1d: large text on the workout floor,
> on the owner's decisions of 23 September. At font 1.6 and above the stats
> row shows Last alone before and after the first working set, so that set no
> longer pushes the entry down (by 166 dp at font 1.6 on a 360 dp phone). Once
> a working set is logged, the lift's Details shows Best set and Volume under
> "Session in progress", in the floor's words: Best is the standing best,
> "Today" only when this session beat it. Side by side, a value too wide for its
> third of the row (`102.5 × 10` at font 1.3) is drawn smaller to fit one line
> instead of wrapping, never below its label's size; a value too long even
> then still wraps and moves the entry (ADR-030 lists when). The − / + on the weight and reps are
> drawn whole at font 1.6 and 2.0; at 2.0 the − had vanished. A weight wider
> than the `888.8` the numeral is sized from (99,999.99 kg at 360 dp, font
> 2.0; 1102.5 lb at 412 dp) steps down and keeps its unit, and the plates
> beneath stay put. The hosted layout test measures that the way the JVM gate
> does (ADR-030 and ADR-027 amended).
>
> 23 Sep 2026 — Whole-app audit packet X4, a clean foundation. A rest length
> picked while the rest page, or a lift on the Log, was still loading was
> overwritten by the coach's (a user's 1:45 became 2:30, and Start ran 2:30);
> both seeds now fill only a length nobody chose for that lift. It surfaced as
> a once-in-a-hundred test timeout, and three new tests force the timing. Weight,
> reps and effort were already safe. The routine editor's Leave anyway threw
> when a save finished at the same instant — its copy of the running saves
> read a size, then an element that had just gone — and the screen stopped
> answering Back, Save and every edit. That was the 30-second
> `RoutineEditorViewModelTest` wedge open since 10 September; the copies are
> now made safely. The hosted layout tests' three
> stale expectations are corrected. The static gate fetches the Kotlin
> compiler when it is missing and fails when it still cannot parse, unless
> `PT_ALLOW_NO_COMPILER=1`. Kotlin's plugin markers come from Maven Central
> only, so a refused request reads as a network error, not a supply-chain
> one; cloud sessions reach Central through Google's mirror of it, checksums
> still checked. The checkers' string stripper handles a string or a char
> literal nested in a template. Quiet; rides along with the next drop.
>
> 23 Sep 2026 — Whole-app audit packet W1c: the weight and reps wells hold
> still when the first working set of a lift is saved. Until that set, the
> stats row shows Last alone, and that cell was one caption line shorter than
> the full row, so the save pushed the entry down 16 dp (the hosted journey's
> 955 → 997 px since #374). The lone cell now keeps the full row's height at
> side-by-side text sizes (ADR-030 amended). A value that wraps, and font 1.6
> and above, still move it; the owner chose on 23 September how W1d fixes both.
> The timer overlay's restore test now types without raising the keyboard, so
> its tap on Set no longer races the keyboard closing.
>
> 23 Sep 2026 — Whole-app audit packet X3: the retired emulator goldens leave
> the hosted instrumented lane. The golden checks, their 232 PNGs and 7
> manifests, `FloorGoldenTest`, `FoundationGoldenTest`, `WorkoutFrozenFrame`,
> `GoldenImageAssert` and `GoldenPageCatalog` are gone. The layout and journey
> tests stay, with every reachability check, and the lane still blocks nothing.
> Test-only: nothing the app does changes, so it rides along with the next
> drop. The owner moved it ahead of W3 on 23 September; W3 keeps the floor's
> render matrix.
>
> 23 Sep 2026 — Whole-app audit packet X1: the docs tell the truth again.
> HANDOFF-NEXT is rewritten (Room v7, sync paused, the audit's packet order),
> CURRENT_STRUCTURE is re-measured, and README and FOUNDATION_PROGRAM no longer
> say sync does not exist. The audit is recorded in
> `docs/design-audit/2026-09-22/AUDIT.md`. ADR-031 makes Temper Account a
> trusted-server lane (not E2EE) that stays paused until a named bar is met and
> the owner says yes; ADR-032 makes JVM renders and JVM migration tests the
> evidence lanes and retires the emulator goldens.
>
> 23 Sep 2026 — Whole-app audit packet Q1: the first-launch chooser is a real
> gate — taps, Back and TalkBack no longer reach Home beneath it, and it scrolls
> so every choice is reachable in landscape and at large text. After Account or
> Drive, the permission prompts wait until you leave Settings instead of landing
> on the sign-in form. Home's headline follows the selected day: "TRAINING
> COMPLETE" for a finished past day, "TRAINED TODAY" only today, and "Back to
> today" beside the date when browsing the week (design audit D01).
>
> 22 Sep 2026 — Whole-app audit packet S0b: the Temper Account pull now updates
> rows in place, so a pulled workout, template or routine keeps its sets, blocks,
> lifts and history link, and a set already on the phone no longer stalls the
> pull. A custom lift deleted on another phone is deleted here too once its
> routine lift goes, and kept (with its muscle credits) only when this phone's
> own history uses it. Sync stays paused until S1.
>
> 22 Sep 2026 — Whole-app audit packet S0a: Temper Account sync is **paused**
> (`AccountSyncGate`). No pass runs, so a pull can no longer replace rows and
> cascade away an activity's sets or a routine's lifts; edits keep queuing for
> when it resumes. In-app account deletion is off until a server-side delete
> exists; Settings → Account says how to ask. Account copy now names what sync
> covers and that live-logged workouts stay on the phone. Release R8 keeps
> `data.sync.**` for Gson.
>
> 22 Sep 2026 — Live floor polish (ADR-030): bodyweight+added lifts at 0 added show **BW** / **No added weight** on the hero, not `0 lb`; **BW + N** when loaded. Loaded lifts unchanged.
>
> 21 Sep 2026 — CoachEngine v1 (ADR-029): evidence seed
> [`docs/coach/evidence-seed.json`](coach/evidence-seed.json), on-device next-set
> suggestions with citation chip on the active strength workout; extends existing
> `Coach.decide` ladder. v2: paper ingestion UI, cardio coach, commercial gate,
> crowd learning.
>
> 21 Sep 2026 — Retired physical TalkBack as a Public Candidate / Play
> rehearsal ship gate; `publicCandidateReady()` is automated evidence only.
>
> 21 Sep 2026 — Temper Account sync Packet 2: custom exercises (with muscle
> credits) and bodyweight weigh-ins replicate when signed in; built-in catalog
> seed stays local. Supabase DDL in `docs/supabase/packet-2-account-sync-ddl.sql`.
> Packet 3 account sync (goals, coach/reminder/display prefs, save posture) ships in draft PR; catalog seed still deferred.
>
> 21 Sep 2026 — Phase 11 step 7: Privacy Policy and Play Data Safety docs
> save posture Packet 1 (first-launch chooser + Settings → How you save + ADR-028);
> updated for Temper Account; in-app account deletion and Settings/About
> privacy links. `debugLiveCode` unchanged until the next authorized drop.
>
> 18 Sep 2026 — Active workout logging screen redesigned to the owner's
> reference ([ADR-027](architecture/ADR-027-workout-logging-redesign.md)):
> progress header, image-led identity, Last/Best/Volume, hero numerals, RPE,
> Next set with Why/Apply, set history chips, rest card, two-line Log set.
> Business rules, timer and persistence untouched; floor goldens owed a
> re-record on `temper-tests-api29`. Live 81 carries it for the phone check:
> `debugLiveCode` 81; suffix from `python3 tools/debug-drop-plan.py`.
>
> 17 Sep 2026 — Live 80: Google Drive backup works again. Every backup
> text ends in a newline, and the upload skipped the CRLF that must precede
> the closing multipart boundary, so Drive refused every upload since Live 60
> ("Missing end boundary in multipart body"). Manual and after-workout
> backups both land now. `debugLiveCode` 80; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 16 Sep 2026 — Live 77: idle rest on the 56 dp instrument bar (duration
> sheet, Time-set in-bar) on `trunk`.
> `debugLiveCode` 77; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 16 Sep 2026 — Idle rest is the same 56 dp instrument bar as running rest
> (dim numeral, empty track, Start). Duration presets, Custom, and
> planned ±15 live in a sheet. Time set is a 48 dp mark in the idle bar.
> Gym-floor `appVersionCode` stays 1. No Obtainium drop in this packet.
>
> 16 Sep 2026 — Live 76: compact gym-floor rest / hold / set instrument bar on
> `trunk`.
> `debugLiveCode` 76; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 16 Sep 2026 — Compact gym-floor rest / hold / set instrument bar on
> `trunk`. One 56 dp countdown-fill row; REST no longer collides with
> the coach line at 360×800. Gym-floor `appVersionCode` stays 1.
>
> 16 Sep 2026 — Live 75: image-led gym floor (112 dp exercise hero) on
> `trunk`.
> `debugLiveCode` 75; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 16 Sep 2026 — Image-led gym floor: 112 dp exercise hero, compact
> Warm-up row, stable timer / context / Log dock. Zero-weight copy
> stays “no weight”. Gym-floor `appVersionCode` stays 1.
>
> 16 Sep 2026 — Live 74: working weight 0 for Walking Lunge and 63 other
> catalog lifts on `trunk`.
> `debugLiveCode` 74; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 16 Sep 2026 — Working weight 0 is a first-class value for bodyweight
> and empty-hands dumbbell lifts (Walking Lunge and the rest of that
> catalog set). Loaded barbell / dumbbell still default as before.
> Gym-floor `appVersionCode` stays 1. Live 74 is the Obtainium drop.
>
> 15 Sep 2026 — Live 73: floor packet H goldens / accessibility final pass
> on `trunk`. Final drop of the workout-entry plan.
> `debugLiveCode` 73; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 15 Sep 2026 — Floor packet H: goldens catalog wiring, TalkBack matrix,
> RPE recommended spoken word, 360/font/reduced-motion/H4 ratchets on
> `trunk`. Program complete pending owner's physical TalkBack + alarm pass.
> Gym-floor `appVersionCode` stays 1.
>
> 15 Sep 2026 — Live 72: floor packet G set-row overflow, Skip for now,
> LIFO undo on `trunk`.
> `debugLiveCode` 72; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 15 Sep 2026 — Floor packet G: set-row overflow, Skip for now, LIFO undo
> with TalkBack dwell and process restore on `trunk`. Next Kotlin is
> Packet H (goldens/a11y evidence only). Live 72 is the Obtainium drop.
>
> 15 Sep 2026 — Live 71: floor packet F receipt, named Next / Finish,
> Coach.decide on `trunk`.
> `debugLiveCode` 71; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 15 Sep 2026 — Floor packet F: receipt, named Next / Finish, Coach.decide
> in the entry, Why + Keep my numbers. Next Kotlin is Packet G. Live 71
> is the Obtainium drop. Gym-floor `appVersionCode` stays 1.
>
> 15 Sep 2026 — Live 70: floor packet E one dock clock, rest presets,
> hold and stopwatch on `trunk`.
> `debugLiveCode` 70; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 15 Sep 2026 — Live 69: floor packet D RPE, warm-up ramp, and ordinals
> on `trunk`.
> `debugLiveCode` 69; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 15 Sep 2026 — Floor packet E: one dock clock, rest presets / ±15 /
> Custom, hold and stopwatch recover from elapsed realtime. Next Kotlin
> is Packet F. Live 70 is the Obtainium drop. Gym-floor
> `appVersionCode` stays 1.
>
> 15 Sep 2026 — Floor packet D: RPE always on working drafts, warm-up
> ramp chips, visible WU / Set n of target / Extra ordinals. Next Kotlin
> is Packet E. Live 69 is the Obtainium drop. Gym-floor
> `appVersionCode` stays 1.
>
> 15 Sep 2026 — Live 68: floor packet C one current lift, switcher, and
> minute telemetry on `trunk`.
> `debugLiveCode` 68; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 15 Sep 2026 — Floor packet C: one current lift, lift switcher, per-lift
> drafts in SavedStateHandle, minute telemetry, notes off the log loop.
> Next Kotlin is Packet D. Live 68 is the Obtainium drop. Gym-floor
> `appVersionCode` stays 1.
>
> 15 Sep 2026 — Live 67: floor packet B gym-floor steppers on `trunk`.
> `debugLiveCode` 67; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 15 Sep 2026 — Floor packet B: gym-floor weight / reps / hold draft are
> plates + tap-to-type. Live wheels stay on Extra/paste, reminder,
> onboarding, and rest length. Next Kotlin is Packet C.
> Live 67 is the Obtainium drop. Gym-floor `appVersionCode` stays 1.
>
> 15 Sep 2026 — Live 66: floor packet A gym-floor entry state on `trunk`.
> `debugLiveCode` 66; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 15 Sep 2026 — Floor packet A: gym-floor entry state (committed wheels,
> lift ready/dirty, re-tap no-op, empty dock, hide Start next, Log haptic
> after save, Finish needs a set). Appearance waits for packet B.
> Live 66 is the Obtainium drop. Gym-floor `appVersionCode` stays 1.
>
> 15 Sep 2026 — Workout-entry experience report is a recommendation
> only (not law): [workout-entry-experience-report.md](workout-entry-experience-report.md).
>
> 15 Sep 2026 — Workout-entry implementation plan (Packets A–H, docs only,
> no drop): [workout-entry-implementation-plan.md](workout-entry-implementation-plan.md).
> Packet B is on `trunk`. Next Kotlin is Packet C.
> Gym-floor `appVersionCode` stays 1.
>
> 15 Sep 2026 — Live 65: floor packet 5 undo host plus progression
> engine (`Coach.decide` ladder) on `trunk`.
> `debugLiveCode` 65; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 15 Sep 2026 — Progression engine packets 0–9: one `Coach.decide()`
> ladder, rep climb on a close hold, stall card (lighter week only),
> prescribed starting rest, extra-set invite, goal as an AddDefaults
> axis, per-kit increments, warm-up ramp, weekly volume as a card.
> Live 65 is the Obtainium drop. Gym-floor `appVersionCode` stays 1.
>
> 15 Sep 2026 — Floor packet 5: one undo host (~6s) for delete set,
> remove lift, and skip day. Finish, discard, and leave still ask.
> Live 65 is the Obtainium drop. Gym-floor `appVersionCode` stays 1.
>
> 15 Sep 2026 — Live 64: floor packet 4 (HOLD / +N / BACK OFF kicker
> with Why; weight, reps/time, RPE, rest glyphs) on `trunk`.
> `debugLiveCode` 64; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 15 Sep 2026 — Floor packet 4: HOLD / +N / BACK OFF kicker sits on the
> Next line with Why still opening the trace. Weight, reps/time, RPE,
> and rest glyphs replace those floor labels. Live 64 is the Obtainium
> drop. Gym-floor `appVersionCode` stays 1.
>
> 15 Sep 2026 — Live 63: floor packet 3 (optional set stopwatch in the
> dock timer slot) on `trunk`. `debugLiveCode` 63; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 15 Sep 2026 — Floor packet 3: optional set stopwatch in the dock
> timer slot (count-up). Unused leaves duration blank; used writes
> seconds beside reps. Does not cancel a pending rest alarm. Does not
> bump live 62. Gym-floor `appVersionCode` stays 1.
>
> 15 Sep 2026 — Live 62: floor packet 2 (instrument strip, timer dock,
> inline rest wheel, one clock two modes) on `trunk`. `debugLiveCode` 62;
> suffix from `python3 tools/debug-drop-plan.py`.
>
> 15 Sep 2026 — Floor packet 2: read-only instrument strip (elapsed ·
> sets · volume · rest/hold state) taps open the timer; LogBar owns the
> timer slot, Next/Another, and the one Volt Log set; rest length is an
> inline SnapValueWheel; one clock (rest down / set up). Does not bump
> live 61. Gym-floor `appVersionCode` stays 1.
>
> 15 Sep 2026 — Live 61: floor packet 1 (RPE fit, standing Next/Another,
> 6s dwell) plus Extra ChatGPT stills on `trunk`. `debugLiveCode` 61;
> suffix from `python3 tools/debug-drop-plan.py`.
>
> 15 Sep 2026 — Floor packet 1: RPE 6–10 fit at 360/font 2.0 with Warm-up
> outside the track; standing Next lift / Another set (no dwell
> auto-advance); status banner dwell ~6s. Does not bump live 60.
> Gym-floor `appVersionCode` stays 1.
>
> 15 Sep 2026 — Extra ChatGPT stills replace the fourteen family
> stand-ins (`ex_floor_woodchop` through `ex_doorway_chest_stretch`).
> Does not bump live 60. Gym-floor `appVersionCode` stays 1.
>
> 14 Sep 2026 — Live 60: floor snap-scroll weight and reps wheels on
> `trunk`. `debugLiveCode` 60; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 14 Sep 2026 — Gym floor weight and reps are snap-scroll wheels
> (plate steps 2.5 kg / 5 lbs, reps by 1, holds in seconds). Does
> not bump live 59. Gym-floor `appVersionCode` stays 1.
>
> 14 Sep 2026 — Live 59: floor card restack (header ⋮, weight then reps)
> and in-app APK download/install on `trunk`. `debugLiveCode` 59; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 14 Sep 2026 — debug-live.yml skips the obsolete SDK `tools` package so
> the live 59 drop can restore the stable signer. Gym-floor
> `appVersionCode` stays 1.
>
> 14 Sep 2026 — GymNoticeBanner keeps modifier as the first optional
> parameter so lintDebug can publish live 59. Gym-floor `appVersionCode`
> stays 1.
>
> 14 Sep 2026 — Floor card: overflow ⋮ on the identity row; compact
> weight row then reps row. Does not bump live 58. Gym-floor
> `appVersionCode` stays 1.
>
> 14 Sep 2026 — Temper Debug downloads the new package in-app and hands it
> to Android's installer. Does not bump live 58. Gym-floor does not install
> GitHub APKs. Obtainium is not a required step.
>
> 14 Sep 2026 — Live 58: compact gym-floor chrome (one current lift,
> one Log set) on `trunk`. `debugLiveCode` 58; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 14 Sep 2026 — Gym floor chrome is compact: one current-lift copy
> (the expanded card), idle rest as a quiet line, Log set as the one
> Volt. Does not bump live 57. Gym-floor `appVersionCode` stays 1.
>
> 14 Sep 2026 — Live 57: Create a routine paste reads `Lower A (strength)**`
> and quotes whole-text fails, plus Home still+name rows from live 56
> on `trunk`. `debugLiveCode` 57; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 14 Sep 2026 — Create a routine paste reads `Lower A (strength)**`
> without a `##` heading (leftover `**` stripped). A blob that is still
> not a workout quotes why (no session name / no numbered lifts). Does
> not bump live 56. Gym-floor `appVersionCode` stays 1.
>
> 14 Sep 2026 — Live 56: Home session cards picture each lift beside its
> name, and Create a routine paste quotes failing lines and fills
> Upper/Lower on `trunk`. `debugLiveCode` 56; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 14 Sep 2026 — Create a routine paste quotes the failing line (unknown
> name, bad dose, two lifts, hold vs reps) and fills Allen's numbered
> Upper A / Lower A / Upper B / Lower B. Does not bump live 55.
> Gym-floor `appVersionCode` stays 1.
>
> 14 Sep 2026 — Home session cards (and the empty-agenda leftover head)
> picture each lift beside its number and name. The 4-up still strip is
> gone. A typical session (Upper A, 7) names every lift; past eight rows
> the rest is `+N`. Title, `7 lifts · about 60 min`, and Start stay.
> Does not bump live 55. Gym-floor `appVersionCode` stays 1.
>
> 14 Sep 2026 — Live 55: Extra forty locked combos after kit pick,
> tab and Settings glyphs, and numbered Home session lifts on
> `trunk`. `debugLiveCode` 55; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 14 Sep 2026 — Extra recommends forty locked combos after Bodyweight
> (none) / Free weights / Machines / Mixed. Catalog v9 adds eight floor
> stretch names. Does not bump live 54. Gym-floor `appVersionCode` stays 1.
>
> 14 Sep 2026 — Bottom tabs and Settings index rows use Allen's glyphs
> (house, calendar, stick figure, list+clock, gear; nine row leftovers).
> Tint on draw. Debug Update / Log / Foundation keep their current marks.
> Does not bump live 54. Gym-floor `appVersionCode` stays 1.
>
> 14 Sep 2026 — Home session cards (and the empty-agenda leftover head) name
> lifts as a numbered list, one pictured lift per line, remainder `+N`
> on its own line. Stills, `7 lifts · about 60 min`, and Start stay.
> Does not bump live 54. Gym-floor `appVersionCode` stays 1.
>
> 13 Sep 2026 — Live 54: Extra asks what equipment is here (None /
> Free weights / Machines / Mixed), then matching warm-up and mobility
> packs with pictures on `trunk`. `debugLiveCode` 54; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 13 Sep 2026 — Extra asks what equipment is here (None / Free weights /
> Machines / Mixed), then shows matching warm-up and mobility packs.
> Settings kit is a suggestion. Does not bump live 53. Gym-floor
> `appVersionCode` stays 1.
>
> 13 Sep 2026 — Live 53: one Start a workout sheet (no Add row) and
> hold set timer (Dead Hang is SETS + TIME; floor Start hold is a
> countdown, then rest) on `trunk`. `debugLiveCode` 53; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 13 Sep 2026 — Hold set timer: Dead Hang and other static holds
> use TIME in the editor and a work countdown on the floor. Rest
> still starts after the set. Temper `v4 → v5` adds hold seconds;
> gym-floor `appVersionCode` stays 1. Does not bump live 52.
>
> 13 Sep 2026 — Home: one **Start a workout** sheet (free / Plan routine /
> cardio pictures / Extra pictures). Add row gone. Plan still adds.
> Does not bump live 52. Gym-floor `appVersionCode` stays 1.
>
> 13 Sep 2026 — Live 52: Extra/cardio pictures, Body stills, Add-lifts
> muscle chips, and paste-to-routine on `trunk`. `debugLiveCode` 52;
> suffix from `python3 tools/debug-drop-plan.py`.
>
> 13 Sep 2026 — Extra and cardio pickers show pictures: horizontal cardio
> cards (Walk, Run / sprints, Ride, Row, Swim, Hike) and a catalog still
> on each warm-up and mobility row. Does not bump live 51.
>
> 13 Sep 2026 — Paste a written workout on Create a routine. Temper
> reads the lifts, sets, reps, rest, timed holds, alternatives, weekly
> layout, and progression. Timed work is not stored as reps. Catalog v8
> adds only the named holds that were missing. Does not bump live 51.
> Gym-floor `appVersionCode` stays 1.
>
> 13 Sep 2026 — Body muscle rows carry a Temper still of that body part.
> Does not bump live 51. Gym-floor `appVersionCode` stays 1.
>
> 13 Sep 2026 — Add lifts: horizontal muscle chips (All plus Body's ten
> groups). Chest hides a back squat; search still works inside the filter.
> Does not bump live 51.
>
> 13 Sep 2026 — Live 51: Temper Debug notices a newer live drop and offers a
> quiet prompt on `trunk`. `debugLiveCode` 51; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 13 Sep 2026 — Temper Debug notices a newer live drop and offers a quiet
> prompt. Does not bump live 50. Gym-floor does not check debug pre-releases.
> Android's install safety gate is unchanged.
>
> 13 Sep 2026 — Live 50: Settings layout polish (icons, three groups,
> Log/Foundation under About) on `trunk`. `debugLiveCode` 50; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 13 Sep 2026 — Settings rows carry Temper marks. Log and Foundation
> sit under About in debug. Week generator is sectioned. Does not bump live 49.
>
> 13 Sep 2026 — Live 49: Settings as an index of focused screens
> on `trunk`. `debugLiveCode` 49; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 13 Sep 2026 — Settings is an index of rows. Week generator and Reminders
> are focused screens under Settings. Reminder time is a scroll wheel with
> AM/PM, not hour chips. Does not bump live 48.
>
> 12 Sep 2026 — Live 48: Home, Plan, and Settings from the 12 Sep phone shots
> on `trunk`. `debugLiveCode` 48; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 12 Sep 2026 — Home / Plan / Settings: Saturday pin shows on Home and Plan
> today; Get started sheet is gone; reminder alarms and the week generator
> live in Settings. Does not bump live 47.
>
> 12 Sep 2026 — Live 47: History survives an Obtainium update of Temper Debug
> on `trunk`. `debugLiveCode` 47; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 12 Sep 2026 — History must survive an Obtainium update of Temper Debug
> (`com.sinura.personaltrainer.debug`). Upgrade proof is on `temper.db`.
> Does not bump live 46. Gym-floor `appVersionCode` stays 1.
>
> 12 Sep 2026 — Live 46: floor phone-check and A-03 on
> `trunk`. `debugLiveCode` 46; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 11 Sep 2026 — Live 45: D-04 and D-08 on
> `trunk`. `debugLiveCode` 45; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 11 Sep 2026 — Live 44: L-05 and D-06 on
> `trunk`. `debugLiveCode` 44; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 11 Sep 2026 — Live 43: W-06 and W-11 on
> `trunk`. `debugLiveCode` 43; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 11 Sep 2026 — Live 42: E-12 and I-01 on
> `trunk`. `debugLiveCode` 42; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 11 Sep 2026 — Live 41: I-04, S-02, and E-04 on
> `trunk`. `debugLiveCode` 41; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 11 Sep 2026 — Live 40: B-02, G-02, G-05/W-02/T-12, and T-16 on
> `trunk`. `debugLiveCode` 40; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 11 Sep 2026 — T-16: first rest names unrestricted battery so the clock
> does not die on Samsung. Inherits live 39; does not bump it.
>
> 11 Sep 2026 — G-05 / W-02 / T-12: idle rest says Not running. A warm-up
> names that rest did not start. Planned duration is a label, not a
> countdown. Inherits live 39; does not bump it.
>
> 11 Sep 2026 — G-02: rest Start/Skip and Log set sit in the lower dock
> during a session. Finish stays in the header. Inherits live 39; does
> not bump it.
>
> 11 Sep 2026 — B-02: first-launch Body is a gym wall, not an empty figure.
> Catalog lifts sit under the silhouette; a muscle with no history opens
> the lifts that train it. No Start Volt. Inherits live 39; does not bump it.
>
> 11 Sep 2026 — Live 39: architecture stack, R18 steps 3–4, and N-01
> (Play complete cue) on `trunk`. `debugLiveCode` 39; suffix from
> `python3 tools/debug-drop-plan.py`.
>
> 11 Sep 2026 — N-01: Settings Rest timer has **Play complete cue**.
> The row calls `RestTimerAlerts.preview`, the same bundled
> `rest_done.ogg` on the alarm stream as 0:00. Sound is forced on for
> the sample so the row is never dead; vibration follows the switch.
> Does not bump 38. Does not start B-02.
>
> 11 Sep 2026 — R18 step four: activity-edit capability split (notes and
> delete on completed activities; set repair and repeat stay refused),
> five use-case extractions (`CompleteTraining`, `RecordsCalculator`,
> `ProtectBackup`/`OpenBackup`, `DraftStore`, live-bar finish through
> the façade), and the seven-row parity table. Inherits live 38; does
> not bump it. Next is not a numbered R18 step 5 — R17 measurement,
> TalkBack, DESIGN_AUDIT.
>
> 11 Sep 2026 — Live 38: architecture stack `#232`–`#238` on `trunk`.
> `debugLiveCode` 38; suffix from `python3 tools/debug-drop-plan.py`.
>
> 10 Sep 2026 — R18 step three: both completed-training detail screens share
> `CompletedTrainingDetailLoad` (load / missing / failed; `retry()`). A
> thrown session read is unavailable, not "no longer on this phone".
> `#226` shipped live 37 as `debug-live-2026-09-10-9`. This packet
> inherits 38 and does not bump it.
>
> 10 Sep 2026 — Live 37: `#202` is on `trunk` (the lift page reads both
> stores). Obtainium still offers 36 until the number rises. This packet
> is that rise. `#225` is on `trunk` (Drive refusal copy) and rides this
> drop. Open `#227` is the preflight gate (tools/docs) and does not.
> Live test 37 (`debugLiveCode` 37); the suffix is
> `python3 tools/debug-drop-plan.py` after merge, not a name typed here.
>
> 10 Sep 2026 — I: a drop is claimed, not assumed. Two packets merging nine
> minutes apart both bumped `debugLiveCode` to 35 and both cut a drop;
> `debug-live-2026-09-10-6` and `-7` therefore carry the same number, which is
> the one thing Obtainium refuses to offer as an update. The `-6` run built
> `#218`, found the release already there, could not attach an asset of the
> same name, said so and went green — a drop that shipped the previous
> packet's APK under this packet's name. Now: the publish step claims the tag
> through `git/refs`, which is atomic and 422s on a name already taken, so a
> race is a red run that publishes nothing rather than a green one that
> publishes the wrong thing; `tools/debug_drop.py` holds the rules, with
> `check-debug-live-code.py` gating the drop (not preflight — after a drop the
> tree equals the tag and every unrelated PR would go red) and
> `debug-drop-plan.py` naming the free suffix and the number to use;
> `test_debug_drop.py` replays the incident. `SETUP.md` and the owner loop
> stop telling sessions to pick either by hand. Live test 36
> (`debugLiveCode` 36), drop `debug-live/2026-09-10-8`.
>
> 10 Sep 2026 — H: the app offers, it does not retype. Choosing an RPE
> filled the entry wells from the recommendation it unlocks, so a load and
> a rep count the lifter had just typed were replaced by numbers they had
> not asked for — the app editing their entry in the act of being told
> about it. Asking for an extra set did the same. Both raise the
> recommendation exactly as before: it sits above Log with its own **Use**,
> and only that tap moves it into the wells (`applyIntentRecToDraft` is
> gone; `applyMicroRec` was always the consented path). Live test 35
> (`debugLiveCode` 35), tag `debug-live-2026-09-10-7` — **not** `-6`, which
> is the other build that also carries 35; see the collision note below.
>
> 10 Sep 2026 — G: the entry wells belong to the next set. Logging one took
> a snapshot of weight and reps at the tap and wrote it back over the wells
> when Room returned, so a load nudged or a rep count typed in the tens of
> milliseconds the write takes was taken back by the log's own tail — the
> owner's "sometimes it resets one or the other". The tail now clears the
> two per-set flags (warm-up, RPE) on the draft as it stands; the row that
> was written keeps the tapped values. Typed reps stop being a delta
> measured against a well that may have moved: `setReps` takes the number.
> Live test 34 (`debugLiveCode` 34), drop `debug-live/2026-09-10-5`.
>
> 10 Sep 2026 — **Two builds ship as version 35.** `#217` and `#218` each
> bumped `debugLiveCode` to 35 from branches cut before the other merged,
> and git resolves that silently because the two edits never touch the same
> line context. The tags disagree with the branch names as a result: tag
> `debug-live-2026-09-10-6` is `#217` (45acbb8) while the *branch* of that
> name points at `#218` (83aeebe), whose tag is `-7`. Both APKs install as
> `1.0.0+debug.35`, so Obtainium will never offer one as an update to the
> other. Not repaired by renaming anything — the fix is forward: drop 36
> (`debug-live-2026-09-10-8`, `#220`) is higher than both and carries both,
> so it supersedes the pair. The phone instruction now names version 36 and
> the tag, never a branch. `#220` had already added the publish-time
> tag-claim guard that refuses a drop whose name is taken; what it does not
> yet catch is two branches choosing the same *code*, which is the case
> here. A version-code claim check against merged tags is the follow-up.
>
> 10 Sep 2026 — ADR-024: the deterministic hosted job may gate `trunk`;
> the emulator may not. ADR-002 §6 refused hosted runners as the test lane
> outright, and every clause of it described a moment that has passed — the
> account had no working runner, runs died before checkout, and a red mark
> genuinely was noise because nothing had run. Today that job runs the same
> three things the local gate runs and was green on nine packets in a row,
> and its two reds were both real. So the refusal keeps one named exception,
> and draws the line at the emulator lane, whose golden passed and failed on
> the same commit the same day. The local gate is unchanged and still comes
> first; a red on the required job is never routed around. The setting
> itself is the owner's to enable. Docs only.
>
> 10 Sep 2026 — The golden comparator gets a rounding allowance, and the
> open question above is answered. One level on one channel is SwiftShader's
> edge coverage, not a change: two runs of the same commit differed by
> seventeen such pixels on the Volt button's corners and the golden passed
> once and failed once. `GoldenImageAssert` now reads a difference of at most
> one level per channel as the same colour, capped at 256 such pixels, and is
> otherwise exact — two levels fail on the first pixel, and a surface nudged
> by one level fails on the budget. Three tests pin it. It is an allowance,
> not a tolerance: F3's recolour, the thing the last golden actually caught,
> fails under it on 6,954 pixels. Neither constant may be raised to make a
> golden pass. Test-only; no app change and no drop.
>
> 10 Sep 2026 — A tick with no preferences yet stays silent. The service
> seeded `tickPreferences` with the defaults — everything on — until
> DataStore's first emission, so a boundary that fell before that read
> landed ticked against the defaults rather than the owner's choice; the
> way to see it is a rest with seconds left when the process is killed,
> the sticky restart posting the next boundary while the container is
> cold. The field is null until the first emission, and a tick that finds
> it null makes no sound and still schedules the next one. Live test 35
> (`debugLiveCode` 35), tag `debug-live-2026-09-10-6`.
>
> 10 Sep 2026 — Day board follow-ups, from the same six-reviewer pass over
> #205: a tappable block reads as a button again (`Role.Button`, which
> `Card(onClick)` does not set and `InstrumentRow` did); Skip and Up / Down
> are drawn inside the block they act on, through a `controls` slot; an
> auxiliary pack's meta line is its own caption, as the confirm already
> shows, not a second estimate; quiet ink follows
> `DailyAgenda.canOpenStart`, so a session skipped on an earlier day reads
> settled; the order line may take two lines at 360 dp. Live test 33
> (`debugLiveCode` 33), drop `debug-live/2026-09-10-4`.
>
> 10 Sep 2026 — Tick follow-ups, from a six-reviewer pass over #206: the
> ticks re-anchor from every running snapshot the service collects, not
> only from the `ACTION_SYNC` that trails the disk write, so a -15 s that
> lands the countdown on five ticks five at once (`RestTick.nextTick`
> counts a boundary at exactly now, with a `ticked` guard against a
> runnable asking for itself). Preflight proves `rest_tick.wav` is byte-
> identical to its generator; the backup threat model names `REST_TICK`
> among the exclusions and a round-trip test shows a restore leaves the
> toggle alone; the Settings caption claims only what the code gives.
> Live test 32 (`debugLiveCode` 32), drop `debug-live/2026-09-10-3`.
>
> 10 Sep 2026 — Lane fixes: the hosted emulator pass is meant to be green.
> Four failures, one pull request each, none skipped or loosened: the
> exact-alarm test read `lastAlarmSchedule` before the IO-scope arm had
> run (it waits, bounded, now — #207); the History pass looked for
> `Records` where the kicker draws `RECORDS` (#208); the workout journey
> asserted the summary's lift breakdown without scrolling to it (#209);
> and the golden's 7,091-pixel diff, printed from the lane as base64
> (#210), so the baseline is re-recorded from the lane's own capture with
> the comparator still exact
> (`docs/foundation-program/evidence/golden-rerecord-2026-09-10.md`).
> The lane is now the reference renderer.
>
> 10 Sep 2026 — Correction to that entry, from the review pass: the
> golden's diff was **not** renderer anti-aliasing. 7,074 of the 7,091
> pixels are the two cards' `PRIMARY` / `SECONDARY` labels, which packet
> F3 (`6787b17`, 3 Sep) recoloured from `#5F6B73` to `#7F8B93` for
> contrast — 3,934 of them exactly that pair, the rest its blends — and
> the golden, committed 2 Sep, was never re-recorded for it. The lane had
> been comparing shipped ink against a stale screenshot for a week. Only
> the last 17 pixels, on the Volt button's corners, are the renderer.
> VISUAL_TESTING now says a colour-token change is a golden change.
>
> **Open, needs the owner:** those 17 pixels are not stable run to run.
> Two runs of the same commit (#212) differed by exactly them, each by one
> level in one channel, and the golden passed once and failed once. With
> an exact comparator the lane cannot reach the ten consecutive green runs
> the CI header sets as the bar for making the job blocking. The options
> are to keep the comparator exact and accept a coin-flip golden, or to
> treat a difference of at most one level per channel as equal under a
> tight cap on how many pixels may differ, documented as a rounding
> allowance rather than a tolerance. Nothing is loosened until it is
> decided.
>
> 10 Sep 2026 — Tick: the last five seconds of rest tick. `RestTick` says
> where the boundaries fall; `RestTimerService` posts one runnable per
> boundary and re-asks on every sync, so a ±15 s moves the ticks with the
> deadline and an old tick is never sounded against a new one (`isDue`).
> Each tick is a click (`res/raw/rest_tick.wav`, written by
> `tools/build-rest-tick.py`, kept loaded in a `SoundPool` on the alarm
> stream) and a 40 ms pulse, under the existing Sound and Vibration
> toggles and a new **Last five seconds** toggle. The toggle is
> device-local — not in the backup document, not restored — like the last
> preset. Reach is the countdown's: process alive, CPU awake; in doze the
> alarm path's completion cue is the whole alert, and the Settings caption
> says so. Closes R-04, T-02, T-05, T-17, N-02 and G-10. Live test 31
> (`debugLiveCode` 31), drop `debug-live/2026-09-10-2`.
>
> 10 Sep 2026 — F: Home's day board. Each of today's sessions is its own
> bordered block — the title, the first four catalog stills, the numbered
> order, `2 lifts · about 13 min` — with Start (leftover: Do it today) in
> Volt ink on the foot. The whole block is the tap, into the same ADR-021
> confirm, under the same test tag; Skip and reorder sit under it as
> before, and Add keeps its own row under Today. Still open and the
> empty-agenda leftover card draw the same head (`DayBlockHead`), so a
> session looks the same on every Home surface. Aux packs needed no new
> plumbing: `AuxiliaryBlocks` already mints them as routines, so their
> stills resolve like any routine's. Done and moved blocks go quiet in
> ink; the stills stay (ADR-022: identity, not state). The words are
> `DayBlockCopy`, pure and tested; the confirm's count line reads the same
> function. No ADR: the behaviour is ADR-021 as it stands, only the
> drawing changed. Live test 30 (`debugLiveCode` 30), drop
> `debug-live/2026-09-10`.
>
> 10 Sep 2026 — Lane: the emulator job says why it failed. Its script is
> `tools/ci-instrumented.sh`, which dumps the device log before the runner
> tears the emulator down (the runner executes each `script:` line as its
> own `sh -c`, so the fallback could not be inline), and a
> `Print instrumented failures` step prints every failure body from the
> JUnit XML — the semantics trees, the golden diff figures, the caught
> exception — into the job log, where they can be read from any network.
> Still non-blocking; the bar for the gate is unchanged. CI only.
>
> 9 Sep 2026 — R18 step two: exercise detail bests and history read
> `CompletedTrainingRepository.observeExerciseSets` (both stores). A
> backdated strength day counts as a PR on that lift, not only in
> Records. `#217`–`#224` are on `trunk`. `#220` shipped live 36 as
> `debug-live-2026-09-10-8`. This packet inherits 36 and does not bump
> it; the lift page needs 37 after this packet lands.
>
> 9 Sep 2026 — F: the multi-add picker writes as it goes. A tap in Add lifts
> puts the lift on the routine (or on the custom week's day) immediately and a
> second tap takes it back out; the numbers are the session's own order, and the
> footer button is **Done**, not Add. Closing the sheet — scrim, back, a stray
> tap — no longer empties a cart the owner built by hand. `LiftCart` keeps the
> in-flight taps (`picked`/`settle`), `planConfirm` and `ExercisePickerEvent.Confirmed`
> are gone. With it: a target typed into a card while that card's previous
> commit is still in Room is no longer dropped by the commit's tail
> (`stagedTargets` is a `ConcurrentHashMap`, removed by compare-and-remove) —
> the lost update that made `stagedTargetsCommitWhenTheyDifferAndRejectZeroSets`
> time out on a two-core runner. No schema change. Live test 29
> (`debugLiveCode` 29), drop `debug-live/2026-09-09-8`.
>
> 9 Sep 2026 — `#175` (Robolectric 4.16.1) and `#180` (AGP 8.9.3)
> closed unmerged. Robolectric stays pinned at `4.16`; 8.9.3 waits for
> aapt2 ledger + checker in one packet. `#173`, `#199` and `#200` are
> on `trunk`. `#201` is the live-29 drop. `#181` does not bump 28.
>
> 9 Sep 2026 — W3: `SettingsViewModelTest`'s four waits go through
> `awaitFirst` now that `#188` has landed; `unbounded_waits` 4 → 0. Every
> ViewModel wait in the suite has a ceiling and names what it last saw.
>
> 9 Sep 2026 — J: `DESIGN_AUDIT` re-read against the code. 42 of the 64
> rows still marked P1 were closed by shipped work (keyed stills on every
> picker, chip and header; the bundled rest cue on the alarm stream; the
> nine-tenths picker with the create row only on no match; equipment on
> the lift; staged targets and persist-on-exit; Room v4 with the catalog
> versioned; the overlay superseded) and now say so with file evidence;
> two are partly closed. What is genuinely open: the last-5-second tick
> (R-04, T-02, T-05, T-17, N-02, G-10), Body's first-launch emptiness
> (B-02), routine-card and recommendation pictures (S-02, B-03, I-01),
> editor target steppers and a load-type control (E-04, E-12), a cue
> preview in Settings (N-01), the battery-restriction copy (T-16), and
> the walkthrough rows (W-02, W-11, G-02, G-05, T-12, I-04) and the
> chip's set progress and rest badge (W-06). Docs only.
>
> 9 Sep 2026 — I: the hosted emulator lane boots the Nexus 5X profile the
> goldens were recorded on (411 dp at 420 dpi, the `temper-tests-api29`
> device); it had been booting a 320 px default. Still red, and now
> honestly so — five of eighty need an emulator in front of someone:
> `FoundationGoldenTest` (0.43% of pixels in `[84,664..858,1321]`, the
> figure region, SwiftShader vs the recording GPU), `ExactAlarmCapability`
> `apiBelow31SchedulesExact` (`FAILED` where API 29 must give `EXACT`),
> `ProductionScreensPass.historyAt360Font2` (`Records` unreachable), and
> both `ActiveWorkoutJourney` journeys (`Top set 202.5 kg × 5`, `Set 1`
> not displayed). The lane stays non-blocking until it is green ten runs
> in a row on `trunk`. No app code; no drop.
>
> 9 Sep 2026 — C: `tools/check-cancellation.py` fails preflight when a
> `catch (Exception)` that can see a suspension has no `CancellationException`
> clause ahead of it. 32 such sites (every ViewModel `launch`, the app's
> start-up imports, two receivers, Drive sign-out, the foundation reset)
> now rethrow cancellation; `cancellation_swallow` 32 → 0. No drop.
>
> 9 Sep 2026 — D: Body says what the figure was built from — a facts line
> under the map ("3 sessions this week · last finished yesterday"), **Show
> this month** in one tap when Day or Week is empty, and the Front / Back
> chips in their own strip under the figure (`DESIGN_AUDIT` B-05 closed).
> Live test 28 (`debugLiveCode` 28), drop `debug-live/2026-09-09-7`.
> `#181` does not bump it.
>
> 9 Sep 2026 — E: Golf cool-down pack (`golf-cooldown`, Mobility: couch
> stretch, incline pigeon, calf stretch, elephant walk, dead bug). After a
> round, where the Golf warm-up is before one. Live test 27
> (`debugLiveCode` 27), drop `debug-live/2026-09-09-6` — the `-5` cut died
> on the `#188` / `#190` compile break that `#196` fixed.
>
> 9 Sep 2026 — `#194` / `#188`: verified backup drop. A finished
> backup can be opened, a silent Drive account switch is refused, and
> the sealed password can be shown. `debugLiveCode` 27.
>
> 9 Sep 2026 — W2: every ViewModel test wait goes through
> `Flow.awaitFirst` (sharedTest `TestWaits.kt`): `withTimeout(FLOW_MS)`
> and, on giving up, the last value the flow showed. 203 sites in 16
> classes; `unbounded_waits` 207 → 4 (`SettingsViewModelTest`, owned by
> `#188`, follows). No app code; no drop.
>
> 9 Sep 2026 — W: `tools/check-unbounded-waits.py` fails preflight when a
> `*ViewModelTest.kt` waits on a ViewModel flow with no `withTimeout`
> around it — the shape that wedged CI twice on 9 Sep. Ratcheted at 207
> (`unbounded_waits`); `test_unbounded_waits.py` is its fixture proof.
> No app code; no drop.
>
> 9 Sep 2026 — `#190`: ErrorSlot in the eight remaining ViewModels
> (StartOptions, ExerciseLibrary, ActivityComposer, CustomWeek,
> Settings, Onboarding, LiveCardio, SessionDetail). History's three
> sites ride `#181`, which owns that file. No drop.
>
> 9 Sep 2026 — `#189`: the 31-minute CI hang was a ViewModel error race
> (every action wrote null into one shared error flow on success), not a
> deadlock. `util/ErrorSlot`: a success clears only its own family, and
> only refusals older than its own start. `tools/hang-watchdog.sh` wraps the
> CI unit-test step and thread-dumps a wedged worker from outside the JVM.
> B2 follows: the same slot in the eight remaining ViewModels
> (`HistoryViewModel` waits for `#181`). No app-visible change; no drop.
> `DESIGN_AUDIT` W-14/W-15 were already closed in code and are marked so.
>
> 8 Sep 2026 — R18 step one: History horizon and block reviews read
> `CompletedTraining` from both stores. A backdated strength day counts
> as a PR in the readout, not only in Records. `#184` (flow waits are
> `TestWaits.FLOW_MS`) is on `trunk`.
>
> 8 Sep 2026 — Dependabot `#174` (coroutines 1.11.0) and `#176`
> (android-all-instrumented 17) closed unmerged. `#178` named
> `kotlinx-coroutines-android` and the J3 API-35 jar on the ignore
> list. Live test 22 is still the phone APK; Obtainium is still 20.
>
> 5 Sep 2026 — Live test 21 (`debugLiveCode` 21) on `trunk`: C–J4
> plus lint publisher. Obtainium attach waits on a hosted runner;
> sideload Temper Debug until a `debug-live-*` pre-release exists.
> Gym-floor Temper unchanged.
>
> 3 Sep 2026 — H1: History horizon names PRs and the lift that moved
> most; calendar opens on this week; Home last session is a signed
> delta versus the previous visit of the same routine.
>
> 3 Sep 2026 — G6: reduced motion finishes the gate; Volt/PrGold/Warn
> collisions stay with a mandatory non-colour channel (ADR-023).
>
> 3 Sep 2026 — G5: Body figure 45% (300–440), legend above the
> silhouette, calendar cells 48 dp, Plan-day Remove confirms in
> danger ink, idle Start rest is Volt.
>
> 1 Sep 2026 — Obtainium live test 17 (`debugLiveCode` 17, tag
> `debug-live-2026-09-01-2`): keyed catalog stills on the phone
> (Library thumbs are the Brokenout WebPs). Pull Obtainium on
> Temper Debug. [ADR-022](architecture/ADR-022-keyed-catalog-stills.md).
>
> 1 Sep 2026 — Keyed catalog stills: one WebP per built-in lift,
> `imageKey` written at catalog v7. Family stills stay the fallback
> for customs. Body unlit/heat unchanged. Gym-station photo
> portfolios wait. [ADR-022](architecture/ADR-022-keyed-catalog-stills.md).
> Live test 17 is the Obtainium drop.
>
> 1 Sep 2026 — Obtainium live test 16 (`debugLiveCode` 16): Home row
> starts the planned session (confirm); filled Volt is Start a workout;
> Add under Today with just-today vs weekly; Skip on Still open; Plan
> routines collapsed; routine editor Save. Gym-station photo portfolios
> wait. [ADR-021](architecture/ADR-021-home-start-and-day-add.md).
>
> 31 Aug 2026 — Obtainium live test 15 (`debugLiveCode` 15, tag
> `debug-live-2026-08-31`): warm-up extras (golf, lower-body,
> upper-body, shoulder), Home Add extra once, untimed day board,
> Up/Down reorder. [ADR-020](architecture/ADR-020-warmup-extras.md).
>
> 31 Aug 2026 — Warm-up extras (golf, lower-body, upper-body, shoulder)
> plus the existing mobility packs. Plan Add session is weekly. Home
> **Add extra** is once for that day. Home and Plan hide session clocks;
> Up / Down rearranges the day's blocks. [ADR-020](architecture/ADR-020-warmup-extras.md).
> Live test 15 is the Obtainium drop.
>
> 30 Aug 2026 — Obtainium live test 14 (`debugLiveCode` 14, tag
> `debug-live-2026-08-30`): Task 2 queue (dead code, StartOccurrence,
> onboarding retry, insights perf, silent-defect tests, build fat,
> small UX) plus the audit/emulator-lane work from live test 13.
>
> 30 Aug 2026 — Small UX: bodyweight wheel unit toggle, cardio catalog
> banner, Move-to-today MOVED ids, previous-week Still open. Live test
> 14 is the Obtainium drop.
>
> 30 Aug 2026 — Debug APK fat: Gson toolchain pins, vendored outlined
> marks, no kotlinx.serialization or material-icons-extended. Live test
> 13 is still the Obtainium drop.
>
> 30 Aug 2026 — Silent-defect tests: mapper wire names, ExerciseRepository,
> reminder receivers/worker. Live test 13 is still the Obtainium drop.
>
> 30 Aug 2026 — Insights perf: one coach-hint query per pass, Body's
> window chip retargets the shared snapshot, PR/summaries stop
> rescanning history on every live set, rest seconds share one clock.
> Live test 13 is still the Obtainium drop (`debugLiveCode` 13).
>
> 29 Aug 2026 — Full-tree adversarial audit: eight independent passes,
> every finding source-verified. Worst: occurrences generated on the
> wrong weekday for non-Monday week starts; REPLACE+CASCADE wiping a
> rule’s history on an hour change; discard deleting finished
> sessions; the plan-row binding marking the wrong day DONE; the
> reminder Start button dead on Android 12+; restore recovery
> silently dropping preferences. All fixed on this branch, with the
> deferred queue and owner actions in
> [FD-full-audit.md](foundation-program/evidence/FD-full-audit.md).
> These fixes shipped as live test 13 (`debugLiveCode` 13, tag
> `debug-live-13`).
>
> 29 Aug 2026 — The empty-agenda leftover card’s Volt (Start this
> session) opens the same session-summary confirm as the agenda card
> before starting — it was the one Home start left that jumped straight
> into the log. No clock line: a slot day is untimed. Live test 13 is
> this drop (`debugLiveCode` 13)
> ([ADR-018](architecture/ADR-018-home-start-confirm.md), amended).
>
> 29 Aug 2026 — A leftover Home session (yesterday’s Friday, a missed
> block) moves onto today when you confirm **Do it today**. Today lists
> **Still open**. Recurrence does not change. Live test 12 is this drop
> (`debugLiveCode` 12)
> ([ADR-019](architecture/ADR-019-move-to-today.md)).
>
> 29 Aug 2026 — Home planned rows and the Volt open a start confirm
> with the session summary. Confirm starts it. The tag prefers a
> workout over Stretch. Live test 11 is this drop (`debugLiveCode` 11)
> ([ADR-018](architecture/ADR-018-home-start-confirm.md)).
>
> 29 Aug 2026 — Home is the day’s board: a selectable week bound to
> occurrences, not leftover Friday-on-Saturday titles. Completion is
> rest / none / some / all on planned blocks. Plan is fill-the-day;
> Add session is the Volt; Tune and header New are gone. Reminder
> prefs live on Settings; hours live on the day. Live test 10 is this
> drop (`debugLiveCode` 10)
> ([ADR-017](architecture/ADR-017-home-week-board.md)).
>
> 29 Aug 2026 — Settings opens on lbs/kg and Regular/Military hours.
> Schedule and coaching are the same store as the questionnaire.
> Weekly weigh-in follows the first training day. Equipment is
> grouped and filters generated weeks. Reminders and session hours
> live on Plan. Goals UI is gone. Home is Start, not a second tab
> bar ([ADR-016](architecture/ADR-016-settings-home-trim.md)).
>
> 29 Aug 2026 — Plan is a schedule workshop. A weekday is a pushed
> page of untimed blocks (workout, cardio, auxiliary). Start is Home.
> No Start Cardio, Swap, or Unpin. Add session is on Plan and the day
> page. Live test 8 is this drop ([ADR-015](architecture/ADR-015-plan-day-blocks.md)).
>
> 29 Aug 2026 — Settings is the fifth tab. The gear is gone from Home
> and Plan. Library and Goals stay pushed.
>
> 29 Aug 2026 — Body is a readout. Day / Week / Month chips wash the
> silhouette from logged sets, reps, and RPE. Empty still shows the
> figure. No Start on this tab. Start lives on Home.
>
> 28 Aug 2026 — A weekday can hold more than two sessions: morning
> cardio, the pinned workout, and a later accessory / Hyper Pro
> session. Plan’s day sheet adds the later row; Home lists the stack.
> One live activity at a time. Finish one, then start the next.
>
> 28 Aug 2026 — Prescribed sets done turns Log into Next. A + after the
> last logged set asks for an extra. Selected RPE retargets reps/weight
> from this session and last time. Rest countdown sits on the lock
> screen (chronometer, not overlay rest on the log).
>
> 29 Aug 2026 — Temper Debug Obtainium drops bump `debugLiveCode`.
> Live test 9 is Settings / Home trim (lbs/kg, Regular/Military,
> weekly weigh-in, Plan reminders, Goals UI gone).
> Same versionCode is why a check for updates can show nothing.
>
> 28 Aug 2026 — Phone lane is Obtainium, not Android Studio. Cursor
> lands on `trunk`. Temper Debug is a GitHub pre-release
> (`debug-live-*`, `PersonalTrainer-*-debug.apk`). Gym-floor Temper
> stays on the signed APK. Do not mix the two Obtainium entries.
>
> 28 Aug 2026 — Builder and live log stack lifts as full-width
> vertical cards. Tap expands in place. Rest floor wraps the remaining
> clock in a countdown ring. Log bar stays linear. Overlay rest and a
> 240 dp ring on the log stay won’ts.
>
> 28 Aug 2026 — Cross-tab layout fit. Instrument stays. Overflow,
> hit-target splits, Volt outside grouped windows, Why in a dialog
> so the log dock does not grow. Not a restyle, not DIRECTION_A.
>
> 28 Aug 2026 — In-set next load is a compact `Next:` line above Log.
> Use fills the draft only. Why is a local `RuleTrace`. The rest floor
> shows the same line with no Use. ProgressionStrip stays session-grain.
> Home `RecommendationEngine` is unchanged.
>
> 28 Aug 2026 — Live rest on the log is a condensed bar. Tap opens
> `session/{id}/rest` with a huge remaining clock. One RestTimerGateway.
> Skip is never Volt. Overlay rest and a 240 dp ring stay won’ts.
>
> 28 Aug 2026 — Hyper Pro is its own kit. Catalog v6 adds the official
> 28-movement laundry list as `EquipmentType.HYPER_PRO`. Questionnaire
> place mixes gym / Hyper Pro / home / bodyweight. Resilience goal
> opens with nordic, reverse hyper, and reverse nordic. Gym-only weeks
> do not assign Hyper Pro lifts.
>
> 27 Aug 2026 — First visit opens Home. Generate a schedule, build a
> week, or start a workout. The questionnaire is a pushed route, not a
> launch gate. Generated weeks size sets/reps from age, goal, and days
> (ACSM / Schoenfeld landmarks), emit a `RuleTrace`, and keep strength
> sessions on compounds.
>
> 27 Aug 2026 — Live testing is Temper Debug beside gym-floor Temper.
> Obtainium watches signed GitHub Releases. Cursor lands on `trunk`.
>
> 27 Aug 2026 — Body tab is the unlit ChatGPT still with a live heat
> wash. This week and Last 30 days stay the two windows. Rest stays
> the photograph. Overlay plates will not pixel-match every still
> seam. Library thumbs stay the posed stills.
>
> 27 Aug 2026 — Library thumbs are the locked 18-still pack (WebP),
> keyed by family. Not a second drawing of those stills. Not 101
> catalog keys. Customs stand on the unlit front/back still. Body
> live heat stays the standing map.
>
> 27 Aug 2026 — Library poses retarget to the locked 18-still bible
> (front/back unlit, front/back demo heat, 14 family poses). Same
> Temper plates on a skeleton. Not a PNG pack. Customs still stand.
>
> 26 Aug 2026 — Library thumbs are the Temper figure posed: the same
> polygonal plates and hairline seams as Body, Heat3 on the working
> plates, kit in the hands. Not a second pictogram, not a PNG pack.
> Customs with no family still stand.
>
> 26 Aug 2026 — Audit leftovers on the further-design vehicle: lighter-week
> and "moved most" captions are not Volt; Tune Done and a met goal are
> status, not the act; a noon-stamped receipt omits duration; rest
> controls have no emphasised fill. One-live confirm shares the start
> lock; History/Goals/insights read activity summaries; named-args
> ignores Compose `path(fill)`; bodyweight backup keeps the ADR-011
> four-tuple. Restore refuses live cardio as well as live strength.
> ADR-005 stays closed. Evidence:
> [AUDIT-hygiene.md](foundation-program/evidence/AUDIT-hygiene.md).
>
> 26 Aug 2026 — Further-design of gym-floor chrome: composer Save dock,
> kind-aware live bar, one Volt on workout/plan/summary, honest Why and
> History totals, reminder quiet hours, rest gold-finish, PR glow, set
> GroupedList, chart PR line, un-nested Home agenda, week-strip two-a-day
> mark. Not a restyle. ADR-005 / ADR-006 stay closed.
>
> 26 Aug 2026 — Studio sync may request `-sources.jar` / javadoc / the
> Gradle `-src.zip`. Those stay trusted artifacts. The checksum ledger
> stays on; do not disable verification to install debug. Host `aapt2`
> for linux / windows / osx is checksummed so a Windows Studio install
> is not a Linux-only ledger miss.
>
> 26 Aug 2026 — Activity composer Save is the one Volt. Type chips speak
> Run / Ride / Walk via CardioCopy, not `RUN`. Weight fields follow
> LocalWeightUnit. Add set / Add cardio are secondary controls. Remove is
> a named Danger control — the row itself does not delete. Live cardio
> Finish is the one Volt and sits above the system navigation inset, same
> job as strength Log. Leave running and Discard are stacked secondary
> controls (Danger ink on Discard), not footnotes; Discard confirms.
> System back opens a leave dialog whose Volt is Leave running. After
> finish, activity-summary Done is pinned with the same inset.
>
> 25 Aug 2026 — Home agenda speaks the session you built. Strength rows
> show the same 1 · 2 · 3 order as Plan and the editor. A two-a-day keeps
> one Volt Start (strength preferred); other planned rows stay tappable.
> The masthead never prints CARDIO DAY · N LIFTS. Editor empty copy says
> Add, matching the picker. Custom-week Add lifts is disabled while Confirm
> writes. Home does not host the start-options sheet — Body, History, and
> Plan still do. The sheet now starts today’s plan itself (same occurrence
> binding Home uses). Plan free opens that sheet. Agenda/Plan rows never
> dump `Planned`. Missed-work Keep the dates is the one Volt. Gym-floor
> errors say lift, not exercise. Active-workout Log and summary Done sit
> above the system navigation inset (three-button / gesture pill).
> Leave-workout Keep and exit is the filled Volt; Stay is a real control;
> Discard stays Danger ink and still confirms.

Derived from the full audit of 19 Aug 2026 ([AUDIT.md](AUDIT.md)). Phases land in order;
each one ends at a gate that must be verified before the next begins.

Status legend: **done** · **next** · *later* · **superseded**

---

## Phase 0 — Foundation · **done**

Config-cache-safe release build, Room schema export wired, adaptive launcher icon,
day/night window background, volume thousands separators, week-start consistency, leave
dialog anatomy.

> **Closed 20 Aug 2026.** `app/schemas/com.sinura.personaltrainer.data.local.TrainerDatabase/1.json`
> was generated by a real build and committed — six entities, `identityHash`
> `6d58ad40d5c03785ab29aaf61157f369`. Phase 4 is no longer gated. Never hand-edit it.

## Phase 1a — Rest timer trust · **done**

`AlarmManager.setAlarmClock` wakeup, persisted timer state with boot rebasing, single
idempotent completion path, silent done-channel so the sound preference is real,
notification-permission recovery banner, ceiling countdown, phantom-timer guard.

## Phase 1b — Data trust · **done**

Transactional snapshot excluding unfinished sessions, `BackupValidator` (rejects malformed,
foreign, dangling-reference and empty-destructive documents), SAF export/import with no
Google dependency, restore blocked during an active workout, pre-restore safety snapshot,
`lastBackup` vs `lastRestore` separation.

## Phase 1c — Coaching trust · **done**

Progression judged on the **top set** of the last finished session (not the last set
logged), planner recovery override no longer downgrades lower-body days, week start
verified as a single source of truth.

## Phase 2 — Lifecycle hygiene · **done**

Consume-once notification intent, explicit `SessionLoadState` so a missing session is
terminal rather than an infinite spinner, workout draft surviving process death via
`SavedStateHandle`, system-back parity with the top-bar exit.

Then the state-graph work the audit called for:

- **A5/A7** — one analytics pipeline (`TrainingInsightsCalculator` +
  `TrainingInsightsSource`) behind Home, Schedule and Progress, computed off the main
  thread. The three copies had drifted: only Progress passed the exercise catalog and
  honoured the week-start preference, so the same history produced a different body map on
  Progress than the weekly plan was built from.
- **A6** — navigation is one-shot state with an explicit ack, not a lambda captured into a
  coroutine that outlives its `NavController`.
- **A8** — session notes debounce to a typing pause and write in order. They were one
  unordered write per keystroke, so a shorter earlier string could land after a longer
  later one.
- **A9** — the routine editor's four load flags became one tested `RoutineEditorLoad`.
- **A3** — prefill left the `uiState` chain, which it was both an output of and an input
  to; actions read hot `StateFlow`s instead of a `WhileSubscribed` projection.

> **A1 closed 22 Aug 2026.** ViewModels take `AppDependencies` in the constructor.
> Production still resolves the graph from `AppContainer` via `@JvmOverloads` so the
> default `viewModel()` factory is unchanged. Tests construct a ViewModel with a fake
> graph instead of reaching through `Application`.

---

## Phase 3 — The Mirror · **done**

The app recorded years of sets and showed almost none of it back.

- **Exercise detail screen** — lifetime totals, standing records, weekly volume and
  estimated-1RM trends, every session the lift appears in. Reached from the library or from
  an exercise block in a past session.
- **Personal records** — weight, reps-at-weight and estimated 1RM, computed from history
  rather than stored. Announced in-workout as a dismissible banner, never a dialog.
- **Workout finish summary** — replaces the silent pop back to Home.
- **Previous-session values** — every working set of the last session for the current lift,
  above the steppers.
- **Training calendar** — the month at a glance, shaded by how hard each day was relative
  to that month's own hardest day.

Not built: **editable finished sessions**. Editing history means relaxing the guards that
protect finished sessions from writes, and that deserves its own change rather than being
folded in at the end of a feature phase.

Everything here is a read over the existing schema, so none of it waited on the v1 baseline.

## Phase 4 — Schema v2 · **done** (superseded; game-plan Phase 3 shipped it)

> Superseded 20 Aug 2026. The monolithic Phase 4 bundled the migration with behaviour
> changes this roadmap itself said deserved their own change (see the Phase 3 note above on
> editable sessions). It is split and re-ordered value-first across the game plan below: the
> migration core is game-plan Phase 3; the test substrate and session hygiene ship first
> (Phases 2 then 1); the catalog and imagery ship last (Phases 7/8). Execution rules:
> `docs/archive/gameplan/PROTOCOL.md`.

## Phase 5 — The physical product · **done, verified on device**

The visual redesign. Full critique and the reasoning behind every decision are in
`docs/UI_REDESIGN.md`; the design language itself is `docs/ui-redesign/DIRECTION_B_INSTRUMENT.md`.

- **One dark theme, mapped completely.** The half-mapped scheme let Material's baseline
  neutrals through into the nav bar, every dialog, every sheet and every default card —
  invisible in review, unmissable on screen. `surfaceTint` is now transparent, so the
  elevation overlay can never tint a surface with the accent again.
- **Two bundled faces with real tabular figures**, replacing the system monospace that was
  carrying the largest numerals in the product. `tools/build-fonts.py` instances and subsets
  them and asserts the tabular widths rather than trusting them.
- **Token layer** — colour, type, shape, spacing, motion and haptics — enforced by
  `tools/check-design-tokens.py` so it cannot fragment again.
- **The workout screen staged around resting and lifting**: the rest clock pinned outside
  the scroll, live session telemetry in the header, entry compacted to one panel, state
  drawn rather than narrated.
- **Haptics on the logging loop**, including press-and-hold repeat with a detent per step.
- **One intensity ramp**, colourblind-safe, shared by the body map, the calendar and Home.
- **Charts split by kind** — bars for additive volume, a focused-domain line for levels, so
  an estimated 1RM can finally show a few percent of progress.

Built, installed and used on a real phone on 20 Aug 2026 — the first time any of this code
was compiled, which had been the single largest caveat on it. Two defects surfaced that no
static check here had caught, both since fixed: a missing `Surface1` import in
`ExerciseDetailScreen`, and the routine editor discarding an unsaved rename on back.
`tools/check-missing-imports.py` was written to close the first class permanently.

Not done here: **exercise imagery** and the equipment field it needs, which belong with the
Phase 4 schema change; **rest-as-instrument sound design**; the **plate calculator**; and a
full **accessibility pass at font scale 2.0**.

*Amended 21 Aug:* the equipment field shipped in Phase 3 and the imagery in Phase 8 — composed
in Compose rather than commissioned, so the line-art budget was never spent. The other three
are still not done and are still unowned by any phase.

## Phase 6 — Platform *later*

Glance rest-timer widget, Health Connect, bodyweight log, CSV import from Strong/Hevy.
Recorded decisions **not** to build: overlay bubble, Wear OS app.

## The game plan — 20 Aug 2026

Phase numbers below are game-plan numbers, independent of the historical phases above.
Full packets live in `docs/archive/gameplan/`; the execution protocol is
`docs/archive/gameplan/PROTOCOL.md`. Phases land in order; each closes only on owner sign-off.

**Phase numbers are identifiers, not sequence** — the execution order is 0, 2, 1, 3, 4, 5,
6a, 6b, 7, 8 (ten PRs), and the table is in that order.

| Order | Phase | One line | Executor-days | Owner-days |
|---|---|---|---|---|
| 1st | **0 — Decisions & doctrine** | Docs only: ROADMAP/DESIGN_AUDIT restructure, IA adjudication, schedule-semantics sign-off, surface map, cut list, branch ground truth. **BLOCKING checkpoint.** | 0.5–1 | 0.5–1 |
| 2nd | **2 — Test substrate** | androidTest scaffold + deps, Robolectric JVM lane hosting MigrationTestHelper, smoke migration test vs `schemas/1.json`, `tools/preflight.sh`, connectedAndroidTest runbook. | 1–2 | 0.5 |
| 3rd | **1 — Session hygiene (packets 1A + 1B)** | One branch `claude/phase-1-session-hygiene`, one PR, one owner evening. **1A:** FinishWorkout/DiscardWorkout use cases, LiveSessionBar, zero-set discard-only policy, 4-hour stale nudge (in-app), RestRemainingStrip deleted, one-live-affordance gate. **1B (on top of a green 1A gate):** editable finished sessions (completedAt preserved), guarded session delete, repeat-last-session, delete-set undo. | 5–7 | 1–1.5 |
| 4th | **3 — Schema v2 migration** | ONE additive migration (equipment/loadType/movementKey/imageKey/nameKey, exercise_muscles, seed_meta, schedule_slots from D2), versioned seeding behind the maintenance mutex, batch-1 catalog (the 37, keyed on the Phase 7 family vocabulary from the start) + review artifact, Backup v2 + round trip, pre-open raw DB copy, rehearsal runbook, junction-first heat. | 3–5 | 1–2 |
| 5th | **4 — Plan tab & the pinned week** | Reconciliation rules as pure Kotlin first (from D2), ScheduleRepository owns the persisted week (sixth insights source), planner demoted to proposing fills, `insights.weekPlan` rewired, Plan tab built inside whichever tab bar D1 settles on, pushed ScheduleScreen deleted, ThisWeekHomeCard extracted, planner fixes. | 4–6 | 0.5–1 |
| 6th | **5 — Honest heat & coach** | Absolute weekly-set bands, windows → THIS_WEEK + LAST_30_DAYS, coach on trailing 14 days, RPE, imbalance by weighted sets, per surface map. | 4–5 | 0.5–1 |
| 7th | **6a — Tab consolidation** ✅ 21 Aug | Ran **Branch A** per signed D1: four tabs (Home · Body · Plan · History), Library demoted to a pushed route with a back arrow, `isTabRoute` and the `restoreState = false` hacks deleted. The history work landed in History in place — month grouping with pinned headers, a sheet for multi-session days, a Records section. Body was not restructured. | 4–5 | 1–1.5 |
| 8th | **6b — Home "Today" rework** ✅ 21 Aug | Masthead states today rather than the app's name (`MastheadCopy`, pure and tested). The week strip is now one composable shared with Plan, not a Home-only copy. ONE next-session module: the balance card, the heat tile and the recent-activity list are gone. The StartWorkout interstitial is deleted — `StartOptionsSheet` is a modal every entry point opens, so the live bar stays the only live-session affordance. | 2–3 | 0.5–1 |
| 9th | **7 — Catalog to ~98 + Library UX** ✅ 21 Aug | Catalog at **98** built-ins across two seed bumps (v3, v4), every per-bucket count matching plan. Library groups into 39 movement families with equipment chips; the muscle filter reads junction credits, so it finally includes secondary-credit lifts, and the route carries a canonical enum rather than display text. Collisions surface as rename-or-keep-both — no merge tool, no FK rewriting. One increment table replaced three disagreeing ones: the "+5.5 lbs" defect is dead and bodyweight lifts are told to add a rep. Per-loadType add defaults replaced the universal 3×5/90s. Swap-equipment in both the routine editor and the live session. Bodyweight leftover is closed: Settings log + `SetWork` split (reps vs added kg), not a 40 kg fiction. | 4–5 | staged review |
| 10th | **8 — Imagery** ✅ 21 Aug *(was optional; run anyway)* | Compose-drawn thumbnails: a body figure with the trained muscles lit from a **fixed** Heat3 (identity, not the owner's live band) plus an equipment badge, on picker rows, library rows, the 56dp detail header and — glyph only — the in-workout chips. The anatomy moved to `ui/components/FigureArt.kt` as a verified pure move; all 233 coordinates are character-identical, so the Body tab is untouched. Zero assets, zero `res/` additions. **Outstanding, owner-side: the release-APK measurement and the glyph verdicts.** | 2–4 | 0.5–1 |

| 11th | **9 — The guided setup** ✅ 21 Aug *(not in the original plan)* | Came out of a UX audit of the finished ten phases, which found every screen assumed a lifter who already had routines and a pinned week — and a fresh install had neither. Six questions, one per screen, then a preview of the real week with the real lifts, then one button. The split is derived, never asked. Nothing is written until "Use this plan", and re-running it from Settings deletes nothing. Also closed four defects on the cold-start path, including a **compile break that had been on the branch for three phases**. | 2–3 | 0.5 |

**A1 (the DI seam) landed 22 Aug 2026** as the opportunistic refactor this note always
was — not a phase. ViewModels are constructor-injected with `AppDependencies`.
`WorkoutRepository` and `ScheduleRepository` now have `androidTest` coverage on real
SQLite. ViewModel and screen instrumented tests are still unscheduled.

**UX page pass (22 Aug 2026).** The screens already exist. The work was making each
one tell the truth and offer one act. Living plan: [UX_PAGE_PASS.md](UX_PAGE_PASS.md).

**Job 2 · code-done on `trunk` (22 Aug 2026).** Daily logging is mature. No
workout in mind → short path → the app builds the week. P0–P4 are on `trunk`
(shared-structure figure, emphasis, Athletic, preview copy, later fills /
Suggest honours emphasis). Phone gates remain the owner's.
[JOB2_ACTION_PLAN.md](archive/jobs/JOB2_ACTION_PLAN.md). P5 sex waits for a real sentence.
P6 catalog is won't — families already exist.

**Job 3 · code-done on `trunk` (22 Aug 2026).** Replay stored answers
without creating routines; lighter-week marker (HOLD, not scaled sets);
Home / Plan / setup ViewModel JVM tests; debug is
`com.sinura.personaltrainer.debug`. Phone gates remain the owner's
(unpin+replay, Tune+HOLD, two icons). Packets, gates, won'ts:
[JOB3_ACTION_PLAN.md](archive/jobs/JOB3_ACTION_PLAN.md).

**Job 4 · code-done on `trunk` (22 Aug 2026).** Deload card marks this
week; Home replay when routines exist; Settings / History / Progress
JVM contracts; Backup caption and Finish helper tell the truth before
the tap. Phone gates remain the owner's (card+HOLD, Home replay,
Backup/Finish copy). Packets, gates, won'ts:
[JOB4_ACTION_PLAN.md](archive/jobs/JOB4_ACTION_PLAN.md).

**Job 5 · code-done on `trunk`.** Rest cue, plates, type-in, pounds
default, font-scale 2.0, prompted backup, and `ci.yml` listing
`trunk` are on `trunk`. Phone gates remain the owner's. GitHub
runners are not a test lane. Room v3, fifth tab, LLM, rename, sex,
and catalog seed stay signed won't. Packets, gates, won'ts:
[JOB5_ACTION_PLAN.md](archive/jobs/JOB5_ACTION_PLAN.md).

**Job 6 (superseded as current program, 24 Aug 2026).** Regroup for the
four-tab logger. Leftover P0–P8 are on `trunk`. Phone-week floor finding
(RPE explainer and sticky rest dock) landed in `2484396`. Job 6 is no
longer the current program. [JOB6_REGROUP.md](archive/jobs/JOB6_REGROUP.md) is
historical leftover paper. Current work:
[FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md).

**Review baseline (22 Aug 2026).** Full-tree audit after Job 2 landed on `trunk`.
Closed 22–23 Aug. Do not reopen as live work. Q4 onboarding ANR on the
cloud emulator is environment. Debug is `.debug` (Job 3 / P4).

| Item | Severity | Packet |
|---|---|---|
| ~~Snapshot retry can copy v2 into the v1 rollback folder~~ | ~~P0~~ | merged [#17](https://github.com/sinura7/PersonalTrainer/pull/17) |
| ~~Snapshot treats any dest directory as success~~ | ~~P1~~ | same |
| ~~Rest timer `apply()` can lose disk state before the alarm~~ | ~~P1~~ | merged [#18](https://github.com/sinura7/PersonalTrainer/pull/18) |
| ~~`preflight.sh` links Robolectric annotations / junit~~ | ~~P1~~ | merged [#19](https://github.com/sinura7/PersonalTrainer/pull/19) |
| ~~CI `on.push` still lists `main`, not `trunk`~~ | ~~P1~~ | done 23 Aug — `db787f5`. Hosted runners are not a test lane. |
| ~~Suggest stays up on a fully pinned week~~ | ~~P2~~ | merged [#20](https://github.com/sinura7/PersonalTrainer/pull/20) |
| ~~Planner still listens for deleted `recovery-upper`~~ | ~~P2~~ | merged [#21](https://github.com/sinura7/PersonalTrainer/pull/21) |
| ~~Setup preview has no catalog-empty error~~ | ~~P3~~ | merged [#22](https://github.com/sinura7/PersonalTrainer/pull/22) |
| ~~`DEVELOPMENT.md` still says `2.json` is uncommitted~~ | ~~P2~~ | merged [#23](https://github.com/sinura7/PersonalTrainer/pull/23) |

---

## Known open items

Carried forward deliberately, with the phase that will address them.
**Phase numbers refer to the game plan.**

| Item | Phase |
|---|---|
| Instrumented tests cover migrations plus `WorkoutRepository` / `ScheduleRepository`; remaining ViewModels and screens still untested (Home / Plan / setup / Settings / History / Progress have JVM tests) | later — opportunistic |
| ~~ViewModels untestable by construction (service-locator `AppViewModel`) — A1~~ | ~~opportunistic~~ done 22 Aug 2026 — constructor-injected `AppDependencies` |
| Finished sessions cannot be edited | ~~1~~ fixed 21 Aug — sets, notes, session delete, repeat |
| Routine editor loses an unsaved rename on back | ~~4~~ fixed 20 Aug |
| Imbalance advice compares tonnage, not working-set counts | ~~5~~ fixed 21 Aug — weighted weekly sets |
| ~~Progression increment is a global 2.5 kg; LBS users see "+5.5 lbs"~~ | ~~7~~ done 21 Aug — `IncrementTable.STEP_LBS` is 5 lbs; storage is `STEP_LBS_IN_KG` |
| RPE is stored and backed up but read by nothing | ~~5~~ fixed 21 Aug — two top sets at RPE 9+ hold the load |
| Planner assigns focus to days already in the past | ~~4~~ fixed 21 Aug — proposals only for open days ≥ today |
| `arrangeKinds` can still produce back-to-back same-family days | ~~4~~ fixed 21 Aug — guarded rotation replaces the swap |
| ~~Toolchain ~20 months stale; release unminified~~ | ~~later (platform)~~ · **done** — Phase 4 took AGP 8.9.2 / Kotlin 2.0.21 (`gradle/libs.versions.toml`) and release is minified (`app/build.gradle.kts`) |
| Exercise imagery and the equipment field it needs | ~~3 (field)~~ / ~~7 (catalog)~~ / ~~8 (imagery)~~ — all done 21 Aug |
| ~~Rest-timer sound design; plate calculator; font-scale-2.0 pass~~ | ~~Job 5 / P2–P4~~ done 22 Aug — cue, plates, type-in, 2.0 layout |
| ~~No scheduled auto-backup (manual + prompted only)~~ | ~~Job 5 / P5~~ done 22 Aug — 14-day caption nag, not WorkManager |

---

## Decisions

**These D1–D6 decisions built the strength logger.** They remain historically
true. Current binding decisions are the ADRs. D7 records the supersession.

A historical decision is marked **Signed** below. Executors starting a
*foundation-program* packet verify [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md)
and `docs/architecture/`, not this grep.

### D7 — Foundation program supersession

**Chosen:** [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md) and
[docs/architecture/](architecture/README.md) are current law. Room v3
won’t is superseded for the one authorized Phase 5 cutover. Backdated
activity creation is required. Drive is backup, not sync. Temper Account sync (Phase 11 steps 4–5) replicates finished activity and plan rows when signed in. One live
activity; many completed and scheduled activities per day. FND-037 is
not a finding.

**Signed: foundation program P0.1, 24 August 2026.**

### D1 — Information architecture

The app ships **four tabs** (Home · Body · Plan · History). Library is a
pushed route. DESIGN_AUDIT NAV-01 called five a lot; that sentence is
stale — Routines folded into Plan and Library left the bar. UI_REDESIGN
§6 recorded a three-tab target that Phase 5 deferred. This decision
closes the contradiction. **Circle one option and sign.**

**What both options give you, whichever you circle.** Library stops being a tab and
lives on as a pushed screen — entered from Plan, from recommendation cards, and from the
muscle detail sheet; every pushed route survives. Routines folds into the new **Plan**
tab, so the week and the routines that fill it are one place instead of a tab plus an
orphaned screen in Settings. The **LiveSessionBar** (a docked strip whenever a session is
live — elapsed, sets, rest countdown, tap to resume) lands as chrome, not a tab, and
becomes the **only** live-session affordance anywhere. And the history work lands either
way: sessions grouped by month, a sheet when a day holds more than one session, and a
personal-records row.

**Option A (recommended): four tabs — Home · Body · Plan · History.** History keeps its
tab. The calendar and the session log stay exactly one tap away, Body stays
silhouette-first, and Phase 6a is the tab-bar rewrite plus the nav retargets — Body does
not absorb History. What it costs you: a fourth tab in the bar, and "what has my training
done" is answered in two related places rather than one.

**Option B: three tabs — Home · Body · Plan — plus the LiveSessionBar.** This is the
recorded target IA of UI_REDESIGN §6: the reflection tab keeps the name **Body**, leads
with the silhouette, and *absorbs* History — the calendar and session log move under the
body map, making Body the single "what has my training done" surface instead of two thin
ones. What it costs you: the largest single piece of nav work in the plan, and the reach
described below.

**Why the recommendation flipped to four tabs (new evidence, 21 Aug 2026).** When the
merged Body screen was specced in full, the session log came out **five sections deep** —
window picker, silhouette, muscle rows, calendar, coach cards, and only then the session
list, with personal records under it. At the same time the Home rebuild deletes Home's
"Recent" list in **both** branches, and the only scroll anchor built into the merged
screen targets the **calendar**, not the session list. Net effect if you circle B: "what
did I do last session" goes from one tap today to a tab change plus a long scroll. Four
tabs keeps every win listed above and drops only the large merge — and it is closer to
what you originally asked for.

**Three tabs is still a legitimate choice.** If you circle B, two mitigations become
mandatory and are built in the same phases, not deferred: (i) a `section=sessions` scroll
anchor on Body alongside the `section=calendar` one, so anything that means "show me my
log" lands on the log; and (ii) a single **"Last session"** link row on Home. That row is
a link, not a recommendation surface, so the D3 surface map is untouched by it.

Either choice closes NAV-01. There is no keep-five option: leaving the contradiction open
means doing the nav work twice.

**Chosen option: A — four tabs (Home · Body · Plan · History).**
**Signed: repo owner, 21 Aug 2026.**

Consequence for execution: Phase 6a runs **Branch A** — History keeps its tab, the
month grouping / multi-session-day sheet / PR row land in `HistoryScreen` and
`HistoryViewModel` in place, and Body is not restructured. Phase 6b's calendar chip is a
plain tab jump to History; the `Route.Progress` section-anchor work and the "Last
session" row on Home are **not built** (they were Option B's mitigations). Executors of
6a/6b take Branch A and never blend branches.

### D2 — Schedule semantics

Spec: `docs/SCHEDULE_SEMANTICS.md`. Phase 3 derives the `schedule_slots` DDL
from this signed spec; Phase 4 implements the derivation. Read the five worked examples
and ask of each: "is this what I'd expect my week to do?" Rule 6 (a missed day does *not*
carry into next week) is the one deliberately open question — strike it and initial the
margin if you want carry-over instead; the DDL is unaffected either way.

**Signed: repo owner, 21 Aug 2026. Rule 6 KEPT as written** — a missed day does not
carry into the next week; the cycle restarts at the week boundary and nothing is owed.
Phase 4's `weekRolloverResetsSatisfaction` test pins exactly this behaviour.

### D3 — Recommendation surfaces

Recommendations appear on exactly four surfaces, and nowhere else. **Home:** one "next
session" module — today's plan and the top recommendation composed into a single card
with a one-line reason; never two recommendation slots. **Body:** the full explanation
cards. **The start-options sheet** and **the in-workout add-exercise sheet:** action
surfaces, one pinned suggestion each. Phases 5 and 6b both conform to this map; any
executor adding a fifth surface is wrong.

### D4 — Cut list

Cut, recorded 20 Aug 2026. None of these may reappear in a packet without a new signed
decision here.

- **LLM / chat coach** — breaches DESIGN_AUDIT §15 and offline-first; the rule engine is
  the coach.
- **Muscle-head-level granularity** — not derivable from set logs; weighted
  primary/secondary credit is the ceiling.
- **Day and year heat windows** — windows are This week + Last 30 days (Phase 5).
- **Per-routine equipment override** — a variant is its own catalog row (`movementKey`
  family); DESIGN_AUDIT §7's row is superseded.
- **The FK re-pointing custom-merge tool** — collisions are skip-and-surface: the seeder
  always inserts the built-in and flags the collision; a "needs attention" row in Library
  lets the owner rename their custom or keep both. History FKs are never rewritten.
- **The line-art commission ($1.5–4k)** — imagery is Compose-drawn composed thumbnails
  (Phase 8); the commission survives only as a non-committal appendix there.
- **A1 as a phase** — opportunistic, 2-day timebox, gates nothing.
- **The rest overlay bubble** — reaffirming Phase 6 above; DESIGN_AUDIT §10.2 now carries
  the superseding banner.

### D5 — Branch ground truth

**Corrected 22 Aug 2026.** This decision was recorded on a false premise. It said `main`
held only the initial commit; `git rev-list --count main` was 52, and 50 of those are the
shared ancestry this branch is built on. The sessions that wrote D5 could see their own
branch and inferred the rest. Since branch-as-trunk is the conclusion this whole plan
rests on, it is worth being exact about what was actually true.

What was true: the two lineages forked at `319c5f5` on 20 Aug. `main` then took two
commits of its own — a schema v2 adding `equipment`, `load_type` and `secondary_muscles`
as nullable snake_case columns, and a committed Room-generated `2.json` baseline for
them. This branch took 52, including its own schema v2: camelCase columns with SQL
defaults, plus `exercise_muscles`, `schedule_slots` and `seed_meta`. Both declare
`version = 2`, and they are not the same schema.

**Chosen: branch-as-trunk — and for a reason the original entry did not have.** Not
because `main` is empty. Because the two cannot be merged: `main`'s
`domain/ExerciseTaxonomy.kt` and this branch's `domain/ExerciseTraits.kt` each declare
`enum class LoadType` in the same package, and both are one-sided additions, so git
reports no conflict and Kotlin fails with `Redeclaration: LoadType`. A merge produces a
tree that looks clean and cannot compile. Recovery cost decides the direction: everything
unique to `main` is ~2,060 lines and its one irreplaceable artifact regenerates from a
single Gradle task; this branch is ~34,800 lines that no command reproduces.

`main`'s tip is preserved before it is overwritten, and the five things worth keeping from
it are grafted individually rather than merged — see the consolidation commits following
`36ac45a`. Thereafter: branch-per-phase `claude/phase-<n>-<slug>`, one PR per phase, owner
merges, no phase starts before the previous PR lands
(`docs/archive/gameplan/PROTOCOL.md` §2–§3).

### D6 — Second-pass amendments *(informational)*

Recorded 21 Aug 2026, after the second-pass adversarial audit (six independent auditors,
every finding evidence-verified against the repo). These amend **how** the game plan
executes; they do not change what it builds, and nothing here reopens D1–D5.

- **Execution order changes; phase numbers do not.** Phase numbers are identifiers, not
  sequence. The order is **0 (Decisions) → 2 (Test substrate) → 1 (Session hygiene) →
  3 (Schema v2) → 4 (Plan tab) → 5 (Heat & coach) → 6a (Tabs + Body) → 6b (Home) →
  7 (Catalog) → 8 (Imagery, optional)**. Phase 2 is the phase that ships
  `tools/preflight.sh` and the Robolectric lane, so running it first (a) keeps Phase 2's
  verified gate literals true rather than stale, (b) gives Phase 1's gates a working
  domain-test lane instead of a command that exits non-zero on a cold clone, and (c)
  gives Phase 1's repository writes — restore-set, repeat-session,
  delete-finished-session — a Robolectric lane they otherwise lack. The cost is that
  session hygiene reaches your phone roughly one to two executor-days later.
- **Phases 1a and 1b merge into one phase: "Phase 1 — Session hygiene."** One branch
  `claude/phase-1-session-hygiene`, one PR, one combined owner evening. Both packets go
  to the same executor session and are executed in order — 1a in full with its gate
  green, then 1b on top. 1b's gate greps re-assert 1a's invariants, so the sequence
  self-verifies. Saves one owner evening; no safety is lost.
- **The full second-pass findings and the rest of this reconciliation:**
  `docs/archive/gameplan/SECOND_PASS.md`.

**No signature required; recorded for the record.**

---

## ~~Open follow-up — the deload advice has no affordance~~ · **done** (Job 3 / P2)

**Raised:** 21 August 2026, by the coach rework (Phase 5). **Closed:** Job 3 / P2
shipped the lighter-week marker (HOLD, not scaled sets). The Plan Tune
control marks this week; progression HOLDs load. Do not rediscover this
as an open hole.

**Chosen: A** (historical). Named as Job 3 / P2 in
[JOB3_ACTION_PLAN.md](archive/jobs/JOB3_ACTION_PLAN.md).
