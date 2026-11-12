# SWI Android — Live Hardware Binding Report

**Date:** 2026-11-12  
**Branch:** `feature/live-hardware-binding-2026-11-12`  
**Base (main):** `8d7fd46ab13a6e433fbb46f73965ba5187dd5146`  
**Status:** PROTOTYPE IMPLEMENTATION  
**PROVEN = NO · SEALED = NO · PRODUCTION_AUTHORIZED = NO**

## Governing distinctions

```
HARDWARE_PRESENT != HARDWARE_BOUND
HARDWARE_BOUND != SENSOR_VERIFIED
SENSOR_VERIFIED != HUMAN_AUTHORIZED
HUMAN_AUTHORIZED != EXECUTION_AUTHORIZED

UNKNOWN != FALSE
STALE != VALID
OBSERVATION != AUTHORIZATION
```

Sensor readings, touch events, proximity, and biometric *capability* reports are **observations only**. They do not authorize workflow actions and do not populate the SWI human-authority layer (H).

## What was implemented

| Component | Path | Role |
|-----------|------|------|
| Observation model | `hardware/HardwareObservation.kt` | Capability + binding states, dual timestamps, non-claims |
| Inventory | `hardware/HardwareInventory.kt` | SensorManager + biometric + touchscreen + Keystore discovery |
| Live binder | `hardware/SensorBinder.kt` | Proximity, accelerometer, gyroscope, light; stale/HOLD |
| Store + export | `hardware/ObservationStore.kt` | Ring buffer, JSON export with non-claims |
| Dashboard UI | `HardwareDashboardActivity.kt` | Orange/black/blue/white live board + touch pad |
| Manifest | `AndroidManifest.xml` | Optional sensor features; activity registration |
| Entry point | `MainActivity.kt` | "OPEN HARDWARE DASHBOARD" — crypto flow unchanged |

## Hardware capability inventory (discovery logic)

| Source | Discovery method | Absent reporting |
|--------|------------------|------------------|
| Proximity | `Sensor.TYPE_PROXIMITY` | Explicit ABSENT |
| Accelerometer | `Sensor.TYPE_ACCELEROMETER` | Explicit ABSENT |
| Gyroscope | `Sensor.TYPE_GYROSCOPE` | Explicit ABSENT |
| Ambient light | `Sensor.TYPE_LIGHT` | Explicit ABSENT |
| Touchscreen | `FEATURE_TOUCHSCREEN` | Explicit ABSENT |
| Biometric strong | `BiometricManager.canAuthenticate` | ABSENT / UNAVAILABLE / UNKNOWN codes |
| Android Keystore | API present | Hardware-backed status deferred to key probe |

Device fingerprint: `MANUFACTURER|MODEL|DEVICE|SDK_INT`.

## Binding behaviour

- **BOUND** — active sensor event or active touch / resumed listening  
- **UNBOUND** — present but not streaming, or absent  
- **STALE** — was BOUND, no event within `staleAfterMs` (default 5s)  
- **HOLD** — unreliable accuracy or app paused  
- **UNKNOWN** — no observation yet  

Missing evidence remains UNKNOWN. Stale bindings do not become VALID automatically.

## Preserved cryptographic workflow

`MainActivity` biometric + Android Keystore EC sign path is unchanged:

- Alias `SWI_ANDROID_AUTHORITY_KEY_V1`
- `BIOMETRIC_STRONG` + authenticated `CryptoObject`
- Explicit `SWI AUTHORIZATION: NOT YET VERIFIED` / `EXECUTION: NOT AUTHORIZED`

Hardware dashboard does not call Keystore and does not issue permits.

## Tests

| Class | Status |
|-------|--------|
| Unit: observation stale / non-claims | Not yet executed in CI on this branch |
| Instrumentation: SensorManager | Not yet executed |
| Physical device: discovery / proximity / touch | **Not run in this session** |
| Background / screen-off / reboot | **Not run in this session** |
| Tamper / replay / stale rejection at verifier | Pending Python bridge |

**No test is reported as passed unless it actually ran.** Compilation success is not proof.

## Performance / battery

Not measured on device in this session. Sensor sampling uses `SENSOR_DELAY_UI`. Expect continuous accelerometer/gyroscope to affect battery; production tuning is out of scope for this prototype.

## Known limitations

1. No physical-device evidence captured in this engineering session.  
2. No Android instrumentation test suite committed yet.  
3. Export is clipboard JSON only (no file provider / Termux bridge yet).  
4. No ECDSA proof + hardware observation co-export package.  
5. No link into Python `evaluate_governing_permit` (observation export only).  
6. Touch MOVE events are lightly throttled; not a full gesture recognizer.  
7. StrongBox / hardware-backed Keystore attestation not probed.  
8. Screen-off sensor behaviour is OEM-dependent; not claimed.  

## Security non-claims (binding)

- Sensor readings ≠ authorization  
- Touch ≠ authorization  
- Proximity ≠ human identity  
- Fingerprint success ≠ arbitrary workflow authorization  
- Hardware presence ≠ ownership  
- Successful build ≠ PROVEN / SEALED / PRODUCTION_AUTHORIZED  

## Next safe steps

1. Add JVM unit tests for `HardwareObservation.isStale` / `effectiveBinding` / non-claims.  
2. Add instrumentation tests with shadowed SensorManager where feasible.  
3. On-device matrix: discovery, near/far, background, screen-off.  
4. Export file to app storage + optional Termux-readable path.  
5. Python adapter: hardware export → V2 `StatusRecord` (still not H).  
