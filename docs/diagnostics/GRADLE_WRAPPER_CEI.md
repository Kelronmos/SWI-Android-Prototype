# SWI CEI: Gradle Wrapper Recovery

Status: DOCUMENTED / NOT YET INDEPENDENTLY VALIDATED

## Purpose

Prevent repeated Gradle wrapper failures across SWI Android development
environments, including Termux, Linux, macOS and Windows.

## Error family

CEI-GRADLE-WRAPPER-001

### Signature A: Empty temporary directory

Message:
Directory does not contain a Gradle build.

Cause:
The wrapper task was invoked outside the real Gradle project.

Rule:
Locate settings.gradle or settings.gradle.kts before wrapper generation.
Never initialize an empty temporary directory merely to generate a wrapper.

### Signature B: Distribution URL validation

Message:
Test of distribution url failed.

Cause:
The preliminary HTTP HEAD request may be blocked, redirected,
rejected, or fail because of network/TLS configuration.

Rule:
A failed HEAD request does not prove that the ZIP download itself fails.

Recovery:
Use a supported Gradle wrapper generation command with
--no-validate-url when the failure is specifically URL validation.

Security:
This setting disables URL validation in the generated wrapper.
It is not a substitute for distribution integrity verification.
Use the official Gradle distribution SHA-256 checksum.

### Signature C: Actual distribution download failure

Investigate separately:
- DNS resolution
- TLS certificates and Java trust store
- redirects and HTTP responses
- proxy configuration
- available storage
- download interruption

Do not classify this as a successful URL-validation repair.

### Signature D: AGP / Gradle mismatch

Read the actual Android Gradle Plugin declaration.
Determine the minimum compatible Gradle version from the official
Android compatibility table.

Known SWI Android baseline:
- AGP 8.6.1
- Gradle 8.7
- Java 17
- compileSdk 35
- targetSdk 35

Do not downgrade SDK levels to conceal a wrapper problem.

## Diagnostic entry point

From a project directory:

    bash scripts/swi-gradle-doctor.sh

Controlled wrapper generation:

    bash scripts/swi-gradle-doctor.sh --repair-wrapper

The repair backs up existing wrapper files before changing them.
It does not build the APK or change Android SDK configuration.

## Evidence requirements

Record:
- repository and commit
- operating system and architecture
- Java version
- installed Gradle version
- AGP version
- wrapper distribution URL
- SDK platform availability
- HTTP/TLS diagnostic result
- complete build log
- repair result
- APK SHA-256, if produced

## SWI state rules

DETECTED != DIAGNOSED
DIAGNOSED != REPAIRED
REPAIRED != BUILD_SUCCESSFUL
BUILD_SUCCESSFUL != TESTED
TESTED != PROVEN
PROVEN != SEALED
SEALED != PRODUCTION_AUTHORIZED

Unknown causes remain UNKNOWN, not FALSE.

## Official references

- https://docs.gradle.org/current/userguide/gradle_wrapper.html
- https://developer.android.com/build/releases/about-agp
- https://developer.android.com/build/releases/agp-8-6-0-release-notes
