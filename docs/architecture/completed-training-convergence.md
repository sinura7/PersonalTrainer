# Completed-training convergence

- **Status:** Accepted plan — 2026-09-06 handoff (R18)
- **Date:** 6 September 2026
- **Authority:** [ADR-007](ADR-007-activity-model.md) (the activity model),
  [ADR-010](ADR-010-schema-reset-migrations.md) (Room v4 stays frozen),
  [activity-contract.md](activity-contract.md)
- **Does not reopen:** the Room generation; existing IDs; `BackupJson` v3;
  the strength `WorkoutSession` as the live logging path

Temper holds two representations of completed training: the strength
`WorkoutSession` (`workout_sessions` / `session_exercises` / `set_logs`,
the live logging path with rest, PR detection and set repair) and the
typed `ActivitySession` (`activity_*` tables: backdated strength,
cardio-only and mixed days, plus live cardio). Consumers merge them for
some reads and not others, so detail, edit, error, restore and draft
behaviour can drift between the two. This record is the inventory of
where they agree today, the read contract they converge on, and the
order of work — incremental, behind parity tests, with no storage
merge and no schema change.

## 1. Capability matrix (as of the 2026-09-06 handoff)

Columns are the four supported kinds of completed training. "Strength
session" is the live `WorkoutSession`; the other three are
`ActivitySession` by block content.

| Capability | Strength session | Backdated strength activity | Cardio-only activity | Mixed activity | Source of truth |
|---|---|---|---|---|---|
| History list and month groups | yes | yes | yes | yes | `SessionSummary` from both repositories, merged in `HistoryViewModel` |
| Calendar day marks | yes | yes | yes | yes | same summaries |
| Horizon totals (sets, volume, minutes, cardio) | yes | yes | yes | yes | `DailyProjectionBuilder` over merged summaries |
| Horizon readout: PRs broken, moved most | yes | yes | n/a | yes | `BlockReviewBuilder.overRange` over `CompletedTraining` from both stores |
| Past-block reviews | yes | yes | n/a | yes | `BlockReviewBuilder.build` over `CompletedTraining` from both stores |
| Lifetime Records (History) | yes | yes | n/a | yes | `standingRecords` over both record-set queries (R08) |
| PR badge at log time | yes | **no** (no live logging) | n/a | **no** | `WorkoutRepository.recordsBrokenBy` |
| Exercise detail bests and history | yes | yes | n/a | yes | `CompletedTrainingRepository.observeExerciseSets` unions `set_logs` and `activity_strength_sets` |
| Body heat and coach insights | yes | yes | n/a | yes | `TrainingInsightsSource` merges both (32-day window) |
| Home last session tile | yes | yes | yes | yes | merged summaries |
| Detail screen | Session detail | Activity detail | Activity detail | Activity detail | two screens, two view models |
| Edit after finish: set weight/reps, delete, undo | yes | **no** | n/a | **no** | `SessionDetail` edits `set_logs`; activity blocks are immutable once completed |
| Edit after finish: notes, delete session | yes | **no** | **no** | **no** | `WorkoutRepository.updateSessionNotes` / `deleteFinishedSession` only |
| Repeat as a new live session | yes | **no** | n/a | **no** | `WorkoutRepository.repeatSession` |
| Draft survives process death | yes (`WorkoutDraftCache`, `SavedStateWorkoutDraft`) | yes (`SavedStateComposerDraft`, R09) | yes (composer or live-cardio saved inputs) | yes | per-screen saved state |
| Read fault is `failed`, not `missing` | yes (`DataHealth` on history flows) | yes (R10) | yes (R10) | yes (R10) | `observeHealth` on every observed flow; `failed` on the two detail screens |
| Backup export, restore, restore witness | yes | yes | yes | yes | `BackupJson` v3 carries both; `RestoreWitness` covers both (R01) |
| Plan link and reminder cleanup | yes (`PendingOccurrence`) | yes (`afterCommit`, R06) | yes | yes | occurrence marked DONE inside the write; cleanup after |
| One-live-at-a-time | yes | n/a | yes (live cardio) | n/a | `serialized` maintenance lock in both repositories |

Bold **no** cells are the drift. They fall into two groups: reads that
still query the strength store alone (horizon readout, past-block
reviews, exercise detail, PR badge), and edits the activity path never
had (set repair, notes, delete, repeat).

## 2. The read contract

One domain type, no new table:

```kotlin
/** A finished piece of training, whichever store holds it. */
data class CompletedTraining(
    val id: String,
    val kind: Kind,                 // STRENGTH_SESSION, ACTIVITY
    val title: String?,
    val performedAtMs: Long,
    val localEpochDay: Long,
    val finishedAtMs: Long?,
    val summary: SessionSummary,    // already shared by both stores
    val strength: List<RecordSet>,  // working sets with lift name and class (R08 projection)
    val cardioSeconds: Long,
    val cardioDistanceMeters: Double?,
)
```

Both repositories already produce every field: `SessionSummary` for the
list, `RecordSet` for records, the cardio aggregates in
`ActivitySummaryRow`. The contract is a read model assembled in the
repository layer (`CompletedTrainingRepository` over the two DAOs), not
a third store. IDs are the existing row ids; nothing is rewritten.

What moves onto it, in order, each behind a parity test from §4:

