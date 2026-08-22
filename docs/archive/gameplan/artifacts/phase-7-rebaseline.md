# Phase 7 — re-baseline report

> PROTOCOL.md §6 / packet preamble. Written and committed **before** any Phase 7 work item.
> Every count, path and line number in `PHASE_7_CATALOG.md` is a baseline taken at audit
> commit `2212628`. Seven phase commits have landed since, several of which rewrote the files
> the packet cites. This report is the reconciliation: what drifted, why, and which value the
> executor adopted.

## 1. Trunk state

- Tip at start of Phase 7: `c3478e0` — *Phase 6b: Home states today, and there is one way to start*.
- Branch: `claude/app-hierarchy-navigation-cjzigo`.
- Commits since the packet's audit baseline `2212628` — **16**, of which these are phase work:

  | Commit | Phase |
  |---|---|
  | `c298d0a` | 0 — decisions and doctrine |
  | `bddd6d3` + `ca34fe9` | 2 — test substrate (+ the Windows/host fix) |
  | `5579992` | 1a — session hygiene |
  | `21bfdd6` | 1b — correctable history |
  | `2d1e8a2` | 3 — schema v2, versioned seeding, backup v2, junction heat |
  | `87f8087` | 4 — Plan tab |
  | `d7d301c` | 5 — heat and coach |
  | `f42c17f` | 6a — four tabs |
  | `c3478e0` | 6b — Home "Today" |

  The rest are game-plan document commits. Execution order `0 → 2 → 1 → 3 → 4 → 5 → 6a → 6b → 7`
  is intact; Phase 8 is untouched and stays optional.

- **Phase 0 verified** as the packet requires: `grep -c "Signed:" docs/ROADMAP.md` → **3**.

### Branch deviation, carried forward

The packet says branch `claude/phase-7-catalog`, one PR per phase. Every phase so far has
instead landed on `claude/app-hierarchy-navigation-cjzigo`, because the executor's standing
instruction is to develop on that branch and never push to another without explicit
permission. That instruction outranks the packet. Same deviation, same reason, recorded again
here rather than silently.

## 2. Test lane, measured not quoted

From a real `tools/preflight.sh` run at `c3478e0`:

- **288 domain tests**, **44 test classes**, all green.
- 35 test files under `app/src/test/java/com/sinura/personaltrainer/domain/`; the rest live
  under `data/`, `timer/`, `util/` and `workout/` and are Gradle-only.
- Nine static checks green — **nine, not the packet's eight**: Phase 6b added
  `tools/check-state-members.py` and wired it into `preflight.sh`. §7's gate line
  ("all eight static checks") is stale by one; the command it names is unchanged.

## 3. Packet literals: verified, drifted, adopted

