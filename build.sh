#!/usr/bin/env bash
set -euo pipefail

#? BtTakeover builder
#? 
#? Description:
#?   Builds the Windows Forms app (net8.0-windows). On non-Windows hosts,
#?   cross-targeting is attempted if Microsoft.NET.Sdk.WindowsDesktop is found.
#?   Optionally publishes a single-file, self-contained executable.
#? 
#? Usage:
#?   ./build.sh [options]
#? 
#? Options:
#?   -t, --type           Build target: windows | android | all (default: windows)
#?   -c, --configuration   Build config: Debug | Release (default: Release)
#?   -r, --runtime         Target RID (default: win-x64)
#?       --publish         Publish single file: true | false (default: true)
#?       --self-contained  Self-contained publish: true | false (default: true)
#?       --bootstrap-android-sdk  Download Android SDK locally if missing (android/all)
#?   -h, --help            Show this help
#? 
#? Examples:
#?   ./build.sh -t windows --publish true
#?   ./build.sh -t android
#?   ./build.sh -t all -c Release

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

CONFIG="Release"
RUNTIME="win-x64"
PUBLISH=true
SELF_CONTAINED=true
BUILD_TYPE="windows"
BOOTSTRAP_ANDROID_SDK=false

print_help() { awk '/^#\?/{sub("^#\\? ?"," ");print}' "$0"; }

include_helpers() {
  local candidates=(
    "script-helpers/shell/lib.sh"
    "script-helpers/bash/lib.sh"
    "script-helpers/init.sh"
    "script-helpers/helpers.sh"
  )
  for f in "${candidates[@]}"; do
    if [[ -f "$f" ]]; then
      # shellcheck source=/dev/null
      . "$f" || true
      return 0
    fi
  done
  # Attempt to bootstrap submodule if available
  if [[ -f .gitmodules ]] && grep -q "\[submodule \"script-helpers\"\]" .gitmodules 2>/dev/null; then
    if command -v git >/dev/null 2>&1; then
      echo "[INFO] Bootstrapping script-helpers submodule..."
      if git submodule update --init --depth 1 script-helpers 2>/dev/null; then
        for f in "${candidates[@]}"; do
          if [[ -f "$f" ]]; then . "$f" || true; return 0; fi
        done
      else
        echo "[WARN] Failed to init script-helpers (network/SSH required)."
      fi
    fi
  fi
  return 1
}

# Try to load helper libraries if script-helpers provides a loader
load_helper_libs() {
  local libs=(log args path fs)
  if declare -F shlib::load >/dev/null 2>&1; then
    shlib::load "${libs[@]}" || true
  elif declare -F sh::load >/dev/null 2>&1; then
    sh::load "${libs[@]}" || true
  elif declare -F helpers_load >/dev/null 2>&1; then
    helpers_load "${libs[@]}" || true
  elif declare -F use_lib >/dev/null 2>&1; then
    use_lib "${libs[@]}" || true
  fi
}

# Ensure a command exists; log and return non-zero otherwise
ensure_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log_error "Missing required command: $1"
    log_error "Install it and retry (e.g., apt-get install -y $1)."
    return 1
  fi
}

log_info()  { echo "[INFO] $*"; }
log_warn()  { echo "[WARN] $*"; }
log_error() { echo "[ERROR] $*" >&2; }

include_helpers || true
load_helper_libs || true
log_info()  { echo "[INFO] $*"; }
log_warn()  { echo "[WARN] $*"; }
log_error() { echo "[ERROR] $*" >&2; }

is_windows() {
  case "$(uname -s 2>/dev/null || echo)" in
    MINGW*|MSYS*|CYGWIN*|Windows_NT) return 0;;
    *) return 1;;
  esac
}

check_windowsdesktop_sdk() {
  # Try to locate Microsoft.NET.Sdk.WindowsDesktop on this machine
  local base
  base=$(dotnet --info 2>/dev/null | sed -n 's/^Base Path: *//p' | head -n1)
  if [[ -z "$base" ]]; then return 1; fi
  local probe1="$base/Sdks/Microsoft.NET.Sdk.WindowsDesktop"
  local parent="$(dirname "$base")"
  local found=""
  if [[ -d "$probe1" ]]; then found="$probe1"; fi
  if [[ -z "$found" ]]; then
    found=$(find "$parent" -maxdepth 3 -type d -name Microsoft.NET.Sdk.WindowsDesktop 2>/dev/null | head -n1 || true)
  fi
  [[ -n "$found" ]]
}

gen_icon() {
  local svg="assets/logo/headphones-noise.svg"
  local ico="src/Assets/AppIcon.ico"
  if [[ ! -f "$svg" || -f "$ico" ]]; then return 0; fi
  if command -v magick >/dev/null 2>&1; then
    log_info "Generating Windows .ico from SVG via ImageMagick..."
    magick convert -background none -density 384 "$svg" -define icon:auto-resize=256,128,64,48,32,16 "$ico" || true
    if [[ -f "$ico" ]]; then log_info "Generated: $ico"; else log_warn "ICO generation failed."; fi
  elif command -v convert >/dev/null 2>&1; then
    log_info "Generating Windows .ico from SVG via convert..."
    convert -background none -density 384 "$svg" -define icon:auto-resize=256,128,64,48,32,16 "$ico" || true
    if [[ -f "$ico" ]]; then log_info "Generated: $ico"; else log_warn "ICO generation failed."; fi
  else
    log_warn "ImageMagick not found; skipping ICO generation."
  fi
}

