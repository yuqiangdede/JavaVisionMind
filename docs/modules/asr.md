# ASR Module

- App: `vision-mind-asr-app`
- Domain: `asr`

## Unified API

- Transcribe: `/api/v1/asr/infer`
- Source Input(JSON): `/api/v1/asr/transcribe/source`
- Config Store: `/api/v1/asr/store/hotwords`
- Rule Index: `/api/v1/asr/index/phrase-rules`
- Health: `/api/v1/asr/runtime/health`

兼容保留：`/api/v1/asr/transcribe`（multipart）。
