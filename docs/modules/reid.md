# ReID Module

- App: `vision-mind-reid-app`
- Domain: `reid`

## Unified API

- Infer single: `/api/v1/reid/infer/single`
- Infer multi: `/api/v1/reid/infer/multi`
- Store: `/api/v1/reid/store`
- Search: `/api/v1/reid/search/image`
- Index flow: `/api/v1/reid/index/search-or-store`

## 配置

ReID app 只读取 `vision-mind-reid-app/src/main/resources/application.yml`。
模型使用 `vision-mind.reid.model.onnx`，向量库和 Elasticsearch 使用
`vision-mind.reid.vector-store.*`，投影矩阵使用 `vision-mind.common.matrix-path`。
旧的 `application.properties` 和旧顶层配置键不再读取。
