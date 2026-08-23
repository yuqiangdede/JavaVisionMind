# FFE Module

- App: `vision-mind-ffe-app`
- Domain: `face`

## Unified API

- Infer: `/api/v1/face/infer`
- Store: `/api/v1/face/store`
- Search: `/api/v1/face/search`
- Preview: `/api/v1/face/preview`
- Index delete: `/api/v1/face/index/delete`

## 配置

FFE app 只读取 `vision-mind-ffe-app/src/main/resources/application.yml`。
模型使用 `vision-mind.ffe.models.*`，向量库和 Elasticsearch 使用
`vision-mind.ffe.vector-store.*`，OpenCV 使用 `vision-mind.native.*`。旧的
`application.properties` 和 `native-defaults.properties` 配置不再读取。