| Packet literal | Packet value | Shipped value | Verdict |
|---|---|---|---|
| `CATALOG_VERSION` (the packet's N) | unstated, "Phase 3's shipped value" | **2** | ✅ resolved. Batch 2 = **3**, batch 3 = **4** |
| Batch-1 catalog count | 37 | **37** | ✅ |
| Batch-1 `movementKey` values (§4.14) | 23-family list | identical, all 37 rows | ✅ **no re-keying needed** — see §4 |
| `MOVEMENT_FAMILIES` | not cited | shipped as a closed 23-member set in `DefaultExercises.kt` | ✅ tail extends it |
| Catalog row type | "the Phase-3 evolution of DefaultExercises" | `SeedExercise(id, name, muscleGroup, equipment, loadType, movementKey, credits)` | ✅ adopted verbatim |
| Slug rule | `DefaultExercises.kt:44-47` | `internal fun slugOf` at **:271** | ⚠️ line moved, rule identical |
| `ExerciseDao` LIKE search | `:19-27` | `:22-23` | ⚠️ line moved, still unescaped |
| `ProgressionCalculator.INCREMENT_KG` | `:11` | `:11` | ✅ |
| `RecommendationEngine` coach copy | `:213-223` | **:247** | ⚠️ Phase 5 rewrote this file |
| `ActiveWorkoutScreen.incrementLabel` | `:129` | **:134** | ⚠️ line moved |
| `WeightUnit.step` | `WeightFormat.kt:12-25` | `:12`, `:18`, `:24` | ✅ |
| `Route.Library.create` call sites | `AppNav.kt:244-252, 259-267` | **`AppNav.kt:312` and `:355`** | ⚠️ Phases 6a/6b re-plumbed nav, as the packet predicted |
| `catalogLabel` dispatch hop | `RecommendationCards.kt:77` | **:85** | ⚠️ line moved; the hop is real and still there |
| Review artifact path | `docs/catalog/batch-<n>-review.md` | **`docs/gameplan/artifacts/catalog-v2-review.md`** | ⚠️ **adopted the shipped path** — see §5 |

Every ⚠️ above is a line-number or path move fully explained by a merged phase. Per the
packet's own rule, they are recorded and adopted, not escalated.

## 4. The one mismatch that would have stopped work — and did not

§4.14 says a batch-1 `movementKey` that is not the family key listed there is an unexplained
drift, to be reported rather than silently re-keyed, because `LibraryGroupingTest` rides on the
exact vocabulary. Verified row by row against the shipped catalog: **all 37 match**, including
the four `bench-press` rows (Barbell, Incline, Dumbbell, Close-Grip) the family test counts on.
Nothing to escalate.

## 5. Shipped substrate that supersedes the packet

**The review artifact is already enforced, and better than the packet's design.** Phase 3
shipped `CatalogReviewRenderer` plus `CatalogReviewArtifactTest`, which fails the build if the
committed artifact does not equal the renderer's current output. The packet asks for two new
hand-regenerated files under `docs/catalog/`; regenerating by hand is exactly the drift that
test exists to prevent. Adopted instead: the artifact stays at
`docs/gameplan/artifacts/catalog-v2-review.md`, is renamed per version as the catalog is bumped,
and the existing test keeps guarding it. The owner still gets one artifact to skim per batch,
which is what §8 step 11 actually asks for.

**Batch-1 `loadType` for the bodyweight family** — §8 steps 6–7 read off these, and §10 item 3
requires them in the hand-back:

| Lift | equipment | loadType |
|---|---|---|
| Push-Up | BODYWEIGHT | `BODYWEIGHT` |
| Plank | BODYWEIGHT | `BODYWEIGHT` |
| Hanging Leg Raise | BODYWEIGHT | `BODYWEIGHT` |
| Pull-Up | BODYWEIGHT | `BODYWEIGHT_PLUS` |
| Chin-Up | BODYWEIGHT | `BODYWEIGHT_PLUS` |

So §8 step 7's conditional resolves: Push-Up gets rep copy; **Pull-Up and Chin-Up do not** —
they shipped as `BODYWEIGHT_PLUS`, which takes a weight step like any other loadable lift. The
owner should expect "+2.5 kg" / "+5 lbs" on a pull-up, and rep copy on a push-up.

`EquipmentType` and `LoadType` shipped with exactly the members §4.1 assumes, in that order.

## 6. Consequences for the work items

- **WI-1** — batch 2 is `CATALOG_VERSION = 3`, batch 3 is `4`. No family re-keying. The
  invariant test extends `DefaultExercisesTest`, which already asserts the strengthened
  `muscleKey == CanonicalMuscle.<X>.name.lowercase()` rule over batch 1.
- **WI-3** — retarget `AppNav.kt:312` and `:355`, not the packet's `:244`/`:259`. The
  `catalogLabel` hop to delete is `RecommendationCards.kt:85`, and `ProgressScreen.kt:205`
  carries a second one the packet does not name.
- **WI-5** — `INCREMENT_KG` currently has **four** readers, not the packet's three:
  `ProgressionCalculator.kt:11` (the constant and two uses), `RecommendationEngine.kt:247`,
  and `ActiveWorkoutScreen.kt:134`. All must end at zero.
- **§7 gate** — "all eight static checks" reads as nine.