while (( "$#" )); do
  case "$1" in
    -t|--type)          BUILD_TYPE="${2,,}"; shift 2;;
    -c|--configuration) CONFIG="$2"; shift 2;;
    -r|--runtime)       RUNTIME="$2"; shift 2;;
    --publish)          PUBLISH="$2"; shift 2;;
    --self-contained)   SELF_CONTAINED="$2"; shift 2;;
    --bootstrap-android-sdk) BOOTSTRAP_ANDROID_SDK=true; shift;;
    -h|--help)          print_help; exit 0;;
    *) log_error "Unknown option: $1"; print_help; exit 1;;
  esac
done

build_windows() {
  if ! command -v dotnet >/dev/null 2>&1; then
    log_error "dotnet CLI not found. Install .NET 8 SDK."
    return 1
  fi

  gen_icon

  log_info "Restoring packages..."
  dotnet restore src/BtTakeover.csproj

  if is_windows; then
    log_info "Building ($CONFIG) on Windows..."
    dotnet build src/BtTakeover.csproj -c "$CONFIG" --nologo
  else
    # Non-Windows host: attempt cross-target only if WindowsDesktop SDK is present
    if check_windowsdesktop_sdk; then
      log_info "Building ($CONFIG) on non-Windows with WindowsDesktop SDK present..."
      dotnet build src/BtTakeover.csproj -c "$CONFIG" -p:EnableWindowsTargeting=true --nologo
    else
      log_error "Microsoft.NET.Sdk.WindowsDesktop not found on this machine."
      log_error "Windows Forms/WPF builds are only supported on Windows."
      log_error "Options: build on Windows (build.ps1), or push a tag to build via GitHub Actions."
      return 2
    fi
  fi

  if [[ "$PUBLISH" == "true" ]]; then
    local OUT_DIR="publish/$RUNTIME"
    log_info "Publishing single-file (self-contained=$SELF_CONTAINED) to $OUT_DIR..."
    if is_windows; then
      dotnet publish src/BtTakeover.csproj -c "$CONFIG" -r "$RUNTIME" \
        /p:PublishSingleFile=true /p:SelfContained=$SELF_CONTAINED /p:IncludeNativeLibrariesForSelfExtract=true \
        -o "$OUT_DIR"
    else
      if check_windowsdesktop_sdk; then
        dotnet publish src/BtTakeover.csproj -c "$CONFIG" -r "$RUNTIME" \
          /p:PublishSingleFile=true /p:SelfContained=$SELF_CONTAINED /p:IncludeNativeLibrariesForSelfExtract=true \
          -p:EnableWindowsTargeting=true -o "$OUT_DIR"
      else
        log_error "Cannot publish on non-Windows without WindowsDesktop SDK."
        return 3
      fi
    fi
    log_info "Publish complete: $OUT_DIR/BtTakeover.exe"
  else
    log_info "Publish disabled."
  fi
}

build_android() {
  local mode="debug"
  if [[ "${CONFIG,,}" == "release" ]]; then mode="release"; fi
  if [[ -x scripts/build-android.sh ]]; then
    log_info "Invoking Android build (mode=$mode)..."
    if [[ "$BOOTSTRAP_ANDROID_SDK" == true ]]; then
      scripts/build-android.sh --mode "$mode" --bootstrap-sdk
    else
      scripts/build-android.sh --mode "$mode"
    fi
  else
    log_warn "scripts/build-android.sh not found or not executable. Falling back to Gradle wrapper."
    if [[ -x android/gradlew ]]; then
      # Optionally bootstrap Android SDK if requested and missing
      if [[ "$BOOTSTRAP_ANDROID_SDK" == true && ! -x android/.sdk/cmdline-tools/latest/bin/sdkmanager ]]; then
        ensure_cmd curl && ensure_cmd unzip || return 4
        ( cd android && \
          mkdir -p .sdk/cmdline-tools && \
          curl -L -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
          unzip -q /tmp/cmdline-tools.zip -d .sdk/cmdline-tools && \
          mv .sdk/cmdline-tools/cmdline-tools .sdk/cmdline-tools/latest && \
          yes | .sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=.sdk --licenses >/dev/null || true && \
          yes | .sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=.sdk "platform-tools" "platforms;android-35" "build-tools;35.0.0" )
      fi
      ( cd android && ./gradlew --no-daemon $([[ "$mode" == release ]] && echo ":app:bundleRelease :app:assembleRelease" || echo ":app:assembleDebug") -x lint -x test )
      return $?
    else
      log_warn "Gradle wrapper missing at android/gradlew."
      log_warn "Please run Gradle manually once to generate it or use scripts/bootstrap steps."
      return 4
    fi
  fi
}

# Normalize build type
case "${BUILD_TYPE,,}" in
  windows|android|all) :;;
  *) log_error "Invalid --type: $BUILD_TYPE (use windows|android|all)"; exit 1;;
esac

if [[ "${BUILD_TYPE,,}" == "windows" ]]; then
  build_windows
elif [[ "${BUILD_TYPE,,}" == "android" ]]; then
  build_android
else
  # all
  set +e
  build_windows; win_rc=$?
  if [[ $win_rc -eq 2 || $win_rc -eq 3 ]]; then
    log_warn "Windows build skipped (environment missing WindowsDesktop SDK)."
  fi
  build_android; and_rc=$?
  set -e
  if [[ $win_rc -eq 0 || $and_rc -eq 0 ]]; then
    log_info "Build summary: windows rc=$win_rc, android rc=$and_rc"
    exit 0
  else
    log_warn "One or more builds failed (windows rc=$win_rc, android rc=$and_rc)."
    exit 1
  fi
fi
