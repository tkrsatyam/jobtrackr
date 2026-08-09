#!/usr/bin/env bash
#
# Seeds mock-applications.json into JobTrackr via POST /api/applications.
# Delegates all HTTP work to seed-helper.js (Node.js).
#
# Requirements: node
#
# Usage:
#   export JWT_TOKEN="eyJhbGc..."
#   ./seed-applications.sh
#
# Optional overrides:
#   export API_BASE="http://localhost:8080"

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ -z "${JWT_TOKEN:-}" ]]; then
  echo "ERROR: JWT_TOKEN is not set. Log in first, then:"
  echo "  export JWT_TOKEN=\"eyJhbGc...\""
  exit 1
fi

if ! command -v node >/dev/null 2>&1; then
  echo "ERROR: node is required but not found on PATH."
  exit 1
fi

node "${SCRIPT_DIR}/seed-helper.js"
