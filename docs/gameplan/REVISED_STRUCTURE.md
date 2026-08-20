# REVISED GAME-PLAN STRUCTURE — binding brief for all spec writers

This supersedes docs/HIERARCHY_PLAN.md §4 where they differ. It folds in every accepted
finding from the five-attack critique (the accepted findings are enumerated below; the
full attack text lived in the planning session). Every spec packet MUST conform and
to the shared decisions below. Where this brief says "pre-decided", the packet states the
decision as settled and does not reopen it.

## Why the structure changed (accepted critique findings)

1. FATAL (tech): partial unique index on lower(name) is inexpressible in Room 2.6.1 and
   raw-SQL-created indexes fail Room's open-time validation → crash loop. REPLACED with:
   a `nameKey` normalized TEXT column + plain declared (non-unique) @Index, uniqueness of
   built-ins enforced by a seed-data invariant test + app-layer checks in
   createCustom/updateCustom. Rule recorded: no index or table may exist outside the
   exported Room schema JSON.
2. FATAL (tech): routine_schedule(routineId × day) cannot express rotation-first
   semantics. REPLACED with the ordered-slot model (below), whose DDL is derived from a
   semantics spec the owner signs in Phase 0, BEFORE the migration freezes it.
3. FATAL (integration): backup format co-evolution was absent. Backup v2 (document,
   validator, v1-upgrade path, restore→seed reconciliation) is now IN the migration
   phase with round-trip gates.
4. FATAL (integration): "auto-backup before migrating" cannot go through Room (migration
   runs on first open). REPLACED with a raw file copy of personal_trainer.db + -wal/-shm
   in Application.onCreate before any DB access, gated on a persisted version marker;
   recorded as the ONLY rollback path (v1 code refuses v2 JSON; Room refuses downgrades).
5. FATAL (integration): nobody owned "the week". A ScheduleRepository now owns the
   persisted week; it joins TrainingInsightsSource as a sixth source flow; unpinned
   fills ARE persisted; insights.weekPlan is rewired in the SAME phase pins appear, so
   Home/Body/Plan never disagree.
6. FATAL (scope): Phase 4 was ~11 workstreams under one label and inverted value
   ordering. Split and reordered (see phase list): session hygiene ships FIRST; catalog
   and imagery ship LAST; A1 DI refactor is CUT from the critical path (opportunistic,
   2-day timebox, no phase gates on it).
7. FATAL (scope): 3.95's "fix the billing block" is not executable by the model.
   Robolectric-on-JVM is the primary MigrationTestHelper lane; connectedDebugAndroidTest
   on the owner's phone is the device-truth lane; the GitHub billing fix is a standing
   owner errand that gates nothing.
8. FATAL (contradictions): LiveSessionBar vs Home hero vs RestRemainingStrip = three
   resume surfaces. RULE: when a session is in progress, the LiveSessionBar is the ONLY
   live-session affordance anywhere; Home's hero shows plan/start state only and never
   relabels to Resume; RestRemainingStrip is deleted. Gate wording: "exactly one
   live-session affordance visible anywhere, counting docked chrome."
9. FATAL (contradictions): the 4.5→5.5 split did nav work twice, and Body-absorbs-History
   was never scheduled. The Plan-tab construction (including ScheduleScreen deletion and
   ThisWeekHomeCard extraction) is ONE phase; the tab consolidation + Body/History merge
   is ONE later phase, explicitly scoped and sized L.
10. MAJOR (executor): main is empty (all 52 commits live on
    claude/app-hierarchy-navigation-cjzigo); execution protocol now states branch truth,
    branch-per-phase, PR-per-phase, owner checkpoints, and a packet template.
11. MAJOR (scope): the FK re-pointing custom-merge tool is CUT. Collisions are
    skip-and-surface: the seeder always inserts the built-in, flags the collision, a
    "needs attention" row in Library lets the owner rename their custom or keep both.
    No history FKs are ever rewritten.
12. MAJOR (scope): the $1.5-4k line-art commission is CUT to a non-committal appendix.
    Imagery = Compose-drawn composed thumbnails only (DrawScope mini-silhouette +
    equipment glyph, colored from Heat tokens; VectorDrawable XML carrying ramp colors
    is forbidden — check-design-tokens polices the token system).
