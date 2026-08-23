# Quick Start

## 1) 环境校验

```bash
# Linux/macOS
bash scripts/verify-env.sh

# Windows PowerShell
powershell -ExecutionPolicy Bypass -File scripts/verify-env.ps1
```

## 2) 构建

```bash
mvn -DskipTests clean package
```

## 3) 启动重点模块

```bash
mvn -pl vision-mind-yolo-app spring-boot:run
mvn -pl vision-mind-asr-app spring-boot:run
```

## 4) 最小连通验证

```bash
curl http://localhost:17001/vision-mind-yolo/api/v1/health
curl http://localhost:17008/vision-mind-asr/api/v1/health
```

## 5) 配置文件

每个可启动模块只读取自身 `src/main/resources/application.yml`。配置统一放在
`vision-mind` 命名空间下，并使用小写 kebab-case；例如 YOLO 深度配置使用
`vision-mind.yolo.depth.model`、`vision-mind.yolo.depth.max-pixels`，OCR 模型使用
`vision-mind.ocr.models.*`，LLM 使用 `vision-mind.llm.openai.*` 或
`vision-mind.llm.ollama.*`。

core/app 的 Maven 模块拆分和启动命令不变。旧的业务 `.properties` 文件及旧顶层
配置键不再支持。`VISION_MIND_PATH` 仍用于指定资源根目录，深度服务的
`VISION_MIND_YOLO_DEPTH_*` 变量仍用于运行时覆盖安全限制。