1. **Horizon readout and past-block reviews.** `BlockReviewBuilder`
   takes `List<CompletedTraining>` (its record counting already runs on
   `ExerciseSetRecord`; `RecordSet` carries the same plus class). This
   closes the largest drift row and removes the last `sessionsBetween`
   full-graph read from History — the R17 measurement target.
2. **Exercise detail.** `observeFinishedWorkingSets(exerciseId)` has a
   sibling over `activity_strength_sets`; the detail screen reads the
   union through `CompletedTrainingRepository.observeExerciseSets`. The
   PR badge at log time stays strength only (activities are not logged
   live), which is a product fact, not drift.
3. **Detail screens.** Keep two composables; give them one
   `CompletedTrainingDetailViewModel` shape (load, `missing`, `failed`,
   retry) so the R10 semantics cannot diverge again.
4. **Edits.** Decide per capability, not by store: notes and delete are
   cheap to add to activities (a completed row is replaced in one
   transaction, revision bumped, same as `completeLive`); set repair on
   an activity block needs an ADR because blocks are snapshots by design
   ([activity-contract.md](activity-contract.md) §8). Repeat-as-live
   stays strength only until live activities carry strength.

## 3. Use cases to extract

The three largest files (`ActiveWorkoutScreen` ≈1,500 lines,
`ActiveWorkoutViewModel` ≈1,200, `WorkoutRepository` ≈1,000) hold
responsibilities the activity path re-implements. Extract by
responsibility, each with the tests it already has:

| Use case | Today | Target |
|---|---|---|
| Save / finish completed training | `FinishWorkout`, `ConfirmActivity`, `FinishActivity`, `afterCommit` | one `CompleteTraining` façade with the same outcome type (`Accepted` / `Rejected` / `Failed`), so the composer, live cardio and the workout bar report saves the same way |
| Record calculation | `standingRecords`, `PersonalRecords.detect`, `recordsBrokenBy`, `BlockReviewBuilder.countRecords` | one `RecordsCalculator` over `RecordSet`; priors still read by SQL aggregate |
| Backup protection | `BackupEnvelope`, `BackupScaleBudget`, the export paths in `BackupRepository` and `SettingsViewModel` | `ProtectBackup` / `OpenBackup` use cases; the safety-copy export stops calling `wrap` from the view model |
| Draft recovery | `WorkoutDraftCache` + `SavedStateWorkoutDraft`, `SavedStateComposerDraft`, live-cardio saved inputs | one `DraftStore<T>` contract with clear-on-accepted-save; no shared storage (per-entry saved state stays) |
| Live-session bar actions | `LiveSessionBarViewModel.finishFromBar` / discard | route through `CompleteTraining` so a thrown finish is handled once |

None of these change storage. Each is a move with its tests, then a
parity test added.

## 4. Parity tests

The same contract cases run against four fixtures: strength-only
session, backdated strength activity, cardio-only activity, mixed
activity. Robolectric (real in-memory Room through `FakeAppDependencies`):

| Case | Assertion |
|---|---|
| Chronology | appears once in History, on its local date, in the right month group and calendar cell; Home's last session picks the newest of the four |
| Totals | horizon totals equal the fixture's known sets, volume, cardio seconds and distance under each horizon |
| Records | `standingRecords` returns the fixture's known best; a later, weaker set does not replace it; an edited set (where supported) moves it |
| Horizon readout | PRs broken counts the fixture's records once the readout reads the contract (§2 step 1) |
| Edits | where supported: edit, delete, undo leave totals and records consistent; where not: the screen offers no edit affordance and the repository rejects the write |
| Restore | export, wipe, restore; witness matches; all four reappear with the same ids |
| Errors | a delegating DAO whose read throws yields `failed` (not `missing`) on the detail screen and a stale-marked History, and retry recovers without a duplicate row |

`StandingRecordsTest` (pure JVM) and the R06–R10 Robolectric tests are
the first rows of this table; the rest are written as each consumer
moves.

## 5. Measurement before optimisation (R17)

Structural share is done (`HistoryViewModel.catalog` is `shareIn`'d;
one upstream subscription per screen). Everything else waits on numbers:

- Fixtures: 500 and 15,000 finished working sets, mixed across the four
  kinds, generated by a test helper (never real data).
- Counters: Room `QueryCallback` query counts per History open and per
  horizon switch; allocation and p50/p95 frame time from a
  `androidx.benchmark` macrobenchmark on the debug build.
- Targets to decide from the first run: the horizon readout's
  full-history `sessionsBetween(0, end)` read is the expected outlier;
  §2 step 1 replaces it with the bounded `RecordSet` projection plus a
  prior-record baseline computed by SQL aggregate (`recordPriorsBefore`
  already exists per lift).

No paging and no data-layer rewrite until the counters say where the
time goes.

## Review questions

- *Why not one table?* The frozen schema and `BackupJson` v3 are the
  contract the owner's history depends on; a read model costs nothing
  to undo and a storage merge cannot be undone.
- *Why do activities not get set repair now?* Blocks are snapshots by
  signed contract; changing that is an ADR, not a convergence step.
- *Why keep two detail composables?* They show different things
  (cardio blocks, a receipt); the parity is in state semantics and
  error handling, not in layout.
