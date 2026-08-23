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

## Resource Path and `VISION_MIND_PATH`

YOLO and other modules that load the OpenCV native library can use
`VISION_MIND_PATH` as an explicit resource root. If it is not set, startup
searches the current project path and its parents for `resource`. The resource
directory must contain `lib/opencv/` and the required model resources. To set
the explicit path, run the following from the repository root in PowerShell:

```powershell
$env:VISION_MIND_PATH = (Resolve-Path -LiteralPath '.\resource').Path
mvn -pl vision-mind-yolo-app -am spring-boot:run
```

To run an existing JAR:

```powershell
$env:VISION_MIND_PATH = (Resolve-Path -LiteralPath '.\resource').Path
java -jar '.\vision-mind-yolo-app\target\vision-mind-yolo.jar'
```

The variable only applies to the current terminal. To save it for the current
Windows user, run this once and open a new terminal afterwards:

```powershell
[Environment]::SetEnvironmentVariable(
    'VISION_MIND_PATH',
    (Resolve-Path -LiteralPath '.\resource').Path,
    'User'
)
```

When starting from IntelliJ IDEA, add the following environment variable to
the Run/Debug Configuration and set the working directory to the repository
root:

```text
VISION_MIND_PATH=<repository-root>\resource
```

`vision-mind.resource.root: ./resource` is the YAML resource configuration.
The legacy OpenCV loading path uses `VISION_MIND_PATH` when it is set and falls
back to the project `resource` directory when it is not.

## Configuration

Each runnable module has one configuration entry point:
`src/main/resources/application.yml`. Business configuration is YAML only;
the core/app Maven split is retained, but core JARs do not carry runtime
`.properties` configuration. Configuration keys use lower kebab-case under the
`vision-mind` namespace, for example:

```yaml
vision-mind:
  resource:
    root: ./resource
    fallback-env: VISION_MIND_PATH
  native:
    use-gpu: false
  yolo:
    confidence-threshold: 0.3
    models:
      detect: /yolo/model/yolo26s.onnx
    depth:
      model: /yolo/model/yolo26n-depth.onnx
      max-pixels: 2100000
```

The service-specific roots are `vision-mind.asr`, `vision-mind.ffe`,
`vision-mind.lpr`, `vision-mind.ocr`, `vision-mind.reid`, `vision-mind.tbir`,
`vision-mind.tbir-cn`, `vision-mind.tts`, and `vision-mind.llm`.
`VISION_MIND_PATH` and the `VISION_MIND_YOLO_DEPTH_*` environment variables
remain runtime environment variables. The former `.properties` files and
their old keys are no longer loaded.

## Minimal Demos

- YOLO demo: `examples/yolo-demo`
- ASR demo: `examples/asr-demo`
- TTS web page: `http://127.0.0.1:17010/vision-mind-tts/`

## TTS Resource Path Convention

- `vision-mind-tts-app` does not rely on environment variables for model loading.
- On startup it auto-locates the repository root and reads resources from relative path: `./resource`.
- Default paths:
- `./resource/tts/model/sherpa-onnx-vits-zh-ll`
- `./resource/lib/sherpa-onnx`

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

## RF-DETR Small

- Module: `vision-mind-rfdetr-app` + `vision-mind-rfdetr-core`
- Runtime: CPU ONNX Runtime, port `17011`, context path `/vision-mind-rfdetr`
- Initialize the ignored local model resources with `powershell -ExecutionPolicy Bypass -File scripts\init-rfdetr.ps1`; this downloads/exports the RF-DETR Small checkpoint.
- Start with `powershell -ExecutionPolicy Bypass -File scripts\start-rfdetr.ps1` and verify with `powershell -ExecutionPolicy Bypass -File scripts\verify-rfdetr.ps1`.
- The module exposes the same detection routes as YOLO under its own context: `/api/v1/vision/detect`, `/api/v1/vision/detect/preview`, `/api/v1/vision/detect/upload`, and `/api/v1/vision/video/detect`.
- `Box.type` and request `types` use sparse RF-DETR COCO IDs (`person=1`, `car=3`, `toothbrush=90`). Video detection additionally requires the project OpenCV native resource.
