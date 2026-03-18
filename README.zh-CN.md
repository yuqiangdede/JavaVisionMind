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

## Roadmap

- 继续将模型加载迁移到 `ModelRegistry` 与 `OnnxSessionFactory`
- 完善所有图像/音频域统一输入适配（upload / URL / base64）
- 新旧接口并行至少一个版本周期后再评估收敛
- 增补模块级兼容测试与 CI 覆盖
