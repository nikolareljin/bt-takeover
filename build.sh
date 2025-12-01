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

log_info "Building ($CONFIG)..."
dotnet build src/BtTakeover.csproj -c "$CONFIG" --nologo

if [[ "$PUBLISH" == "true" ]]; then
  OUT_DIR="publish/$RUNTIME"
  log_info "Publishing single-file (self-contained=$SELF_CONTAINED) to $OUT_DIR..."
  dotnet publish src/BtTakeover.csproj -c "$CONFIG" -r "$RUNTIME" \
    /p:PublishSingleFile=true /p:SelfContained=$SELF_CONTAINED /p:IncludeNativeLibrariesForSelfExtract=true \
    -o "$OUT_DIR"
  log_info "Publish complete: $OUT_DIR/BtTakeover.exe"
else
  log_info "Publish disabled."
fi

