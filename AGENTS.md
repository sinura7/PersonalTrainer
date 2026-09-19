# Working on Temper

- Read `docs/FOUNDATION_PROGRAM.md` and the applicable accepted decisions in
  `docs/architecture/` before changing product behavior. Use `docs/DEVELOPMENT.md`
  for verification and `SETUP.md` for distribution.
- The long-lived branch is `trunk`. Keep changes to one implementation packet
  at a time. Inspect existing work before editing overlapping files.
- On the Windows desktop, `tools/dev-windows.ps1` selects the installed JDK 17,
  Android SDK 36, Git shell tools, and Python. With no arguments it checks Gradle
  startup. The full local command is:
  `./tools/dev-windows.ps1 testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest`.
- Never bypass static checks or describe a toolchain check as a successful build.
  Report actual test results and distinguish host limitations from app failures.
  Consult the Windows notes in `docs/DEVELOPMENT.md` for database test limitations.
- ADR-024 amends older blanket statements against hosted CI: the deterministic
  `Tests, lint, debug build` job may block merges; the hosted emulator job does not.
  Hosted checks supplement the local gate.
- The owner tests Temper Debug through Obtainium. This route is already working.
  Keep the stable debug distribution signing identity and increasing version codes.
  Follow `tools/debug-drop-plan.py` and `SETUP.md` when preparing an authorized drop.
  Local debug builds may have a different signer; do not install them over the
  Obtainium build without checking the signing identity.
- Keep everyday Temper and Temper Debug separate. Never use the owner's real
  workout history as disposable test data.
- Explain outcomes in plain language and state the next step. The owner makes
  product decisions; the agent handles routine implementation and verification.
