#!/usr/bin/env bash
set -euo pipefail

# bt-takeover build helper (Linux/macOS)
# Cross-builds for Windows using EnableWindowsTargeting in the csproj.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

CONFIG="Release"
RUNTIME="win-x64"
PUBLISH=true
SELF_CONTAINED=true

usage() {
  cat <<USAGE
Usage: ./build.sh [options]
  -c, --configuration   Build configuration (Debug|Release). Default: Release
  -r, --runtime         Runtime identifier. Default: win-x64
      --publish         Publish single-file build (true|false). Default: true
      --self-contained  Self-contained publish (true|false). Default: true
  -h, --help            Show help
Examples:
  ./build.sh --publish true
  ./build.sh -c Debug --publish false
USAGE
}

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
  return 1
}

log_info()  { echo "[INFO] $*"; }
log_warn()  { echo "[WARN] $*"; }
log_error() { echo "[ERROR] $*" >&2; }

include_helpers || log_warn "script-helpers not found; using built-in logging."

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

while (( "$#" )); do
  case "$1" in
    -c|--configuration) CONFIG="$2"; shift 2;;
    -r|--runtime)       RUNTIME="$2"; shift 2;;
    --publish)          PUBLISH="$2"; shift 2;;
    --self-contained)   SELF_CONTAINED="$2"; shift 2;;
    -h|--help)          usage; exit 0;;
    *) log_error "Unknown option: $1"; usage; exit 1;;
  esac
done

if ! command -v dotnet >/dev/null 2>&1; then
  log_error "dotnet CLI not found. Install .NET 8 SDK."
  exit 1
fi

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
    exit 2
  fi
fi

if [[ "$PUBLISH" == "true" ]]; then
  OUT_DIR="publish/$RUNTIME"
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
      exit 3
    fi
  fi
  log_info "Publish complete: $OUT_DIR/BtTakeover.exe"
else
  log_info "Publish disabled."
fi
