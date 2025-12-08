#!/usr/bin/env bash
set -euo pipefail

#? Android builder
#? 
#? Description:
#?   Builds the Android app via Gradle wrapper. Supports debug and release
#?   variants. Requires an Android SDK (local or downloaded in android/.sdk).
#? 
#? Usage:
#?   scripts/build-android.sh [--mode debug|release] [--no-skip-tests]
#? 
#? Options:
#?   --mode            Build mode: debug | release (default: debug)
#?   --no-skip-tests  Run tests (by default tests are skipped)
#?   --bootstrap-sdk  Download and set up Android SDK locally if missing
#?   -h, --help       Show this help
#? 
#? Examples:
#?   scripts/build-android.sh --mode debug
#?   scripts/build-android.sh --mode release

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Option parsing (simple)
MODE="debug"
SKIP_TESTS=true
BOOTSTRAP_SDK=false
print_help(){ awk '/^#\?/{sub("^#\\? ?"," ");print}' "$0"; }
while (( "$#" )); do
  case "$1" in
    --mode) MODE="${2,,}"; shift 2;;
    --no-skip-tests) SKIP_TESTS=false; shift;;
    --bootstrap-sdk) BOOTSTRAP_SDK=true; shift;;
    -h|--help) print_help; exit 0;;
    *) echo "Unknown arg: $1"; print_help; exit 1;;
  esac
done

# Include helpers if available; attempt multiple known entry points
if [[ -f script-helpers/shell/lib.sh ]]; then . script-helpers/shell/lib.sh; fi
if [[ -f script-helpers/init.sh ]]; then . script-helpers/init.sh; fi
# Try to load libraries through common loader names
if declare -F shlib::load >/dev/null 2>&1; then shlib::load log args || true; fi
if declare -F sh::load >/dev/null 2>&1; then sh::load log args || true; fi
if declare -F helpers_load >/dev/null 2>&1; then helpers_load log args || true; fi
log_info(){ echo "[ANDROID] $*"; }

cd android
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$PWD/.sdk}"

if [[ ! -f gradlew ]]; then
  echo "Gradle wrapper missing in android/. Aborting." >&2
  exit 1
fi

# Require a command (with friendly message)
require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ANDROID] Missing required command: $1" >&2
    echo "           Please install it (e.g., apt-get install -y $1) and retry." >&2
    exit 1
  fi
}

bootstrap_sdk() {
  local sdk="$ANDROID_SDK_ROOT"
  local tools_bin="$sdk/cmdline-tools/latest/bin"
  if [[ -x "$tools_bin/sdkmanager" ]]; then return 0; fi
  echo "[ANDROID] Bootstrapping Android SDK into $sdk ..."
  require_cmd curl
  require_cmd unzip
  require_cmd java
  mkdir -p "$sdk/cmdline-tools"
  local zip="/tmp/cmdline-tools.zip"
  curl -L -o "$zip" https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q "$zip" -d "$sdk/cmdline-tools"
  mv "$sdk/cmdline-tools/cmdline-tools" "$sdk/cmdline-tools/latest"
  export PATH="$tools_bin:$PATH"
  yes | sdkmanager --sdk_root="$sdk" --licenses >/dev/null || true
  yes | sdkmanager --sdk_root="$sdk" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
}

# Optionally initialize SDK if missing
if [[ "$BOOTSTRAP_SDK" == true ]]; then
  if [[ ! -x "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
    bootstrap_sdk
  fi
fi

TASKS=(":app:assembleDebug")
if [[ "$MODE" == "release" ]]; then TASKS=(":app:bundleRelease" ":app:assembleRelease"); fi
EXTRA=(-x lint)
if [[ "$SKIP_TESTS" == true ]]; then EXTRA+=(-x test); fi

log_info "Building Android ($MODE) ..."
./gradlew --no-daemon "${TASKS[@]}" "${EXTRA[@]}"

if [[ "$MODE" == "debug" ]]; then
  ls -la app/build/outputs/apk/debug || true
else
  ls -la app/build/outputs/bundle/release app/build/outputs/apk/release || true
fi

log_info "Done."
