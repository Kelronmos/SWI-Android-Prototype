# SWI Android — Termux Build Environment Evidence

Date: 2026-11-12
Branch: review/android-efpp-2026-11-12
Recorded HEAD: b9a7b7d8eb79b31e34e8498c2a85bb0d49e70334

## Confirmed environment

- Architecture: aarch64
- Java: 21.0.11 (Termux)
- Gradle 8.7 distribution downloaded and version command succeeded.
- Android compile SDK configured in project: API 35.
- Android SDK platform directory: absent at inspected path.
- ANDROID_HOME: empty during recorded environment inspection.
- Android SDK API 35 android.jar: absent.

## Android build results

Recorded exit codes:
- Android unit tests: 1
- Android lint: 1
- Debug APK assembly: 1

All three logs report:
SDK location not found.

This is an environment/configuration blocker.
It is not evidence that the Android source tests failed.

The logs also reference Gradle 9.6 documentation.
The separate Gradle 8.7 version check does not prove that
Gradle 8.7 executed the failed Android tasks.

## Existing APK artifacts

The following APKs passed ZIP integrity checks.
Their build provenance and relationship to the recorded HEAD
have not yet been established.

- dist/app-debug.apk
  SHA-256: 57c5ced149dc64731082968f85a52402e9d4e3ea750a8c83d04b4f6f07c42cf8

- dist/build-42887876576/app-debug.apk
  SHA-256: f1d6c86f45d9213c9d12da137c038747b25fc3fbf876770b5dc877fcf93ac341

- dist/build-42893964630/app-debug.apk
  SHA-256: 90ddb44b2260f10606469118bbdc4e2c486767fbbf10fff0692fac9c0760fdfd

ZIP integrity does not establish source provenance, signature
trust, installability, runtime correctness, or security.

## Security and evidence boundary

- No Android SDK API 35 build is claimed.
- No current-source APK assembly is claimed.
- No device installation or physical-device test is claimed.
- No production authorization is claimed.
- Existing APKs remain untrusted artifacts pending provenance review.

PROVEN=NO
SEALED=NO
PRODUCTION_AUTHORIZED=NO
