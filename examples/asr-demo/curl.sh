#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:17008/vision-mind-asr}"
AUDIO_URL="${AUDIO_URL:-https://www2.cs.uic.edu/~i101/SoundFiles/taunt.wav}"

echo "[asr-demo] BASE_URL=$BASE_URL"
echo "[asr-demo] AUDIO_URL=$AUDIO_URL"

curl -sS -X POST "$BASE_URL/api/v1/audio/asr/transcribe/source" \
  -H "Content-Type: application/json" \
  -d "{\"audioUrl\":\"$AUDIO_URL\",\"enablePunctuation\":true}"
echo
