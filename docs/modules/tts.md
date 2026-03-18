# TTS 模块

- 模块名：`vision-mind-tts-app`
- 状态：`beta`
- 领域：文本转语音（Text To Speech）

## 接口分组

- 旧接口：`/vision-mind-tts/tts/*`
- 新接口（并行）：`/vision-mind-tts/api/v1/audio/tts/*`

## 统一接口

- `POST /api/v1/audio/tts/infer`
- `GET /api/v1/audio/tts/runtime/health`
- `POST /api/v1/audio/tts/index/refresh`

## 输入模式

- 文本参数：`text`
- 运行参数：语速、音色等（兼容旧参数名）

## 资源依赖

- 参考 [`resource/manifest.json`](../../resource/manifest.json) 的 `tts` 节点
- sherpa runtime 缺失时，启动阶段会给出明确缺失路径
