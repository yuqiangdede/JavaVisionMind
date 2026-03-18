#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${1:-.}"
ROOT="$(cd "$PROJECT_ROOT" && pwd)"
echo "[verify-env] root: $ROOT"

if ! command -v java >/dev/null 2>&1; then
  echo "java is not available" >&2
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "warning: mvn is not available, rely on CI for full verification" >&2
fi

RESOURCE_ROOT="$ROOT/resource"
if [[ -d "$RESOURCE_ROOT" ]]; then
  echo "[verify-env] using local resource root: $RESOURCE_ROOT"
elif [[ -n "${VISION_MIND_PATH:-}" ]]; then
  echo "[verify-env] using env resource root: $VISION_MIND_PATH"
  RESOURCE_ROOT="$VISION_MIND_PATH"
else
  echo "resource root missing and VISION_MIND_PATH is not set" >&2
  exit 1
fi

if [[ ! -f "$RESOURCE_ROOT/manifest.json" ]]; then
  echo "missing manifest.json under resource root: $RESOURCE_ROOT" >&2
  exit 1
fi

java -version
if command -v mvn >/dev/null 2>&1; then
  mvn -version
fi

echo "[verify-env] ok"
