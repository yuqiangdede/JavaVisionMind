# JavaVisionMind

[中文文档](README.zh-CN.md)

## Project Positioning

JavaVisionMind is a Java + Spring Boot + ONNX Runtime + OpenCV multimodal inference service toolkit.  
The repository keeps multi-module Maven structure and supports progressive evolution with backward compatibility.

## Core Capabilities

- Vision inference: detection, OCR, face feature extraction, ReID, LPR
- Audio inference: ASR, TTS
- Retrieval and multimodal: TBIR, TBIR-CN, LLM integration
- Platform baseline: unified `HttpResult<T>`, error code, traceId, request log, global exception handling, OpenAPI, startup resource validation

## Module Matrix

| Module | Type | Status |
| --- | --- | --- |
| `vision-mind-yolo-app` | Application | `stable` |
| `vision-mind-asr-app` | Application | `stable` |
| `vision-mind-ocr-app` | Application | `beta` |
| `vision-mind-ffe-app` | Application | `beta` |
| `vision-mind-reid-app` | Application | `beta` |
| `vision-mind-lpr-app` | Application | `beta` |
| `vision-mind-tbir-app` | Application | `beta` |
| `vision-mind-tbir-cn-app` | Application | `beta` |
| `vision-mind-tts-app` | Application | `beta` |
| `vision-mind-llm-core` | Application | `beta` |
| `vision-mind-yolo-core` | Core | `beta` |
| `vision-mind-ocr-core` | Core | `beta` |
| `vision-mind-common` | Shared | `beta` |
| `vision-mind-platform-common` | Platform | `beta` |
| `vision-mind-starter-web` | Platform | `beta` |
| `vision-mind-test-sth` | Experiment | `experimental` |

## Quick Start

```bash
# 1) Verify environment
bash scripts/verify-env.sh

# 2) Build
mvn -DskipTests clean package

# 3) Run key apps
mvn -pl vision-mind-yolo-app spring-boot:run
mvn -pl vision-mind-asr-app spring-boot:run
```

PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-env.ps1
```

## Minimal Demos

- YOLO demo: `examples/yolo-demo`
- ASR demo: `examples/asr-demo`
- TTS web page: `http://127.0.0.1:17010/vision-mind-tts/`

```bash
bash examples/yolo-demo/curl.sh
bash examples/asr-demo/curl.sh
```

## Documentation Entry

- Architecture: `docs/architecture/`
- Deployment: `docs/deployment/`
- Module docs: `docs/modules/`
- Troubleshooting: `docs/troubleshooting/`
- Resource manifest: `resource/manifest.json`

## Roadmap

- Continue migrating model loading to `ModelRegistry` and `OnnxSessionFactory`
- Expand unified input adapters across all image/audio domains
- Keep old API and new API in parallel for at least one release cycle
- Increase module-level tests and CI coverage around compatibility paths
