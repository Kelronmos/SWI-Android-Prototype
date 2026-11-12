# SWI Android Prototype

Structured Workflow Intelligence Android companion prototype.

## Current implementation target

### Cryptographic human-presence proof

- Android BiometricPrompt using BIOMETRIC_STRONG.
- Android Keystore EC signing key.
- Authentication-required cryptographic operation.
- Signature payload bound to workflow, exact action, nonce and timestamp.
- Private key remains in Android Keystore.
- No biometric template or fingerprint image collection.

### Live hardware binding (feature branch)

- SensorManager discovery: proximity, accelerometer, gyroscope, ambient light.
- Touchscreen event observation.
- Biometric capability reporting via BiometricManager.
- Explicit ABSENT / UNKNOWN / STALE / HOLD states.
- Live dashboard (orange / black / blue / white).
- Observation history and JSON evidence export.

```
HARDWARE_PRESENT != HARDWARE_BOUND
HARDWARE_BOUND != SENSOR_VERIFIED
SENSOR_VERIFIED != HUMAN_AUTHORIZED
HUMAN_AUTHORIZED != EXECUTION_AUTHORIZED
OBSERVATION != AUTHORIZATION
```

## Authority boundary

BIOMETRIC_AUTHENTICATED is not SWI_AUTHORIZED.

Hardware observations are not authorization.

The prototype does not yet connect its signed proof or sensor export to the SWI Python governing spine as a permit. It does not authorize or execute device actions.

```
PROVEN = NO
SEALED = NO
PRODUCTION_AUTHORIZED = NO
```

## Build

GitHub Actions builds the debug APK. The artifact is a development prototype, not a production release.

See `docs/HARDWARE_BINDING_REPORT_2026-11-12.md` on branch `feature/live-hardware-binding-2026-11-12`.
