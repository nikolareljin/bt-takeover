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
print_help(){ awk '/^#\?/{sub("^#\\? ?"," ");print}' "$0"; }
while (( "$#" )); do
  case "$1" in
    --mode) MODE="${2,,}"; shift 2;;
    --no-skip-tests) SKIP_TESTS=false; shift;;
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
