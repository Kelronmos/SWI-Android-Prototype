# SWI Device Shield — Phase 1 Acceptance Report

**Mode:** READ → INSPECT → VERIFY → RECORD  
**Date (UTC):** 2026-11-10T23:02:00Z  
**Repository:** Kelronmos/SWI-Android-Prototype  
**Branch:** feature/android-device-network-diagnostics  
**Target commit:** 166891346b73a30dd76ad93007af23bd1ca92977  
**Previous HEAD:** fbc7bcdfec17d684a3499a53da79b6234d8c00b0  
**Version:** 0.4.0-launcher-phase1  

```text
NAMED ≠ IMPLEMENTED ≠ TESTED ≠ PROVEN ≠ SEALED ≠ PRODUCTION_AUTHORIZED
SOURCE INSPECTION ≠ PHYSICAL DEVICE TEST
BUILD SUCCESS ≠ SECURITY PROOF
```

---

## A. Repository and branch identity

| Field | Value |
|-------|--------|
| Remote | https://github.com/Kelronmos/SWI-Android-Prototype |
| Branch | feature/android-device-network-diagnostics |
| Target commit | 166891346b73a30dd76ad93007af23bd1ca92977 |
| Commit subject | feat(launcher): Phase 1 real SWI Android HOME launcher |
| Commit timestamp | 2026-11-10T22:59:24Z |
| Target is branch HEAD | YES (at time of inspection) |
| Working tree (remote) | Clean relative to this commit |

---

## B. Exact commit verification

| Check | Result |
|-------|--------|
| Commit exists on remote | YES |
| Branch points at target | YES |
| Parent | fbc7bcdfec17d684a3499a53da79b6234d8c00b0 |
| Changed files in commit | SwiLauncherActivity.kt (new), AndroidManifest.xml, app/build.gradle.kts, .github/workflows/android.yml |
| Existing modules preserved | WelcomeActivity, MainActivity, PresenceChallengeActivity, DeviceDiagnosticsActivity |

---

## C. Build results

| Check | Result | Notes |
|-------|--------|-------|
| Local Gradle assembleDebug | NOT_ACCESSIBLE | No Android SDK in this execution environment |
| Local unit tests | NOT_ACCESSIBLE | Same |
| Local lintDebug | NOT_ACCESSIBLE | Same |
| GitHub Actions workflow | UPDATED | Now triggers on feature/android-device-network-diagnostics |
| CI APK artifact for this commit | UNKNOWN | Requires workflow run confirmation |
| APK SHA-256 | UNKNOWN | Pending successful CI build |

---

## D. Source-level verification (IMPLEMENTED vs TESTED)

### Launcher registration (manifest)

| ID | Requirement | Source status |
|----|-------------|---------------|
| L1 | HOME intent present | IMPLEMENTED — SwiLauncherActivity has MAIN + HOME + DEFAULT |
| L2 | Exactly one HOME activity | IMPLEMENTED — only SwiLauncherActivity |
| L3 | Standard LAUNCHER icon | IMPLEMENTED — WelcomeActivity retains LAUNCHER |
| L4–L7 | Default selection / restore | IMPLEMENTED in code (opens ACTION_HOME_SETTINGS); physical selection NOT_TESTED |

### Home screen

| ID | Requirement | Source status |
|----|-------------|---------------|
| H1–H6 | Branding, clock, status, governance footer | IMPLEMENTED in SwiLauncherActivity |
| H5 | Honest security state | IMPLEMENTED — "OBSERVATION ONLY", PROVEN=NO footer |
| H7–H8 | Screen sizes / text scaling | NOT_TESTED |

### App drawer

| ID | Requirement | Source status |
|----|-------------|---------------|
| D1–D6 | PackageManager list, icons, launch, sort | IMPLEMENTED — queryIntentActivities + MATCH_ALL |
| D7 | Package visibility honesty | IMPLEMENTED — empty-state message acknowledges visibility limits |
| D8–D9 | Refresh after install/uninstall | IMPLEMENTED via onResume reload; physical NOT_TESTED |

### Search

| ID | Requirement | Source status |
|----|-------------|---------------|
| S1–S5 | Name/package filter, empty, no-match | IMPLEMENTED |

### Wallpaper

| ID | Requirement | Source status |
|----|-------------|---------------|
| W1–W2 | System picker | IMPLEMENTED — ACTION_SET_WALLPAPER |
| W3 | Persistence after restart | PARTIAL — preference flag recorded; system WallpaperManager holds image; physical NOT_TESTED |
| W4–W5 | Flag + no upload | IMPLEMENTED — local SharedPreferences only |

### Notifications

| ID | Requirement | Source status |
|----|-------------|---------------|
| N1 | Four channels | IMPLEMENTED — swi_info, swi_warning, swi_high, swi_critical |
| N2–N4 | Test notification + permission handling | IMPLEMENTED; physical NOT_TESTED |
| N5 | System shade preserved | IMPLEMENTED by design (no shade replacement) |

### Regression (existing modules)

