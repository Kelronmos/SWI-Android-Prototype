# SWI Android — Validation baseline

**Date:** 2026-11-12 (Botswana)  
**Branch:** `feature/live-hardware-binding-2026-11-12`  
**Banner:** **EXECUTION EVIDENCE PENDING**

```
PROVEN = NO
SEALED = NO
PRODUCTION_AUTHORIZED = NO
MERGE = NOT GRANTED
```

## Current state

| Validation dimension | Current state |
|----------------------|---------------|
| Test implementation | Reported implemented |
| Local test execution | Not executed |
| CI test execution | Unknown |
| Android build (local) | Blocked |
| Device hardware verification | Unknown |
| APK integrity | Unknown |
| Merge authorization | Not granted |

**Scar:** `docs/scars/SWI-ANDROID-2026-11-12-001.md` (OPEN)

## Recommended improvements (pre-complete)

| Improvement | Purpose | Status |
|-------------|---------|--------|
| Commit Gradle wrapper | Reproducible Gradle version | NOT DONE |
| Pin JDK and Android SDK in CI | Reproducible build environment | PARTIAL (Java 17 + SDK packages in workflow) |
| Preserve JUnit XML | Machine-readable test evidence | CI upload path added; results UNKNOWN |
| Preserve lint reports | Static-analysis evidence | NOT yet artifacted explicitly |
| Verify APK SHA-256 | Artifact integrity | CI step present; hash UNKNOWN until run |
| Hardware-unavailable tests | UNKNOWN/HOLD behaviour | PARTIAL (unit-level ABSENT/UNKNOWN) |
| Stale observation replay | Defensive reflex verification | PARTIAL (unit stale tests) |
| Export integrity tests | Non-claims survive export | PARTIAL (ObservationStoreTest) |
| Guard `isReturnDefaultValues` | Defaults ≠ real hardware | **Process note only** — not automated |

## Next validation sequence

Each step must record its **actual** result. Blocked ≠ pass.

```text
01 BASELINE_REPOSITORY
02 VERIFY_GRADLE_WRAPPER
03 VERIFY_DEPENDENCIES
04 RUN_JVM_TESTS
05 RUN_LINT
06 BUILD_DEBUG_APK
07 VERIFY_APK_HASH
08 RUN_REGRESSION_TESTS
09 RUN_HARDWARE_ABSENCE_TESTS
10 RUN_BYPASS_TESTS
11 GENERATE_EVIDENCE_REPORT
12 REVIEW_AND_AUTHORIZE
```

| Step | Result at baseline |
|------|--------------------|
| 01 | DONE (branch inventory) |
| 02 | FAIL / GAP — wrapper absent |
| 03 | PARTIAL — declared in Gradle; not resolved locally |
| 04 | BLOCKED locally; CI UNKNOWN |
| 05 | BLOCKED locally; CI UNKNOWN |
| 06 | BLOCKED locally; CI UNKNOWN |
| 07 | UNKNOWN |
| 08–10 | NOT RUN |
| 11 | This baseline + scar + CI validation doc |
| 12 | NOT GRANTED |

## Architectural connection

Android contributes **evidence and tested defensive lessons** to the shared SWI foundation:

```text
Observations / failures
  → CEI (error intelligence)
  → SCAR (failure memory)
  → COMMON SENSE (consistency)
  → REFLEX (defensive responses)
  → GOVERNING SPINE (evidence → validation → human authority → action)
```

Do **not** create a separate Android-only intelligence or authorization system.  
Hardware-specific controls remain on-device; authorization remains on the single spine.

## Milestone definition

The next meaningful milestone is **not** a green dashboard.

It is:

1. A reproducible CI run with inspectable JUnit XML, lint output, and APK SHA-256.
2. Then real-device testing for sensors / biometric / lifecycle.

**No merge until evidence supports it.**
