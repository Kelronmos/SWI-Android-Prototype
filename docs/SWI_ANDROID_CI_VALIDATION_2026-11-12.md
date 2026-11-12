# SWI Android — CI + Unit Test Validation

**Date:** 2026-11-12  
**Branch:** `feature/live-hardware-binding-2026-11-12`  
**HEAD at validation authoring:** see latest commit on branch after this file  
**Base (main):** `8d7fd46ab13a6e433fbb46f73965ba5187dd5146`

```
PROVEN = NO
SEALED = NO
PRODUCTION_AUTHORIZED = NO
```

## Phase A — Baseline

| Item | Result |
|------|--------|
| Branch | `feature/live-hardware-binding-2026-11-12` |
| HEAD (pre-test commits) | `e8398ce0db30c1829c4f31b6461f4dcd8a6a1df5` then subsequent test/CI commits |
| Working tree on GitHub | Clean remote branch; changes committed |
| Gradle wrapper in repo | **ABSENT** — CI uses `gradle/actions/setup-gradle` with Gradle 8.7 |
| Root `build.gradle.kts` | AGP 8.6.1, Kotlin 2.0.21 |
| App module | `com.trustsmotion.swiandroid`, minSdk 30, compile/target 35 |
| Existing CI | `.github/workflows/android.yml` (assemble only on main) |
| Local sandbox JDK | OpenJDK 21 present |
| Local Android SDK | **UNAVAILABLE** (`ANDROID_HOME` empty) → local APK **BLOCKED** |

## Phase B — JVM tests added

| File | Coverage |
|------|----------|
| `HardwareObservationTest.kt` | BOUND→STALE, HOLD no upgrade, ABSENT/UNKNOWN not stale, null proximity value, wall-clock age math, non-claims, export map |
| `ObservationStoreTest.kt` | Export JSON non-claims false; capacity eviction |
| `GuidedBreathingSessionTest.kt` | start/pause/stop observations; authorization NOT_AUTHORIZED |
| `VoiceMatchObservationTest.kt` | MATCH/MISMATCH/EMPTY; voice≠identity; biometric≠workflow auth non-claim |

`app/build.gradle.kts` now includes `testImplementation` JUnit and `testOptions.unitTests.isReturnDefaultValues = true`.

### Local execution

| Command | Exit |
|---------|------|
| Local `gradle testDebugUnitTest` | **BLOCKED** — no Android SDK / no project Gradle wrapper in this environment |
| Local `assembleDebug` | **BLOCKED** — same |

Tests are intended to run in GitHub Actions after push to this branch.

## Phase C — Android build (this environment)

| Step | Result |
|------|--------|
| Clean generated outputs | N/A (no local build tree) |
| JVM unit tests | **NOT RUN locally** |
| Android lint | **NOT RUN locally** |
| Assemble debug APK | **BLOCKED** (no Android SDK) |
| APK SHA-256 | **UNKNOWN** (no artifact) |
| Manifest inspect | Source manifest reviewed: `USE_BIOMETRIC` only dangerous-adjacent; sensors optional features; no RECORD_AUDIO |
| Crypto proof flow | **Unchanged** in `MainActivity.kt` (biometric + Keystore sign; NOT AUTHORIZED messaging retained) |

## Phase D — CI changes

`.github/workflows/android.yml` updated to:

- Trigger on `main`, `feature/live-hardware-binding-2026-11-12`, PRs to main, `workflow_dispatch`
- `gradle testDebugUnitTest`
- `gradle lintDebug`
- `gradle assembleDebug`
- APK size + `sha256sum` + artifact upload
- Unit test results artifact upload (`if: always()`)

CI run outcome after this push: **UNKNOWN until Actions completes** — do not treat this document as a green CI certificate.

## Phase E — Honest totals

| Category | Count / status |
|----------|----------------|
| Unit test classes added | 4 |
| Unit tests designed | 15+ methods |
| Unit tests executed in this session | **0** (SDK blocked) |
| Unit tests passed | **UNKNOWN** |
| Unit tests failed | **UNKNOWN** |
| APK built here | **NO** |
| Device tests | **NOT RUN** |
| Merge to main | **NOT DONE** |

## Non-claims

- Successful CI (if later green) ≠ PROVEN ≠ SEALED ≠ PRODUCTION_AUTHORIZED  
- Unit tests ≠ physical sensor evidence  
- Export JSON ≠ authorization record  
- Voice MATCH ≠ human identity  
- Biometric success ≠ workflow authorization  

## Safe install posture

Source and tests on this branch may be reviewed. Debug APK may be produced by CI. **Do not** treat any artifact as production-authorized.
