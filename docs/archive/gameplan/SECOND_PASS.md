# Second pass — 21 August 2026

Due-diligence round against the committed game plan. Six independent auditors over
`docs/gameplan/` and the full checkout at HEAD, ~1.1 M tokens:

1. **Cross-packet contracts** — every value one packet produces and another consumes.
2. **Baseline drift** — every count, line number and repo-state assertion re-measured.
3. **Code-truth sweep A** and **4. Code-truth sweep B** — two independent passes
   re-verifying the packets' file:line claims against HEAD, not against commit `2212628`.
5. **External mechanisms** — every claim about Room, Robolectric, Gradle, Gson and AOSP
   checked against the primary source (library source, generated code, or platform code),
   never against recollection.
6. **Strategy red team** — the plan attacked as a plan: order, sequencing, what the owner
   actually gets if it stops halfway.

## Verdict

**The engineering core held. Two decisions were overturned, one contradiction would have
made a phase's own tests unpassable, and one class of drift the plan's own commits
created.**

- The technical substrate — the migration design, the backup co-evolution, the pre-open
  raw copy, the slot model, the band model, the one-live-affordance rule — survived every
  attack intact. No FATAL-class finding landed against it.
- Two decisions were overturned on new evidence: the **execution order** (D-A) and
  **D1's recommended default** (D-C).
- One contradiction was disqualifying rather than cosmetic: Phase 3 and Phase 7 shipped
  **two incompatible `movementKey` vocabularies** (D-D), and under Phase 3's reading
  Phase 7's own `LibraryGroupingTest` could never pass.
- One drift class was self-inflicted: packets asserted `docs/gameplan/` did not exist and
  that PROTOCOL.md's presence meant Phase 0 had merged — both falsified by the game
  plan's own commits.

## Decisions changed

| # | Decision | Evidence, in one line |
|---|---|---|
| D-A | Execution order becomes **0 → 2 → 1 → 3 → 4 → 5 → 6a → 6b → 7 → 8**; phase numbers stay as identifiers | Phase 1's gates call `tools/run-domain-tests.sh`, which exits 2 on a cold clone (`:20-24` needs a jar dir only Phase 2's bootstrap creates); Phase 2's gate literals (188 tests / 24 classes / 23 files, confirmed accurate at HEAD) go stale the moment any code phase lands first; Phase 1b's repository writes have no test lane without Phase 2. |
| D-B | Phases 1a and 1b **merge into one phase** — one branch `claude/phase-1-session-hygiene`, one PR, one combined owner evening | 1b's acceptance greps already re-assert 1a's invariants, so running them back-to-back on one branch self-verifies; splitting them buys a second owner evening and nothing else. |
| D-C | D1's recommended default flips to **four tabs** (Home · Body · Plan · History); three tabs stays signable **with two mandatory mitigations** | PHASE_6A's settled Body order puts the session log below FIVE sections (window picker, silhouette, muscle rows, calendar, coach cards), and PHASE_6B deletes Home's `RecentSection` in **both** branches while building only a `section=calendar` anchor — so "what did I do last session" costs a tab change plus a long scroll. Four tabs keeps month grouping, the multi-session-day sheet, the PR row, the Library demotion and the `isTabRoute`/`restoreState` shim deletion, and drops only the size-L merge (6a: ~2-2.5 executor-days vs ~4-5). Mitigations if three tabs is signed: (i) a `section=sessions` anchor beside `section=calendar`; (ii) a **"Last session"** link row on Home — a link, not a recommendation surface, so the D3 map is untouched. |
| D-D | `movementKey` unifies on the **family** set; Phase 3 ships family keys for batch 1 from the start | Phase 3 read `movementKey` as a movement *pattern* (`squat`/`hinge`/`press`), Phase 7 as a lift *family* (`bench-press`, `row`); Phase 7's `LibraryGroupingTest` asserts "the `bench-press` family has 8 members" (4 batch-1 + 4 batch-2), which the pattern reading cannot satisfy. Re-keying later would rewrite rows the owner already reviewed. |
| D-E | `muscleKey` is **`CanonicalMuscle.name.lowercase()`** everywhere; Phase 7's `quads` becomes `quadriceps` | `quads` is an **alias** that normalizes to QUADRICEPS (`CanonicalMuscle.kt:122-129, :60`), so the old invariant ("normalizes to a non-OTHER CanonicalMuscle") passes on a value that is wrong in the column. The test is strengthened to exact equality, making drift a build failure. |
| D-F | **No phase gate may depend on a CI run**; gates become owner-machine output pasted in the PR | CI has never executed in this repo (account billing block, `docs/DEVELOPMENT.md:95-105`); four packets carried "CI green on the PR" gate lines that were unsatisfiable by construction. CI green survives as an *additional* check once the owner's standing, non-gating errand lands. |
| D-G | **Mandatory phase-start re-baseline** as the executor's first commit on every phase branch | Packet literals are a snapshot of commit `2212628`, and prior phases (plus the game plan's own doc commits) legitimately move them; without a stated rule, a fresh executor reads expected drift as "the packet is wrong" and stops. Drift explained by merged phases is EXPECTED; only unexplained drift stops the phase. |

