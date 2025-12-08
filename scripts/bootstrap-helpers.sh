#!/usr/bin/env bash
set -euo pipefail

# Ensure script-helpers git submodule is initialized.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -d script-helpers && -f script-helpers/README.md ]]; then
  echo "script-helpers: already present"
  exit 0
fi

if [[ ! -f .gitmodules ]] || ! grep -q "\[submodule \"script-helpers\"\]" .gitmodules; then
  echo "Adding script-helpers submodule..."
  git submodule add git@github.com:nikolareljin/script-helpers.git script-helpers
fi

echo "Initializing/updating script-helpers..."
git submodule update --init --depth 1 script-helpers
echo "Done."

