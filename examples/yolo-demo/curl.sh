#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:17001/vision-mind-yolo}"
IMAGE_URL="${IMAGE_URL:-https://images.pexels.com/photos/1108099/pexels-photo-1108099.jpeg}"

echo "[yolo-demo] BASE_URL=$BASE_URL"
echo "[yolo-demo] IMAGE_URL=$IMAGE_URL"

echo "[yolo-demo] call new api"
curl -sS -X POST "$BASE_URL/api/v1/vision/detect" \
  -H "Content-Type: application/json" \
  -d "{\"imgUrl\":\"$IMAGE_URL\"}"
echo

echo "[yolo-demo] call legacy api"
curl -sS -X POST "$BASE_URL/imgAnalysis/detect" \
  -H "Content-Type: application/json" \
  -d "{\"imgUrl\":\"$IMAGE_URL\"}"
echo
