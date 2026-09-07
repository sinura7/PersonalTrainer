Declaration-only stubs for `tools/compile-check.sh`.

WHAT THESE ARE. Kotlin source files that declare — with no working bodies — the
external APIs this app compiles against but which cannot be downloaded in an
environment with no access to Google's Maven. They exist so a plain Kotlin
compiler can type-check `app/src/main/java`. They are NEVER executed, never
packaged, and never on any Gradle classpath: `./gradlew` links the real
artifacts and remains the source of truth.

WHY THEY EXIST. `dl.google.com` and every mirror is denied by this
environment's egress proxy, so androidx.room, androidx.datastore,
androidx.work, androidx.lifecycle and Play Services have no downloadable jar.
Everything that DOES exist on Maven Central is used for real instead of being
stubbed (see the jar list in tools/compile-check.sh). These files cover only
the remainder.

THE SOUNDNESS RULE. A stub must be **no more permissive than the real API**.
A loose stub hides real errors: `set(key: Any, value: Any?)` makes every write
type-check, and the lane then reports GREEN on exactly the mistakes it exists
to catch. Concretely:
  * every generic parameter keeps its real variance (invariant stays invariant);
  * a nullable return stays nullable (`get(): T?`, never `T`);
  * `suspend` on a function or on a lambda parameter is preserved;
  * a required parameter of the real API gets NO default here;
  * no member is invented that the real API does not have.
Being STRICTER than the real API is allowed and is the safe direction: it can
only produce a visible failure ("unresolved reference — extend the stub"),
never a silent pass. Several stubs below are deliberately narrower than the
real thing, and each says so.

Every function body is `TODO("compile-only stub")` or a throw. None of them
returns a plausible value, so a stub can never be mistaken for a runtime fake.

THEY ARE THEIR OWN MODULE. `tools/compile-check.sh` compiles this directory
first, under `-module-name stubs`, and puts the OUTPUT on the classpath of the
app compilation — it does not hand these files to the app compile as source.
That is load-bearing, not tidiness: `internal` in Kotlin means "visible inside
this module", so while the stubs and `app/src/main` shared one module every
`internal constructor` here was reachable from app code. Twenty-nine of them
were, including `Preferences.Key<Boolean>("x")`, `MutablePreferences()`,
`androidx.work.Data()`, `WorkerParameters()`, `OneTimeWorkRequest.Builder()`
and `AuthorizationRequest.Builder()` — all of which compiled clean and all of
which Gradle rejects, because in the real artifacts those constructors are in
another module. Keep using `internal` for anything the app must not be able to
construct; it now means what it means in a jar.

A NOTE ON SCOPE. Anything the TEST source set needs and main does not belongs
in `tools/test-stubs/`, so that a main source which starts using it is a
visible RED. The exception is a member of a class declared HERE — a Room
builder method, a `SavedStateHandle` constructor — which has to be added here,
because a second copy of the class on the test classpath would shadow this one
wholesale. Those additions are marked with the reason at the declaration.
