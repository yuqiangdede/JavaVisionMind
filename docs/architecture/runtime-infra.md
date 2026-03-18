# Runtime Infrastructure

## Native Library

- 统一使用 `NativeLibraryManager` 加载 OpenCV。
- 支持 `vision-mind.native-load.skip-open-cv=true` 跳过 native 加载（测试/CI 推荐）。

## ONNX Session

- 统一使用 `OnnxSessionFactory` 创建 `OrtSession`。
- 统一入口管理 provider 与线程参数。

## Model Registry

- `ModelDescriptor` 定义模型元数据（名称、路径、是否必需）。
- `ModelRegistry` 统一注册与必需模型校验。

## Resource Validation

- 读取 `resource/manifest.json`。
- 启动时按模块校验 `required` 资源，并输出缺失文件列表。
