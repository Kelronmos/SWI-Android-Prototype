# SWI Android Recovery Suite — implementation status

This is an incremental, local recovery-evidence feature. It does not replace the Python SWI governing spine, grant authority, or perform recovery writes.

Author: Keletso Ronald Mosidila

## Implemented in this increment

- A user enters an existing SWI key and stable item identity; the app does not generate substitute identities.
- The app records the original user-selected document URI and SHA-256 in an app-private append-only journal. Every later attempt repeats the same key, item identity, last known location, and baseline digest.
- The journal is hash chained and refuses further writes after an invalid or incomplete record. This detects some accidental changes; it is not a signature, secure audit anchor, or proof of origin.
- SAF document selection limits inspection to user-authorized document URIs. The app does not crawl storage or search undocumented locations.
- Source unavailability, access denial, provider/storage failure, digest mismatch, and a matching candidate are recorded distinctly. Provider errors do not prove deletion.
- A matching user-selected candidate is only `CANDIDATE_INTEGRITY_MATCH`; it is not copied, restored, or treated as recovered. No overwrite/delete/restore action is implemented.
- Each attempt records detection time, selected search URI, action, content source/hash when readable, verification result, uncertainty, `HUMAN_AUTHORIZATION=REQUIRED`, and no approval record unless one is later supplied by an authorized system.
- A separate local update notice checks the installed app version when `MainActivity` opens and posts a SWI-orange Android notification after a version increase, subject to the Android notification permission. This is not server push; it does not run until the app is opened and it does not prove update authenticity.

## Not implemented or not established

- No automatic file-system scan, migration/repository recovery, dependency/runtime repair, or full system fault diagnosis.
- No FCM or other remote push integration, signed update manifest verification, or automatic notification before the updated app is opened.
- No reliable distinction between a deleted item, moved item, disconnected storage, provider failure, and corruption when the Android document provider does not expose that evidence.
- No key recovery, signing-key export, or remote identity recovery. An SWI key string in the ledger does not prove the underlying file exists or can be recovered.
- No restore/copy/replacement function and no SWI authorization backend. A future write/restore path must bind a separate human approval to the exact item, source, destination, and operation.
- Journal tamper evidence is local and unkeyed. Device compromise, app-data loss, or a recomputed chain is outside this increment.
- No production authorization, proof, or seal.

## Local learning corpus review

- Internal storage `/storage/emulated/0/Download` contained 805 top-level entries, 362 with SWI-like names at inspection. The SD-card `/storage/1291-1611/Download` folder had no top-level entries. The separate `Downloads` folders contained one SWI-named ZIP each; they were not treated as the canonical source.
- The local `swi-v5.12_... (91% ).zip` was read without extraction. Its SHA-256 is `c7c6a280866201947ba053e9ad32a9b23063204bebb28f49ca86e077b7fcb037`. Its included `SWI_SYSTEM_STATE.json` labels the package `RESEARCH_PROTOTYPE`, marks crypto and ZK as simulated, consensus as not implemented, and says `DO_NOT_DEPLOY`. This archive is not production-readiness evidence.
- The archive includes `SWI-PAY-010 RECOVERY_UNSAFE` → `HALT` and the invariant `ERROR → RECORDED → OUTCOME → EVIDENCE`; those labels informed the fail-closed journal behavior. Its `RollbackAuthorityEngine.ts` uses a hard-coded allowlist, so it was not adopted as the Android authority source.
- Two local `SWI_SYSTEM_ERROR_KEY` markdown copies have identical SHA-256 (`5fa4e9508467b1cc727b7d7554fc603e1a2e937982c8dac7a7d4126bf393f881`); this establishes byte equality only, not source authenticity.
- This is a targeted review of relevant local material, not a complete audit of every download or every account repository. No downloaded archive was extracted or copied into the application.

## Verification and scope record

- Android checkout branch: `feature/joe-alita-assistant-foundation`; final local HEAD after the recovery commits: `96be97f7da9188f5e2f2aaa67302f3e754c688f7`. No upstream is configured for this branch.
- A local metadata survey found 44 checkouts with a `github.com/Kelronmos` origin URL across 18 distinct origin URLs. This counts local clones/worktrees, not all online account repositories, and is not a source audit of those repositories.
- `git diff --check` passed for staged changes before commit. Python's XML parser reported the working manifest as well formed; this does not replace Android manifest/resource compilation.
- `gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` exited 1 before any task ran: `SDK location not found`. `ANDROID_HOME` and `ANDROID_SDK_ROOT` were unset, and no Android SDK was found in the inspected local paths.
- RecoveryLedger JUnit tests: **NOT RUN**. Android lint: **NOT RUN**. Debug APK build and device/emulator runtime: **NOT RUN**. No APK from these commits was produced.
- The Recovery Suite test and feature are committed as an unverified incremental change; they are not proven, sealed, or production authorized.

## Required distinctions

`DETECTED != RECOVERED != VERIFIED`

`KEY_EXISTS != FILE_EXISTS`

`RECOVERED != AUTHORIZED`

`TESTED != PROVEN`

`PROVEN=NO`

`SEALED=NO`

`PRODUCTION_AUTHORIZED=NO`
