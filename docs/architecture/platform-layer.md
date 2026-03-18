# Platform Layer

平台层由两个模块组成：

- `vision-mind-platform-common`
- `vision-mind-starter-web`

## Unified Capabilities

- `HttpResult<T>` 与错误码规范
- 全局异常处理
- traceId 注入（`X-Trace-Id`）
- 请求日志过滤器
- 统一健康检查 `/api/v1/health`
- OpenAPI 默认配置
- 统一配置前缀 `vision-mind.*`
- 统一资源路径解析与校验
- 统一 NativeLibraryManager / OnnxSessionFactory / ModelRegistry
- 通用 `ImageSource` / `ImageLoader`

## Startup Behavior

`vision-mind.resource.validate-on-startup=true` 时会读取 `resource/manifest.json`，按 `spring.application.name` 校验资源。
