# 缺失资源排查

## 现象

- 启动时报错：`RESOURCE_MISSING`
- 日志中出现缺失路径列表

## 原因

- 未放置 `resource/` 目录
- 或 `VISION_MIND_PATH` 未配置 / 配置错误
- 或 `resource/manifest.json` 与实际模型文件不一致

## 处理步骤

1. 执行环境检查：
   - Windows: `powershell -ExecutionPolicy Bypass -File scripts/verify-env.ps1`
   - Linux/macOS: `bash scripts/verify-env.sh`
2. 检查资源根目录解析顺序：
   - `./resource`
   - `VISION_MIND_PATH`
3. 对照 [`resource/manifest.json`](../../resource/manifest.json) 补齐 `required` 资源。

## 备注

- `optional` 资源缺失不会阻止启动，但会影响部分接口能力。