## Defects fixed

- **Phase 0's three baseline collisions**, one of them a hard blocker: the packet's
  acceptance gate ran an `ls` on `docs/gameplan/` expecting it to be **absent** and its
  first work item was "commit `docs/gameplan/`" — both already done at HEAD (15 files
  committed), so the gate could never go green and the work item was a no-op. The third:
  "has Phase 0 merged?" was tested by the presence of `PROTOCOL.md`, which is committed
  on the working branch; the correct test is `grep -c "Signed:" docs/ROADMAP.md`
  returning `0`.
- **PROTOCOL's contradictory preflight**: the documented invocation would have reported
  success while **four of the eight static checks silently passed** without running. The
  script contract and the documented gate are now the same thing.
- **`LIVE_BAR_HIDDEN_ROUTES` ownership gap**: Phase 1a creates the list, Phase 6b deletes
  `Route.StartWorkout` — one of the routes in it — and no packet owned the amendment. It
  is now an explicit 6b work item in both IA branches.
- **`ThisWeekCard` naming**: Phase 4 extracts `ThisWeekHomeCard` to
  `ui/home/ThisWeekCard.kt` while Phase 6b referenced the old symbol; one name now, in
  both packets.
- **`normalizeKey` visibility**: `nameKey` generation was specified as "the same function"
  as `MuscleNormalizer.normalizeKey`, which is `private` (`CanonicalMuscle.kt:161`).
  Phase 3 now has an explicit, non-skippable work item to expose it (public `normalizeKey`
  or a public `nameKeyOf`) — otherwise the seeder silently grows a second normalizer and
  the uniqueness invariant is enforced against the wrong string.
- **The sixth rest-overlay mandate**: the plan enumerated five places the rest-overlay
  contradiction appears; `DESIGN_AUDIT.md:711` carries a sixth, and Phase 0's repair list
  now closes all six.
- **Estimate mismatches** between ACTION_PLAN and the packets' own §9 blocks, and **dead
  pointers** (cross-references to sections that had moved or been renamed).
- **The 179-count**: a domain-test count that no longer matched the tree. The verified
  figures at HEAD are **188 tests across 24 classes in 23 files** — the literals Phase 2's
  gate asserts, which is exactly why Phase 2 now runs before any code phase (D-A).

## What held

The strongest results of the round, all evidence-first:

- **All 37 batch-1 catalog ids re-slugged by hand: zero mismatches.** Every id in Phase 3's
  normative table regenerates from its display name under the stated rule.
- **The batch-1 credit table is internally consistent** — primaries at 1.0, weighted
  secondaries in range, no row contradicting another, no muscle over-credited across a
  family.
- **The `arrangeKinds` adjacency bug is real**, confirmed by hand-trace of the current
  implementation, not inferred: Phase 4's fix and its test target the actual defect.
- **Room's extra-index crash-loop was verified in Room's own source**: validation filters
  indices by `origin == 'c'` and then requires **strict set equality** — so an index
  created in raw SQL and absent from the entity crash-loops the app at open with no
  destructive fallback. The "nothing exists outside the exported schema JSON" rule is
  correct as written.