13. MAJOR (scope): catalog authoring is staged. Batch 1 (~37 rows: the existing catalog
    upgraded in place with equipment/movementKey/weighted credits — these ARE the
    owner's history) is reviewed carefully via a generated review artifact in the
    migration phase; the tail to ~98 lands in later seed bumps at lower scrutiny.
14. MAJOR: editable-session rules: edits preserve completedAt; sets added post-finish
    are timestamped inside [startedAt, finishedAt]; durationMinutes never recomputed;
    domain tests pin heat-window attribution and PR chronology to the original day.
15. MAJOR: FinishWorkout/DiscardWorkout are extracted as shared use cases (zero-set
    guard, restTimer.stop, draft clear — cache AND SavedStateHandle — one-shot nav)
    BEFORE any bar/nudge UI exists; finish-side invariant mirrors the StartTrainingDay
    rule: every finish/discard routes through the use cases.
16. MAJOR: seed/restore/merge writes are serialized behind one DB-maintenance mutex;
    the seeder never resolves collisions; every restore (any version) ends with an
    idempotent catalog-reconciliation pass (re-seed missing built-ins, rebuild junction
    rows — for v1 documents via MuscleNormalizer — re-run collision detection);
    hasLocalData learns to count schedule slots.
17. MAJOR: muscle resolution contract: junction keys must normalize to a CanonicalMuscle
    via the alias index; unknown keys map to the nearest parent group, never OTHER;
    exercises.muscleGroup survives as denormalized display text until a later cleanup;
    MuscleLoadCalculator switches to catalog-first junction resolution.
18. MAJOR: StartTrainingDay pinned contract: a pin whose routine is deleted/emptied
    surfaces an explicit error state (never a silent free-workout fallback); when a
    different session is in progress, the UI offers explicit resume-or-discard, never
    silent Open(current.id).
19. MINOR accepted: per-loadType increments replace BOTH ProgressionCalculator
    .INCREMENT_KG and the coach copy quoting it, reconciled with WeightUnit.step;
    BODYWEIGHT loadType suppresses add-weight hints. RPE + imbalance-by-sets are owned
    by the heat/coach phase ONLY. Library muscle-filter route param becomes a canonical
    muscle key when the junction becomes truth (catalog phase owns the flip end-to-end).

## The phases (execution order — value-first)

Phase 0  — Decisions & doctrine (docs only). 0.5-1 executor-day + BLOCKING owner
           checkpoint. Concrete edit lists for ROADMAP/DESIGN_AUDIT/UI_REDESIGN; IA
           adjudication recorded (recommended: three tabs Home·Body·Plan; four-tab
           fallback branch text ready); the schedule-semantics spec (below) signed;
           the recommendation-surface map (below) recorded; cut list recorded (LLM
           coach, head-level granularity, day/year windows, per-routine equipment
           override, FK merge tool, line-art commission → appendix). Also: merge the
           working branch to main (or record branch-as-trunk), so executors have ground
           truth.
Phase 1a — Session lifecycle. 3-4 days. FinishWorkout/DiscardWorkout use cases;
           LiveSessionBar (pulled forward — it is nav-chrome, tab-count-independent,
           and is the surface the limbo policies need); zero-set policy (discard-only,
           clears WorkoutDraftCache entry + SavedStateHandle); stale-session nudge
           (in-app evaluation only: bar state + resume surfaces; threshold 4h since
           last activity; zero-set stale → discard-only); RestRemainingStrip deleted;
           Home hero never shows Resume again; one-live-affordance gate.
Phase 1b — Log repair. 2-3 days. Editable finished sessions (rules in item 14); session
           delete (guarded, from SessionDetail); repeat-last-session (repository entry
           through the single-in-progress transaction + insertSessionIfIdle; UI on
           History rows + SessionDetail); delete-set undo (UI_REDESIGN §6 item,
           dispositioned here).
Phase 2  — Test substrate. 1-2 days. androidTest scaffold + testInstrumentationRunner +
           room-testing/androidx.test deps; Robolectric JVM lane hosting
           MigrationTestHelper (primary); one smoke migration test against
           schemas/1.json; documented connectedDebugAndroidTest runbook for the owner's
           machine; tools/preflight.sh chaining all eight static checks + domain tests;
           jar bootstrap for tools/run-domain-tests.sh documented. GitHub billing =
           owner errand, gates nothing.
Phase 3  — Schema v2 migration. 3-5 executor-days + 1-2 owner-days. ONE reviewed
           additive migration: columns equipment, loadType, movementKey, imageKey
           (nullable), nameKey (+ plain index); tables exercise_muscles(@Entity,
           exerciseId, muscleKey TEXT, weight REAL), seed_meta(@Entity, catalogVersion),
           schedule_slots (DDL derived from the Phase-0 signed semantics). Versioned
           seeding behind the maintenance mutex; batch-1 catalog (the 37, upgraded in
           place, + review artifact with invariant tests: weights sum ≤1.0 rule,
           primary ≥0.5, unique nameKeys among built-ins, ids never re-slugged).
           Backup v2 (document/validator/v1-upgrade/replaceWith order/restore
           reconciliation + hasLocalData). Pre-open raw DB file copy safety net.
           Rehearsal runbook verbatim (owner JSON export → v1 APK on emulator →
           restore → v2 APK upgrade → verify counts + spot-checks). Heat calculator
           switches to junction (item 17) — owner reviews before/after heat diff.
Phase 4  — Plan tab & the pinned week. 4-6 days. Domain reconciliation rules as pure
           Kotlin with tests FIRST (from the Phase-0 spec); ScheduleRepository owns the
           persisted week and joins TrainingInsightsSource; planner demoted to
           proposing fills for empty slots; accepted fills persist; insights.weekPlan
           rewired HERE; StartTrainingDay pinned contract (item 18); planner fixes
           (past-day, arrangeKinds) land here once. Full Plan tab construction inside
           the five-tab bar: Routines renamed Plan, week strip on top, routines below,
           Tune behind header action, gear icon added to Plan header (Settings' second
           home — named), pushed ScheduleScreen DELETED, ThisWeekHomeCard extracted to
           its own file and retargeted, Home hero-card tap → Plan tab, Settings'
           schedule row → removed (Plan hosts Tune).
Phase 5  — Honest heat & coach. 3-5 days. Absolute bands on weighted weekly sets
           (thresholds pre-decided below); HeatWindow → THIS_WEEK + LAST_30_DAYS
           (7D/14D deleted; when-exhaustive ripple enumerated: ProgressScreen
           pickerLabel/sentenceLabel, TrainingInsightsSource default, HomeScreen
           'Last 7 days' fallback; check-when-exhaustive cited as proof; stored window
           preference migrated); coach decoupled onto a fixed trailing-14-day basis;
           RPE consumption; imbalance by weighted working-set counts (supersedes
           tonnage); symmetric do-less/deload rules; recommendations name owned lifts;
           goals + available-equipment preference inputs; voice spec codified.
           Surfaces per the map below, including the in-workout add-sheet pinned
           suggestion and mid-workout swap/remove (§6 item, dispositioned here).
Phase 6a — Tab consolidation + Body absorbs History. 3-5 days. Size L honestly. Tab bar
           per recorded IA decision (three-tab primary spec; four-tab fallback branch
           included). Body screen becomes: window picker, silhouette, per-muscle rows,
           calendar (moved from History), recommendations (explanation cards),
           month-grouped session list (grouping added during the move), PR summary.
           Multi-session day sheet fixes the first-session-only calendar tap. The
           nine-site nav retarget checklist (file:line, from the attack findings) is IN
           the packet; isTabRoute query-param shim deleted; Home stays NavHost start
           destination (popUpTo contract recorded).
Phase 6b — Home 'Today' rework. 2-3 days. Masthead string table (all seven states,
           literals in the packet); week strip reading the persisted week; calendar
           jump → Body calendar section anchor; ONE next-session module (per surface
           map); Ready-to-progress rows deep-link the named lift; StartWorkout
           interstitial becomes a start-options sheet from the hero (§6 item,
           dispositioned here); duplicate heat card and Recent list removed (Body owns
           them). Separate device pass from 6a.
Phase 7  — Catalog to ~98 + Library UX. 3-5 days + staged owner review. Seed bumps
           carry the tail batches; family grouping + equipment chips + popularity/
           recency ordering + escaped search in Library and picker; muscle-filter route
           contract flips to canonical keys end-to-end (RecommendationCards string hop
           deleted); skip-and-surface collision UX; per-loadType add-to-routine
           defaults + the increment table (item 19).
Phase 8  — Imagery. 2-4 days. Compose-drawn composed thumbnails (mechanism pinned:
           DrawScope, Heat tokens, ~20 glyph/silhouette primitives); imageKey stays
           null → composed; APK delta budget ≤2 MB. Appendix (non-committal): line-art
           option if the owner ever wants it.

A1 DI seam: NOT a phase. Recorded as opportunistic timeboxed refactor, only if
instrumented ViewModel tests ever get scheduled.

## Shared pre-decided designs (packets state these as settled)

SCHEDULE SEMANTICS (Phase 0 signs; Phase 3 derives DDL; Phase 4 implements):
- schedule_slots(id PK, position INT, routineId TEXT? FK→routines ON DELETE CASCADE,
  focusKind TEXT?, anchorDay INT? 0-6, createdAt, updatedAt). A slot is a routine slot,
  a focus-only slot, or (by convention) absent = rest.
- The week is an ordered cycle of slots. "Next up" = the first slot not yet satisfied
  this week; a slot is satisfied when a finished session traces to it (started via its
  pin) or matches its routine within the current week.
- anchorDay is a preference, not a constraint: the effective week places anchored slots
  on their day when reachable; a missed anchored day SHIFTS forward (next open day),
  never skips. Derivation is a pure function of (slots, completion history, today);
  stored slots are never rewritten by the derivation. Regeneration/planner proposals
  never touch user-created slots; deleting a routine cascades its slots and the
  reconciliation test proves the derived week heals.
- Worked examples for the five cases (missed pinned day, missed unpinned day, week
  rollover, regeneration, routine deletion) go in the Phase 0 doc for owner sign-off.

BAND MODEL (Phase 5): a set credits each muscle by its junction weight (primary 1.0);
weekly weighted-set totals band at <4 untrained / 4-9 low / 10-20 productive / >20
high; LAST_30_DAYS displays the per-week average (total × 7 ÷ days-in-window); band →
ramp: untrained=heat0, low=heat1, productive=heat3, high=heat4 (heat2 reserved for
between-band interpolation on the silhouette). Coach basis: fixed trailing 14 days.

RECOMMENDATION SURFACE MAP (final; Phase 5 and 6b both conform): Home = ONE
"next session" module (plan + top recommendation composed, one-line reason); Body =
full explanation cards; StartWorkout options sheet + in-workout add-exercise sheet =
action surfaces (one pinned suggestion each); no other surfaces. Home never shows two
recommendation slots.

INCREMENT TABLE (Phase 7): progression step comes from (loadType, displayUnit):
EXTERNAL/STACK kg→2.5 kg, lbs→5 lb (2.27 kg applied); BODYWEIGHT_PLUS same;
BODYWEIGHT → no add-weight hints (rep-progression copy instead). One table replaces
ProgressionCalculator.INCREMENT_KG and the coach copy; WeightUnit.step reads it.

LIVESESSIONBAR CONTRACT (Phase 1a): visible on all tab routes and pushed routes EXCEPT
ActiveWorkout, WorkoutSummary, StartWorkout; carries its own navigationBarsPadding when
the tab bar is absent; shows elapsed (ticker), working-set count (new observed query),
rest countdown when running; tap resumes; overflow = Finish (≥1 set) / Discard (always,
guarded dialog); zero-set state shows Discard only. While it is visible, no other
surface may show a live-session affordance.

## Execution protocol (packet A specifies fully; every packet references it)

- Ground truth: all code lives on claude/app-hierarchy-navigation-cjzigo; main holds
  only the initial commit. Phase 0 merges the branch to main (owner approves) OR
  records branch-as-trunk. Thereafter: branch-per-phase claude/phase-<n>-<slug>
  (matches ci.yml's claude/** trigger), one PR per phase, owner reviews and merges;
  no phase starts before the previous PR merges.
- Packet template (every packet): (1) files to read first (paths), (2) binding doctrine
  by section (DESIGN_AUDIT IDs, UI_REDESIGN §5.1+§8, DIRECTION_B_INSTRUMENT), (3)
  explicit out-of-scope list, (4) acceptance gate as literal commands + expected
  output, (5) owner device checklist, numbered steps, (6) phase closes ONLY on owner
  sign-off; estimates in executor-days and owner-days.
- Mechanical proof per phase: tools/preflight.sh green (all eight static checks +
  domain tests) before every push; check-when-exhaustive and check-screen-wiring cited
  wherever enums/callbacks change.
