# tools/compose-stubs — the Android-only half of the Compose classpath

These files are read ONLY by `tools/compile-check.sh --compose`. They are never compiled into
the app, never packaged, and never executed. Every body is `TODO("compile-only stub")`.

## Why they exist

The Compose stage compiles the app's Compose sources against **JetBrains Compose Multiplatform
1.8.2** (`org.jetbrains.compose:*-desktop`), which is a real build of `androidx.compose.*` and is
the only one reachable from Maven Central. It is a *different build of a nearby version*: the app
builds against Compose BOM 2026.06.01, i.e. UI/foundation/runtime **1.11.4** and Material3 1.4.0
(see `docs/architecture/compose-toolchain.md`).

Two kinds of thing are therefore missing from that classpath, and both live here:

1. **Android-only APIs.** Compose Multiplatform has no `android.content.Context`, so it has no
   `LocalContext`, no `LocalView`, no `painterResource(Int)`, no `Font(resId)`. These exist in
   Android Compose and are not optional — they are stubbed at the signature the Android artifact
   declares.
2. **APIs newer than 1.8.2.** `WindowInfo.containerDpSize` (1.9) and `LocalResources` /
   `LocalLocale` (1.9 / 1.11) are real Android Compose APIs that the substituted build predates.

Separately, `androidx.activity`, `androidx.activity.compose`, `androidx.lifecycle.compose`,
`androidx.lifecycle.viewmodel.compose` and `androidx.navigation(.compose)` are Google-only
artifacts with no Maven Central mirror at all, so their (small) used surface is stubbed here too.

## The soundness rule

**A stub must be no more permissive than the real API.** Being *stricter* is the safe direction:
it can only produce a RED that the merge gate would not, which is visible and fixable. Being
looser hides a real error, which is the failure mode this whole lane exists to avoid.

Concretely, that means:

* Nullability is copied from the real declaration, never relaxed. `ActivityResultLauncher.launch`
  takes `I`, `NavDestination.route` is `String?`, `SoftwareKeyboardController` arrives nullable
  from its composition local, and every one of those forces a null check in the app source.
* Overloads that exist in the real API but that no app source calls are deliberately OMITTED.
  A new use of one shows up as a RED, which is a prompt to widen the stub on purpose, rather
  than passing silently against a surface nobody checked.
* Nothing returns a plausible value. Every body throws, so a stub can never make a *behavioural*
  test pass — this lane is a type-check and only a type-check.
* Variance is copied exactly (`ActivityResultContract<I, O>`, `CompositionLocal<T>`), because a
  wrong variance is precisely the kind of looseness that swallows an error.

`tools/compile-check.sh` prints the full substitution ledger with `--explain`.

## Two things about how these are compiled

**They are their own module.** `tools/compile-check.sh` compiles this directory (together with
`tools/compile-stubs/`) under `-module-name stubs` and puts the output on the classpath of the app
compilation. `internal` therefore means what it means in a real artifact — invisible to app
sources — instead of being visible to everything the way it was when the stubs were compiled as
app sources.

One consequence to keep in mind: `compose_ui_windowinfo.kt` SHADOWS the 1.8.2
`androidx.compose.ui.platform.WindowInfo`, and that shadow is now **classpath order** (the stubs
output precedes the Compose jars) rather than source-beats-classpath. It still fails safe — if it
stopped applying, `containerDpSize` would be unresolved, i.e. a RED — and `--self-test`'s positive
control asserts it.

**The Compose jars are filtered first.** Before anything is compiled, every desktop-only class is
deleted from the Compose Multiplatform jars — 703 of them: `VerticalScrollbar`,
`rememberScrollbarAdapter`, `Modifier.onClick`, `Modifier.onPointerEvent`, the
`androidx.compose.ui.res` desktop loaders, `painterResource(String)`, `Font(String)`, the
`androidx.compose.ui.window` desktop API and `androidx.compose.ui.awt`. None of those exists in
any version of Android Compose, and none can now be resolved however the call is spelled. That is
why the `painterResource(Int)` and `Font(resId)` stubs in this directory are the ONLY overloads
that resolve, and why `--explain` calls the six kept facades the residual risk.
