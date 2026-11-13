#!/data/data/com.termux/files/usr/bin/bash
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "===== SWI GRADLE CEI REGRESSION TESTS ====="

test -x scripts/swi-gradle-doctor.sh
test -f docs/diagnostics/GRADLE_WRAPPER_CEI.md
test -f diagnostics/cei/gradle-wrapper-recovery.yaml

grep -q 'CEI-GRADLE-WRAPPER-001' \
  diagnostics/cei/gradle-wrapper-recovery.yaml

grep -q 'EMPTY_PROJECT_DIRECTORY' \
  diagnostics/cei/gradle-wrapper-recovery.yaml

grep -q 'DISTRIBUTION_URL_VALIDATION' \
  diagnostics/cei/gradle-wrapper-recovery.yaml

grep -q 'AGP_GRADLE_MISMATCH' \
  diagnostics/cei/gradle-wrapper-recovery.yaml

grep -q 'automatic_sdk_downgrade: false' \
  diagnostics/cei/gradle-wrapper-recovery.yaml

grep -q 'BUILD_SUCCESSFUL != TESTED' \
  docs/diagnostics/GRADLE_WRAPPER_CEI.md

echo "PASS: CEI documentation exists"
echo "PASS: Error signatures registered"
echo "PASS: Safety rules registered"
echo "PASS: Evidence requirements documented"
echo "RESULT=PASS"
