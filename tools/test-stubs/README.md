# tools/test-stubs — the test-only half of the compile classpath

Read ONLY by the **test stage** of `tools/compile-check.sh`. Never compiled into the app, never
packaged, never executed. Every body is `TODO("compile-only stub")`.

## Why there are so few files here

`./gradlew testDebugUnitTest` is the first half of the merge gate, and it compiles
`app/src/test/java` (258 files) plus `app/src/sharedTest/java` (3, wired into the `test` source
set by `app/build.gradle.kts`). Almost every `testImplementation` dependency that source set uses
is **real**, fetched from Maven Central by the lane:

| dependency | where it comes from |
|---|---|
| `junit:junit`, `org.hamcrest:hamcrest-core` | real, at the catalog's version |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | real, at the catalog's version |
| `org.robolectric:robolectric` + `annotations`, `shadowapi`, `shadows-framework`, `junit` | **real** — Robolectric publishes to Maven Central, so `RobolectricTestRunner`, `@Config`, `Shadows.shadowOf` and `Robolectric.buildService` are the actual classes |

Only three artifacts have no Maven Central route, and between them the test source set uses five
declarations. Those five are what this directory holds:

| file | stands in for | used by |
|---|---|---|
| `androidx_test.kt` | `androidx.test:core` → `ApplicationProvider.getApplicationContext<T>()` | 194 call sites |
| `androidx_test_platform.kt` | `androidx.test:monitor` → `InstrumentationRegistry.getInstrumentation()` | the 5 migration tests |
| `room_testing.kt` | `androidx.room:room-testing` → `MigrationTestHelper` | the 5 migration tests |
| `datastore_factory.kt` | `PreferenceDataStoreFactory.create(...)` | `FakeAppDependencies` and one repository test |
| `core_res.kt` | `androidx.core.content.res.ResourcesCompat.getFont` | `TabularNumeralTest` |

## The rules these follow

Same as `tools/compile-stubs/README.md`, plus one that is specific to this directory:

* **A stub must be no more permissive than the real API.** `ApplicationProvider`'s type parameter
  is bounded by `Context`; `ResourcesCompat.getFont` returns `Typeface?`; `MigrationTestHelper`'s
  class parameter is `Class<out RoomDatabase>`.
* **New top-level declarations only.** Anything that changes an EXISTING class from
  `tools/compile-stubs/` (a Room builder method, a `SavedStateHandle` constructor) belongs
  *there*, not here — a second copy of `RoomDatabase.Builder` on the test classpath would shadow
  the first one wholesale and this lane would stop meaning anything. What lives here is only what
  the base and compose stages must NOT be able to resolve, so that a main source which started
  using it is a visible RED instead of an unnoticed widening.
* **Declare only what is used.** A member the tests do not call is not declared, so a new use
  fails with "unresolved reference" and this directory gets one more line.

## What this cannot check

The stubs are declarations, not behaviour. `MigrationTestHelper` here validates nothing; the real
one reads `app/schemas/*.json` and asserts the migrated schema matches. `ApplicationProvider`
returns nothing. Only `./gradlew testDebugUnitTest` runs any of it. This stage answers exactly one
question: **do the unit tests still compile against the app?**
