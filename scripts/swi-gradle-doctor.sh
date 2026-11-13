#!/data/data/com.termux/files/usr/bin/bash
# SWI Common Error Intelligence: Gradle recovery
# Diagnostic by default. Mutation requires --repair-wrapper.

set -uo pipefail

MODE="${1:---diagnose}"
ROOT="$(pwd)"

while [[ "$ROOT" != "/" ]]; do
    if [[ -f "$ROOT/settings.gradle" || \
          -f "$ROOT/settings.gradle.kts" ]]; then
        break
    fi
    ROOT="$(dirname "$ROOT")"
done

if [[ ! -f "$ROOT/settings.gradle" && \
      ! -f "$ROOT/settings.gradle.kts" ]]; then
    echo "CEI_CLASS=GRADLE_PROJECT_ROOT_NOT_FOUND"
    echo "CAUSE=No Gradle settings file found in current directory or parents."
    echo "ACTION=Enter the actual project root. Do not generate a wrapper in an empty temporary directory."
    exit 2
fi

cd "$ROOT" || exit 2

echo "===== SWI GRADLE CEI ====="
echo "PROJECT_ROOT=$ROOT"
echo "MODE=$MODE"

if [[ -f settings.gradle.kts ]]; then
    SETTINGS=settings.gradle.kts
else
    SETTINGS=settings.gradle
fi

echo "SETTINGS_FILE=$SETTINGS"

AGP_VERSION="$(python3 - <<'PY'
import pathlib, re

files = [
    pathlib.Path("build.gradle.kts"),
    pathlib.Path("build.gradle"),
    pathlib.Path("settings.gradle.kts"),
    pathlib.Path("settings.gradle"),
]

for p in files:
    if not p.exists():
        continue
    text = p.read_text(errors="replace")
    match = re.search(
        r'com\.android\.application.*?version\s*["\']'
        r'([0-9]+\.[0-9]+(?:\.[0-9]+)?)',
        text,
        re.S
    )
    if not match:
        match = re.search(
            r'com\.android\.tools\.build:gradle:'
            r'([0-9]+\.[0-9]+(?:\.[0-9]+)?)',
            text
        )
    if match:
        print(match.group(1))
        break
PY
)"

REQUIRED_GRADLE="$(python3 - "$AGP_VERSION" <<'PY'
import sys

v = sys.argv[1]
mapping = {
    "8.0": "8.0",
    "8.1": "8.0",
    "8.2": "8.2",
    "8.3": "8.4",
    "8.4": "8.6",
    "8.5": "8.7",
    "8.6": "8.7",
    "8.7": "8.9",
    "8.8": "8.10.2",
    "8.9": "8.11.1",
    "8.10": "8.11.1",
    "8.11": "8.13",
    "8.12": "8.13",
    "8.13": "8.13",
}

print(mapping.get(".".join(v.split(".")[:2]), "UNKNOWN"))
PY
)"

echo "AGP_VERSION=${AGP_VERSION:-UNKNOWN}"
echo "REQUIRED_GRADLE=$REQUIRED_GRADLE"

echo
echo "===== JAVA ====="
java -version 2>&1 || true

echo
echo "===== INSTALLED GRADLE ====="
gradle --version 2>&1 | head -18 || true

echo
echo "===== ANDROID SDK ====="
echo "ANDROID_HOME=${ANDROID_HOME:-UNSET}"
echo "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-UNSET}"

if [[ -n "${ANDROID_HOME:-}" ]]; then
    for api in 34 35; do
        if [[ -f "$ANDROID_HOME/platforms/android-$api/android.jar" ]]; then
            echo "ANDROID_API_${api}=FOUND"
        else
            echo "ANDROID_API_${api}=MISSING"
        fi
    done
fi

echo
echo "===== EXISTING WRAPPER ====="
if [[ -f gradle/wrapper/gradle-wrapper.properties ]]; then
    cat gradle/wrapper/gradle-wrapper.properties
else
    echo "WRAPPER_PROPERTIES=MISSING"
fi

echo
echo "===== DISTRIBUTION CONNECTIVITY ====="
if command -v curl >/dev/null 2>&1; then
    curl -sSIL --connect-timeout 15 --max-time 60 \
      https://services.gradle.org/distributions/gradle-${REQUIRED_GRADLE}-bin.zip \
      -o /dev/null \
      -w 'HTTP_STATUS=%{http_code}\nFINAL_URL=%{url_effective}\n' \
      || echo "HEAD_CHECK=FAILED_OR_BLOCKED"
else
    echo "CURL=NOT_INSTALLED"
fi

echo
echo "===== CEI CLASSIFICATION ====="

if [[ "$REQUIRED_GRADLE" == "UNKNOWN" ]]; then
    echo "CEI_CLASS=AGP_VERSION_UNRECOGNIZED"
    echo "ACTION=Consult the official AGP compatibility table."
elif [[ ! -f gradlew || ! -f gradle/wrapper/gradle-wrapper.properties ]]; then
    echo "CEI_CLASS=GRADLE_WRAPPER_MISSING"
    echo "ACTION=Generate wrapper from the actual project root."
else
    echo "CEI_CLASS=WRAPPER_PRESENT"
fi

if [[ "$MODE" != "--repair-wrapper" ]]; then
    echo "DIAGNOSIS_ONLY=YES"
    exit 0
fi

echo
echo "===== CONTROLLED WRAPPER REPAIR ====="

if [[ "$REQUIRED_GRADLE" == "UNKNOWN" ]]; then
    echo "REPAIR_REFUSED=Unknown AGP compatibility."
    exit 3
fi

if ! command -v gradle >/dev/null 2>&1; then
    echo "REPAIR_REFUSED=Installed Gradle is unavailable."
    exit 3
fi

LOCAL_VERSION="$(gradle --version 2>/dev/null |
    awk '/^Gradle / {print $2; exit}')"

LOCAL_MAJOR="${LOCAL_VERSION%%.*}"

if [[ ! "$LOCAL_MAJOR" =~ ^[0-9]+$ ]] || \
   (( LOCAL_MAJOR < 9 )); then
    echo "REPAIR_REFUSED=This recovery procedure requires Gradle 9+ for --no-validate-url."
    echo "Use the documented manual wrapper-properties recovery."
    exit 3
fi

BACKUP="$HOME/.swi-backups/gradle-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP"

for item in gradlew gradlew.bat gradle/wrapper; do
    if [[ -e "$item" ]]; then
        mkdir -p "$BACKUP/$(dirname "$item")"
        cp -a "$item" "$BACKUP/$item"
    fi
done

echo "BACKUP=$BACKUP"

gradle :wrapper \
    --gradle-version "$REQUIRED_GRADLE" \
    --distribution-type bin \
    --no-validate-url \
    --network-timeout 60000

RESULT=$?

if [[ "$RESULT" -ne 0 ]]; then
    echo "REPAIR_RESULT=FAILED"
    echo "BACKUP_PRESERVED=YES"
    exit "$RESULT"
fi

if [[ ! -f gradle/wrapper/gradle-wrapper.properties || \
      ! -f gradlew ]]; then
    echo "REPAIR_RESULT=INCOMPLETE"
    exit 4
fi

chmod +x gradlew

echo "REPAIR_RESULT=WRAPPER_GENERATED"
echo "DISTRIBUTION_URL_VALIDATION=DISABLED"
echo "NOTE=Verify official distribution integrity and configure a verified SHA-256 checksum before relying on this wrapper."
echo "NEXT=Run ./gradlew --version, then the project build."

