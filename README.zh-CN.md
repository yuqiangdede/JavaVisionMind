# JavaVisionMind

[English README](README.md)

## 项目定位

JavaVisionMind 是一个基于 Java + Spring Boot + ONNX Runtime + OpenCV 的多模态推理服务工具箱。  
仓库保持多模块 Maven 结构，通过“旧接口兼容 + 新接口并行 + 平台层托底”方式演进。

## 核心能力

- 视觉推理：目标检测、OCR、人脸特征、ReID、车牌识别
- 音频推理：ASR、TTS
- 检索与多模态：TBIR、TBIR-CN、LLM 接入
- 平台基础：统一 `HttpResult<T>`、错误码、traceId、请求日志、全局异常、OpenAPI、启动资源校验

## 模块矩阵

| 模块 | 类型 | 状态 |
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

## 快速开始

```bash
# 1) 环境检查
bash scripts/verify-env.sh

# 2) 构建
mvn -DskipTests clean package

# 3) 启动重点模块
mvn -pl vision-mind-yolo-app spring-boot:run
mvn -pl vision-mind-asr-app spring-boot:run
```

PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-env.ps1
```

## 资源路径与 `VISION_MIND_PATH`

YOLO 以及其他需要加载 OpenCV 原生库的模块，可以通过
`VISION_MIND_PATH` 显式指定资源根目录。如果没有设置，启动时会从当前项目路径
及其父目录查找 `resource`。资源目录应包含 `lib/opencv/` 和所需的模型资源。
如需显式设置，请在仓库根目录的 PowerShell 中执行：

```powershell
$env:VISION_MIND_PATH = (Resolve-Path -LiteralPath '.\resource').Path
mvn -pl vision-mind-yolo-app -am spring-boot:run
```

运行已经构建好的 JAR：

```powershell
$env:VISION_MIND_PATH = (Resolve-Path -LiteralPath '.\resource').Path
java -jar '.\vision-mind-yolo-app\target\vision-mind-yolo.jar'
```

上面的 `$env:VISION_MIND_PATH = ...` 只对当前终端有效。如果需要保存为当前
Windows 用户的环境变量，可以执行一次下面的命令，然后重新打开终端：

```powershell
[Environment]::SetEnvironmentVariable(
    'VISION_MIND_PATH',
    (Resolve-Path -LiteralPath '.\resource').Path,
    'User'
)
```

如果使用 IntelliJ IDEA 启动，请在 Run/Debug Configuration 中添加环境变量，
并将 Working directory 设置为仓库根目录：

```text
VISION_MIND_PATH=<项目根目录>\resource
```

`vision-mind.resource.root: ./resource` 是 YAML 资源配置。旧版 OpenCV 加载链
会优先使用 `VISION_MIND_PATH`，未设置时自动回退到项目路径下的 `resource`。

## 配置约定

每个可启动模块只有一个配置入口：`src/main/resources/application.yml`。
业务配置统一使用 YAML；保留 core/app 的 Maven 模块拆分，但 core JAR 不再携带
运行时 `.properties` 配置。配置键统一使用 `vision-mind` 命名空间和小写
kebab-case，例如：

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

各服务使用对应的 `vision-mind.asr`、`vision-mind.ffe`、`vision-mind.lpr`、
`vision-mind.ocr`、`vision-mind.reid`、`vision-mind.tbir`、
`vision-mind.tbir-cn`、`vision-mind.tts` 和 `vision-mind.llm` 配置段。
`VISION_MIND_PATH` 以及 `VISION_MIND_YOLO_DEPTH_*` 环境变量仍作为运行环境变量
使用。旧的 `.properties` 文件和旧配置键不再读取。

## 最小 Demo

- YOLO 示例：`examples/yolo-demo`
- ASR 示例：`examples/asr-demo`
- TTS 测试页面：`http://127.0.0.1:17010/vision-mind-tts/`

## TTS 资源路径约定

- `vision-mind-tts-app` 不依赖环境变量读取模型。
- 启动时会自动定位仓库根目录，并按相对路径读取资源：`./resource`。
- 默认读取路径：
- `./resource/tts/model/sherpa-onnx-vits-zh-ll`
- `./resource/lib/sherpa-onnx`

```bash
bash examples/yolo-demo/curl.sh
bash examples/asr-demo/curl.sh
```

## 文档入口

- 架构文档：`docs/architecture/`
- 部署文档：`docs/deployment/`
- 模块文档：`docs/modules/`
- 故障排查：`docs/troubleshooting/`
- 资源清单：`resource/manifest.json`

## RF-DETR Small 目标检测

`vision-mind-rfdetr-app` 提供 CPU ONNX Runtime 的 RF-DETR Small 目标检测服务，默认地址为
`http://localhost:17011/vision-mind-rfdetr`。模型资源位于
`resource/rfdetr/model/`；首次使用运行 `./scripts/init-rfdetr.ps1`，随后运行
`./scripts/start-rfdetr.ps1`。

图片检测统一接口为 `POST /api/v1/vision/detect`，并保留 YOLO 兼容别名
`/api/v1/img/detect`；还提供标注预览、文件上传和视频抽帧检测。类别编号遵循官方
COCO 稀疏编号（例如 person=1、car=3、toothbrush=90）。详细说明见
[RF-DETR 模块文档](docs/modules/rfdetr.md)。

## Roadmap

- 继续将模型加载迁移到 `ModelRegistry` 与 `OnnxSessionFactory`
- 完善所有图像/音频域统一输入适配（upload / URL / base64）
- 新旧接口并行至少一个版本周期后再评估收敛
- 增补模块级兼容测试与 CI 覆盖