| ID | Requirement | Source status |
|----|-------------|---------------|
| R1–R2 | Diagnostics reachable | IMPLEMENTED — navigation present; activity unchanged |
| R3–R6 | MainActivity / presence / biometric | Source preserved; runtime NOT_TESTED |
| R7–R8 | Onboarding / Home behaviour | Source preserved; runtime NOT_TESTED |

### Governance boundaries (G1–G5)

| ID | Check | Source status |
|----|-------|---------------|
| G1 | No production claim on UI | PASS (source) — explicit NO states |
| G2 | No forced default launcher | PASS (source) — user must select |
| G3 | No fabricated app list | PASS (source) — PackageManager only |
| G4 | Existing modules not weakened | PASS (source) — files unchanged in behaviour |
| G5 | No second authorization engine | PASS (source) — launcher does not grant execution |

---

## E. Physical device results

| Category | Result |
|----------|--------|
| adb devices | NOT_ACCESSIBLE |
| Manufacturer / model / API | NOT_ACCESSIBLE |
| HOME selection on device | NOT_TESTED |
| App drawer on device | NOT_TESTED |
| Wallpaper persistence on device | NOT_TESTED |
| Notification delivery on device | NOT_TESTED |
| Biometric / camera regression on device | NOT_TESTED |
| Emulator | NOT_TESTED |

**All L1–L7, H1–H8, D1–D9, S1–S5, W1–W5, C1–C7, N1–N5, R1–R8, B1–B5 physical observations: NOT_TESTED**

---

## F. Security findings (static)

| Severity | Finding | Path | Notes |
|----------|---------|------|-------|
| Low | Unused imports in SwiLauncherActivity | SwiLauncherActivity.kt | WallpaperManager, BroadcastReceiver, ApplicationInfo, Uri, GridLayout imported but unused — cleanup recommended, not security |
| Info | exported=true on HOME and Welcome | AndroidManifest.xml | Required for HOME and LAUNCHER roles; no sensitive data exposed |
| Info | POST_NOTIFICATIONS declared | AndroidManifest.xml | Required for Android 13+; runtime grant still needed |
| Info | SET_WALLPAPER declared | AndroidManifest.xml | Used only via system picker |
| None critical | No secrets, no hardcoded credentials, no network transmission of inventory, no biometric template storage | — | Confirmed by inspection |
| None critical | No second authorization engine | — | Launcher never sets EXECUTION_ALLOWED |

No critical or high security regressions identified in source for Phase 1 scope.

---

## G. Evidence files

| File | Status |
|------|--------|
| evidence/phase1/PHASE1_ACCEPTANCE_REPORT.md | This document |
| evidence/phase1/phase1_results.json | Companion machine-readable record |
| evidence/phase1/build_report.txt | Build environment limitations |
| evidence/phase1/security_review.md | Static security notes |
| evidence/phase1/device_record.json | Physical device unavailable |

---

## H. Outstanding physical-device actions (user / Termux)

```bash
# 1. Confirm commit
git fetch origin
git checkout feature/android-device-network-diagnostics
git rev-parse HEAD
# Expect: 166891346b73a30dd76ad93007af23bd1ca92977

# 2. Build (local or wait for CI)
./gradlew assembleDebug
sha256sum app/build/outputs/apk/debug/app-debug.apk

# 3. Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 4. Device identity
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk

# 5. HOME registration check
adb shell dumpsys package com.trustsmotion.swiandroid | grep -A5 "Category: 'android.intent.category.HOME'"

# 6. Manual acceptance criteria from Phase 1 test plan
#    L1–L7, H1–H8, D1–D9, S1–S5, W1–W5, N1–N5, R1–R8, G1–G5
```

---

## I. Phase 1 decision

```text
PHASE_1_STATUS     = PARTIAL
PHASE_1_DECISION   = HOLD

Reason:
  Source implementation of launcher, drawer, search, wallpaper picker,
  notification channels, and governance UI is present at the target commit.
  Physical-device verification of HOME selection, app launch, wallpaper
  persistence, notifications, and regression of biometric/camera paths
  has not been performed from this environment.
  Local Gradle build and APK SHA-256 are not available here.

Critical acceptance criteria still requiring physical evidence:
  4–12 of the Phase 1 acceptance gate (HOME on device, drawer, search,
  wallpaper persistence, diagnostics, notifications, launcher restore,
  biometric/camera regression).
```

---

## J. Phase 2 authorization

```text
PHASE_2 = BLOCKED
```

Phase 2 (Device Shield monitoring) must not start until Phase 1 physical-device evidence is recorded and the gate decision is ACCEPT.

---

## Governance (unchanged)

```text
PROVEN                 = NO
SEALED                 = NO
PRODUCTION_AUTHORIZED  = NO
SWI_AUTHORIZED         = NO
EXECUTION_ALLOWED      = NO
```

---

## Summary counts (this environment)

| Result | Count |
|--------|-------|
| PASS (source-level governance / structure) | G1–G5 and selected structural checks |
| FAIL | 0 confirmed |
| NOT_TESTED | Majority of runtime and physical criteria |
| NOT_ACCESSIBLE | Build, lint, unit tests, adb, physical device |
| UNKNOWN | CI APK artifact SHA for this exact commit |

**TRUE STATE > GREEN STATUS**
