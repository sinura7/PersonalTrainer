# Verifier clusters (relaunch with VERIFY.md)
V1 sync/auth/server: B1 §2 S-11..S-14; B2 §2 PV-3, S-11, PV-2, S-12; B3 §2 S-12, S-11, S-13, S-14; B7 §2 S-11. Dups: B1 S-13≈B3 S-12 (serializeNulls); B2 S-12≈B3 S-14 (deleteAllRows); B1 S-14 vs B3 S-11 (second account). Arbitrate P1: S-14/B3 S-11 (data under another account); PV-2 (headers in logs).
V2 data/backup: B4b §2 DB-1, DB-2; B5 §2 BK-1..BK-4; B7 §2 DB-1 (usageFor); B10b §2 UI-1 (=BK-1), UI-3 (generate-week deletes block); B9a §2 L-3 (=DB-2 root). P3 spot: B4a DB-1, DB-4; B4b DB-14. Arbitrate P1: UI-3, BK-3, L-3.
V3 timer/reminders/updater: B6 §2 RT-1..RT-3; B7 §2 RM-1; B11 §2 UI-1 (=RT-2). P3 spot: B6 RT-4, RT-5; B7 RM-10, RM-2. Arbitrate: RT-3 owner decision; RT-1 stands unless shade paints dark bg.
V4 domain/arch/tests/build: B8 §2 DM-1, DM-2; B13a §2 all (AR-1, AR-2); B13b §2 TS-1..TS-4; B14 §2 BR-1, BR-2, DC-1, BR-3. Dups: TS-2≈BR-3; AR-1≈B4b DB-1. P3 spot: B8 DM-3, C-3. Arbitrate: BR-1 leads build section.
V5 screens: B9a §2 UI-1, UI-12; B9b §2 UI-1, UI-3; B10a §2 UI-1, UI-2; B10b §2 UI-2, UI-4; B10c §2 UI-1..UI-5 (UI-3≈B9b UI-3). P3 spot: B9a UI-13; B10a UI-5; B9b UI-4. Arbitrate: B10c UI-1 first-run blocker?; B9a UI-12.
V6 floor/design: B11 §2 UI-2; B12 §2 AX-15, AX-14, AX-1, AX-2; B9b UI-2 (=AX-2); B10b UI-5 (=AX-15). P3 spot: B12 heading inversion, contrast 3.15:1; two B11 P3s. Arbitrate: B11 UI-2 P1?
Probe files: verify/probes/AuditProbe{Sync,Data,Timer,Domain,Screens,Floor}Test.kt, package com.sinura.personaltrainer.audit, method claim_<ID>_holds(), green = confirmed.
