# SWI Android Prototype

Structured Workflow Intelligence Android companion prototype.

## Current implementation target

- Android BiometricPrompt using BIOMETRIC_STRONG.
- Android Keystore EC signing key.
- Authentication-required cryptographic operation.
- Signature payload bound to workflow, exact action, nonce and timestamp.
- Private key remains in Android Keystore.
- No biometric template or fingerprint image collection.

## Authority boundary

BIOMETRIC_AUTHENTICATED is not SWI_AUTHORIZED.

The prototype does not yet connect its signed proof to the SWI Python governing spine. It does not authorize or execute device actions.

## Build

GitHub Actions builds the debug APK. The artifact is a development prototype, not a production release.
