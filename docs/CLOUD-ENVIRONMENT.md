# Cloud environment

How a Claude Code cloud session is set up to build and test Temper, what it
can reach, and what it can never do. Written 2026-09-07, after a session that
could run neither the Android compiler nor the Android tests and had to
substitute careful reading for both.

## Why this file exists

A cloud session runs on a fresh Ubuntu VM that is destroyed when the session
ends. Out of the box it carries Java, Python, Git, Gradle and Node, but no
Android SDK, and its default network policy refuses Google's Android hosts.
That leaves roughly half of this app unbuildable and untestable inside the
session: the pure-Kotlin lane runs, and everything touching Compose, Room or
AndroidX cannot even be compiled.

Three defects shipped to the branch that week for exactly that reason: an
import of a Compose test-rule member as though it were a top-level function,
a call whose generic could not be inferred, and a saved-state guard keyed on
the wrong field. A compiler catches the first two instantly. Reading found
them eventually, at the cost of three review agents.

## What the environment needs

Open the environment selector at [claude.ai/code](https://claude.ai/code):
the cloud icon in the row above the message box. Hover the environment and
select the settings icon. There is no settings page or direct URL for it.

**Network access: Custom**, with these in Allowed domains:

```text
dl.google.com
maven.google.com
```

Tick **Also include default list of common package managers**. Custom
replaces the Trusted list rather than extending it, and Trusted is what
supplies Maven Central, `services.gradle.org`, `plugins.gradle.org` and the
Ubuntu archives. Losing those breaks the lanes that already work. Trusted
carries `developer.android.com`, which is documentation, not artifacts; the
two hosts above are the ones that serve the SDK and every AndroidX, Compose
and Room artifact.

**Environment variables:**

```text
ANDROID_HOME=/opt/android-sdk
ANDROID_SDK_ROOT=/opt/android-sdk
```

**Setup script:** the contents of [`tools/cloud-setup-android.sh`](../tools/cloud-setup-android.sh).
It installs the command-line tools, platform 36, build-tools and
platform-tools, accepts the licences, and adds JDK 17 to match `ci.yml`.
The script is snapshotted after its first run, so later sessions start with
the SDK already on disk and skip it. It re-runs when the script or the
allowed hosts change, and when the snapshot expires after about a week.

## What that unlocks, and what it does not

| Lane | Before | After |
|---|---|---|
| Static gate, `tools/preflight.sh` | works | works |
| Pure-JVM lane, `tools/run-domain-tests.sh` | works | works |
| `./gradlew compileDebugKotlin` | impossible | works |
| `./gradlew testDebugUnitTest` including Robolectric | impossible | works |
| `./gradlew lintDebug` | impossible | works |
| `./gradlew assembleDebug`, a real APK | impossible | works |
| `connectedDebugAndroidTest`, goldens, journeys | impossible | still impossible |

The last row is hardware, not policy. `/dev/kvm` does not exist on these VMs
and the CPU exposes no virtualisation flags, so an Android emulator cannot
start at any speed. Device work stays with the owner's phone or with a
machine that has an emulator. No setting changes this.

## Limits worth knowing

Sessions get roughly four vCPUs, sixteen gigabytes of memory and thirty
gigabytes of disk. A Gradle build of this project fits comfortably. The
first build in each session still downloads the Gradle dependency cache,
around a gigabyte, because the snapshot captures what the setup script wrote
and the setup script deliberately does not spend its five-minute budget
warming `~/.gradle`.

`gradle/verification-metadata.xml` pins a checksum for every dependency and
`verify-metadata` is on. CI has already failed on entries missing from that
ledger. Expect the first real build to surface a few more, and extend the
ledger rather than disabling verification.

Google's hosts are the only ones added. Do not reach for an unofficial
mirror of the SDK if something is missing: this repository pins every
dependency and runs a supply-chain checker, and pulling the build toolchain
itself from an unverified source would defeat the thing that policy exists
to protect. Name the blocked host instead; the agent proxy records it at
`$HTTPS_PROXY/__agentproxy/status`.