- **TEXT-column default auto-quoting was verified in the Room compiler**, and — the part
  that matters — **quote mismatches are NOT forgiven** during validation. Phase 3's S1
  rule ("annotation defaults and migration SQL must agree byte-for-byte with the generated
  `2.json`") is not pedantry; it is the difference between a phone that opens and one that
  does not.
- **The Gson null hazard reproduced empirically** with the repo's exact Gson 2.11.0 +
  Kotlin 2.0.21 — and it is **worse than documented**: Gson's `Unsafe` allocation bypasses
  constructors, so Kotlin **default parameter values are bypassed too**, not just
  non-null enforcement. A field absent from a backup JSON lands as `null` in a
  non-nullable Kotlin property regardless of the default written next to it. Phase 3's
  validator-before-decode ordering is load-bearing.
- **`run-as` refusal verified in AOSP source**: a release-signed, non-debuggable install
  genuinely cannot have its database pulled. Phase 3's rehearsal runbook — SAF JSON export
  → v1 APK on an emulator → restore → upgrade in place — is the only shape that works.
- **The androidx.test pins are byte-identical to Robolectric 4.14.1's own catalog**, so
  Phase 2's version block cannot drag in a mismatched transitive runner.

## New risk

**Robolectric 4.14.1 falls back to LEGACY SQLite on Windows, and LEGACY breaks Room
validation of composite primary keys.** `SQLiteModeConfigurer.defaultValue()` selects
NATIVE SQLite on every host **except** Windows, where it hard-falls back to LEGACY
(SQLite 3.7.10). LEGACY's `PRAGMA table_info` cannot express a composite primary key —
which is exactly the shape of `exercise_muscles(exerciseId, muscleKey)` — so Room's
open-time schema validation fails there for reasons that have nothing to do with the
migration under test.

Consequence, recorded in PHASE_2_TEST_SUBSTRATE: **the JVM/Robolectric migration lane
requires a macOS or Linux host.** On a Windows host the `connectedDebugAndroidTest`
emulator lane is the only valid migration lane, and Phase 3's gate must be met there
instead. This must be settled before Phase 3 is planned, not discovered inside it.

## Scope additions

Small, owner-visible, all recorded in packets rather than left implicit:

- **Post-finish notes editing** added to Phase 1b (work item 1b-6). `updateSessionNotes`'s
  finished-guard is relaxed on the same reasoning as `updateSet`'s: notes carry no
  timestamp, PR or heat semantics, so nothing downstream shifts.
- **Backdated / manual session entry** recorded as an explicit **non-goal** (Phase 1b).
  Real backdating needs an editable session date and its own decision; nothing in this
  plan smuggles it in.
- **Bodyweight input** recorded as an **owned follow-up** in Phase 7 with two signable
  dispositions. Phase 7 ships 20+ `BODYWEIGHT`/`BODYWEIGHT_PLUS` rows while the app stores
  the owner's bodyweight nowhere, so those lifts log 0 kg of volume and blank e1RMs. Ship
  either way; do not ship the rows and the copy with the arithmetic unexplained.
- **The deload affordance gap** recorded in Phase 5: the Deload rule can fire, but the app
  offers no way to *act* on it beyond training less. Named as a follow-up requiring an
  owner decision rather than quietly built.
- **Phase 8 marked explicitly optional.** Nothing depends on it; `imageKey == null` means
  "compose the thumb", which is a complete shipping state. Deferring it indefinitely costs
  nothing.
- **The empty-week cliff mitigated in Phase 4**: this phase deliberately deletes the
  auto-generated ghost week, so the empty state is what the owner sees on the first launch
  after the update. `ThisWeekCard`'s empty state gets **"Suggest a week"** as its primary
  action — recovery is one tap from Home, not a tab hunt followed by a button hunt.

## Residual risk

1. **The migration against the only real dataset** (Phase 3) — unchanged as the plan's
   top risk; the rehearsal runbook, the pre-open raw copy and the two lanes are the fence.
2. **Host OS for the JVM lane** (new, above) — settle before Phase 3.
3. **~800 authored data points** across Phases 3 and 7 feeding the heat map silently —
   invariant tests, generated review artifacts and the owner's heat-diff sign-off.
4. **Owner evenings are the scarce resource.** D-B recovers one; working CI would recover
   more, which is why the billing errand is now a *requested* (still non-gating) Phase 0
   item.
5. **Estimate risk concentrates in Phases 4 and 6a** (the six-flow combine and the `when`
   ripple; the merged-screen construction under the three-tab branch).
6. **Packet literals keep drifting** as phases merge — mitigated by D-G, not eliminated.

## The one pre-start verification that matters

**The owner's first `./gradlew testDebugUnitTest` with Phase 2's dependencies in place.**
That single run settles three open questions at once: whether Robolectric 4.14.1 is
compatible with this project's Gradle/AGP/Kotlin toolchain, whether the host OS gives the
NATIVE SQLite the migration lane requires, and whether the toolchain assumptions written
into the packets survive contact with a real compiler — no in-session environment here has
an Android SDK, and CI has never run.

That is exactly why Phase 2 now runs first (D-A): the plan's largest unverified assumption
is retired on the first evening, in the cheapest phase, before any code phase depends on
it.

---

## Addendum — 21 Aug 2026: the Windows risk is confirmed, not hypothetical

The second pass recorded one **new risk**: Robolectric falls back to legacy SQLite on
Windows hosts, where `PRAGMA table_info` cannot express composite primary keys, so Room
schema validation fails falsely for entities that have one — exactly what Phase 3's
`exercise_muscles` uses. It was written as a contingency.

**It is now a fact.** The owner's machine is Windows/PowerShell, discovered when the
Phase-2 owner gate was first run. What it changes:

- **The JVM/Robolectric lane cannot gate any migration work on this project.** A red
  `SchemaV1BaselineTest` or `Migration1To2Test` on this host proves nothing about the
  schema. Phase 3's gate is amended: `connectedDebugAndroidTest` on an emulator is
  **required**, not optional, and is the migration lane of record. JVM-lane results are
  informational.
- **The `tools/` scripts need Git Bash or WSL**; they are `#!/bin/sh` and cannot run in
  PowerShell. They remain the *executor's* pre-push gate, so this never blocks a phase —
  the owner's gate is Gradle plus the device checklist.
- **Gradle is `.\gradlew.bat`**, not `./gradlew`.
- **`.gitattributes` added.** Without it a Windows clone can check out the shell scripts
  with CRLF, and `#!/bin/sh\r` fails as "bad interpreter" — a failure whose message points
  nowhere near its cause. The attributes pin `*.sh` and `gradlew` to LF and `*.bat` to
  CRLF, and mark the binary assets so they are never converted.

Optional upgrades that would restore the fast lane, neither of them blocking: WSL2 (clone
into the Linux filesystem, run the JVM lane there), or unblocking CI (ubuntu runners get
the JVM lane free on every push).

Recorded because it is the clearest vindication of the second pass's method: the finding
came from verifying an external mechanism against primary sources rather than from
anything visible in this repository, and it arrived one phase before it would have cost a
migration.
