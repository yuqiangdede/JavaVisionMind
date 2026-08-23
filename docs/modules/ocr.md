# OCR Module

- App: `vision-mind-ocr-app`
- Domain: `ocr`

## Unified API

- Detect: `/api/v1/ocr/infer`
- Preview: `/api/v1/ocr/preview`
- LLM refine: `/api/v1/ocr/infer/llm`
- LLM preview: `/api/v1/ocr/preview/llm`

## 配置

OCR app 只读取 `vision-mind-ocr-app/src/main/resources/application.yml`。
模型使用 `vision-mind.ocr.models.det`、`vision-mind.ocr.models.rec` 等键，字典使用
`vision-mind.ocr.dict-path`，OpenCV 路径使用 `vision-mind.native.dll-path` 和
`vision-mind.native.so-path`。旧的 `application.properties` 和
`ocr-core.properties` 配置不再支持。
