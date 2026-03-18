# Architecture Overview

JavaVisionMind 是一个基于 Java + Spring Boot + ONNX Runtime + OpenCV 的多模态推理服务工具箱。

## Core Layers

- API Layer: 各 `*-app` 模块，负责 HTTP 接口与输入输出编排。
- Platform Layer: `vision-mind-platform-common` + `vision-mind-starter-web`，提供统一响应、异常、traceId、日志、OpenAPI、健康检查、资源校验。
- Capability Layer: `yolo-core`、`ocr-core` 等核心推理实现。
- Resource Layer: `resource/` 下的模型、词典、native 库与运行时 jar。

## Engineering Principles

- 保留原有能力模块，优先兼容旧接口。
- 新接口统一到 `/api/v1/{domain}/...` 与 `/preview` 风格。
- 启动前资源校验，缺失项可定位到具体文件。
