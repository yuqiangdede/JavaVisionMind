#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${1:-.}"
ROOT="$(cd "$PROJECT_ROOT" && pwd)"
echo "[bootstrap] root: $ROOT"

bash "$ROOT/scripts/verify-env.sh" "$ROOT"

echo "[bootstrap] next commands:"
echo "  mvn -B -DskipTests clean package"
echo "  mvn -pl vision-mind-yolo-app spring-boot:run"
echo "  mvn -pl vision-mind-asr-app spring-boot:run"
